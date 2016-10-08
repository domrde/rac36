package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import com.dda.brain.BrainMessages.{Control, Position, Sensory}

/**
  * Created by dda on 9/27/16.
  */
class CatBrain extends Actor with ActorLogging {

  log.info("\n\n\n           Brain started           \n\n\n")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      chooseNextAction(id, payload)

    case other =>
      log.error("Brain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(id: String, payload: Set[Position]) = {
    val catPos = payload.find(p => p.name == id)
    val mousePos = payload.find(p => p.name != "obstacle")
    if (catPos.isDefined && mousePos.isDefined) {
      val colInc = mousePos.get.col - catPos.get.col
      val rowInc = mousePos.get.row - catPos.get.row
      sender() ! Control("{\"name\": \"move\", \"id\":\"" + id + "\", \"colInc\":\"" + colInc + "\"" +
      "\"rowInc\":\"" + rowInc + "\"")
      log.info("CatBrain [{}]: control sended", id)
    } else {
      log.info("CatBrain [{}]: cat [{}] or mouse [{}] not found ", id, catPos, mousePos)
    }
  }
}
