package messages
import java.io.Serializable
import java.util.UUID

import akka.actor.ActorRef

/**
  * Created by dda on 7/28/16.
  */
object Constants {
  val PIPE_SUBSCRIPTION = "PIPE_SUBSCRIPTION"
}

object Messages {
  case class ArgumentRange(lower: Long, upper: Long)
  case class Command(name: String, range: Option[ArgumentRange])
  case class Api(commands: List[Command])

  sealed trait NumeratedMessage { val uuid: UUID }
  @SerialVersionUID(1L) case class CreateAvatar(uuid: UUID, api: Api) extends Serializable with NumeratedMessage
  @SerialVersionUID(1L) case class GetState(uuid: UUID) extends Serializable with NumeratedMessage
  @SerialVersionUID(1L) case class Control(uuid: UUID, command: Command) extends Serializable with NumeratedMessage
  @SerialVersionUID(1L) case class Sensory(uuid: UUID, sensorType: String, data: String) extends Serializable with NumeratedMessage
  @SerialVersionUID(1L) case class TunnelEndpoint(uuid: UUID, endpoint: ActorRef) extends Serializable with NumeratedMessage
  @SerialVersionUID(1L) case class AvatarCreated(uuid: UUID) extends Serializable with NumeratedMessage
}
