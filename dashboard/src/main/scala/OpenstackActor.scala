import ServerClient.LaunchCommand
import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import com.typesafe.config.ConfigFactory

/**
  * Created by dda on 9/7/16.
  */
object OpenstackActor {
  case class ToOpenstackActor(msg: Any)
}

class OpenstackActor extends Actor with ActorLogging {

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Put(self)

  val config = ConfigFactory.load()
  val knownImages = Map("Pipe" -> config.getString("application.pipeImage"),
    "Avatar" -> config.getString("application.avatarImage"))

  override def receive: Receive = pendingStarts(Set.empty)

  def pendingStarts(roles: Set[String]): Receive = {
    case LaunchCommand(image, _) =>
      if (knownImages.contains(image)) {
        log.info("Launching image [{}]", image)
        if (!roles.contains(image)) {
          context.become(pendingStarts(roles + image))
          // do start
        }
      } else {
        log.error("Unknown image")
      }

    case other =>
      log.error("OpenstackActor: other {} from {}", other, sender())
  }
}
