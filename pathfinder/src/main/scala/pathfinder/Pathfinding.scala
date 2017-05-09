package pathfinder

import akka.actor.{Actor, ActorLogging, Props}
import com.dda.brain.PathfinderBrain
import com.typesafe.config.ConfigFactory
import pipe.TunnelManager
import upickle.default._
import utils.zmqHelpers.ZeroMQHelper
import vivarium.Avatar
import vivarium.Avatar.Create

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 07.05.17.
  */
object Pathfinding {
  def apply(): Props = Props(classOf[Pathfinding])
}

class Pathfinding extends Actor with ActorLogging {
  log.info("Pathfinding actor started")

  private val config = ConfigFactory.load()
  val helper = ZeroMQHelper(context.system)

  val id = "pathfinder"

  private val dealer = helper.connectDealerActor(
    id = id,
    url = "tcp://192.168.31.102",
    port = 34671,
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = self)


  override def receive: Receive = {
    case TunnelManager.TunnelCreated(_, _, _id) if _id == id =>

    case Avatar.FromAvatarToRobot(_id, message) if _id == id =>
      Try {
        read[PathfinderBrain.FindPath](message)
      } match {
        case Success(value) =>
          context.actorOf(Worker.apply(value))

        case Failure(exception) =>
      }

    case pf: PathfinderBrain.PathFound =>
      dealer ! Avatar.FromRobotToAvatar(id, write(pf))

    case other =>
      log.error("[-] Pathfinding: received other: [{}] from [{}]", other, sender())
  }

  dealer ! Create(id, config.getString("pathfinder.brain-jar"), "com.dda.brain.PathfinderBrain")

}
