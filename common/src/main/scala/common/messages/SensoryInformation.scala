package common.messages

/**
  * Created by dda on 02.04.17.
  */
object SensoryInformation {
  @SerialVersionUID(101L) case class Position(name: String, row: Int, col: Int, angle: Int) extends Serializable
  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position]) extends NumeratedMessage
}
