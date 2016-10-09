package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import com.dda.brain.BrainMessages.{FromAvatarToRobot, Position, Sensory, TellToOtherAvatar}

/**
  * Created by dda on 9/27/16.
  */
class MouseBrain extends Actor with ActorLogging {

  log.info("\n\n\n           Brain started           \n\n\n")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      chooseNextAction(id, payload)

    case t @ TellToOtherAvatar(to, from, message) =>
      println("\n\n" + t + "\n\n")
      if (Integer.parseInt(message) > 0)
        sender() ! TellToOtherAvatar(from, to, (Integer.parseInt(message) - 1).toString)

    case other =>
      log.error("Brain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(id: String, payload: Set[Position]) = {
    val catPos = payload.find(p => p.name != "obstacle")
    val mousePos = payload.find(p => p.name == id)
    if (catPos.isDefined && mousePos.isDefined) {
      val colInc = catPos.get.col - mousePos.get.col
      val rowInc = catPos.get.row - mousePos.get.row
      sender() ! FromAvatarToRobot("{\"name\": \"move\", \"id\":\"" + id + "\", \"colInc\":\"" + colInc + "\"" +
      "\"rowInc\":\"" + rowInc + "\"")
      log.info("MouseBrain [{}]: control sended", id)
    } else {
      log.info("MouseBrain [{}]: cat [{}] or mouse [{}] not found ", id, catPos, mousePos)
    }
  }
}
