package common

import common.SharedMessages._
import play.api.libs.json.Json

/**
  * Created by dda on 9/21/16.
  */
object Implicits {
  implicit val tunnelInfoWrites = Json.writes[TunnelCreated]
  implicit val failedToCreateTunnelWrites = Json.writes[FailedToCreateTunnel]
  implicit val controlWrites = Json.writes[Control]

  implicit val createAvatarReads = Json.reads[CreateAvatar]
  implicit val posReads = Json.reads[Position]
  implicit val sensoryReads = Json.reads[Sensory]

  implicit val avatarWrites = Json.writes[CreateAvatar]
  implicit val tunnelCreatedReads = Json.reads[TunnelCreated]
  implicit val failedToCreateTunnelReads = Json.reads[FailedToCreateTunnel]

  implicit val controlReads = Json.reads[Control]
  implicit val positionWrites = Json.writes[Position]
  implicit val sensoryWrites = Json.writes[Sensory]

  implicit val avatarCreatedReads = Json.reads[AvatarCreated]
  implicit val failedToCreateAvatarReads = Json.reads[FailedToCreateAvatar]
}
