package com.dda.brain

import com.dda.brain.BrainMessages.{FromAvatarToRobot, Position}
import common.Constants

import scala.util.Random

/**
  * Created by dda on 9/27/16.
  */
class CarBrain(id: String) extends BrainActor(id) {

  override protected def handleSensory(payload: Set[Position]): Unit = {
    payload.find { case Position(_id, _, _, _, _) => id == _id }.foreach { currentPosition =>
      val obstacleDistanceDelta = 0.1
      val nearestObstacle = payload.find { case Position(name, y, x, _, _) =>
        name == Constants.OBSTACLE_NAME &&
          (Math.abs(y - currentPosition.y) < obstacleDistanceDelta ||
            Math.abs(x - currentPosition.x) < obstacleDistanceDelta)
      }

      val posibilities = List("left", "right", "forward", "forward", "forward", "forward", "forward")
      sender() ! FromAvatarToRobot(posibilities(Random.nextInt(posibilities.length)))
    }
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
//    sender() ! TellToOtherAvatar(from, message)
  }

  override protected def handleRobotMessage(message: String): Unit = {
//    sender() ! FromAvatarToRobot(message)
  }

}
