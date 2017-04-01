package messages

/**
  * Created by dda on 01.04.17.
  */
object BrainMessages {
  @SerialVersionUID(101L) case class FromAvatarToRobot(command: String)
  @SerialVersionUID(101L) case class FromRobotToAvatar(command: String)
  @SerialVersionUID(101L) case class FromAvatarToService(command: String)
  @SerialVersionUID(101L) case class FromServiceToAvatar(command: String)
  @SerialVersionUID(101L) case class TellToOtherAvatar(to: String, from: String, command: String)
  @SerialVersionUID(101L) case class Position(name: String, row: Int, col: Int, angle: Int)
  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position])
}
