package com.dda.brain

import com.dda.brain.PathfinderBrain.{PathFound, PathPoint, Request}
import upickle.default._

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 9/27/16.
  */
class CarBrain(id: String) extends BrainActor(id) {
  import com.dda.brain.BrainMessages._

  val target = PathPoint(5.0, 5.0)
  val pathDelta = 1.0
  var previousCommand = ""
  var path: PathFound = PathFound(id, List.empty, isStraightLine = true)

  def distance(p1: Position, p2: PathPoint): Double = {
    Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
  }

  // find point nearest to robot and leave it and following points
  private def spanPath(curPos: Position, path: List[PathPoint]): List[PathPoint] = {
    val (behind, forward) = path.span(point => distance(curPos, point) > pathDelta)
    if (forward.isEmpty) behind
    else forward
  }

  private def getCommandToRobot(curPos: Position): String = {
    if (path.path.nonEmpty) {
      val nextStep = path.path.head
      val angleToPoint = Math.atan2(nextStep.y - curPos.y, nextStep.x - curPos.x) * 180.0 / Math.PI
      if (Math.abs(curPos.angle - angleToPoint) > 30) {
        "rotate=" + Math.ceil(angleToPoint)
      } else {
        "forward"
      }
    } else {
      "stop"
    }
  }

  override protected def handleSensory(payload: Set[Position]): Unit = {
    payload.find { case Position(_id, _, _, _, _) => id == _id }.foreach { curPos =>
      if (distance(curPos, target) > pathDelta) {
        avatar ! TellToOtherAvatar("pathfinder", write(Request(target)))
        path = path.copy(path = spanPath(curPos, path.path))
        val newCommand = getCommandToRobot(curPos)
        if (newCommand != previousCommand) {
          log.info("{} {}", id, newCommand)
          avatar ! FromAvatarToRobot(newCommand)
          previousCommand = newCommand
        }
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
