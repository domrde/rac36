import akka.actor.{ActorSystem, Props}

/**
  * Created by dda on 02.08.16.
  */
object Boot extends App {
  implicit val system = ActorSystem("AvatarSystem")
  system.actorOf(Props[ClusterMain])
}
