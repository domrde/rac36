package pathfinder

import scala.language.implicitConversions

import com.dda.brain.BrainMessages.Position
import com.dda.brain.PathfinderBrain.PathPoint

/**
  * Created by dda on 01.05.17.
  */
object Globals {
  val ANGLE_OF_SEARCH = 70.0

  val STEP_OF_PATH = 0.35

  implicit def obstacleToPoint(a: Obstacle): Point = Point(a.y, a.x)
  implicit def pathPointToPoint(a: PathPoint): Point = Point(a.y, a.x)
  implicit def positionToPoint(a: Position): Point = Point(a.y, a.x)
  implicit def pointToPathPoint(a: Point): PathPoint = PathPoint(a.y, a.x)

  def distance(p1: Point, p2: Point): Double = Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))

  def pointInBetween(a: Point, b: Point)(t: Double): Point = Point((1.0 - t) * a.y + t * b.y, (1.0 - t) * a.x + t * b.x)

  def getPivotPoints(start: Point, finish: Point, pointOfInterest: Point, l: Double): (Point, Point) = {
    def solve(a: Double, b: Double, c: Double): (Double, Double) = {
      val d = Math.pow(b, 2.0) - 4 * a * c
      ((-b + Math.sqrt(d)) / 2.0 / a, (-b - Math.sqrt(d)) / 2.0 / a)
    }

    def calculateNeighbourPoints(point: Point, m: Double, b: Double): (Point, Point) = {
      val (first, second) = solve(
        1 + Math.pow(m, 2),
        2 * m * (b - point.y) - 2 * point.x,
        Math.pow(point.x, 2) + Math.pow(b - point.y, 2) - Math.pow(l, 2)
      )

      (Point(m * first + b, first), Point(m * second + b, second))
    }

    val m = (finish.y - start.y + 1e-6) / (finish.x - start.x + 1e-6)
    val mp = -1 / m

    calculateNeighbourPoints(pointOfInterest, mp, pointOfInterest.y - mp * pointOfInterest.x)
  }

  final case class Obstacle(y: Double, x: Double, r: Double)

  final case class Example(y: Double, x: Double, c: Double, r: Double = 0.2)

  final case class Point(y: Double, x: Double)

  case class SvmResult(path: List[Point])

  final case class MapPatch(var id: Int, coordinates: List[Point], centroid: Point, var exits: Set[Int] = Set.empty) {
    def addExit(node: MapPatch): Unit = exits = exits + node.id
  }

}
