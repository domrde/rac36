package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import messages.BrainMessages._

/**
  * Created by dda on 9/27/16.
  */
class MouseBrain extends Actor with ActorLogging {

  log.info("[-] MouseBrain started")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      chooseNextAction(id, payload)

    case t @ TellToOtherAvatar(to, from, message) =>
      if (Integer.parseInt(message) > 0)
        sender() ! TellToOtherAvatar(from, to, (Integer.parseInt(message) - 1).toString)

    case other =>
      log.error("[-] MouseBrain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(id: String, payload: Set[Position]) = {
    val catPos = payload.find(p => p.name != "obstacle")
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
}
