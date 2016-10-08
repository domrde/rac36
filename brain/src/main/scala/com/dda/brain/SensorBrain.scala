package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import com.dda.brain.BrainMessages.Sensory

/**
  * Created by dda on 9/27/16.
  */
class SensorBrain extends Actor with ActorLogging {

  log.info("\n\n\n           Brain started           \n\n\n")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      log.info("Brain received [{}] from [{}]", s, sender())

    case other =>
      log.error("Brain: received unknown message [{}] from [{}]", other, sender())
  }
}
