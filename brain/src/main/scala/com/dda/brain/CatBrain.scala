package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import messages.BrainMessages._

/**
  * Created by dda on 9/27/16.
  */
class CatBrain extends Actor with ActorLogging {

  log.info("[-] CatBrain started")

  override def receive: Receive = {
    case Sensory(id, payload) =>
      chooseNextAction(id, payload)

    case t @ TellToOtherAvatar(to, from, message) =>
      if (Integer.parseInt(message) > 0)
        sender() ! TellToOtherAvatar(from, to, (Integer.parseInt(message) - 1).toString)

    case other =>
      log.error("[-] CatBrain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(id: String, payload: Set[Position]) = {
    val catPos = payload.find(p => p.name == id)
    val mousePos = payload.find(p => p.name != "obstacle")
    if (catPos.isDefined && mousePos.isDefined) {
      sender() ! TellToOtherAvatar(mousePos.get.name, id, "2")
      val colInc = mousePos.get.col - catPos.get.col
      val rowInc = mousePos.get.row - catPos.get.row
      sender() ! FromAvatarToRobot("{\"name\": \"move\", \"id\":\"" + id + "\", \"colInc\":\"" + colInc + "\"" +
      "\"rowInc\":\"" + rowInc + "\"")
      log.info("[-] CatBrain [{}]: control sended", id)
    } else {
      log.info("[-] CatBrain [{}]: cat [{}] or mouse [{}] not found ", id, catPos, mousePos)
    }
  }
}
