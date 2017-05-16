package pathfinder.pathfinding

import pathfinder.Globals._

import scala.collection.parallel.mutable.ParArray
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by dda on 19.04.17.
  *
  * todo: Doesn't work with concave polygons or polygons with intersecting bounding boxes, but no checks performed
  *
  */
object Patcher {
  def findIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Point = {
    // https://wikimedia.org/api/rest_v1/media/math/render/svg/c51a9b486a6ef5a7a08b92d75e71a07888034a9a
    val x1x2 = p1.x - p2.x
    val x3x4 = p3.x - p4.x
    val y1y2 = p1.y - p2.y
    val y3y4 = p3.y - p4.y
    val x1y2y1x2 = p1.x * p2.y - p1.y * p2.x
    val x3y4y3x4 = p3.x * p4.y - p3.y * p4.x
    val denominator = x1x2 * y3y4 - y1y2 * x3x4
    Point((x1y2y1x2 * y3y4 - y1y2 * x3y4y3x4) / denominator, (x1y2y1x2 * x3x4 - x1x2 * x3y4y3x4) / denominator)
  }

  final case class Polygon(points: List[Point]) {
    val centroid: Point = if (points.size == 1) points.head else {
      val (sumcx, sumcy, suma) = (points :+ points.head).sliding(2)
        .foldLeft((0.0, 0.0, 0.0)) {
          case ((cx, cy, a), i :: j :: Nil) =>
            val m = i.x * j.y - j.x * i.y
            (cx + (i.x + j.x) * m, cy + (i.y + j.y) * m, a + m)

          case other =>
            throw new RuntimeException("Illegal list sequence in Patcher: " + other)
        }

      val ma = suma * 0.5
      Point(sumcy / (6.0 * ma), sumcx / (6.0 * ma))
    }

    def shrink(amount: Double): Polygon = {
      val newPoints = points.map { case Point(y, x) =>
        val yMod = Math.abs(centroid.y - y) * amount / 100
        val xMod = Math.abs(centroid.x - x) * amount / 100
        val newY = if (y < centroid.y) y + yMod else y - yMod
        val newX = if (x < centroid.x) x + xMod else x - xMod
        Point(newY, newX)
      }
      Polygon(newPoints)
    }

    def isIncludes(another: Polygon): Boolean = {
      another.points.exists { point =>
        var isInside = false
        var isOnEdge = false

        (points.last :: points).sliding(2).foreach {
          case a :: b :: Nil =>
            if ((a.y > point.y) != (b.y > point.y) &&
              (point.x - a.x) < (b.x - a.x) * (point.y - a.y) / (b.y - a.y)) isInside = !isInside

            isOnEdge = isOnEdge || distance(a, point) + distance(point, b) - distance(a, b) < PATHFINDING_EPS

          case other =>
            throw new RuntimeException("Illegal list sequence in Patcher: " + other)
        }

        !isOnEdge && isInside
      }
    }

    def isIntersectsEdges(another: Polygon): Boolean = {
      (points.last :: points).sliding(2).exists {
        case a1 :: a2 :: Nil =>
          (another.points.last :: another.points).sliding(2).exists {
            case b1 :: b2 :: Nil =>
              val ip = findIntersection(a1, a2, b1, b2)
              Math.abs(distance(a1, ip) + distance(ip, a2) - distance(a1, a2)) <= PATHFINDING_EPS &&
                Math.abs(distance(b1, ip) + distance(ip, b2) - distance(b1, b2)) <= PATHFINDING_EPS

            case other =>
              throw new RuntimeException("Illegal list sequence in Patcher: " + other)
          }

        case other =>
          throw new RuntimeException("Illegal list sequence in Patcher: " + other)
      }
    }

    def isIntersects(another: Polygon): Boolean = {
      val shrinked = another.shrink(30)
      val includes = isIncludes(shrinked)
      val intersects = isIntersectsEdges(shrinked)
      includes || intersects
    }

    def getBoundingBox: (Point, Point) = {
      val xs = points.map(_.x)
      val ys = points.map(_.y)
      (Point(ys.min, xs.min), Point(ys.max, xs.max))
    }

    def getBoundingBoxPoints: List[Point] = {
      val (min, max) = getBoundingBox
      List(min, Point(min.y, max.x), max, Point(max.y, min.x))
    }
  }

  private def isInsideObstacleBoundingBoxIncludingEdges(point: Point)(implicit obstacles: ParArray[Polygon]): Boolean = {
    obstacles.exists { polygon =>
      val (min, max) = polygon.getBoundingBox
      min.y <= point.y && point.y <= max.y &&
        min.x <= point.x && point.x <= max.x
    }
  }

  private def isIntersectsObstacleBoundingBox(a: Point, b: Point)(implicit obstacles: ParArray[Polygon]): Boolean = {
    obstacles.exists { polygon =>
      val points = polygon.getBoundingBoxPoints
      (points.last :: points).sliding(2).exists {
        case v1 :: v2 :: Nil =>
          val ip = findIntersection(a, b, v1, v2)
          Math.abs(distance(v1, ip) + distance(ip, v2) - distance(v1, v2)) <= PATHFINDING_EPS &&
            Math.abs(distance(a, ip) + distance(ip, b) - distance(a, b)) <= PATHFINDING_EPS

        case other =>
          throw new RuntimeException("Illegal list sequence in Patcher: " + other)
      }
    }
  }

  private def checkCandidate(candidate: Polygon)(implicit obstacles: ParArray[Polygon]): Boolean = {
    val shrinked = candidate.shrink(5)
    !(shrinked.points.last :: shrinked.points).sliding(2).exists {
      case a :: b :: Nil =>
        val isInside = isInsideObstacleBoundingBoxIncludingEdges(a)
        val isIntersects = isIntersectsObstacleBoundingBox(a, b)
        isInside || isIntersects

      case other =>
        throw new RuntimeException("Illegal list sequence in Patcher: " + other)
    }
  }

  def mapObstaclesToPatches(dims: Point, rawObstacles: List[Polygon]): Future[List[MapPatch]] = {
    if (rawObstacles.exists(obstacle => obstacle.points.length < 3))
      throw new IllegalArgumentException("Obstacle with less than 3 sides")

    case class OrthogonalLine(coordinate: Double, isHorizontal: Boolean)

    Future {
      rawObstacles.toArray.par
    } flatMap { parrallelObstacles =>
      implicit val obstacles = parrallelObstacles

      /*                                                                                  *\
       ------------------------------  ORTHOGONAL ------------------------------
      \*                                                                                  */

      def calculateOrthogonalPatches(): Future[List[MapPatch]] = Future {
        val (orthogonalHorizontalLines, orthogonalVerticalLines) = (
          // box lines
          List(OrthogonalLine(dims.y, isHorizontal = true), OrthogonalLine(0, isHorizontal = true),
            OrthogonalLine(dims.x, isHorizontal = false), OrthogonalLine(0, isHorizontal = false))

            :::

            // lines started from obstacles corners
            obstacles.flatMap { a =>
              a.points.flatMap { case Point(y, x) =>
                List(OrthogonalLine(y, isHorizontal = true), OrthogonalLine(x, isHorizontal = false))
              }
            }.toList
          )
          .distinct
          .sortBy(_.coordinate)
          .partition(_.isHorizontal)

        orthogonalHorizontalLines.sliding(2).flatMap {
          case horA :: horB :: Nil =>
            orthogonalVerticalLines.sliding(2).flatMap {
              case verA :: verB :: Nil =>
                val points = Polygon(List(Point(horA.coordinate, verA.coordinate), Point(horA.coordinate, verB.coordinate),
                  Point(horB.coordinate, verB.coordinate), Point(horB.coordinate, verA.coordinate)))
                if (checkCandidate(points)) Some(MapPatch(-1, points.points, points.centroid)) else None

              case other =>
                throw new RuntimeException("Illegal list sequence in Patcher: " + other)
            }

          case other =>
            throw new RuntimeException("Illegal list sequence in Patcher: " + other)
        }.toList
      }

      /*                                                                                   *\
           ------------------------------  NON-ORTHOGONAL ------------------------------
      \*                                                                                   */

      def calculateNonOrthogonalPatches(): Future[List[MapPatch]] = Future(obstacles.flatMap { obstacle =>
        val (min, max) = obstacle.getBoundingBox
        (obstacle.points.last :: obstacle.points).sliding(2).flatMap {
          case a :: b :: Nil =>
            if ((a.y != b.y) && (a.x != b.x)) {
              val verA = OrthogonalLine(min.x, isHorizontal = false)
              val verB = OrthogonalLine(max.x, isHorizontal = false)
              val horA = OrthogonalLine(Math.min(a.y, b.y), isHorizontal = true)
              val horB = OrthogonalLine(Math.max(a.y, b.y), isHorizontal = true)

              val lower = List(a, b).maxBy(_.y)
              val upper = List(a, b).minBy(_.y)

              val m = (a.y - b.y) / (a.x - b.x)

              val candidates: List[Polygon] = {
                if (m < 0) {
                  if (Math.abs(m) <= 1) {
                    List(
                      {
                        //      B-------C horA
                        //     X|       |
                        //    X |       |
                        //   X  |       |
                        //  X   |       |
                        // A----+-------D horB
                        //
                        // ^            ^
                        // verA         verB

                        val pointA = lower
                        val pointB = upper
                        val pointC = Point(horA.coordinate, verB.coordinate)
                        val pointD = Point(horB.coordinate, verB.coordinate)
                        if (pointB == pointC) {
                          Polygon(List(pointA, pointB, pointD))
                        } else {
                          Polygon(List(pointA, pointB, pointC, pointD))
                        }
                      }, {
                        // 	    verA        verB
                        //      v            v
                        //
                        // horA D-------+----A
                        //      |       |   X
                        //      |       |  X
                        //      |       | X
                        //      |       |X
                        // horB C-------B

                        val pointA = upper
                        val pointB = lower
                        val pointC = Point(horB.coordinate, verA.coordinate)
                        val pointD = Point(horA.coordinate, verA.coordinate)
                        if (pointB == pointC) {
                          Polygon(List(pointD, pointB, pointA))
                        } else {
                          Polygon(List(pointD, pointC, pointB, pointA))
                        }
                      }
                    )
                  } else {
                    List(
                      {
                        //      B horA
                        //     X|
                        //    X |
                        //   X  |
                        //  X   |
                        // A----C
                        // |    |
                        // |    |
                        // |    |
                        //  ----  horB
                        //
                        // ^    ^
                        // verA verB


                        val pointA = a
                        val pointB = b
                        val pointC = Point(pointA.y, pointB.x)
                        Polygon(List(pointA, pointB, pointC))
                      }, {
                        //      verA verB
                        //      v    v
                        //
                        // horA  ----
                        //      |    |
                        //      |    |
                        //      |    |
                        //      C----A
                        //      |   X
                        //      |  X
                        //      | X
                        //      |X
                        // horB B


                        val pointA = b
                        val pointB = a
                        val pointC = Point(pointA.y, pointB.x)
                        Polygon(List(pointA, pointB, pointC))
                      }
                    )
                  }
                } else {
                  if (Math.abs(m) < 1) {
                    List(
                      {
                        // verA         verB
                        // v            v
                        //
                        // A----+-------D horA
                        //  X   |       |
                        //   X  |       |
                        //    X |       |
                        //     X|       |
                        //      B-------C horB
                        val pointA = upper
                        val pointB = lower
                        val pointC = Point(horB.coordinate, verB.coordinate)
                        val pointD = Point(horA.coordinate, verB.coordinate)
                        if (pointB == pointC) {
                          Polygon(List(pointD, pointB, pointA))
                        } else {
                          Polygon(List(pointD, pointC, pointB, pointA))
                        }
                      }, {
                        // horA C-------B
                        //      |       |X
                        //      |       | X
                        //      |       |  X
                        //      |       |   X
                        // horB D-------+----A
                        //
                        //      ^            ^
                        //      verA         verB

                        val pointA = lower
                        val pointB = upper
                        val pointC = Point(horA.coordinate, verA.coordinate)
                        val pointD = Point(horB.coordinate, verA.coordinate)
                        if (pointB == pointC) {
                          Polygon(List(pointD, pointB, pointA))
                        } else {
                          Polygon(List(pointD, pointC, pointB, pointA))
                        }
                      }
                    )
                  } else {
                    List(
                      {
                        // horA A
                        //      |X
                        //      | X
                        //      |  X
                        //      |   X
                        //      C----B
                        //      |    |
                        //      |    |
                        //      |    |
                        // horB  ----
                        //
                        //      ^    ^
                        //      verA verB


                        val pointA = b
                        val pointB = a
                        val pointC = Point(pointB.y, pointA.x)
                        Polygon(List(pointA, pointB, pointC))
                      }, {
                        // verA verB
                        // v    v
                        //
                        //  ----  horA
                        // |    |
                        // |    |
                        // |    |
                        // B----C
                        //  X   |
                        //   X  |
                        //    X |
                        //     X|
                        //      A horB

                        val pointA = a
                        val pointB = b
                        val pointC = Point(pointB.y, pointA.x)
                        Polygon(List(pointA, pointB, pointC))
                      }
                    )
                  }
                }
              }

              candidates.find { candidate =>
                !obstacle.isIntersects(candidate)
              }
            } else {
              None
            }

          case other =>
            throw new RuntimeException("Illegal list sequence in Patcher: " + other)
        }
      }.map(polygon => MapPatch(-1, polygon.points, polygon.centroid)).toList)

      Future.sequence(if (ENABLE_NONORTHO_PATCHES) {
        List(calculateOrthogonalPatches(), calculateNonOrthogonalPatches())
      } else {
        List(calculateOrthogonalPatches())
      })
        .map { patches: List[List[MapPatch]] =>
          patches
            .flatten
            .distinct
            .zipWithIndex
            .map { case (patch, idx) => patch.id = idx; patch }.toArray
        }
        .map { nodes =>
          def twoObstaclesHaveSharedPoints(a: MapPatch, b: MapPatch): Boolean = {
            (a.coordinates.last :: a.coordinates).sliding(2).exists {
              case a1 :: a2 :: Nil =>
                (b.coordinates.last :: b.coordinates).sliding(2).exists {
                  case b1 :: b2 :: Nil =>
                    val a1a2 = distance(a1, a2)
                    val b1b2 = distance(b1, b2)
                    val b1a1 = distance(b1, a1)
                    val b1a2 = distance(b1, a2)
                    val a1b2 = distance(a1, b2)
                    val a2b2 = distance(a2, b2)
                    List(
                      Math.abs(b1a1 + a1b2 - b1b2) <= PATHFINDING_EPS,
                      Math.abs(b1a2 + a2b2 - b1b2) <= PATHFINDING_EPS,
                      Math.abs(b1a1 + b1a2 - a1a2) <= PATHFINDING_EPS,
                      Math.abs(a1b2 + a2b2 - a1a2) <= PATHFINDING_EPS
                    ).count(a => a) > 2

                  case other =>
                    throw new RuntimeException("Illegal list sequence in Patcher: " + other)
                }

              case other =>
                throw new RuntimeException("Illegal list sequence in Patcher: " + other)
            }
          }

          nodes.indices.foreach { a =>
            ((a + 1) until nodes.length).foreach { b =>
              if (twoObstaclesHaveSharedPoints(nodes(a), nodes(b))) {
                nodes(a).addExit(nodes(b))
                nodes(b).addExit(nodes(a))
              }
            }
          }

          nodes.toList
        }
    }
  }

  def preparePatches(dims: Point, obstacles: List[Obstacle]): Future[List[MapPatch]] = {
    val polygons: List[Polygon] = InputMapper.mapObstaclesToPolygons(obstacles)
    mapObstaclesToPatches(dims, polygons)
  }
}
