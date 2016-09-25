package common

import akka.actor.ActorRef
import akka.cluster.ddata.ORSetKey
import common.SharedMessages.{Command, Position}

/**
  * Created by dda on 7/28/16.
  */

//todo: figure out all serializable messages and a better way to manage them
//todo: organize messages in actors
//todo: configure serializer

sealed trait GlobalMessages

object Constants {
  val PIPE_SUBSCRIPTION = "PIPE_SUBSCRIPTION"
  val DdataSetKey = ORSetKey[Position]("SensoryInfoSet")
}

object ApiActor {
  @SerialVersionUID(101L) case object GetInfoFromSharedStorage extends GlobalMessages
  @SerialVersionUID(101L) case class GetInfoFromSharedStorageResult(info: AnyRef) extends GlobalMessages

  @SerialVersionUID(101L) case class GetAvailableCommands(id: String) extends SharedMessages.NumeratedMessage
  @SerialVersionUID(101L) case class GetAvailableCommandsResult(id: String, commands: List[Command]) extends SharedMessages.NumeratedMessage

  @SerialVersionUID(101L) case class SendCommandToAvatar(id: String, name: String, value: Long) extends SharedMessages.NumeratedMessage
}

object SharedMessages {
  @SerialVersionUID(101L) case class ArgumentRange(lower: Long, upper: Long) extends GlobalMessages
  @SerialVersionUID(101L) case class Command(name: String, range: ArgumentRange) extends GlobalMessages
  @SerialVersionUID(101L) case class Api(commands: List[Command]) extends GlobalMessages

  sealed trait NumeratedMessage extends GlobalMessages { val id: String }
  @SerialVersionUID(101L) case class CreateAvatar(id: String, api: Api) extends NumeratedMessage
  @SerialVersionUID(101L) case class GetListOfAvailableCommands(id: String) extends NumeratedMessage
  @SerialVersionUID(101L) case class ListOfAvailableCommands(id: String, api: Api) extends NumeratedMessage
  @SerialVersionUID(101L) case class GetState(id: String) extends NumeratedMessage // for tests
  @SerialVersionUID(101L) case class Control(id: String, name: String, value: Long) extends NumeratedMessage
  // todo: replace when something better comes up
  @SerialVersionUID(101L) case class Position(name: String, row: Int, col: Int, angle: Int) extends GlobalMessages
  // todo: replace when it comes to different sensor types
  @SerialVersionUID(101L) case class Sensory(id: String, sensoryPayload: Set[Position]) extends NumeratedMessage

  @SerialVersionUID(101L) case class TunnelEndpoint(id: String, endpoint: ActorRef) extends NumeratedMessage
  @SerialVersionUID(101L) case class AvatarCreated(id: String) extends NumeratedMessage

  @SerialVersionUID(101L) case class TunnelCreated(url: String, id: String) extends NumeratedMessage
}
