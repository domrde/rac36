package com.dda.brain

import com.dda.brain.BrainMessages.FromAvatarToRobot

/**
  * Created by dda on 04.06.17.
  */
class DirectMovementExperiment(id: String) extends BrainActor(id) {
  override protected def handleSensory(payload: Set[BrainMessages.Position]): Unit = {

  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    avatar ! FromAvatarToRobot(message)
  }

  override protected def handleRobotMessage(message: String): Unit = {

  }
}
