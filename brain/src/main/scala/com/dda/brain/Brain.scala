package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import com.dda.brain.BrainMessages.{Control, Position, Sensory}

/**
  * Created by dda on 9/27/16.
  */
class Brain extends Actor with ActorLogging {

  log.info("\n\n\n           Brain started           \n\n\n")

  override def receive: Receive = {
    case Sensory(payload) =>
      chooseNextAction(payload)

    case other =>
      log.error("Brain: received unknown message [{}] from [{}]", other, sender())
  }

  def chooseNextAction(payload: Set[Position]) = {
    sender() ! Control("testCommand")
  }
}
