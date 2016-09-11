package messages

import java.util.UUID

import akka.actor.ActorRef
import akka.cluster.ddata.ORSetKey
import messages.Messages.Position

/**
  * Created by dda on 7/28/16.
  */
object Constants {
  val PIPE_SUBSCRIPTION = "PIPE_SUBSCRIPTION"
  val DdataSetKey = ORSetKey[Position]("SensoryInfoSet")
}

object Messages {
  case class ArgumentRange(lower: Long, upper: Long)
  case class Command(name: String, range: Option[ArgumentRange])
  case class Api(commands: List[Command])

  sealed trait NumeratedMessage { val uuid: UUID }
  @SerialVersionUID(1L) case class CreateAvatar(uuid: UUID, api: Api) extends NumeratedMessage
  @SerialVersionUID(1L) case class GetState(uuid: UUID) extends NumeratedMessage
  @SerialVersionUID(1L) case class Control(uuid: UUID, command: Command) extends NumeratedMessage
  // todo: replace when something better comes up
  case class Position(name: String, row: Int, col: Int, angle: Int)
  // todo: replace when it comes to different sensor types
  @SerialVersionUID(1L) case class Sensory(uuid: UUID, sensoryPayload: Set[Position]) extends NumeratedMessage
  @SerialVersionUID(1L) case class TunnelEndpoint(uuid: UUID, endpoint: ActorRef) extends NumeratedMessage
  @SerialVersionUID(1L) case class AvatarCreated(uuid: UUID) extends NumeratedMessage
}
