package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import messages.BrainMessages._

/**
  * Created by dda on 9/27/16.
  */
class SensorBrain extends Actor with ActorLogging {

  log.info("[-] SensorBrain started")

  override def receive: Receive = {
    case s @ Sensory(id, payload) =>
      log.info("[-] SensorBrain: received [{}] from [{}]", s, sender())

    case other =>
      log.error("[-] SensorBrain: received unknown message [{}] from [{}]", other, sender())
  }
}
