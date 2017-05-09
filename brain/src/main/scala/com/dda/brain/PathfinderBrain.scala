package com.dda.brain

import com.dda.brain.BrainMessages.{FromAvatarToRobot, Position, TellToOtherAvatar}
import upickle.default._

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 9/27/16.
  */
object PathfinderBrain {
  case class PathPoint(y: Double, x: Double)
  case class Request(to: PathPoint)
  case class FindPath(client: String, sensory: Set[Position], to: PathPoint)
  case class PathFound(client: String, path: List[PathPoint])
}

class PathfinderBrain(id: String) extends BrainActor(id) {
  import PathfinderBrain._

  var sensory: Set[Position] = Set.empty

  override protected def handleSensory(payload: Set[Position]): Unit = {
    sensory = payload
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    Try {
      read[Request](message)
    } match {
      case Success(value) =>
        avatar ! FromAvatarToRobot(write(FindPath(from, sensory, value.to)))

      case Failure(exception) =>
    }
  }

  override protected def handleRobotMessage(message: String): Unit = {
    Try {
      read[PathFound](message)
    } match {
      case Success(value) =>
        avatar ! TellToOtherAvatar(value.client, message)

      case Failure(exception) =>
    }
  }

}
