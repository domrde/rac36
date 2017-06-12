package com.dda.brain

import com.dda.brain.BrainMessages.FromAvatarToRobot

import scala.collection.immutable.Queue

/**
  * Created by dda on 04.06.17.
  */
class DirectMovementExperimentBrain(id: String) extends BrainActor(id) {

  var commandsCache: Queue[String] = Queue.empty
  var sensoryCache: Map[String, Set[BrainMessages.Position]] = Map.empty
  var robotCurrentTask: Option[String] = Option.empty

  override protected def handleSensory(payload: Set[BrainMessages.Position]): Unit = {
    sensoryCache = payload.groupBy(_.name)
  }

  override protected def handleAvatarMessage(from: String, message: String): Unit = {
    if (robotCurrentTask.isEmpty) {
      sendOutCommand(message)
    } else {
      commandsCache += message
    }
  }

  override protected def handleRobotMessage(message: String): Unit = {
    if (message == "Done") {
      if (commandsCache.nonEmpty) {
        val (nextCommand, newCommandsCache) = commandsCache.dequeue
        commandsCache = newCommandsCache
        sendOutCommand(nextCommand)
      } else {
        robotCurrentTask = None
      }
    }
  }

  private def sendOutCommand(command: String): Unit = {

    def translateCommand(): Option[String] = {
      val partsOfCommand = command.split(";")
      if (partsOfCommand.length == 1) {
        Some(partsOfCommand(0))
      } else {
        if (partsOfCommand(0) == "moveto") {
          sensoryCache(partsOfCommand(1)).headOption.map { anotherRobotPosition =>
            "move=" + anotherRobotPosition.y + "," + anotherRobotPosition.x
          }
        } else {
          Some(partsOfCommand(0) + "=" + partsOfCommand(1))
        }
      }
    }

    translateCommand().foreach { commandThatRobotUnderstands =>
      robotCurrentTask = Some(commandThatRobotUnderstands)
      avatar ! FromAvatarToRobot(commandThatRobotUnderstands)
    }
  }

}
