package pathfinder

import scala.language.implicitConversions

import com.dda.brain.BrainMessages.Position
import com.dda.brain.PathfinderBrain.PathPoint

/**
  * Created by dda on 01.05.17.
  */
object Globals {
  val ENABLE_NONORTHO_PATCHES = false

  val STEP_OF_PATH = 0.5

  val PATHFINDING_EPS = 0.01

  implicit def obstacleToPoint(a: Obstacle): Point = Point(a.y, a.x)
  implicit def pathPointToPoint(a: PathPoint): Point = Point(a.y, a.x)
  implicit def positionToPoint(a: Position): Point = Point(a.y, a.x)
  implicit def pointToPathPoint(a: Point): PathPoint = PathPoint(a.y, a.x)

  def distance(p1: Point, p2: Point): Double = Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))

  def pointInBetween(a: Point, b: Point)(t: Double): Point = Point((1.0 - t) * a.y + t * b.y, (1.0 - t) * a.x + t * b.x)

  final case class Example(y: Double, x: Double, c: Double, r: Double = 0.2){
    def intersects(l1: Point, l2: Point): Boolean = {
      val rad = r

      val l1Inside = Math.pow(y - l1.y, 2.0) + Math.pow(x - l1.x, 2.0) <= Math.pow(rad, 2.0)
      val l2Inside = Math.pow(y - l2.y, 2.0) + Math.pow(x - l2.x, 2.0) <= Math.pow(rad, 2.0)

      val a = l1.y - l2.y
      val b = l2.x - l1.x
      val c = (l1.x - l2.x) * l1.y + l1.x * (l2.y - l1.y)
      val value = Math.abs(b * x + a * y + c) / Math.sqrt(b * b + a * a)
      val l1l2Intersects = value <= rad

      l1Inside || l2Inside || l1l2Intersects
    }
  }

  final case class Point(y: Double, x: Double)

  case class RunResults(path: List[Point], message: String)

  final case class MapPatch(var id: Int, coordinates: List[Point], centroid: Point, var exits: Set[Int] = Set.empty) {
    def addExit(node: MapPatch): Unit = exits = exits + node.id
  }

  final case class Obstacle(y: Double, x: Double, r: Double)

}
