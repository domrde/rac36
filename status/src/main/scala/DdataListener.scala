import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSet}
import messages.Constants.DdataSetKey
import messages.Messages.CoordinateWithType

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by dda on 9/5/16.
  */
// todo: test in real-life simulation
class DdataListener extends Actor with ActorLogging {
  val replicator = DistributedData(context.system).replicator
  implicit val cluster = Cluster(context.system)

  context.system.scheduler.schedule(2.seconds, 3.seconds, replicator, Get(DdataSetKey, ReadLocal, None))

  override def receive: Receive = {
    case g @ GetSuccess(DdataSetKey, None) =>
      g.dataValue match {
        case data: ORSet[CoordinateWithType] =>
          log.info("-----------------Ddata--------------")
          log.info(data.elements.toString())
        case _ => log.info("no ddata collected with GetSuccess")
      }

    case NotFound(DdataSetKey, _) =>

    case other =>
      log.error("DdataListener: other [{}] from [{}]", other, sender())
  }
}
