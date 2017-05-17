package com.dda.brain

import com.dda.brain.PathfinderBrain.{PathFound, PathPoint, Request}
import upickle.default._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 9/27/16.
  */
class CarBrain(id: String) extends BrainActor(id) {
  import com.dda.brain.BrainMessages._

  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  context.system.scheduler.schedule(10.second, 10.second) {
    avatar ! TellToOtherAvatar("pathfinder", write(Request(target)))
  }

  val target = PathPoint(5.0, 5.0)
  val pathDelta = 1.0
  var previousCommand = ""
  var path: PathFound = PathFound(id, List.empty, isStraightLine = true)

  def distance(p1: Position, p2: PathPoint): Double = {
    Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
  }

  override protected def handleSensory(payload: Set[Position]): Unit = {
    payload.find { case Position(_id, _, _, _, _) => id == _id }.foreach { curPos =>
      val newCommand =
        if (distance(curPos, target) > pathDelta && path.path.nonEmpty) {
          if (distance(curPos, path.path.head) < pathDelta) {
            path = path.copy(path = path.path.tail)
          }
          if (path.path.nonEmpty) {
            val nextStep = path.path.head
              "move=" + nextStep.y + "," + nextStep.x + ""
          } else {
            "stop"
          }
        } else {
          "stop"
        }

      if (newCommand != previousCommand) {
        log.info("{} {}, {} != {}", id, newCommand, path.path.headOption, curPos)
        avatar ! FromAvatarToRobot(newCommand)
        previousCommand = newCommand
      }
    }
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    if (from == "pathfinder") {
      Try {
        read[PathFound](message)
      } match {
        case Success(value) =>
          val newPathIsBadAndOldPathIsGood = value.isStraightLine && !path.isStraightLine
          if (!newPathIsBadAndOldPathIsGood) {
            path = value
          }

        case Failure(exception) =>
      }
    }
  }

  override protected def handleRobotMessage(message: String): Unit = {
  }

}
