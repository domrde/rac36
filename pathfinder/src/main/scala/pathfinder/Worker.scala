package pathfinder

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

  def doCalculation(request: PathfinderBrain.FindPath, curPos: Position): FutureO[RunResults] = {
    val distance = Globals.distance(Point(curPos.y, curPos.x), Point(request.to.y, request.to.x))
    if (distance < Globals.STEP_OF_PATH) {
      FutureO(Future.successful(Some(RunResults(List(Point(curPos.y, curPos.x)), "Manual one point path"))))
    } else {
      val obstacles = (request.sensory - curPos).map { case Position(_, y, x, r, _) =>
        Obstacle(y, x, r)
      }.toList
      Pathfinder.findPath(dims, curPos, request.to, obstacles)
    }
  }

  private def formStraightLine(cur: Position) = {
    val distance = Globals.distance(cur, request.to)
    val part = Globals.STEP_OF_PATH / distance + 0.05
    (0.0 to 1.0 by part).map(Globals.pointInBetween(cur, request.to)).toList
  }

  override def receive: Receive = {
    case r: PathfinderBrain.FindPath =>
      request.sensory.find { case Position(name, _, _, _, _) => name == request.client } match {
        case Some(cur) =>
          context.become(receiveWithClientPosition(cur))
          self ! r

        case None =>
          log.info("Current position not found")
          context.stop(self)
      }

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  def receiveWithClientPosition(curPos: Position): Receive = {
    case r: PathfinderBrain.FindPath =>
      doCalculation(r, curPos).future pipeTo self

    case Some(results: RunResults) =>
      log.info("Successful pathfinding: {} {}", results.path, results.message)
      context.parent ! PathfinderBrain.PathFound(request.client, results.path.map(pointToPathPoint), isStraightLine = false)
      context.stop(self)

    case None =>
      log.info("Unsuccessful pathfinding. Straight line.")
      context.parent ! PathfinderBrain.PathFound(request.client, formStraightLine(curPos).map(pointToPathPoint), isStraightLine = true)
      context.stop(self)

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  self ! request
}

//object Test extends App {
//  import akka.actor.ActorSystem
//  import akka.testkit.TestProbe
//
//  import scala.concurrent.duration._
//
//  val fullKnowledge =
//    PathfinderBrain.FindPath("#1",Set(
//      Position("obstacle",5.606215476989746,6.753570556640625,0.15,0.0),
//      Position("obstacle",2.9405388832092285,2.3089263439178467,0.15,0.0),
//      Position("obstacle",6.350000381469727,9.724998474121094,0.15,0.0),
//      Position("obstacle",3.350001335144043,9.724998474121094,0.15,0.0),
//      Position("obstacle",4.850000381469727,0.14999771118164062,0.15,0.0),
//      Position("obstacle",3.484323501586914,2.0553555488586426,0.15,0.0),
//      Position("obstacle",7.80000114440918,0.14999771118164062,0.15,0.0),
//      Position("obstacle",0.27500486373901367,7.624997615814209,0.15,0.0),
//      Position("obstacle",4.246754169464111,7.387497901916504,0.15,0.0),
//      Position("obstacle",2.7499868869781494,1.2796499729156494,0.15,0.0),
//      Position("obstacle",7.034125328063965,2.8613333702087402,0.15,0.0),
//      Position("obstacle",7.80000114440918,9.724998474121094,0.15,0.0),
//      Position("obstacle",0.27499741315841675,1.1749961376190186,0.15,0.0),
//      Position("obstacle",9.300003051757812,0.14999771118164062,0.15,0.0),
//      Position("obstacle",4.850000381469727,9.724998474121094,0.15,0.0),
//      Position("obstacle",3.8500008583068848,9.724998474121094,0.15,0.0),
//      Position("obstacle",8.800002098083496,9.724998474121094,0.15,0.0),
//      Position("obstacle",1.3499999046325684,0.14999771118164062,0.15,0.0),
//      Position("obstacle",7.189416885375977,3.440889358520508,0.15,0.0),
//      Position("obstacle",0.27499765157699585,2.674996852874756,0.15,0.0),
//      Position("obstacle",9.824999809265137,3.1249985694885254,0.15,0.0),
//      Position("obstacle",5.350000381469727,9.724998474121094,0.15,0.0),
//      Position("obstacle",0.34999704360961914,0.14999771118164062,0.15,0.0),
//      Position("obstacle",0.2750042676925659,7.124996185302734,0.15,0.0),
//      Position("obstacle",1.8500008583068848,9.724998474121094,0.15,0.0),
//      Position("obstacle",0.27500078082084656,4.124998092651367,0.15,0.0),
//      Position("obstacle",3.2124314308166504,2.182140827178955,0.15,0.0),
//      Position("obstacle",5.062431335449219,7.007141590118408,0.15,0.0),
//      Position("obstacle",9.82499885559082,2.174997091293335,0.15,0.0),
//      Position("obstacle",2.1248619556427,2.6892828941345215,0.15,0.0),
//      Position("obstacle",9.825002670288086,5.124998092651367,0.15,0.0),
//      Position("obstacle",9.825003623962402,6.124997615814209,0.15,0.0),
//      Position("obstacle",1.8529701232910156,2.816068410873413,0.15,0.0),
//      Position("obstacle",9.825004577636719,7.124996185302734,0.15,0.0),
//      Position("obstacle",6.350000381469727,0.14999771118164062,0.15,0.0),
//      Position("obstacle",0.2749985158443451,2.174997091293335,0.15,0.0),
//      Position("obstacle",5.87810754776001,6.6267852783203125,0.15,0.0),
//      Position("obstacle",4.350000381469727,9.724998474121094,0.15,0.0),
//      Position("obstacle",1.8500008583068848,0.14999771118164062,0.15,0.0),
//      Position("obstacle",0.2750033140182495,6.124997615814209,0.15,0.0),
//      Position("obstacle",8.300002098083496,9.724998474121094,0.15,0.0),
//      Position("obstacle",2.850001811981201,9.724998474121094,0.15,0.0),
//      Position("obstacle",9.824997901916504,1.1749961376190186,0.15,0.0),
//      Position("obstacle",4.350000381469727,0.14999771118164062,0.15,0.0),
//      Position("obstacle",2.396754741668701,2.56249737739563,0.15,0.0),
//      Position("obstacle",3.7562155723571777,1.928570032119751,0.15,0.0),
//      Position("obstacle",5.350000381469727,0.14999771118164062,0.15,0.0),
//      Position("obstacle",6.149999618530273,6.5,0.15,0.0),
//      Position("obstacle",0.27500200271606445,5.124998092651367,0.15,0.0),
//      Position("obstacle",0.849998950958252,9.724998474121094,0.15,0.0),
//      Position("obstacle",0.27499788999557495,1.6749964952468872,0.15,0.0),
//      Position("obstacle",7.111771106719971,3.151111364364624,0.15,0.0),
//      Position("obstacle",9.825004577636719,7.624997615814209,0.15,0.0),
//      Position("obstacle",0.2750002145767212,3.6249983310699463,0.15,0.0),
//      Position("obstacle",0.27499961853027344,3.1249985694885254,0.15,0.0),
//      Position("obstacle",6.801187992095947,1.991999864578247,0.15,0.0),
//      Position("obstacle",0.34999704360961914,9.724998474121094,0.15,0.0),
//      Position("obstacle",6.878833770751953,2.281777858734131,0.15,0.0),
//      Position("obstacle",0.2750013470649719,4.624998092651367,0.15,0.0),
//      Position("obstacle",9.825000762939453,4.124998092651367,0.15,0.0),
//      Position("obstacle",6.849999904632568,9.724998474121094,0.15,0.0),
//      Position("obstacle",2.3500008583068848,0.14999771118164062,0.15,0.0),
//      Position("obstacle",6.956479549407959,2.5715556144714355,0.15,0.0),
//      Position("obstacle",0.275005578994751,8.124998092651367,0.15,0.0),
//      Position("obstacle",9.824996948242188,0.6749958992004395,0.15,0.0),
//      Position("obstacle",9.825005531311035,8.124998092651367,0.15,0.0),
//      Position("obstacle",9.825000762939453,3.6249983310699463,0.15,0.0),
//      Position("obstacle",9.825006484985352,8.624998092651367,0.15,0.0),
//      Position("obstacle",3.8500008583068848,0.14999771118164062,0.15,0.0),
//      Position("obstacle",3.350001335144043,0.14999771118164062,0.15,0.0),
//      Position("obstacle",9.300003051757812,9.724998474121094,0.15,0.0),
//      Position("obstacle",5.850000381469727,0.14999771118164062,0.15,0.0),
//      Position("obstacle",3.7029693126678467,7.641069412231445,0.15,0.0),
//      Position("obstacle",7.422354221343994,4.310222625732422,0.15,0.0),
//      Position("obstacle",0.2750070095062256,9.125,0.15,0.0),
//      Position("obstacle",9.82500171661377,4.624998092651367,0.15,0.0),
//      Position("obstacle",4.790538787841797,7.133927345275879,0.15,0.0),
//      Position("obstacle",4.518646240234375,7.260712623596191,0.15,0.0),
//      Position("obstacle",4.299999713897705,1.6749992370605469,0.15,0.0),
//      Position("obstacle",6.849999904632568,0.14999771118164062,0.15,0.0),
//      Position("obstacle",8.300002098083496,0.14999771118164062,0.15,0.0),
//      Position("obstacle",0.27500391006469727,6.624997138977051,0.15,0.0),
//      Position("obstacle",9.800004005432129,0.14999771118164062,0.15,0.0),
//      Position("obstacle",7.300001621246338,9.724996566772461,0.15,0.0),
//      Position("obstacle",2.668646812438965,2.4357118606567383,0.15,0.0),
//      Position("obstacle",9.825004577636719,6.624997138977051,0.15,0.0),
//      Position("obstacle",1.3499999046325684,9.724998474121094,0.15,0.0),
//      Position("obstacle",5.850000381469727,9.724998474121094,0.15,0.0),
//      Position("obstacle",9.824997901916504,1.6749964952468872,0.15,0.0),
//      Position("obstacle",7.26706266401982,3.7306671142578125,0.15,0.0),
//      Position("obstacle",0.2750025987625122,5.624997615814209,0.15,0.0),
//      Position("obstacle",4.028107643127441,1.8017845153808594,0.15,0.0),
//      Position("obstacle",8.800002098083496,0.14999771118164062,0.15,0.0),
//      Position("obstacle",5.334323406219482,6.880356311798096,0.15,0.0),
//      Position("obstacle",0.849998950958252,0.14999771118164062,0.15,0.0),
//      Position("obstacle",2.3500008583068848,9.724998474121094,0.15,0.0),
//      Position("obstacle",7.300001621246338,0.14999628067016602,0.15,0.0),
//      Position("obstacle",9.825007438659668,9.125,0.15,0.0),
//      Position("obstacle",9.825002670288086,5.624997615814209,0.15,0.0),
//      Position("#1",1.0,1.0,0.5,32.9348511589105)),
//      PathPoint(5.0,5.0)
//    )
//
//  implicit val system = ActorSystem("WorkerTest")
//  val testProbe = TestProbe()
//  testProbe.childActorOf(Worker.apply(fullKnowledge))
//  testProbe.expectMsgType[PathfinderBrain.PathFound](25.minutes)
//}