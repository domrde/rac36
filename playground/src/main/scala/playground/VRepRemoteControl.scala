package playground

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.ConfigFactory
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

  system.scheduler.schedule(2.millis, 1000.millis) {
    val payload = robots.map { case (id, robot) =>
      Position(id, robot.gps.position.y, robot.gps.position.x, robot.gps.orientation.gamma)
    }.toSet
    context.parent ! Sensory(null, payload)
  }

  class PioneerP3dx(api: VRepAPI, id: String) {
    private val speed = 2f
    private val leftMotor = api.joint.withVelocityControl("Pioneer_p3dx_leftMotor" + id).get
    private val rightMotor = api.joint.withVelocityControl("Pioneer_p3dx_rightMotor" + id).get
    private val frontSensors =
      for (i <- 1 to 8)
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

    def leftSensor = frontSensors(1)

    def rightSensor = frontSensors(6)

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
