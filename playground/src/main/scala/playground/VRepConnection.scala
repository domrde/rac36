package playground

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.ConfigFactory
import common.Constants
import common.messages.SensoryInformation.{Position, Sensory}
import playground.VRepConnection.{PollPosition, PollSensors}
import vivarium.Avatar.FromAvatarToRobot
import vrepapiscala.VRepAPI
import vrepapiscala.common.{EulerAngles, Vec3}
import vrepapiscala.sensors.{PositionSensor, ProximitySensor}

import scala.concurrent.duration._
import scala.util.{Random, Try}

object VRepConnection {
  case object PollPosition
  case object PollSensors
  case class RobotPosition(position: Position)
  case class RobotSensors(obstacles: List[Position])

  class PioneerP3dx(api: VRepAPI, id: String) {
    //  3  4   6
    //   \ |  /
    // 1--####--8
    //    ####
    //    ####
    // 16-####--9
    //   /    \
    //  14    11

    private val speed = 4f
    private val leftMotor = api.joint.withVelocityControl("Pioneer_p3dx_leftMotor" + id).get
    private val rightMotor = api.joint.withVelocityControl("Pioneer_p3dx_rightMotor" + id).get
    val sensors: List[ProximitySensor] =
      List(1, 3, 4, 6, 8, 11, 14).map(i => api.sensor.proximity("Pioneer_p3dx_ultrasonicSensor" + i + id).get)

    def moveForward(): Unit = {
      leftMotor.setTargetVelocity(speed)
      rightMotor.setTargetVelocity(speed)
    }

    def moveBackward(): Unit = {
      leftMotor.setTargetVelocity(-speed)
      rightMotor.setTargetVelocity(-speed)
    }

    def rotate(angle: Double): Unit = {
      val curAngle = gps.orientation.gamma * 180.0 / Math.PI
      val diff = angle - curAngle
      val sign = diff / Math.abs(diff)
      leftMotor.setTargetVelocity((sign * -1.0f).toFloat)
      rightMotor.setTargetVelocity((sign * 1.0f).toFloat)
    }

    def stop(): Unit = {
      leftMotor.setTargetVelocity(0.01f)
      rightMotor.setTargetVelocity(0.01f)
    }

    val gps: PositionSensor = api.sensor.position("Pioneer_p3dx_gps" + id).get
  }
}

class VRepConnection(id: String, api: VRepAPI) extends Actor with ActorLogging {
  import VRepConnection._
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system
  private val config = ConfigFactory.load()

  private val robot = new PioneerP3dx(api, id)
  if (config.getBoolean("full-knowledge")) {
    context.actorOf(Props(classOf[FullKnowledgePoller], id, api), "FullKnowledgePoller" + id.substring(1))
  } else {
    context.actorOf(Props(classOf[SensoryPoller], id, robot), "SensoryPoller" + id.substring(1))
  }
  context.actorOf(Props(classOf[PositionPoller], id, robot), "PositionPoller" + id.substring(1))

  override def receive: Receive = receiveWithCurrentPosition(None)

  private val rotateRegExp = "rotate=([\\-\\d\\.]+)".r

  def receiveWithCurrentPosition(targetRotation: Option[Double]): Receive = {
    case RobotPosition(robotPosition) =>
      context.parent ! Sensory(id, Set(robotPosition))
      val updatedAngle = robotPosition.angle
      if (targetRotation.isDefined && Math.abs(updatedAngle - targetRotation.get) < 10) {
        robot.stop()
        context.become(receiveWithCurrentPosition(None))
      } else {
        context.become(receiveWithCurrentPosition(targetRotation))
      }

    case RobotSensors(obstacles) =>
      context.parent ! Sensory(id, obstacles.toSet)

    case FromAvatarToRobot(_id, "forward") if _id == id =>
      robot.moveForward()

    case FromAvatarToRobot(_id, "stop") if _id == id =>
      robot.stop()

    case FromAvatarToRobot(_id, command) if _id == id =>
      Try {
        val rotateRegExp(angle) = command
        robot.rotate(angle.toDouble)
        context.become(receiveWithCurrentPosition(Some(angle.toDouble)))
      }

    case other =>
      log.error("VRepConnection: unknown message [{}] from [{}]", other, sender())
  }

  robot.stop()
}

class FullKnowledgePoller(id: String, api: VRepAPI) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  val positions = (0 to 104).map(i => "ConcretBlock" + i)
    .flatMap { name =>
      val block = api.joint.concreteBlock(name)
      if (block.isSuccess) Some(block.get)
      else None
    }
    .flatMap { block =>
      if (0 < block.handle && block.handle < 1000) Some(block.absolutePosition)
      else None
    }
    .map { case Vec3(x, y, _) =>
      Position(Constants.OBSTACLE_NAME, y, x, 0.3, 0)
    }.toList

  context.system.scheduler.schedule((Random.nextInt(500) + 100).millis, (Random.nextInt(10) + 2000).millis, self, PollSensors)

  override def receive: Receive = {
    case PollSensors =>
      context.parent ! VRepConnection.RobotSensors(positions)
  }
}

class SensoryPoller(id: String, robot: VRepConnection.PioneerP3dx) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  context.system.scheduler.schedule((Random.nextInt(500) + 100).millis, (Random.nextInt(10) + 300).millis, self, PollSensors)

  def zeroVelocity(velocity: (Vec3, EulerAngles)): Boolean = {
    velocity._1.x == 0 && velocity._1.y == 0 && velocity._1.z == 0 &&
      velocity._2.alpha == 0 && velocity._2.beta == 0 && velocity._2.gamma == 0
  }

  override def receive: Receive = {
    case PollSensors =>
      Try {
        val obstacles = robot.sensors.par.flatMap { sensor =>
          val read = sensor.read
          if (read.detectionState && zeroVelocity(read.detectedObject.velocity)) {
            val p = read.detectedObject.position
            Some(Position(Constants.OBSTACLE_NAME, p.y, p.x, 0.3, 0))
          } else {
            None
          }
        }.toList
        context.parent ! VRepConnection.RobotSensors(obstacles)
      }

    case other =>
      log.error("SensoryPoller: unknown message [{}] from [{}]", other, sender())
  }
}

class PositionPoller(id: String, robot: VRepConnection.PioneerP3dx) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  context.system.scheduler.schedule((Random.nextInt(500) + 100).millis, (Random.nextInt(10) + 300).millis, self, PollPosition)

  override def receive: Receive = {
    case PollPosition =>
      Try {
        val updatedPosition = robot.gps.position
        val updatedAngle = robot.gps.orientation.gamma * 180.0 / Math.PI
        context.parent ! VRepConnection.RobotPosition(Position(id, updatedPosition.y, updatedPosition.x, 0.5, updatedAngle))
      }

    case other =>
      log.error("PositionPoller: unknown message [{}] from [{}]", other, sender())
  }
}