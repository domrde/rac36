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
}

class Worker(request: PathfinderBrain.FindPath) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val system = context.system

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
        log.error("Current position of client not specified.")
        Future(None)
    }
  }

  override def receive: Receive = {
    case r: PathfinderBrain.FindPath =>
      doCalculation(r) pipeTo self

    case Some(results: RunResults) =>
      val points = results.path.path.map { case Point(y, x) => PathPoint(y, x) }.reverse
      log.info("Successful pathfinding: {}", points)
      context.parent ! PathfinderBrain.PathFound(request.client, points, isStraightLine = false)
      context.stop(self)

    case None =>
      request.sensory.find { case Position(name, _, _, _, _) => name == request.client } match {
        case Some(cur) =>
          log.info("Unsuccessful pathfinding. Straight line.")
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
          log.info("Unsuccessful pathfinding.")
      }
      context.stop(self)

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  self ! request
}

//object Test extends App {
//  import scala.concurrent.duration._
//  import akka.testkit.TestProbe
//  import akka.actor.ActorSystem
//  val fullKnowledge =
//    PathfinderBrain.FindPath("#1",Set(
//      Position("obstacle",1.8500,0.14999,0.15,0.0),
//      Position("obstacle",9.825,5.624,0.15,0.0),
//      Position("obstacle",3.7562,1.928,0.15,0.0),
//      Position("obstacle",4.518,7.260,0.15,0.0),
//      Position("obstacle",4.299,1.6749,0.15,0.0),
//      Position("obstacle",7.344,4.020,0.15,0.0),
//      Position("obstacle",5.350,9.724,0.15,0.0),
//      Position("obstacle",9.300,0.14999,0.15,0.0),
//      Position("obstacle",2.3500,0.14999,0.15,0.0),
//      Position("obstacle",6.350,0.14999,0.15,0.0),
//      Position("obstacle",0.27499,1.6749,0.15,0.0),
//      Position("obstacle",1.3499,9.724,0.15,0.0),
//      Position("obstacle",2.668,2.4357,0.15,0.0),
//      Position("obstacle",0.27499,2.674,0.15,0.0),
//      Position("obstacle",0.27499,0.6749,0.15,0.0),
//      Position("obstacle",0.849,9.724,0.15,0.0),
//      Position("obstacle",3.350,0.14999,0.15,0.0),
//      Position("obstacle",2.1,2.6892,0.15,0.0),
//      Position("obstacle",9.825,8.124,0.15,0.0),
//      Position("obstacle",4.350,0.14999,0.15,0.0),
//      Position("obstacle",0.2750,9.125,0.15,0.0),
//      Position("obstacle",6.350,9.724,0.15,0.0),
//      Position("obstacle",7.422,4.310,0.15,0.0),
//      Position("obstacle",9.825,6.624,0.15,0.0),
//      Position("obstacle",5.850,0.14999,0.15,0.0),
//      Position("obstacle",9.824,1.1749,0.15,0.0),
//      Position("obstacle",0.2750,3.6249,0.15,0.0),
//      Position("obstacle",7.80,0.14999,0.15,0.0),
//      Position("obstacle",2.850,0.14999,0.15,0.0),
//      Position("obstacle",4.028,1.8017,0.15,0.0),
//      Position("obstacle",8.300,9.724,0.15,0.0),
//      Position("obstacle",3.2124,2.182,0.15,0.0),
//      Position("obstacle",0.2750,6.124,0.15,0.0),
//      Position("obstacle",5.606,6.753,0.15,0.0),
//      Position("obstacle",9.825,6.124,0.15,0.0),
//      Position("obstacle",9.800,9.724,0.15,0.0),
//      Position("obstacle",8.800,0.14999,0.15,0.0),
//      Position("obstacle",2.7499,1.2791,0.15,0.0),
//      Position("obstacle",9.825,3.6249,0.15,0.0),
//      Position("obstacle",8.800,9.724,0.15,0.0),
//      Position("obstacle",9.824,2.674,0.15,0.0),
//      Position("obstacle",5.87,6.6267,0.15,0.0),
//      Position("obstacle",7.034,2.8613,0.15,0.0),
//      Position("obstacle",1.8500,9.724,0.15,0.0),
//      Position("obstacle",7.189,3.440,0.15,0.0),
//      Position("obstacle",5.334,6.880,0.15,0.0),
//      Position("obstacle",5.850,9.724,0.15,0.0),
//      Position("obstacle",9.800,0.14999,0.15,0.0),
//      Position("obstacle",0.34999,0.14999,0.15,0.0),
//      Position("obstacle",0.27499,3.1249,0.15,0.0),
//      Position("obstacle",3.8500,9.724,0.15,0.0),
//      Position("obstacle",9.825,9.125,0.15,0.0),
//      Position("obstacle",9.825,5.124,0.15,0.0),
//      Position("obstacle",2.3500,9.724,0.15,0.0),
//      Position("obstacle",9.825,7.624,0.15,0.0),
//      Position("obstacle",1.3499,0.14999,0.15,0.0),
//      Position("obstacle",3.8500,0.14999,0.15,0.0),
//      Position("obstacle",7.300,0.14999,0.15,0.0),
//      Position("obstacle",0.2750,7.124,0.15,0.0),
//      Position("obstacle",6.878,2.281,0.15,0.0),
//      Position("obstacle",6.149,6.5,0.15,0.0),
//      Position("obstacle",0.34999,9.724,0.15,0.0),
//      Position("obstacle",9.824,0.6749,0.15,0.0),
//      Position("obstacle",5.350,0.14999,0.15,0.0),
//      Position("obstacle",1.8529,2.816,0.15,0.0),
//      Position("obstacle",0.27500,4.124,0.15,0.0),
//      Position("obstacle",0.27500,7.624,0.15,0.0),
//      Position("obstacle",9.824,3.1249,0.15,0.0),
//      Position("obstacle",4.246,7.387,0.15,0.0),
//      Position("obstacle",3.7029,7.641,0.15,0.0),
//      Position("obstacle",9.825,8.624,0.15,0.0),
//      Position("obstacle",6.956,2.5715,0.15,0.0),
//      Position("obstacle",4.790,7.133,0.15,0.0),
//      Position("obstacle",7.267,3.7306,0.15,0.0),
//      Position("obstacle",3.484,2.0553,0.15,0.0),
//      Position("obstacle",8.300,0.14999,0.15,0.0),
//      Position("obstacle",9.82,2.174,0.15,0.0),
//      Position("obstacle",2.9405,2.3089,0.15,0.0),
//      Position("obstacle",0.2749,2.174,0.15,0.0),
//      Position("obstacle",6.849,9.724,0.15,0.0),
//      Position("obstacle",6.849,0.14999,0.15,0.0),
//      Position("obstacle",4.850,0.14999,0.15,0.0),
//      Position("obstacle",0.27500,5.124,0.15,0.0),
//      Position("obstacle",7.300,9.724,0.15,0.0),
//      Position("obstacle",9.825,7.124,0.15,0.0),
//      Position("obstacle",4.350,9.724,0.15,0.0),
//      Position("obstacle",0.27500,6.624,0.15,0.0),
//      Position("obstacle",0.27499,1.1749,0.15,0.0),
//      Position("obstacle",9.825,4.124,0.15,0.0),
//      Position("obstacle",2.850,9.724,0.15,0.0),
//      Position("obstacle",9.824,1.6749,0.15,0.0),
//      Position("obstacle",0.2750,4.624,0.15,0.0),
//      Position("obstacle",0.2750,5.624,0.15,0.0),
//      Position("obstacle",9.82,4.624,0.15,0.0),
//      Position("obstacle",5.062,7.007,0.15,0.0),
//      Position("obstacle",7.80,9.724,0.15,0.0),
//      Position("obstacle",2.396,2.56,0.15,0.0),
//      Position("obstacle",7.111,3.151,0.15,0.0),
//      Position("#1",2.7499,1.2867,0.5,-0.032)),
//      PathPoint(5.0, 5.0)
//    )
//
//  implicit val system = ActorSystem("WorkerTest")
//  val testProbe = TestProbe()
//  testProbe.childActorOf(Worker.apply(fullKnowledge))
//  testProbe.expectMsgType[PathfinderBrain.PathFound](25.minutes)
//}
