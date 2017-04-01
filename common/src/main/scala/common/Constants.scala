package common

import akka.cluster.ddata.ORSetKey
import messages.SensoryInformation.Position

/**
  * Created by dda on 7/28/16.
  */
object Constants {
  val PIPE_SUBSCRIPTION = "PIPE_SUBSCRIPTION"
  val DdataSetKey: ORSetKey[Position] = ORSetKey[Position]("SensoryInfoSet")
}
