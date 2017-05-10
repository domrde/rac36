package pathfinder

object Pivots {
  import Learning._

  def getPivotPoints(start: Point, finish: Point, robotSize: Double): List[Point] = {
    val l = Math.sqrt(Math.pow(robotSize, 2) + Math.pow(robotSize, 2))

    def solve(a: Double, b: Double, c: Double): (Double, Double) = {
      val d = Math.pow(b, 2.0) - 4 * a * c
      ((-b + Math.sqrt(d)) / 2.0 / a, (-b - Math.sqrt(d)) / 2.0 / a)
    }

    def calculateNeighbourPoints(point: Point, m: Double, b: Double, inclusive: Boolean = true): List[Point] = {
      val (first, second) = solve(
        1 + Math.pow(m, 2),
        2 * m * (b - point.y) - 2 * point.x,
        Math.pow(point.x, 2) + Math.pow(b - point.y, 2) - Math.pow(l, 2)
      )

      if (inclusive) List(first, point.x, second).map(x => Point(m * x + b, x))
      else List(first, second).map(x => Point(m * x + b, x))
    }

    val m = (finish.y - start.y) / (finish.x - start.x)
    val pointsNearStart = calculateNeighbourPoints(start, m, start.y - m * start.x)
    val pointsNearFinish = calculateNeighbourPoints(finish, m, finish.y - m * finish.x)

    val mp = -1 / m

    val pivotsOfStart = pointsNearStart
      .flatMap(point => calculateNeighbourPoints(point, mp, point.y - mp * point.x, inclusive = false))

    val pivotsOfFinish = pointsNearFinish
      .flatMap(point => calculateNeighbourPoints(point, mp, point.y - mp * point.x, inclusive = false))

    pivotsOfStart ::: pivotsOfFinish
  }
}
