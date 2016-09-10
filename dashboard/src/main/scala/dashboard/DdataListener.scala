package dashboard

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSet}
import messages.Constants.DdataSetKey
import messages.Messages.CoordinateWithType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by dda on 9/5/16.
  */
// todo: test in real-life simulation
object DdataListener {
  case class DdataStatus(data: Set[CoordinateWithType], t: String = "DdataStatus")
}

class DdataListener extends Actor with ActorLogging {
  import DdataListener._
  val replicator = DistributedData(context.system).replicator
  implicit val cluster = Cluster(context.system)

  context.system.scheduler.schedule(2.seconds, 3.seconds, replicator, Get(DdataSetKey, ReadLocal, None))

  override def receive: Receive = {
    case g @ GetSuccess(DdataSetKey, None) =>
      g.dataValue match {
        case data: ORSet[CoordinateWithType] =>
          context.parent ! DdataStatus(data.elements)

        case _ => log.info("no ddata collected with GetSuccess")
      }

    case NotFound(DdataSetKey, _) =>

    case other =>
      log.error("dashboard.DdataListener: other [{}] from [{}]", other, sender())
  }
}
