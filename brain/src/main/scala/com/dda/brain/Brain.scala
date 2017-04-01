package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import messages.BrainMessages._

/**
  * Created by dda on 9/27/16.
  */
class Brain extends Actor with ActorLogging {

  log.info("[-] Brain started")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      log.info("[-] Brain received [{}] from [{}]", s, sender())
      chooseNextAction(id, payload)

    case other =>
      log.error("[-] Brain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(id: String, payload: Set[Position]) = {
    sender() ! FromAvatarToRobot("testCommand")
  }
}
