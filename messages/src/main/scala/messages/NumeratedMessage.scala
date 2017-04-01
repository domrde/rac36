package messages

/**
  * Created by dda on 01.04.17.
  */
//todo: configure serializer
trait NumeratedMessage { val id: String }

object SensoryInformation {
  // todo: replace when something better comes up
  @SerialVersionUID(101L) case class Position(name: String, row: Int, col: Int, angle: Int) extends Serializable
  // todo: replace when it comes to different sensor types
  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position]) extends NumeratedMessage
}