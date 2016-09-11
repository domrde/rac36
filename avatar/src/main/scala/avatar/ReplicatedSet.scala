package avatar

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import messages.Constants.DdataSetKey
import messages.Messages.Position

// todo: extend example so it may be used with different sensor types
object ReplicatedSet {

  def apply() = Props[ReplicatedSet]

  case class AddAll(values: Set[Position])
  case object Lookup
  case class LookupResult(result: Option[Set[Position]])
}

class ReplicatedSet extends Actor with ActorLogging {
  import ReplicatedSet._

  val replicator = DistributedData(context.system).replicator
    implicit val cluster = Cluster(context.system)

  def receive = {
    case AddAll(values) =>
      values.foreach(value => replicator ! Update(DdataSetKey, ORSet(), WriteLocal)(_ + value))

    case UpdateSuccess(_, _) =>

    case Lookup =>
      replicator ! Get(DdataSetKey, ReadLocal, Some(sender()))

    case g @ GetSuccess(DdataSetKey, Some(replyTo: ActorRef)) =>
      g.dataValue match {
        case data: ORSet[Position] => replyTo ! LookupResult(Some(data.elements))
        case _ => replyTo ! LookupResult(None)
      }

    case NotFound(_, Some(replyTo: ActorRef)) =>
      replyTo ! LookupResult(None)

    case other =>
      log.error("\nReplicatedSet: other [{}] from [{}]", other, sender())
  }

}