import ServerClient.LaunchCommand
import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by dda on 9/7/16.
  */
class OpenstackActor extends Actor with ActorLogging {

  val config = ConfigFactory.load()
  val knownImages = Map("Pipe" -> config.getString("application.pipeImage"),
    "Avatar" -> config.getString("application.avatarImage"))

  override def receive: Receive = {
    case LaunchCommand(image, _) =>
      if (knownImages.contains(image)) {
        log.info("Launching image [{}]", image)
      } else {
        log.error("Unknown image")
      }
      context.system.scheduler.scheduleOnce(5.second, sender(), ServerClient.Launched(image))


    case other =>
      log.error("OpenstackActor: other {} from {}", other, sender())
  }
}
