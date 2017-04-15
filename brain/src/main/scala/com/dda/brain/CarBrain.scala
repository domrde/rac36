package com.dda.brain

import com.dda.brain.BrainMessages.{FromAvatarToRobot, Position, TellToOtherAvatar}

import scala.util.Random

/**
  * Created by dda on 9/27/16.
  */
class CarBrain(id: String) extends BrainActor(id) {

  override protected def handleSensory(payload: Set[Position]): Unit = {
    if (id == "#0") {
      val possibleMovements = List("forward", "left", "right")
      sender() ! FromAvatarToRobot(possibleMovements(Random.nextInt(possibleMovements.length)))
    } else {
      val currentPosition = payload.find { case Position(_id, _, _, _) => id == _id }.get
      val preyPosition = payload.find { case Position(_id, _, _, _) => _id == "#0" }.get
      val angleDelta = 40
      val angleToTarget = Math.atan2(preyPosition.y - currentPosition.y, preyPosition.x - currentPosition.x) / Math.PI * 180
      val angleDiff = Math.abs(currentPosition.angle - angleToTarget)
      if (angleDiff < angleDelta) {
        sender() ! FromAvatarToRobot("forward")
      } else {
        if (angleDiff < 0) {
          sender() ! FromAvatarToRobot("left")
        } else {
          sender() ! FromAvatarToRobot("right")
        }
      }
    }
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    sender() ! TellToOtherAvatar(from, message)
  }

  override protected def handleRobotMessage(message: String): Unit = {
    sender() ! FromAvatarToRobot(message)
  }

}
