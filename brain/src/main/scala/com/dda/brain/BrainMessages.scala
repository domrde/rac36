package com.dda.brain

/**
  * Created by dda on 9/28/16.
  */
object BrainMessages {
  @SerialVersionUID(101L) case class Control(command: String)
  @SerialVersionUID(101L) case class Position(name: String, row: Int, col: Int, angle: Int)
  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position])
}
