package com.dda.brain

import com.dda.brain.BrainMessages.{FromAvatarToRobot, TellToOtherAvatar}
import scala.concurrent.duration._

/**
  * Created by dda on 9/27/16.
  */
class ParrotBrain(id: String) extends BrainActor(id) {
  private implicit val executionContext = context.dispatcher

  var sensory: Set[BrainMessages.Position] = Set.empty

//  context.system.scheduler.schedule(5.seconds, 5.seconds) {
//    log.info("Payload: {}", sensory)
//  }

  override protected def handleSensory(payload: Set[BrainMessages.Position]): Unit = {
//    sender() ! BrainMessages.FromAvatarToRobot("testCommand")
    sensory = payload
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    sender() ! TellToOtherAvatar(from, message)
  }

  override protected def handleRobotMessage(message: String): Unit = {
    sender() ! FromAvatarToRobot(message)
  }

}
