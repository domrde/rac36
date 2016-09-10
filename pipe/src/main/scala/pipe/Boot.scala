package pipe

import akka.actor.Props

/**
  * Created by dda on 23.04.16.
  */
object Boot extends App {
  implicit val system = ZeroMQ.system
  system.actorOf(Props[ClusterMain])
}
