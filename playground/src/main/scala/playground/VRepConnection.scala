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
  case class TargetPoint(y: Double, x: Double)

  def distance(p1: Position, p2: TargetPoint): Double = {
    Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
  }

  class PioneerP3dx(api: VRepAPI, id: String) {
    //  3  4   6
    //   \ |  /
    // 1--####--8
    //    ####
    //    ####
    // 16-####--9
    //   /    \
    //  14    11

    private val speed = 2f
    private val leftMotor = api.joint.withVelocityControl("Pioneer_p3dx_leftMotor" + id).get
    private val rightMotor = api.joint.withVelocityControl("Pioneer_p3dx_rightMotor" + id).get

    def moveForward(): Unit = {
      leftMotor.setTargetVelocity(speed)
      rightMotor.setTargetVelocity(speed)
    }

    def moveBackward(): Unit = {
      leftMotor.setTargetVelocity(-speed)
      rightMotor.setTargetVelocity(-speed)
    }

    def forwardRight(): Unit = {
      leftMotor.setTargetVelocity(speed)
      rightMotor.setTargetVelocity(0.5f * speed)
    }

    def forwardLeft(): Unit = {
      leftMotor.setTargetVelocity(0.5f * speed)
      rightMotor.setTargetVelocity(speed)
    }

    def right(): Unit = {
      leftMotor.setTargetVelocity(0.5f)
      rightMotor.setTargetVelocity(-0.5f)
    }

    def left(): Unit = {
      leftMotor.setTargetVelocity(-0.5f)
      rightMotor.setTargetVelocity(0.5f)
    }

    def stop(): Unit = {
      leftMotor.setTargetVelocity(0.01f)
      rightMotor.setTargetVelocity(0.01f)
    }

    val gps: PositionSensor = api.sensor.position("Pioneer_p3dx_gps" + id).get

    //    val leftSensors: List[ProximitySensor] =
    //      List(2, 3).map(i => api.sensor.proximity("Pioneer_p3dx_ultrasonicSensor" + i + id).get)
    //
    //    val rightSensors: List[ProximitySensor] =
    //      List(6, 7).map(i => api.sensor.proximity("Pioneer_p3dx_ultrasonicSensor" + i + id).get)
  }

  sealed trait RobotCommand
  case object Forward extends RobotCommand
//  case object ForwardRight extends RobotCommand
//  case object ForwardLeft extends RobotCommand
  case object RotateRight extends RobotCommand
  case object RotateLeft extends RobotCommand
  case object Stop extends RobotCommand

}

class VRepConnection(id: String, api: VRepAPI) extends Actor with ActorLogging {
  import VRepConnection._
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system
  private val config = ConfigFactory.load()

  log.info("Starting VRepConnection {}", id)

  private val robot = new PioneerP3dx(api, id)
  if (config.getBoolean("playground.full-knowledge")) {
    context.actorOf(Props(classOf[FullKnowledgePoller], id, api), "FullKnowledgePoller" + id.substring(1))
  } else {
    context.actorOf(Props(classOf[SensoryPoller], id, robot), "SensoryPoller" + id.substring(1))
  }
  context.actorOf(Props(classOf[PositionPoller], id, robot), "PositionPoller" + id.substring(1))

  override def receive: Receive = receiveWithTargetPoint(None, Stop)

  private val rotateRegExp = "move=([\\-\\d\\.]+),([\\-\\d\\.]+)".r

  def receiveWithTargetPoint(targetPoint: Option[TargetPoint], previousCommand: RobotCommand): Receive = {
    case RobotPosition(robotPosition) =>
      context.parent ! Sensory(id, Set(robotPosition))
      val updatedAngle = robotPosition.angle
      if (targetPoint.isDefined) {
        val nextStep = targetPoint.get
        val angleToPoint = Math.atan2(nextStep.y - robotPosition.y, nextStep.x - robotPosition.x) * 180.0 / Math.PI
        val angleDiff = updatedAngle - angleToPoint
        val angleAbsDiff = Math.abs(angleDiff)
        if (angleAbsDiff < 30) {
          if (previousCommand != Forward) {
            log.info("{} -> Angle diff {}, forward", id, angleDiff)
            robot.moveForward()
            context.become(receiveWithTargetPoint(targetPoint, Forward))
          }
        }
//        else if (angleAbsDiff < 45) {
//          if (angleDiff < 0) {
//            if (previousCommand != ForwardRight) {
//              robot.forwardRight()
//              context.become(receiveWithTargetPoint(targetPoint, ForwardRight))
//            }
//          } else {
//            if (previousCommand != ForwardLeft) {
//              robot.forwardLeft()
//              context.become(receiveWithTargetPoint(targetPoint, ForwardLeft))
//            }
//          }
//        }
        else {
          if (angleDiff > 0) {
            if (previousCommand != RotateRight) {
              log.info("{} -> Angle diff {}, rotating right", id, angleDiff)
              robot.right()
              context.become(receiveWithTargetPoint(targetPoint, RotateRight))
            }
          } else {
            if (previousCommand != RotateLeft) {
              log.info("{} -> Angle diff {}, rotating right", id, angleDiff)
              robot.left()
              context.become(receiveWithTargetPoint(targetPoint, RotateLeft))
            }
          }
        }
      } else {
        if (previousCommand != Stop) {
          log.info("{} -> Stop", id)
          robot.stop()
          context.become(receiveWithTargetPoint(targetPoint, Stop))
        }
      }

    case RobotSensors(obstacles) =>
      context.parent ! Sensory(id, obstacles.toSet)

    case FromAvatarToRobot(_id, "stop") if _id == id =>
      if (previousCommand != Stop) {
        robot.stop()
        context.become(receiveWithTargetPoint(None, Stop))
      }

    case FromAvatarToRobot(_id, command) if _id == id =>
      Try {
        val rotateRegExp(yString, xString) = command
        log.info(id + " -> " + command)
        context.become(receiveWithTargetPoint(Some(TargetPoint(yString.toDouble, xString.toDouble)), previousCommand))
      }

    case other =>
      log.error("VRepConnection: unknown message [{}] from [{}]", other, sender())
  }
}

class FullKnowledgePoller(id: String, api: VRepAPI) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  context.system.scheduler.schedule((Random.nextInt(500) + 100).millis, (Random.nextInt(10) + 1500).millis, self, PollSensors)

  override def receive: Receive = {
    case PollSensors =>
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
          Position(Constants.OBSTACLE_NAME, y, x, 0.15, 0)
        }.toList
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
    //      Try {
    //        val obstacles = robot.sensors.par.flatMap { sensor =>
    //          val read = sensor.read
    //          if (read.detectionState && zeroVelocity(read.detectedObject.velocity)) {
    //            val p = read.detectedObject.position
    //            Some(Position(Constants.OBSTACLE_NAME, p.y, p.x, 0.15, 0))
    //          } else {
    //            None
    //          }
    //        }.toList
    //        context.parent ! VRepConnection.RobotSensors(obstacles)
    //      }

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
        val curPos = Position(id, updatedPosition.y, updatedPosition.x, 0.5, updatedAngle)
        context.parent ! VRepConnection.RobotPosition(curPos)
        context.become(receiveWithPrevPosition(curPos))
      }

    case other =>
      log.error("PositionPoller: unknown message [{}] from [{}]", other, sender())
  }

  def receiveWithPrevPosition(previousPosition: Position): Receive = {
    case PollPosition =>
      Try {
        val updatedPosition = robot.gps.position
        val updatedAngle = robot.gps.orientation.gamma * 180.0 / Math.PI
        val curPos = Position(id, updatedPosition.y, updatedPosition.x, 0.5, updatedAngle)
        if (Math.abs(curPos.x - previousPosition.x) < 0.5 && Math.abs(curPos.y - previousPosition.y) < 0.5) {
          context.parent ! VRepConnection.RobotPosition(curPos)
          context.become(receiveWithPrevPosition(curPos))
        }
      }
  }
}