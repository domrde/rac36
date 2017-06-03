package playground.simple

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.ConfigFactory
import common.Constants
import common.messages.SensoryInformation.{Position, Sensory}
import playground.ZMQConnection
import vivarium.Avatar.FromAvatarToRobot
import vrepapiscala.VRepAPI
import vrepapiscala.common.{EulerAngles, Vec3}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by dda on 21.05.17.
  */
class FullKnowledgeExperimentRunner(api: VRepAPI) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system
  private val config = ConfigFactory.load()

  case object PollRobotsPositions
  case object PollObstaclesPositions
  case object SendAwayPositions


  context.system.scheduler.schedule(100.millis, 40.millis, self, PollRobotsPositions)
  context.system.scheduler.schedule(200.millis, 2500.millis, self, PollObstaclesPositions)
  context.system.scheduler.schedule(2000.millis, 500.millis, self, SendAwayPositions)

  private val robotIds = config.getStringList("playground.car-ids").toList

  private val cameraAvatar = context.actorOf(Props(classOf[ZMQConnection], "camera"), "Camera")

  private val idToAvatar =
    robotIds.map(id => id -> context.actorOf(Props(classOf[ZMQConnection], id), "ZMQConnection" + id.substring(1))).toMap

  private val idToMover =
    robotIds.map(id => id -> context.actorOf(Props(classOf[RobotMover], api, id))).toMap

  private val handleToId =
    robotIds.map(id => api.sensor.getObjectHandle("Pioneer_p3dx_connection1" + id).get -> id).toMap

  def distance(p1: Vec3, p2: Position): Double = {
    Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
  }

  override def receive: Receive = receiveWithStorage(Set.empty, Set.empty)

  def receiveWithStorage(robotsPositions: Set[Position], obstaclesPositions: Set[Position]): Receive = {
    case SendAwayPositions =>
      cameraAvatar ! Sensory("camera", robotsPositions ++ obstaclesPositions)

    case PollRobotsPositions =>
      val newRobotsPositions =
        api.findAllForceSensorsPositions().map { case (handle, Vec3(x, y, z), EulerAngles(a, b, g)) =>
          val id = handleToId(handle)

          val angle = Math.round(g * 180.0 / Math.PI)

          val position =
            Position(id, Math.round(y * 10.0) / 10.0, Math.round(x * 10.0) / 10.0, 0.5, angle)

          idToMover(id) ! position

          position
        }.toSet
      context.become(receiveWithStorage(newRobotsPositions, obstaclesPositions))

    case PollObstaclesPositions =>
      val newObstaclesPositions =
        api.findAllSimpleShapesPositions()
          .filter(v => v.z > 0.1)
          .filter(v => !robotsPositions.exists(p => distance(v, p) < 0.5))
          .map(v =>
            Position(
              Constants.OBSTACLE_NAME,
              Math.round(v.y.toDouble * 10.0) / 10.0,
              Math.round(v.x.toDouble * 10.0) / 10.0,
              0.15,
              0.0))
          .toSet

      context.become(receiveWithStorage(robotsPositions, newObstaclesPositions))

    case a @ FromAvatarToRobot(id, _) =>
      idToMover(id) ! a

    case other =>
      log.error("FullKnowledgeExperimentRunner other [{}] from [{}]", other, sender())
  }

}

object RobotMover {

  class PioneerP3dx(api: VRepAPI, id: String) {
    private val speed = 1.25f
    private val leftMotor = api.joint.withVelocityControl("Pioneer_p3dx_leftMotor" + id).get
    private val rightMotor = api.joint.withVelocityControl("Pioneer_p3dx_rightMotor" + id).get

    def moveForward(): Unit = {
      leftMotor.setTargetVelocity(speed)
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
  }

  case class TargetPoint(y: Double, x: Double)

  sealed trait RobotCommand
  case object Forward extends RobotCommand
  case object RotateRight extends RobotCommand
  case object RotateLeft extends RobotCommand
  case object Stop extends RobotCommand

}

class RobotMover(api: VRepAPI, id: String) extends Actor with ActorLogging {
  import RobotMover._

  private val robot = new PioneerP3dx(api, id)

  private val moveRegExp = "move=([\\-\\d\\.]+),([\\-\\d\\.]+)".r

  override def receive: Receive = receiveWithTargetPoint(None, Stop)

  def receiveWithTargetPoint(targetPoint: Option[TargetPoint], previousCommand: RobotCommand): Receive = {
    case robotPosition: Position =>
      val updatedAngle = robotPosition.angle
      if (targetPoint.isDefined) {
        val nextStep = targetPoint.get
        val angleToPoint = Math.atan2(nextStep.y - robotPosition.y, nextStep.x - robotPosition.x) * 180.0 / Math.PI
        val angleDiff = updatedAngle - angleToPoint
        val angleAbsDiff = Math.abs(angleDiff)
        if (angleAbsDiff < 10) {
          if (previousCommand != Forward) {
            log.info("{} -> Pos {}. Angle diff {}, forward", id, robotPosition, angleDiff)
            robot.moveForward()
            context.become(receiveWithTargetPoint(targetPoint, Forward))
          }
        } else {
          if (angleDiff > 0) {
            if (previousCommand != RotateRight) {
              log.info("{} -> Pos {}. Angle diff {}, rotating right", id, robotPosition, angleDiff)
              robot.right()
              context.become(receiveWithTargetPoint(targetPoint, RotateRight))
            }
          } else {
            if (previousCommand != RotateLeft) {
              log.info("{} -> Pos {}. Angle diff {}, rotating left", id, robotPosition, angleDiff)
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

    case FromAvatarToRobot(_id, "stop") if _id == id =>
      if (previousCommand != Stop) {
        robot.stop()
        context.become(receiveWithTargetPoint(None, Stop))
      }

    case FromAvatarToRobot(_id, command) if _id == id =>
      Try {
        val moveRegExp(yString, xString) = command
        log.info(id + " -> " + command)
        context.become(receiveWithTargetPoint(Some(TargetPoint(yString.toDouble, xString.toDouble)), previousCommand))
      }

    case other =>
      log.error("RobotMover: unknown message [{}] from [{}]", other, sender())
  }
}
