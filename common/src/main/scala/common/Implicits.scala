package common

import common.ApiActor.{GetAvailableCommands, GetAvailableCommandsResult, SendCommandToAvatar}
import common.SharedMessages._
import play.api.libs.json.Json

/**
  * Created by dda on 9/21/16.
  */
object Implicits {
  implicit val tunnelInfoWrites = Json.writes[TunnelCreated]
  implicit val rangeWrites = Json.writes[ArgumentRange]
  implicit val commandWrites = Json.writes[Command]
  implicit val controlWrites = Json.writes[Control]

  implicit val rangeReads = Json.reads[ArgumentRange]
  implicit val commandReads = Json.reads[Command]
  implicit val apiReads = Json.reads[Api]
  implicit val createAvatarReads = Json.reads[CreateAvatar]
  implicit val posReads = Json.reads[Position]
  implicit val sensoryReads = Json.reads[Sensory]

  implicit val apiWrites = Json.writes[Api]
  implicit val avatarWrites = Json.writes[CreateAvatar]
  implicit val availableCommandsWrites = Json.writes[GetAvailableCommands]
  implicit val sendCommandToAvatarWrites = Json.writes[SendCommandToAvatar]
  implicit val availableCommandsResultReads = Json.reads[GetAvailableCommandsResult]
  implicit val tunnelCreatedReads = Json.reads[TunnelCreated]
  implicit val sendCommandToAvatarReads = Json.reads[SendCommandToAvatar]

  implicit val availableCommandsReads = Json.reads[GetAvailableCommands]
  implicit val availableCommandsResultWrites = Json.writes[GetAvailableCommandsResult]

  implicit val controlReads = Json.reads[Control]
}
