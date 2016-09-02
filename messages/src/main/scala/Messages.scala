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
  @SerialVersionUID(1L) case class Command(name: String, range: Option[ArgumentRange]) extends Serializable
  case class Api(commands: List[Command])

  sealed trait NumeratedMessage { val uuid: UUID }
  @SerialVersionUID(1L) case class CreateAvatar(uuid: UUID, api: Api, returnAddress: ActorRef) extends Serializable with NumeratedMessage

  @SerialVersionUID(1L) case class AvatarCreated(uuid: UUID, actor: ActorRef) extends Serializable
  @SerialVersionUID(1L) case object TunnelEndpoint extends Serializable
  @SerialVersionUID(1L) case class  ZMQMessage(data: String) extends Serializable
}
