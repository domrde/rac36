package messages

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

  sealed trait NumeratedMessage { val id: String }
  @SerialVersionUID(1L) case class CreateAvatar(id: String, api: Api) extends NumeratedMessage
  @SerialVersionUID(1L) case class GetState(id: String) extends NumeratedMessage
  @SerialVersionUID(1L) case class Control(id: String, command: Command) extends NumeratedMessage
  // todo: replace when something better comes up
  case class Position(name: String, row: Int, col: Int, angle: Int)
  // todo: replace when it comes to different sensor types
  @SerialVersionUID(1L) case class Sensory(id: String, sensoryPayload: Set[Position]) extends NumeratedMessage
  @SerialVersionUID(1L) case class TunnelEndpoint(id: String, endpoint: ActorRef) extends NumeratedMessage
  @SerialVersionUID(1L) case class AvatarCreated(id: String) extends NumeratedMessage
}
