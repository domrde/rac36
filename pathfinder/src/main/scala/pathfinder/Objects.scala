package pathfinder

final case class Example(p: Point, c: Double)
final case class Point(y: Double, x: Double)
final case class Path(path: List[Point])

final case class Polygon(points: List[Point]) {
  import pathfinder.Globals._

  val lengthPerPoint = 20.0

  val centroid: Point = if (points.size == 1) points.head else {
    val (sumcx, sumcy, suma) = (points :+ points.head).sliding(2)
      .foldLeft((0.0, 0.0, 0.0)) { case ((cx, cy, a), i :: j :: Nil) =>
        val m = i.x * j.y - j.x * i.y
        (cx + (i.x + j.x) * m, cy + (i.y + j.y) * m, a + m)
      }

    val ma = suma * 0.5
    Point(sumcy / (6.0 * ma), sumcx / (6.0 * ma))
  }

  def isInside(point: Point): Boolean = {
    var isInside = false
    var isOnEdge = false

    (points.last :: points).sliding(2).foreach { case a :: b :: Nil =>

      if ((a.y > point.y) != (b.y > point.y) &&
        (point.x - a.x) < (b.x - a.x) * (point.y - a.y) / (b.y - a.y)) isInside = !isInside

      isOnEdge = isOnEdge || distance(a, point) + distance(point, b) - distance(a, b) < 0.005
    }

    isOnEdge || isInside
  }

  def extractPointsByEdges(): List[Point] = if (points.size == 1) points else {
    (points.last ::  points).sliding(2).flatMap { case i :: j :: Nil =>
      val length = distance(i, j)
      val pointsNumber = length / lengthPerPoint
      (0 until Math.ceil(pointsNumber).toInt).map { num =>
        val m = num / pointsNumber
        Point((1 - m) * i.y + m * j.y, (1 - m) * i.x + m * j.x)
      }
    }.toList
  }
}
