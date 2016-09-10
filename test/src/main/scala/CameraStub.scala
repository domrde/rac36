package test

import CameraStub.{MoveRobot, ObstaclePosition, RobotPosition}
import akka.actor.{Actor, ActorLogging}

import scala.language.postfixOps

/**
  * Created by dda on 9/10/16.
  */
object CameraStub {
  case class RobotPosition(name: String, row: Int, col: Int, angle: Int)
  case class ObstaclePosition(row: Int, col: Int)

  case class MoveRobot(name: String, rowInc: Int, colInc: Int, angleInc: Int)
}

class CameraStub(initialMap: String) extends Actor with ActorLogging {

//  -----#---1--
//  ------------
//  --#------2--
//  ------------
//  -----###----

  override def receive: Receive = receiveWithMap(extractRobots(initialMap), extractObstacles(initialMap))

  def extractRobots(map: String): Map[String, RobotPosition] = {
    val lines = initialMap.split("\n")
    (0 to lines.length) flatMap { row: Int =>
      (0 to lines.head.length) flatMap { col: Int =>
        lines(row)(col) match {
          case '1' => Some("1" -> RobotPosition("1", row, col, 0))
          case '2' => Some("2" -> RobotPosition("2", row, col, 0))
          case '3' => Some("3" -> RobotPosition("3", row, col, 0))
          case _ => None
        }
      }
    } toMap
  }

  def extractObstacles(map: String): IndexedSeq[ObstaclePosition] = {
    val lines = initialMap.split("\n")
    (0 to lines.length) flatMap { row: Int =>
      (0 to lines.head.length) flatMap { col: Int =>
        lines(row)(col) match {
          case '#' => Some(ObstaclePosition(row, col))
          case _ => None
        }
      }
    }
  }

  def receiveWithMap(robots: Map[String, RobotPosition], obstacles: IndexedSeq[ObstaclePosition]): Receive = {
    case MoveRobot(name, rowInc, colInc, angleInc) =>
      val currentPosition = robots(name)
      val newRobots = robots + (name -> RobotPosition(
        name,
        currentPosition.row + rowInc,
        currentPosition.col + colInc,
        currentPosition.angle + angleInc
      ))
      context.become(receiveWithMap(newRobots, obstacles))

    case _ =>

  }

}
