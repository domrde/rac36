package common

import akka.cluster.ddata.ORSetKey
import common.messages.SensoryInformation.Position

/**
  * Created by dda on 7/28/16.
  */
object Constants {
  val PIPE_SUBSCRIPTION = "PIPE_SUBSCRIPTION"
  val AVATAR_STATE_SUBSCRIPTION = "AVATAR_STATE_SUBSCRIPTION"

  val PositionDdataSetKey: ORSetKey[Position] = ORSetKey[Position]("PositionDdataSetKey")
  val AvatarsDdataSetKey: ORSetKey[String] = ORSetKey[String]("AvatarsDdataSetKey")
}
