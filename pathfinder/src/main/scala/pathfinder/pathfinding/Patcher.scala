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

    def getBoundingBox: (Point, Point) = {
      val xs = points.map(_.x)
      val ys = points.map(_.y)
      (Point(ys.min, xs.min), Point(ys.max, xs.max))
    }
  }

  def mapObstaclesToPatches(dims: Point, rawObstacles: List[Obstacle]): List[MapPatch] = {

    val grisStepY = dims.y / 10.0
    val grisStepX = dims.x / 10.0
    val maxDim = Math.max(grisStepX, grisStepY)
    val hypotenuse = Math.sqrt(Math.pow(maxDim / 2.0, 2.0) + Math.pow(maxDim / 2.0, 2.0))

    def checkCandidate(polygon: Polygon, obstacles: List[Obstacle]): Boolean = {
      !obstacles.exists(obstacle => distance(obstacle, polygon.centroid) <= hypotenuse)
    }

    val patches =
      (0.0 to dims.y by grisStepY).toList.sliding(2).flatMap { case horA :: horB :: Nil =>
        (0.0 to dims.x by grisStepX).toList.sliding(2).flatMap { case verA :: verB :: Nil =>
          val polygon = Polygon(List(Point(horA, verA), Point(horA, verB), Point(horB, verB), Point(horB, verA)))
          if (checkCandidate(polygon, rawObstacles)) Some(MapPatch(-1, polygon.points, polygon.centroid)) else None
        }
      }.zipWithIndex.map { case (patch, idx) => patch.id = idx; patch }.toList

    patches.foreach { a =>
      patches.foreach { b =>
        if (a.centroid != b.centroid && distance(a.centroid, b.centroid) < (maxDim + 0.05)) {
          a.addExit(b)
          b.addExit(a)
        }
      }
    }

    patches
  }

  def preparePatches(dims: Point, obstacles: List[Obstacle]): List[MapPatch] = {
    mapObstaclesToPatches(dims, obstacles)
  }
}
