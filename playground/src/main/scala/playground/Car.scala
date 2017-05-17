package playground

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import common.messages.SensoryInformation
import common.messages.SensoryInformation.Sensory
import vivarium.Avatar.{FromAvatarToRobot, FromRobotToAvatar}
import vrepapiscala.VRepAPI

import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by dda on 12.04.17.
  */
object Car {
  def apply(id: String, api: VRepAPI): Props = Props(classOf[Car], id, api)
}

class Car(id: String, api: VRepAPI) extends Actor with ActorLogging {
  case object StartVrep

  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  context.system.scheduler.scheduleOnce((Random.nextInt(10) * 150).millis, self, StartVrep)

  private val avatar = context.actorOf(Props(classOf[ZMQConnection], id), "ZMQConnection" + id.substring(1))

  override def receive: Receive = {
    case StartVrep =>
      val vrep = context.actorOf(Props(classOf[VRepConnection], id, api), "VRepConnection" + id.substring(1))
      log.info("Car {} initialized", id)
      context.become(receiveWithActorStarted(vrep))

    case other =>
      log.error("Car: unknown message [{}] from [{}]", other, sender())
  }

  def receiveWithActorStarted(robot: ActorRef): Receive = {
    case Sensory(_, payload) =>
      val correctPayload = payload.filterNot { case SensoryInformation.Position(_, y, x, _, _) =>
        y.isNaN || x.isNaN || x < 0.01 || y < 0.01 || x > 11.0 || y > 11.0
      }
      if (correctPayload.nonEmpty) {
        val s = Sensory(id, correctPayload)
        avatar ! s
      }

    case a: FromAvatarToRobot =>
      robot ! a

    case a: FromRobotToAvatar =>
      avatar ! a

    case other =>
      log.error("Car: unknown message [{}] from [{}]", other, sender())
  }
}

