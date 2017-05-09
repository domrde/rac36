package com.dda.brain

import com.dda.brain.PathfinderBrain.{PathFound, PathPoint, Request}
import upickle.default._

import scala.annotation.tailrec
import scala.collection.immutable.{::, Nil}
import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 9/27/16.
  */
class CarBrain(id: String) extends BrainActor(id) {
  import com.dda.brain.BrainMessages._

  val pathDelta = 1.0
  var previousCommand = ""
  var path: List[PathPoint] = List.empty

  @tailrec
  private def clipPath(curPos: Position, originalPath: List[PathPoint], modifiable: List[PathPoint]): List[PathPoint] = {
    modifiable match {
      case Nil => originalPath
      case head :: tail => if (distance(curPos, head) < pathDelta) tail else clipPath(curPos, originalPath, tail)
    }
  }

  private def getCommandToRobot(curPos: Position): String = {
    if (path.nonEmpty) {
      val nextStep = path.head
      val dist = distance(curPos, nextStep)
      if (dist < pathDelta) {
        path = path.tail
        getCommandToRobot(curPos)
      } else {
        val angleToPoint = Math.atan2(nextStep.y - curPos.y, nextStep.x - curPos.x) * 180.0 / Math.PI
        if (Math.abs(curPos.angle - angleToPoint) > 30) {
          "rotate=" + angleToPoint
        } else {
          "forward"
        }
      }
    } else {
      log.info("Path is empty")
      "stop"
    }
  }

  override protected def handleSensory(payload: Set[Position]): Unit = {
    payload.find { case Position(_id, _, _, _, _) => id == _id }.foreach { curPos =>
      avatar ! TellToOtherAvatar("pathfinder", write(Request(PathPoint(5.0, 5.0))))
      path = clipPath(curPos, path, path)
      val newCommand = getCommandToRobot(curPos)
      if (newCommand != previousCommand) {
        avatar ! FromAvatarToRobot(newCommand)
        previousCommand = newCommand
      }
    }
  }

  def distance(p1: Position, p2: PathPoint): Double = {
    Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    if (from == "pathfinder") {
      Try {
        read[PathFound](message)
      } match {
        case Success(value) =>
          path = value.path.tail

        case Failure(exception) =>
      }
    }
  }

  override protected def handleRobotMessage(message: String): Unit = {
  }

}
