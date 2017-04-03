package com.dda.brain

import akka.actor.{Actor, ActorLogging}
import com.dda.brain.BrainMessages._

/**
  * Created by dda on 02.04.17.
  */
abstract class BrainActor(id: String) extends Actor with ActorLogging {

  log.info("[-] BrainActor started [{}]", this.getClass.getName)

  override def receive: Receive = stopped

  private lazy val working: Receive = {
    case Sensory(payload) =>
      handleSensory(payload)

    case FromOtherAvatar(from, message) =>
      handleAvatarMessage(from, message)

    case FromRobotToAvatar(message) =>
      handleRobotMessage(message)

    case ChangeState(newState) =>
      log.info("[-] BrainActor: changed state to [{}]", newState)

      newState match {
        case Stop =>
          context.become(behavior = stopped, discardOld = true)
        case Start =>
      }

    case other =>
      log.error("[-] BrainActor: received unknown message [{}] from [{}]", other, sender())
  }

  private lazy val stopped: Receive = {
    case ChangeState(newState) =>
      log.info("[-] BrainActor: changed state to [{}]", newState)

      newState match {
        case Stop =>
        case Start =>
          context.become(behavior = working, discardOld = true)
      }

    case other =>
      log.error("[-] BrainActor: received message while stopped [{}] from [{}]", other, sender())
  }

  protected def handleSensory(payload: Set[Position]): Unit

  protected def handleAvatarMessage(from: String, message: String): Unit

  protected def handleRobotMessage(message: String): Unit

}
