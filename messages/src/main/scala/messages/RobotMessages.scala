package messages

/**
  * Created by dda on 01.04.17.
  */
object RobotMessages {
  @SerialVersionUID(101L) case class Control(id: String, command: String) extends NumeratedMessage
}
