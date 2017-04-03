package com.dda.brain

import com.dda.brain.BrainMessages.{FromAvatarToRobot, TellToOtherAvatar}

/**
  * Created by dda on 9/27/16.
  */
class ParrotBrain(id: String) extends BrainActor(id) {

  override protected def handleSensory(payload: Set[BrainMessages.Position]): Unit = {
    sender() ! BrainMessages.FromAvatarToRobot("testCommand")
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    sender() ! TellToOtherAvatar(from, message)
  }

  override protected def handleRobotMessage(message: String): Unit = {
    sender() ! FromAvatarToRobot(message)
  }

}
