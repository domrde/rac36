package com.dda.brain

/**
  * Created by dda on 9/27/16.
  */
class ReceivingBrain(id: String) extends BrainActor(id) {
  private implicit val executionContext = context.dispatcher

  override protected def handleSensory(payload: Set[BrainMessages.Position]): Unit = {
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
  }

  override protected def handleRobotMessage(message: String): Unit = {
  }

}
