package com.dda.brain

/**
  * Created by dda on 01.04.17.
  */
object BrainMessages {
  @SerialVersionUID(101L) case class FromAvatarToRobot(message: String)
  @SerialVersionUID(101L) case class FromRobotToAvatar(message: String)
  @SerialVersionUID(101L) case class TellToOtherAvatar(to: String, message: String)
  @SerialVersionUID(101L) case class FromOtherAvatar(from: String, message: String)
  @SerialVersionUID(101L) case class Position(name: String, y: Double, x: Double,
                                              width: Double, height: Double, angle: Double)
  @SerialVersionUID(101L) case class Sensory(sensoryPayload: Set[Position])

  sealed trait BrainState
  case object Stop extends BrainState
  case object Start extends BrainState
  @SerialVersionUID(101L) case class ChangeState(newState: BrainState)
}
