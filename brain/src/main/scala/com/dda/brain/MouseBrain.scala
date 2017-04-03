package com.dda.brain

import com.dda.brain.BrainMessages._

/**
  * Created by dda on 9/27/16.
  */
class MouseBrain(id: String) extends BrainActor(id) {

  override protected def handleSensory(payload: Set[BrainMessages.Position]): Unit = {
    val catPos = payload.find(p => p.name != BrainMessages.OBSTACLE_NAME)
    val mousePos = payload.find(p => p.name == id)
    if (catPos.isDefined && mousePos.isDefined) {
      val colInc = catPos.get.col - mousePos.get.col
      val rowInc = catPos.get.row - mousePos.get.row
      sender() ! FromAvatarToRobot("{\"name\": \"move\", \"id\":\"" + id + "\", \"colInc\":\"" + colInc + "\"" +
        "\"rowInc\":\"" + rowInc + "\"")
      log.info("[-] MouseBrain [{}]: control sended", id)
    } else {
      log.info("[-] MouseBrain [{}]: cat [{}] or mouse [{}] not found ", id, catPos, mousePos)
    }
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    if (Integer.parseInt(message) > 0)
      sender() ! TellToOtherAvatar(from, (Integer.parseInt(message) - 1).toString)
  }

  override protected def handleRobotMessage(message: String): Unit = {
    sender() ! FromAvatarToRobot(message)
  }

}
