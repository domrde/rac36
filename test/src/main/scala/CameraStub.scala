package test

import akka.actor.{Actor, ActorLogging}
import common.SharedMessages.{Position, Sensory}
import test.CameraStub.{GetInfo, MoveRobot}

import scala.language.postfixOps

/**
  * Created by dda on 9/10/16.
  */
object CameraStub {
  case class MoveRobot(name: String, rowInc: Int, colInc: Int, angleInc: Int)
  case object GetInfo
}

class CameraStub(initialMap: String) extends Actor with ActorLogging {

  override def receive: Receive = receiveWithMap(extractPositions(initialMap))

  def extractPositions(map: String): Set[Position] = {
    val lines = initialMap.split("\n")
    (0 until lines.length) flatMap { row: Int =>
      (0 until lines.head.length) flatMap { col: Int =>
        lines(row)(col) match {
          case '1' => Some(Position("1", row, col, 0))
          case '2' => Some(Position("2", row, col, 0))
          case '3' => Some(Position("3", row, col, 0))
          case '#' => Some(Position("obstacle", row, col, 0))
          case _ => None
        }
      }
    } toSet
  }

  def receiveWithMap(positions: Set[Position]): Receive = {
    case MoveRobot(name, rowInc, colInc, angleInc) =>
      positions.find { _.name == name }.foreach { currentPosition =>
        val newPositions = (positions - currentPosition) + Position(
          name,
          currentPosition.row + rowInc,
          currentPosition.col + colInc,
          currentPosition.angle + angleInc
        )
        context.become(receiveWithMap(newPositions))
      }

    case GetInfo =>
      sender() ! Sensory(null, positions)

    case _ =>

  }

}
