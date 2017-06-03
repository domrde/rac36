package pathfinder

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import com.dda.brain.BrainMessages.Position
import com.dda.brain.PathfinderBrain
import com.dda.brain.PathfinderBrain.PathPoint
import pathfinder.pathfinding.Pathfinder

import scala.concurrent.Future

/**
  * Created by dda on 07.05.17.
  */
object Worker {
  def apply(request: PathfinderBrain.FindPath): Props = Props(classOf[Worker], request)
}

class Worker(request: PathfinderBrain.FindPath) extends Actor with ActorLogging {
  import Globals._
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  val dims = Point(10.0, 10.0)

  def doCalculation(request: PathfinderBrain.FindPath, curPos: Position): List[Point] = {
    val distance = Globals.distance(Point(curPos.y, curPos.x), Point(request.to.y, request.to.x))
    if (distance < Globals.STEP_OF_PATH) {
      List(Point(curPos.y, curPos.x))
    } else {
      val obstacles = (request.sensory - curPos).map { case Position(_, y, x, r, _) =>
        Obstacle(y, x, r)
      }.toList
      Pathfinder.findPath(dims, curPos, request.to, obstacles)
    }
  }

  override def receive: Receive = {
    case r: PathfinderBrain.FindPath =>
      request.sensory.find { case Position(name, _, _, _, _) => name == request.client } match {
        case Some(cur) =>
          context.become(receiveWithClientPosition(cur))
          self ! r

        case None =>
          log.info("Current position of {} not found", request.client)
          context.stop(self)
      }

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  def receiveWithClientPosition(curPos: Position): Receive = {
    case r: PathfinderBrain.FindPath =>
      val foundPath = doCalculation(r, curPos)
      if (foundPath.isEmpty) {
        log.info("Unsuccessful pathfinding.")
        context.stop(self)
      } else {
        log.info("Successful pathfinding.")
        context.parent ! PathfinderBrain.PathFound(request.client, foundPath.map(pointToPathPoint), isStraightLine = false)
        context.stop(self)
      }

    case Failure(cause) =>
      cause.printStackTrace()

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  self ! request
  log.info("Got request from {} {}", request.client, request)
}
