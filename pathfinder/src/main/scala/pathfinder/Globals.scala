package pathfinder

/**
  * Created by dda on 01.05.17.
  */
object Globals {
  def distance(p1: Point, p2: Point): Double = Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))

  val STEP_OF_NOISE_GRID = 50.0
  val ROBOT_SIZE = 2.0
  val STEP_OF_PATH = 10.0
}
