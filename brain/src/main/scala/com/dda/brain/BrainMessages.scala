package com.dda.brain

/**
  * Created by dda on 9/28/16.
  */
object BrainMessages {
  @SerialVersionUID(101L) case class FromAvatarToRobot(command: String)
  @SerialVersionUID(101L) case class FromRobotToAvatar(command: String) //to brain todo:
  @SerialVersionUID(101L) case class FromAvatarToService(command: String) //to brain todo:
  @SerialVersionUID(101L) case class FromServiceToAvatar(command: String) //to brain todo:
  @SerialVersionUID(101L) case class TellToOtherAvatar(to: String, from: String, command: String)
  @SerialVersionUID(101L) case class Position(name: String, row: Int, col: Int, angle: Int)
  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position])
}
