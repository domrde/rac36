package playground

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.ConfigFactory
import common.Constants
import common.messages.SensoryInformation.{Position, Sensory}
import playground.RelativeToAbsoluteConversion.Vec2
import vivarium.Avatar.FromAvatarToRobot
import vrepapiscala.VRepAPI
import vrepapiscala.common.{EulerAngles, Vec3}
import vrepapiscala.sensors.{PositionSensor, ProximitySensor}

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

  private def time[A](f: => A) = {
    val s = System.nanoTime
    val res = f
    log.info("Elapsed {} ms", (System.nanoTime - s) / 1e6)
    res
  }

  system.scheduler.schedule(0.millis, 400.millis) {
    time {
      val payload = robots.flatMap { case (id, robot) =>
        val robotPosition = robot.gps.position
        val obstacles = robot.sensors.par.flatMap { sensor =>
          val read = sensor.read
          if (read.detectionState && zeroVelocity(read.detectedObject.velocity)) {
            val p = read.detectedObject.position
            Some(Position(Constants.OBSTACLE_NAME, p.y, p.x, 0.3, 0))
          } else {
            None
          }
        }.toList
        Position(id, robotPosition.y, robotPosition.x, 0.5, robot.gps.orientation.gamma * 180 / Math.PI) :: obstacles
      }.toList.toSet
      context.parent ! Sensory(null, payload)
    }
  }

  def zeroVelocity(velocity: (Vec3, EulerAngles)): Boolean = {
    velocity._1.x == 0 && velocity._1.y == 0 && velocity._1.z == 0 &&
      velocity._2.alpha == 0 && velocity._2.beta == 0 && velocity._2.gamma == 0
  }

  //  3  4   6
  //   \ |  /
  // 1--####--8
  //    ####
  //    ####
  // 16-####--9
  //   /    \
  //  14    11

  class PioneerP3dx(api: VRepAPI, id: String) {
    private val speed = 3f
    private val leftMotor = api.joint.withVelocityControl("Pioneer_p3dx_leftMotor" + id).get
    private val rightMotor = api.joint.withVelocityControl("Pioneer_p3dx_rightMotor" + id).get
    val sensors: List[ProximitySensor] = (1 to 16).map(i => api.sensor.proximity("Pioneer_p3dx_ultrasonicSensor" + i + id).get).toList

    def moveForward(): Unit = {
      leftMotor.setTargetVelocity(speed)
      rightMotor.setTargetVelocity(speed)
    }

    def moveBackward(): Unit = {
      leftMotor.setTargetVelocity(-speed)
      rightMotor.setTargetVelocity(-speed)
    }

    def rotateLeft(): Unit = {
      leftMotor.setTargetVelocity(-0.25f * speed)
      rightMotor.setTargetVelocity(0.25f * speed)
    }

    def rotateRight(): Unit = {
      leftMotor.setTargetVelocity(0.25f * speed)
      rightMotor.setTargetVelocity(-0.25f * speed)
    }

    def stop(): Unit = {
      leftMotor.setTargetVelocity(0)
      rightMotor.setTargetVelocity(0)
    }

    val gps: PositionSensor = api.sensor.position("Pioneer_p3dx_gps" + id).get
  }

  override def receive: Receive = {
    case FromAvatarToRobot(id, "forward") =>
      robots(id).moveForward()

    case FromAvatarToRobot(id, "backward") =>
      robots(id).moveBackward()

    case FromAvatarToRobot(id, "left") =>
      robots(id).rotateLeft()

    case FromAvatarToRobot(id, "right") =>
      robots(id).rotateRight()

    case FromAvatarToRobot(id, "stop") =>
      robots(id).stop()

    case other =>
      log.error("VRepRemoteControl: unknown message [{}] from [{}]", other, sender())
  }

  override def postStop(): Unit = {
    super.postStop()
    api.simulation.stop()
  }

}
