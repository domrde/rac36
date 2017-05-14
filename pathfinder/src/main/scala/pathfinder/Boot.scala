package pathfinder

import akka.actor.ActorSystem
import utils.zmqHelpers.ZeroMQHelper

/**
  * Created by dda on 9/10/16.
  */
object Boot extends App {
  val system = ActorSystem("ClusterSystem")

  val helper = ZeroMQHelper(system)

  system.actorOf(Pathfinding(), "Pathfinding")
}
