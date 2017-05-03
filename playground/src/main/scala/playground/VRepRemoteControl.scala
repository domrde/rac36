package playground

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.ConfigFactory
import common.Constants
import common.messages.SensoryInformation.{Position, Sensory}
import vivarium.Avatar.FromAvatarToRobot
import vrepapiscala.VRepAPI

import scala.collection.JavaConversions._
import scala.concurrent.duration._

/**
  * Created by dda on 14.04.17.
  */
class VRepRemoteControl extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system
  private val config = ConfigFactory.load()

  private val api = VRepAPI.connect("127.0.0.1", 19997).get

  private val robots: Map[String, PioneerP3dx] = config.getStringList("playground.car-ids")
    .map(id => id -> new PioneerP3dx(api, id)).toMap

  api.simulation.start()


  system.scheduler.schedule(5.millis, 10.millis) {
    val payload = robots.flatMap { case (id, robot) =>
      val front = robot.frontSensor.read
      val robotAngle = robot.gps.orientation.gamma * 180 / Math.PI
      val obstacle =
        if (front.detectionState) {
          if (-45 < robotAngle && robotAngle < 0 || 0 < robotAngle && robotAngle < 45) {
            // forward
            Some(Position(
              Constants.OBSTACLE_NAME,
              robot.gps.position.y + front.detectedPoint.y,
              robot.gps.position.x + front.detectedPoint.x + 0.15,
              0.15, 0.15, 0))
          } else if (-45 < robotAngle && robotAngle < -135) {
            // right
            Some(Position(
              Constants.OBSTACLE_NAME,
              robot.gps.position.y + front.detectedPoint.y - 0.15,
              robot.gps.position.x + front.detectedPoint.x,
              0.15, 0.15, 0))
          } else if (-135 < robotAngle && robotAngle < -180 || 135 < robotAngle && robotAngle < 180) {
            // down
            Some(Position(
              Constants.OBSTACLE_NAME,
              robot.gps.position.y + front.detectedPoint.y,
              robot.gps.position.x + front.detectedPoint.x - 0.15,
              0.15, 0.15, 0))
          } else if (45 < robotAngle && robotAngle < 135) {
            // left
            Some(Position(
              Constants.OBSTACLE_NAME,
              robot.gps.position.y + front.detectedPoint.y + 0.15,
              robot.gps.position.x + front.detectedPoint.x,
              0.15, 0.15, 0))
          } else {
            None
          }
        } else {
          None
        }
      List(Some(Position(id, robot.gps.position.y, robot.gps.position.x, 0.5, 0.5, robotAngle)), obstacle).flatten
    }.toSet
    context.parent ! Sensory(null, payload)
  }

  class PioneerP3dx(api: VRepAPI, id: String) {
    private val speed = 2f
    private val leftMotor = api.joint.withVelocityControl("Pioneer_p3dx_leftMotor" + id).get
    private val rightMotor = api.joint.withVelocityControl("Pioneer_p3dx_rightMotor" + id).get
    private val sensors =
      for (i <- 1 to 16)
        yield api.sensor.proximity("Pioneer_p3dx_ultrasonicSensor" + i + id).get

    def moveForward(): Unit = {
      leftMotor.setTargetVelocity(speed)
      rightMotor.setTargetVelocity(speed)
    }

    def moveBackward(): Unit = {
      leftMotor.setTargetVelocity(-speed)
      rightMotor.setTargetVelocity(-speed)
    }

    def rotateLeft(): Unit = {
      leftMotor.setTargetVelocity(-speed)
      rightMotor.setTargetVelocity(speed)
    }

    def rotateRight(): Unit = {
      leftMotor.setTargetVelocity(speed)
      rightMotor.setTargetVelocity(-speed)
    }

    def stop(): Unit = {
      leftMotor.setTargetVelocity(0)
      rightMotor.setTargetVelocity(0)
    }

    val frontSensor = sensors(4)

    val leftSensor = sensors(1)

    val rightSensor = sensors(8)

    val gps = api.sensor.position("Pioneer_p3dx_gps" + id).get
  }

  override def receive: Receive = {
    case FromAvatarToRobot(id, "forward") => robots(id).moveForward()

    case FromAvatarToRobot(id, "backward") => robots(id).moveBackward()

    case FromAvatarToRobot(id, "left") => robots(id).rotateLeft()

    case FromAvatarToRobot(id, "right") => robots(id).rotateRight()

    case other =>
      log.error("VRepRemoteControl: unknown message [{}] from [{}]", other, sender())
  }

  override def postStop(): Unit = {
    super.postStop()
    api.simulation.stop()
  }
}
