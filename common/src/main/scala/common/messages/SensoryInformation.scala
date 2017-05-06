package common.messages

/**
  * Created by dda on 02.04.17.
  */
object SensoryInformation {

  @SerialVersionUID(101L) case class Position(name: String, y: Double, x: Double, radius: Double, angle: Double) extends Serializable

  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position]) extends NumeratedMessage

}
