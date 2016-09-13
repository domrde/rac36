import akka.actor.{ActorSystem, Props}

object Boot extends App {
  val system = ActorSystem("ClusterSystem")
  system.actorOf(Props[ClusterMain])
}
