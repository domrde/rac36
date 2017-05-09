package pathfinder

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import com.dda.brain.BrainMessages.Position
import com.dda.brain.PathfinderBrain
import com.dda.brain.PathfinderBrain.PathPoint
import pathfinder.Learning.{Obstacle, Path, Point, RunResults}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by dda on 07.05.17.
  */
object Worker {
  def apply(request: PathfinderBrain.FindPath): Props = Props(classOf[Worker], request)

  val dims = Point(10.0, 10.0)

  def doCalculation(request: PathfinderBrain.FindPath)(implicit ec: ExecutionContext): Future[Option[RunResults]] = {
    val currentClientPosition = request.sensory.find { case Position(name, _, _, _, _) => name == request.client }

    currentClientPosition match {
      case Some(curPos) =>
        val distance = Globals.distance(Point(curPos.y, curPos.x), Point(request.to.y, request.to.x))
        if (distance < Globals.STEP_OF_PATH) {
          Future(Some(RunResults(Path(List(Point(curPos.y, curPos.x))), 0.0, isCorrect = true, "Manual one point path")))
        } else {
          val obstacles = (request.sensory - curPos).map { case Position(_, y, x, r, _) =>
            Obstacle(y, x, r)
          }.toList
          val start = Point(curPos.y, curPos.x)
          val finish = Point(request.to.y, request.to.x)
          new Learning().runRegressionSVM(obstacles, dims, start, finish)
        }

      case None =>
        Future(None)
    }
  }
}

class Worker(request: PathfinderBrain.FindPath) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

  override def receive: Receive = {
    case r: PathfinderBrain.FindPath =>
      Worker.doCalculation(r) pipeTo self

    case Some(results: RunResults) =>
      log.info("Successful pathfinding, sending result to {}", context.parent)
      val points = results.path.path.map { case Point(y, x) => PathPoint(y, x) }.reverse
      context.parent ! PathfinderBrain.PathFound(request.client, points, isStraightLine = false)
      context.stop(self)

    case None =>
      log.info("Unsuccessful pathfinding")
      request.sensory.find { case Position(name, _, _, _, _) => name == request.client } match {
        case Some(cur) =>
          def pointInBetween(t: Double): PathPoint = {
            PathPoint((1.0 - t) * cur.y + t * request.to.y, (1.0 - t) * cur.x + t * request.to.x)
          }

          val distance = Globals.distance(Point(cur.y, cur.x), Point(request.to.y, request.to.x))

          if (distance < Globals.STEP_OF_PATH) {
            context.parent ! PathfinderBrain.PathFound(request.client, List(PathPoint(cur.y, cur.x)), isStraightLine = true)
          } else {
            val part = Globals.STEP_OF_PATH / distance + 0.05
            val steps = (0.0 to 1.0 by part).map(pointInBetween).toList
            context.parent ! PathfinderBrain.PathFound(request.client, steps, isStraightLine = true)
          }

        case None =>
      }
      context.stop(self)

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  self ! request
}
