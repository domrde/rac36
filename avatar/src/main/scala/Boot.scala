package avatar
import akka.actor.{ActorSystem, Props}

object Boot extends App {
  val system = ActorSystem("AvatarSystem")
  system.actorOf(Props[ClusterMain])
}
