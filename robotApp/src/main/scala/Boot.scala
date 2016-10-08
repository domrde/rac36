import akka.actor.{ActorSystem, Props}

/**
  * Created by dda on 9/10/16.
  */
object Boot extends App {
  val system = ActorSystem("ClusterSystem")
  system.actorOf(Props[Control], "Control")
}
