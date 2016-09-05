import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSet}
import messages.Constants.DdataSetKey
import messages.Messages.CoordinateWithType

/**
  * Created by dda on 9/5/16.
  */
// todo: test in real-life simulation
class DdataListener extends Actor with ActorLogging {
  val replicator = DistributedData(context.system).replicator
  implicit val cluster = Cluster(context.system)

  replicator ! Subscribe(DdataSetKey, self)

  override def receive: Receive = {
    case c @ Changed(DdataSetKey) =>
      replicator ! Get(DdataSetKey, ReadLocal, None)

    case g @ GetSuccess(DdataSetKey, None) =>
      g.dataValue match {
        case data: ORSet[CoordinateWithType] =>
          log.info("-----------------Ddata--------------")
          log.info(data.elements.toString())
        case _ =>
      }

    case other =>
      log.error("DdataListener: other [{}] from [{}]", other, sender())
  }
}
