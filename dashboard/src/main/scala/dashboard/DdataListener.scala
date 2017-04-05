package dashboard

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSet}
import common.Constants.PositionDdataSetKey
import common.messages.SensoryInformation.Position

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by dda on 9/5/16.
  */
// todo: test in real-life simulation
class DdataListener extends Actor with ActorLogging {
  val replicator = DistributedData(context.system).replicator
  implicit val cluster = Cluster(context.system)

  context.system.scheduler.schedule(2.seconds, 3.seconds, replicator, Get(PositionDdataSetKey, ReadLocal, None))

  override def receive: Receive = {
    case g @ GetSuccess(PositionDdataSetKey, None) =>
      g.dataValue match {
        case data: ORSet[Position] =>
          context.parent ! MetricsAggregator.DdataStatus(data.elements)

        case _ => log.info("[-] dashboard.DdataListener: no ddata collected with GetSuccess")
      }

    case NotFound(PositionDdataSetKey, _) =>

    case other =>
      log.error("[-] dashboard.DdataListener: other [{}] from [{}]", other, sender())
  }
}
