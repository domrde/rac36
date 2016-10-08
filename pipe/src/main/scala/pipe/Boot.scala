package pipe

import akka.actor.{ActorSystem, Props}

/**
  * Created by dda on 23.04.16.
  */
object Boot extends App {
  ActorSystem("ClusterSystem").actorOf(Props[ClusterMain], "ClusterMain")
}
