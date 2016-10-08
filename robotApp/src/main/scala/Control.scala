import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import common.SharedMessages.{CreateAvatar, Sensory}
import common.zmqHelpers.ZeroMQHelper
import pipetest.CameraStub
import pipetest.CameraStub.GetInfo

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by dda on 10/4/16.
  */
class Control extends Actor with ActorLogging {

  val config = ConfigFactory.load()
  val camera = context.actorOf(Props(classOf[CameraStub], config.getString("robotapp.map")))
  val classes = config.getStringList("robotapp.robots").zipWithIndex

  val helper = ZeroMQHelper(context.system)
  val robots = classes.map{ case (clazz, idx) =>
    (idx, clazz, helper.connectDealerActor(idx.toString, "tcp://" + config.getString("akka.remote.netty.tcp.hostname"), 34671, self))}
  val cameraDealer = helper.connectDealerActor("robotapp.camera", "tcp://" + config.getString("akka.remote.netty.tcp.hostname"), 34671, self)

  log.info("RobotApp started and connected to [{}]", "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + 34671)

  implicit val timeout: Timeout = 3.second
  Future.sequence(robots.map { case (idx, clazz, robot) =>
    robot ? CreateAvatar(idx.toString, "brain-assembly-1.0.jar", clazz)
  }).onComplete(_ => context.system.scheduler.schedule(3.second, 3.second, camera, GetInfo))

  cameraDealer ! CreateAvatar("robotapp.camera", "brain-assembly-1.0.jar", "com.dda.brain.SensorBrain")

  override def receive: Receive = {
    case Sensory(_, payload) =>
      cameraDealer ! Sensory("robotapp.camera", payload)
      log.info("Sensory sended")

    case other =>
      log.error("Control received other: [{}]", other)
  }
}
