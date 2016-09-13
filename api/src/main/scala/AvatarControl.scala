import akka.actor.{Actor, ActorLogging}

/**
  * Created by dda on 9/13/16.
  */
class AvatarControl extends Actor with ActorLogging {

  // user must know robot id to interact with it

  override def receive: Receive = {
    case other =>
      log.error("\nAvatarControl: other [{}] from [{}]", other, sender())
  }
}
