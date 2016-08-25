package messages
import java.io.Serializable
import java.util.UUID

/**
  * Created by dda on 7/28/16.
  */
object Constants {
  val ACTOR_CREATION_SUBSCRIPTION = "ACTOR_CREATION_SUBSCRIPTION"
}

object Messages {
  case class ArgumentRange(lower: Long, upper: Long)
  @SerialVersionUID(1L) case class Command(name: String, range: Option[ArgumentRange]) extends Serializable
  case class Api(commands: List[Command])

  sealed trait NumeratedMessage { val uuid: UUID }
  @SerialVersionUID(1L) case class CreateAvatar(uuid: UUID, api: Api) extends Serializable with NumeratedMessage
  @SerialVersionUID(1L) case class AvatarCreated(uuid: UUID) extends Serializable
  @SerialVersionUID(1L) case object TunnelEndpoint extends Serializable
  @SerialVersionUID(1L) case class  ZMQMessage(data: String) extends Serializable
}
