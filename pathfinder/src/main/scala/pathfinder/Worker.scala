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

  def doCalculation(request: PathfinderBrain.FindPath, curPos: Position): FutureO[RunResults] = {
    if (request.sensory.size < 5) {
      FutureO(Future.successful(None))
    } else {
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
      doCalculation(r, curPos).future pipeTo self

    case Some(results: RunResults) =>
      log.info("Successful pathfinding: {}", results.message)
      context.parent ! PathfinderBrain.PathFound(request.client, results.path.map(pointToPathPoint), isStraightLine = false)
      context.stop(self)

    case None =>
      log.info("Unsuccessful pathfinding. ")
      context.stop(self)

    case Failure(cause) =>
      cause.printStackTrace()

    case other =>
      log.error("[-] Worker: received other: [{}] from [{}]", other, sender())
  }

  self ! request
  log.info("Got request from {}", request.client)
}

//object Test extends App {
//  import akka.actor.ActorSystem
//  import akka.testkit.TestProbe
//
//  import scala.concurrent.duration._
//
//  val request1 = PathfinderBrain.FindPath("#1",Set(
//    Position("obstacle",2.356346368789673,6.374999046325684,0.15,0.0),
//    Position("obstacle",6.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.350001335144043,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.2529687881469727,3.2410683631896973,0.15,0.0),
//    Position("obstacle",4.850000381469727,0.14999771118164062,0.15,0.0),
//    Position("#0",1.9413628578186035,8.259160995483398,0.5,14.323946585817874),
//    Position("obstacle",7.80000114440918,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500486373901367,7.624997615814209,0.15,0.0),
//    Position("obstacle",2.6124300956726074,2.6071407794952393,0.15,0.0),
//    Position("obstacle",7.034125328063965,2.8613333702087402,0.15,0.0),
//    Position("obstacle",7.80000114440918,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27499741315841675,1.1749961376190186,0.15,0.0),
//    Position("obstacle",3.3955764770507812,6.974999904632568,0.15,0.0),
//    Position("#1",2.5743579864501953,1.3179742097854614,0.5,49.346388715482114),
//    Position("obstacle",9.300003051757812,0.14999771118164062,0.15,0.0),
//    Position("obstacle",4.850000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.8500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.3499999046325684,0.14999771118164062,0.15,0.0),
//    Position("obstacle",7.189416885375977,3.440889358520508,0.15,0.0),
//    Position("obstacle",0.27499765157699585,2.674996852874756,0.15,0.0),
//    Position("obstacle",9.824999809265137,3.1249985694885254,0.15,0.0),
//    Position("obstacle",5.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.096538782119751,6.22499942779541,0.15,0.0),
//    Position("obstacle",0.2750042676925659,7.124996185302734,0.15,0.0),
//    Position("obstacle",1.7967532873153687,2.987497329711914,0.15,0.0),
//    Position("obstacle",0.27499663829803467,0.6749958992004395,0.15,0.0),
//    Position("obstacle",2.068645477294922,2.8607118129730225,0.15,0.0),
//    Position("obstacle",1.8500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27500078082084656,4.124998092651367,0.15,0.0),
//    Position("obstacle",4.1749982833862305,7.424999713897705,0.15,0.0),
//    Position("obstacle",9.825002670288086,5.124998092651367,0.15,0.0),
//    Position("obstacle",9.825003623962402,6.124997615814209,0.15,0.0),
//    Position("obstacle",9.825004577636719,7.124996185302734,0.15,0.0),
//    Position("obstacle",6.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.2749985158443451,2.174997091293335,0.15,0.0),
//    Position("obstacle",4.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.8500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",8.300002098083496,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.850001811981201,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.915191173553467,7.274999618530273,0.15,0.0),
//    Position("obstacle",9.824997901916504,1.1749961376190186,0.15,0.0),
//    Position("obstacle",4.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",5.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500200271606445,5.124998092651367,0.15,0.0),
//    Position("obstacle",2.884322166442871,2.4803552627563477,0.15,0.0),
//    Position("obstacle",0.849998950958252,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27499788999557495,1.6749964952468872,0.15,0.0),
//    Position("obstacle",7.111771106719971,3.151111364364624,0.15,0.0),
//    Position("obstacle",9.825004577636719,7.624997615814209,0.15,0.0),
//    Position("obstacle",0.2750002145767212,3.6249983310699463,0.15,0.0),
//    Position("obstacle",0.27499961853027344,3.1249985694885254,0.15,0.0),
//    Position("obstacle",6.801187992095947,1.991999864578247,0.15,0.0),
//    Position("obstacle",6.878833770751953,2.281777858734131,0.15,0.0),
//    Position("obstacle",9.824997901916504,2.674996852874756,0.15,0.0),
//    Position("obstacle",0.2750013470649719,4.624998092651367,0.15,0.0),
//    Position("obstacle",6.849999904632568,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.3500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",6.956479549407959,2.5715556144714355,0.15,0.0),
//    Position("obstacle",0.275005578994751,8.124998092651367,0.15,0.0),
//    Position("obstacle",9.824996948242188,0.6749958992004395,0.15,0.0),
//    Position("obstacle",9.825005531311035,8.124998092651367,0.15,0.0),
//    Position("obstacle",9.825000762939453,3.6249983310699463,0.15,0.0),
//    Position("obstacle",9.825006484985352,8.624998092651367,0.15,0.0),
//    Position("obstacle",3.8500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",3.350001335144043,0.14999771118164062,0.15,0.0),
//    Position("obstacle",9.300003051757812,9.724998474121094,0.15,0.0),
//    Position("obstacle",5.850000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",2.8759613037109375,6.674999237060547,0.15,0.0),
//    Position("obstacle",9.800004005432129,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.1357691287994385,6.824999809265137,0.15,0.0),
//    Position("obstacle",7.422354221343994,4.310222625732422,0.15,0.0),
//    Position("obstacle",0.2750070095062256,9.125,0.15,0.0),
//    Position("obstacle",1.8367314338684082,6.07499885559082,0.15,0.0),
//    Position("obstacle",9.82500171661377,4.624998092651367,0.15,0.0),
//    Position("obstacle",8.300002098083496,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500391006469727,6.624997138977051,0.15,0.0),
//    Position("obstacle",3.4281065464019775,2.2267844676971436,0.15,0.0),
//    Position("obstacle",3.655383586883545,7.124999523162842,0.15,0.0),
//    Position("obstacle",9.800004005432129,0.14999771118164062,0.15,0.0),
//    Position("obstacle",7.300001621246338,9.724996566772461,0.15,0.0),
//    Position("obstacle",1.3499999046325684,9.724998474121094,0.15,0.0),
//    Position("obstacle",5.850000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",9.824997901916504,1.6749964952468872,0.15,0.0),
//    Position("#2",8.620262145996094,1.3905712366104126,0.5,14.323946585817874),
//    Position("obstacle",0.2750025987625122,5.624997615814209,0.15,0.0),
//    Position("obstacle",3.699998378753662,2.099999189376831,0.15,0.0),
//    Position("obstacle",2.3500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",9.825007438659668,9.125,0.15,0.0)),PathPoint(5.0,5.0))
//
//
//  val request2 = PathfinderBrain.FindPath("#2",Set(
//    Position("obstacle",2.356346368789673,6.374999046325684,0.15,0.0),
//    Position("obstacle",6.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.350001335144043,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.2529687881469727,3.2410683631896973,0.15,0.0),
//    Position("obstacle",4.850000381469727,0.14999771118164062,0.15,0.0),
//    Position("#0",1.9413628578186035,8.259160995483398,0.5,14.323946585817874),
//    Position("obstacle",7.80000114440918,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500486373901367,7.624997615814209,0.15,0.0),
//    Position("obstacle",2.6124300956726074,2.6071407794952393,0.15,0.0),
//    Position("obstacle",7.034125328063965,2.8613333702087402,0.15,0.0),
//    Position("obstacle",7.80000114440918,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27499741315841675,1.1749961376190186,0.15,0.0),
//    Position("obstacle",3.3955764770507812,6.974999904632568,0.15,0.0),
//    Position("#1",2.5743579864501953,1.3179742097854614,0.5,49.346388715482114),
//    Position("obstacle",9.300003051757812,0.14999771118164062,0.15,0.0),
//    Position("obstacle",4.850000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.8500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.3499999046325684,0.14999771118164062,0.15,0.0),
//    Position("obstacle",7.189416885375977,3.440889358520508,0.15,0.0),
//    Position("obstacle",0.27499765157699585,2.674996852874756,0.15,0.0),
//    Position("obstacle",9.824999809265137,3.1249985694885254,0.15,0.0),
//    Position("obstacle",5.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.096538782119751,6.22499942779541,0.15,0.0),
//    Position("obstacle",0.2750042676925659,7.124996185302734,0.15,0.0),
//    Position("obstacle",1.7967532873153687,2.987497329711914,0.15,0.0),
//    Position("obstacle",0.27499663829803467,0.6749958992004395,0.15,0.0),
//    Position("obstacle",2.068645477294922,2.8607118129730225,0.15,0.0),
//    Position("obstacle",1.8500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27500078082084656,4.124998092651367,0.15,0.0),
//    Position("obstacle",4.1749982833862305,7.424999713897705,0.15,0.0),
//    Position("obstacle",9.825002670288086,5.124998092651367,0.15,0.0),
//    Position("obstacle",9.825003623962402,6.124997615814209,0.15,0.0),
//    Position("obstacle",9.825004577636719,7.124996185302734,0.15,0.0),
//    Position("obstacle",6.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.2749985158443451,2.174997091293335,0.15,0.0),
//    Position("obstacle",4.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.8500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",8.300002098083496,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.850001811981201,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.915191173553467,7.274999618530273,0.15,0.0),
//    Position("obstacle",9.824997901916504,1.1749961376190186,0.15,0.0),
//    Position("obstacle",4.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",5.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500200271606445,5.124998092651367,0.15,0.0),
//    Position("obstacle",2.884322166442871,2.4803552627563477,0.15,0.0),
//    Position("obstacle",0.849998950958252,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27499788999557495,1.6749964952468872,0.15,0.0),
//    Position("obstacle",7.111771106719971,3.151111364364624,0.15,0.0),
//    Position("obstacle",9.825004577636719,7.624997615814209,0.15,0.0),
//    Position("obstacle",0.2750002145767212,3.6249983310699463,0.15,0.0),
//    Position("obstacle",0.27499961853027344,3.1249985694885254,0.15,0.0),
//    Position("obstacle",6.801187992095947,1.991999864578247,0.15,0.0),
//    Position("obstacle",6.878833770751953,2.281777858734131,0.15,0.0),
//    Position("obstacle",9.824997901916504,2.674996852874756,0.15,0.0),
//    Position("obstacle",0.2750013470649719,4.624998092651367,0.15,0.0),
//    Position("obstacle",6.849999904632568,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.3500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",6.956479549407959,2.5715556144714355,0.15,0.0),
//    Position("obstacle",0.275005578994751,8.124998092651367,0.15,0.0),
//    Position("obstacle",9.824996948242188,0.6749958992004395,0.15,0.0),
//    Position("obstacle",9.825005531311035,8.124998092651367,0.15,0.0),
//    Position("obstacle",9.825000762939453,3.6249983310699463,0.15,0.0),
//    Position("obstacle",9.825006484985352,8.624998092651367,0.15,0.0),
//    Position("obstacle",3.8500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",3.350001335144043,0.14999771118164062,0.15,0.0),
//    Position("obstacle",9.300003051757812,9.724998474121094,0.15,0.0),
//    Position("obstacle",5.850000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",2.8759613037109375,6.674999237060547,0.15,0.0),
//    Position("obstacle",9.800004005432129,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.1357691287994385,6.824999809265137,0.15,0.0),
//    Position("obstacle",7.422354221343994,4.310222625732422,0.15,0.0),
//    Position("obstacle",0.2750070095062256,9.125,0.15,0.0),
//    Position("obstacle",1.8367314338684082,6.07499885559082,0.15,0.0),
//    Position("obstacle",9.82500171661377,4.624998092651367,0.15,0.0),
//    Position("obstacle",8.300002098083496,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500391006469727,6.624997138977051,0.15,0.0),
//    Position("obstacle",3.4281065464019775,2.2267844676971436,0.15,0.0),
//    Position("obstacle",3.655383586883545,7.124999523162842,0.15,0.0),
//    Position("obstacle",9.800004005432129,0.14999771118164062,0.15,0.0),
//    Position("obstacle",7.300001621246338,9.724996566772461,0.15,0.0),
//    Position("obstacle",1.3499999046325684,9.724998474121094,0.15,0.0),
//    Position("obstacle",5.850000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",9.824997901916504,1.6749964952468872,0.15,0.0),
//    Position("#2",8.620262145996094,1.3905712366104126,0.5,14.323946585817874),
//    Position("obstacle",0.2750025987625122,5.624997615814209,0.15,0.0),
//    Position("obstacle",3.699998378753662,2.099999189376831,0.15,0.0),
//    Position("obstacle",2.3500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",9.825007438659668,9.125,0.15,0.0)),PathPoint(5.0,5.0))
//
//
//  val request0 = PathfinderBrain.FindPath("#0",Set(
//    Position("obstacle",2.356346368789673,6.374999046325684,0.15,0.0),
//    Position("obstacle",6.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.350001335144043,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.2529687881469727,3.2410683631896973,0.15,0.0),
//    Position("obstacle",4.850000381469727,0.14999771118164062,0.15,0.0),
//    Position("#0",1.9413628578186035,8.259160995483398,0.5,14.323946585817874),
//    Position("obstacle",7.80000114440918,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500486373901367,7.624997615814209,0.15,0.0),
//    Position("obstacle",2.6124300956726074,2.6071407794952393,0.15,0.0),
//    Position("obstacle",7.034125328063965,2.8613333702087402,0.15,0.0),
//    Position("obstacle",7.80000114440918,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27499741315841675,1.1749961376190186,0.15,0.0),
//    Position("obstacle",3.3955764770507812,6.974999904632568,0.15,0.0),
//    Position("#1",2.5743579864501953,1.3179742097854614,0.5,49.346388715482114),
//    Position("obstacle",9.300003051757812,0.14999771118164062,0.15,0.0),
//    Position("obstacle",4.850000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.8500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.3499999046325684,0.14999771118164062,0.15,0.0),
//    Position("obstacle",7.189416885375977,3.440889358520508,0.15,0.0),
//    Position("obstacle",0.27499765157699585,2.674996852874756,0.15,0.0),
//    Position("obstacle",9.824999809265137,3.1249985694885254,0.15,0.0),
//    Position("obstacle",5.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.096538782119751,6.22499942779541,0.15,0.0),
//    Position("obstacle",0.2750042676925659,7.124996185302734,0.15,0.0),
//    Position("obstacle",1.7967532873153687,2.987497329711914,0.15,0.0),
//    Position("obstacle",0.27499663829803467,0.6749958992004395,0.15,0.0),
//    Position("obstacle",2.068645477294922,2.8607118129730225,0.15,0.0),
//    Position("obstacle",1.8500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27500078082084656,4.124998092651367,0.15,0.0),
//    Position("obstacle",4.1749982833862305,7.424999713897705,0.15,0.0),
//    Position("obstacle",9.825002670288086,5.124998092651367,0.15,0.0),
//    Position("obstacle",9.825003623962402,6.124997615814209,0.15,0.0),
//    Position("obstacle",9.825004577636719,7.124996185302734,0.15,0.0),
//    Position("obstacle",6.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.2749985158443451,2.174997091293335,0.15,0.0),
//    Position("obstacle",4.350000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",1.8500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",8.300002098083496,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.850001811981201,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.915191173553467,7.274999618530273,0.15,0.0),
//    Position("obstacle",9.824997901916504,1.1749961376190186,0.15,0.0),
//    Position("obstacle",4.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",5.350000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500200271606445,5.124998092651367,0.15,0.0),
//    Position("obstacle",2.884322166442871,2.4803552627563477,0.15,0.0),
//    Position("obstacle",0.849998950958252,9.724998474121094,0.15,0.0),
//    Position("obstacle",0.27499788999557495,1.6749964952468872,0.15,0.0),
//    Position("obstacle",7.111771106719971,3.151111364364624,0.15,0.0),
//    Position("obstacle",9.825004577636719,7.624997615814209,0.15,0.0),
//    Position("obstacle",0.2750002145767212,3.6249983310699463,0.15,0.0),
//    Position("obstacle",0.27499961853027344,3.1249985694885254,0.15,0.0),
//    Position("obstacle",6.801187992095947,1.991999864578247,0.15,0.0),
//    Position("obstacle",6.878833770751953,2.281777858734131,0.15,0.0),
//    Position("obstacle",9.824997901916504,2.674996852874756,0.15,0.0),
//    Position("obstacle",0.2750013470649719,4.624998092651367,0.15,0.0),
//    Position("obstacle",6.849999904632568,9.724998474121094,0.15,0.0),
//    Position("obstacle",2.3500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",6.956479549407959,2.5715556144714355,0.15,0.0),
//    Position("obstacle",0.275005578994751,8.124998092651367,0.15,0.0),
//    Position("obstacle",9.824996948242188,0.6749958992004395,0.15,0.0),
//    Position("obstacle",9.825005531311035,8.124998092651367,0.15,0.0),
//    Position("obstacle",9.825000762939453,3.6249983310699463,0.15,0.0),
//    Position("obstacle",9.825006484985352,8.624998092651367,0.15,0.0),
//    Position("obstacle",3.8500008583068848,0.14999771118164062,0.15,0.0),
//    Position("obstacle",3.350001335144043,0.14999771118164062,0.15,0.0),
//    Position("obstacle",9.300003051757812,9.724998474121094,0.15,0.0),
//    Position("obstacle",5.850000381469727,0.14999771118164062,0.15,0.0),
//    Position("obstacle",2.8759613037109375,6.674999237060547,0.15,0.0),
//    Position("obstacle",9.800004005432129,9.724998474121094,0.15,0.0),
//    Position("obstacle",3.1357691287994385,6.824999809265137,0.15,0.0),
//    Position("obstacle",7.422354221343994,4.310222625732422,0.15,0.0),
//    Position("obstacle",0.2750070095062256,9.125,0.15,0.0),
//    Position("obstacle",1.8367314338684082,6.07499885559082,0.15,0.0),
//    Position("obstacle",9.82500171661377,4.624998092651367,0.15,0.0),
//    Position("obstacle",8.300002098083496,0.14999771118164062,0.15,0.0),
//    Position("obstacle",0.27500391006469727,6.624997138977051,0.15,0.0),
//    Position("obstacle",3.4281065464019775,2.2267844676971436,0.15,0.0),
//    Position("obstacle",3.655383586883545,7.124999523162842,0.15,0.0),
//    Position("obstacle",9.800004005432129,0.14999771118164062,0.15,0.0),
//    Position("obstacle",7.300001621246338,9.724996566772461,0.15,0.0),
//    Position("obstacle",1.3499999046325684,9.724998474121094,0.15,0.0),
//    Position("obstacle",5.850000381469727,9.724998474121094,0.15,0.0),
//    Position("obstacle",9.824997901916504,1.6749964952468872,0.15,0.0),
//    Position("#2",8.620262145996094,1.3905712366104126,0.5,14.323946585817874),
//    Position("obstacle",0.2750025987625122,5.624997615814209,0.15,0.0),
//    Position("obstacle",3.699998378753662,2.099999189376831,0.15,0.0),
//    Position("obstacle",2.3500008583068848,9.724998474121094,0.15,0.0),
//    Position("obstacle",9.825007438659668,9.125,0.15,0.0)),PathPoint(5.0,5.0))
//
//  implicit val system = ActorSystem("WorkerTest")
//
//  List(request0, request1, request2).foreach { fullKnowledge =>
//    val testProbe = TestProbe()
//    testProbe.childActorOf(Worker.apply(fullKnowledge))
//    testProbe.expectMsgType[PathfinderBrain.PathFound](2.minutes)
//  }
//
//}