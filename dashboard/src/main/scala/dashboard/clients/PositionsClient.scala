package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import common.Constants.PositionDdataSetKey
import common.messages.SensoryInformation
import dashboard.Server
import vivarium.ReplicatedSet
import vivarium.ReplicatedSet.{Lookup, LookupResult}

import scala.concurrent.duration._

/**
  * Created by dda on 03.05.17.
  */
object PositionsClient {
  def apply(): Props = Props(classOf[PositionsClient])
}

class PositionsClient extends Actor with ActorLogging{

  private implicit val timeout: Timeout = 5.seconds
  private implicit val executionContext = context.dispatcher
  private val config = ConfigFactory.load()
  private val updatePeriod = FiniteDuration(config.getDuration("application.updatePeriod").getSeconds, SECONDS)

  private val positionsStorage = context.actorOf(ReplicatedSet(PositionDdataSetKey))

  context.system.scheduler.schedule(updatePeriod, updatePeriod, positionsStorage, Lookup)

  if (config.getBoolean("application.testData")) {
    context.system.scheduler.schedule(0 second, 5 second) {
      val data: Set[SensoryInformation.Position] = Set(
        SensoryInformation.Position("obstacle",-1.875912189483643,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",2.5665716648101804,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.5759121894836432,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("#0",-1.8905277252197266,-4.596574306488037,0.5,4.781364390494409E-4),
        SensoryInformation.Position("obstacle",3.0319696903228754,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",2.7319696903228756,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.2759121894836434,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.8802230358123784,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",4.109992361068725,5.344433307647705,0.15,0.0),
        SensoryInformation.Position("obstacle",3.31657166481018,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("#2",4.371944904327393,-4.269821643829346,0.5,-3.439021496578838),
        SensoryInformation.Position("obstacle",-1.7302230358123785,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",2.8819696903228755,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.480223035812378,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",2.7165716648101803,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.3259121894836428,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.5802230358123786,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.4759121894836427,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.630223035812378,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",3.809992361068725,5.344433307647705,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.4259121894836433,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",3.959992361068725,5.344433307647705,0.15,0.0),
        SensoryInformation.Position("obstacle",3.01657166481018,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.6259121894836426,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",3.46657166481018,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.330223035812378,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",3.1819696903228754,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",3.3319696903228753,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",2.281969690322876,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",3.481969690322875,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.0302230358123783,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",2.431969690322876,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",2.5819696903228757,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",2.1165716648101807,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",2.131969690322876,4.874606609344482,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.2802230358123787,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",4.259992361068726,5.344433307647705,0.15,0.0),
        SensoryInformation.Position("#1",2.8614509105682373,-4.5966339111328125,0.5,-2.5533749426530982E-5),
        SensoryInformation.Position("obstacle",3.16657166481018,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",2.2665716648101806,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.7259121894836431,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",-1.4302230358123786,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.175912189483643,1.0216152183711529,0.15,0.0),
        SensoryInformation.Position("obstacle",2.4165716648101805,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",2.86657166481018,5.195904731750488,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.180223035812378,1.3626877069473267,0.15,0.0),
        SensoryInformation.Position("obstacle",-2.025912189483643,1.0216152183711529,0.15,0.0)
      )
      self ! LookupResult(Some(data))
    }
  }

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join(Server.PositionsClient)

    case other =>
      log.error("[-] dashboard.PositionsClient: other {} from {}", other, sender())
  }

  def connected(connection: ActorRef): Receive = {
    case l: LookupResult[SensoryInformation.Position] =>
      l.result match {
        case Some(data) =>
          connection ! ServerClient.PositionsData(data)

        case None =>
      }

    case other =>
      log.error("[-] dashboard.PositionsClient: other {} from {}", other, sender())
  }

}
