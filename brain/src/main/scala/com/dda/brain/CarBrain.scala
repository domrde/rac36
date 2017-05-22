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

  val target = PathPoint(5.0, 5.0)
  val pathDelta = 0.5
  var previousCommand = ""
  var path: PathFound = PathFound(id, List.empty, isStraightLine = true)
  var curPosGlobal: Option[Position] = None

  def distance(p1: Position, p2: PathPoint): Double = {
    Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
  }

  override protected def handleSensory(payload: Set[Position]): Unit = {
    payload.find { case Position(_id, _, _, _, _) => id == _id }.foreach { curPos =>
      curPosGlobal = Some(curPos)
      val newCommand =
        if (distance(curPos, target) > pathDelta) {
          if (path.path.nonEmpty) {
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
        } else {
          log.info("Destination reached.")
          cancellation.cancel()
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
            path =
              if (curPosGlobal.isDefined) {
                val closestPointIdx = value.path.zipWithIndex.minBy(p => distance(curPosGlobal.get, p._1))._2
                value.copy(path = value.path.drop(closestPointIdx))
              } else {
                value
              }
          }

        case Failure(exception) =>
      }
    }
  }

  override protected def handleRobotMessage(message: String): Unit = {
  }

  private val cancellation = context.system.scheduler.schedule(5.second, 45.second) {
    avatar ! TellToOtherAvatar("pathfinder", write(Request(target)))
  }

}
