package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import com.dda.brain.BrainMessages.{Control, Position, Sensory}

/**
  * Created by dda on 9/27/16.
  */
class MouseBrain extends Actor with ActorLogging {

  log.info("\n\n\n           Brain started           \n\n\n")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      chooseNextAction(id, payload)

    case other =>
      log.error("Brain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(id: String, payload: Set[Position]) = {
    val catPos = payload.find(p => p.name != "obstacle")
    val mousePos = payload.find(p => p.name == id)
    if (catPos.isDefined && mousePos.isDefined) {
      val colInc = catPos.get.col - mousePos.get.col
      val rowInc = catPos.get.row - mousePos.get.row
      sender() ! Control("{\"name\": \"move\", \"id\":\"" + id + "\", \"colInc\":\"" + colInc + "\"" +
      "\"rowInc\":\"" + rowInc + "\"")
      log.info("MouseBrain [{}]: control sended", id)
    } else {
      log.info("MouseBrain [{}]: cat [{}] or mouse [{}] not found ", id, catPos, mousePos)
    }
  }
}
