package pathfinder

import pathfinder.Globals._
import pathfinder.pathfinding.Pathfinder

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Random, Success, Try}

/**
  * Created by dda on 28.05.17.
  */
object Test extends App {

  def randomPoint(dims: Point): Point = {
    Point(
      Random.nextInt(dims.y.toInt * 10).toDouble / 10.0,
      Random.nextInt(dims.x.toInt * 10).toDouble / 10.0
    )
  }

  def randomStartPoints(dims: Point, obstacles: List[Obstacle]): (Point, Point) = {
    def closeToObstacle(point: Point): Boolean = {
      obstacles.exists(obs => distance(obs, point) < 1.0)
    }

    var start = randomPoint(dims)
    while (closeToObstacle(start)) {
      start = randomPoint(dims)
    }

    var finish = randomPoint(dims)
    while (closeToObstacle(finish) || distance(start, finish) < 4.0) {
      finish = randomPoint(dims)
    }

    (start, finish)
  }

  def time[R](block: => R): (R, Double) = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    val timeInMicroseconds = Math.round((t1 - t0) / 1000.0)
    (result, timeInMicroseconds)
  }

  List(
    Point(10.0,  10.0)
//    Point(12.5,  16.0),
//    Point(20.0,  15.0),
//    Point(20.0,  20.0),
//    Point(20.0,  25.0),
//    Point(20.0,  30.0),
//    Point(20.0,  35.0),
//    Point(25.0,  32.0),
//    Point(30.0,  30.0),
//    Point(25.0,  40.0)
  ).foreach { dims =>

    print(s"$dims -> ${dims.y * dims.x} cells. ")

    // Warm-up
    (1 to 1000).foreach { _ =>
      val obstacles = (1 to 6).map(_ => randomPoint(dims)).map {case Point(y, x) => Obstacle(y, x, 0.15) }.toList

      val (start, finish) = randomStartPoints(dims, obstacles)

      Try {
        Pathfinder.findPath(dims, start, finish, obstacles)
      }
    }

    // Measurement

    val results: List[Double] =
      Stream.from(1).flatMap { _ =>
        val obstacles = (1 to 6).map(_ => randomPoint(dims)).map {case Point(y, x) => Obstacle(y, x, 0.15) }.toList

        val (start, finish) = randomStartPoints(dims, obstacles)

        Try {
          time {
            Await.result(Pathfinder.findPath(dims, start, finish, obstacles).future, 1.millis)
          }
        } match {
          case Success((Some(value), time)) =>
            if (value.path.nonEmpty) {
              if (time < 80000.0) {
                Some(time)
              } else {
                None
              }
            } else {
              None
            }

          case _ =>
            None
        }

      }.take(100).toList

    println(s"Average: ${results.sum / results.size}, max ${results.max}, min ${results.min}")
  }

  sys.exit(0)
}
