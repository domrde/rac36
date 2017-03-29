package vivarium

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import common.Constants.DdataSetKey
import common.SharedMessages.Position

// todo: extend example so it may be used with different sensor types
object ReplicatedSet {

  def apply() = Props[ReplicatedSet]

  case class AddAll(values: Set[Position])
  case class RemoveAll(values: Set[Position])
  case object Lookup
  case class LookupResult(result: Option[Set[Position]])
}

class ReplicatedSet extends Actor with ActorLogging {
  import ReplicatedSet._

  val replicator = DistributedData(context.system).replicator
  replicator ! Subscribe(DdataSetKey, self)
  implicit val cluster = Cluster(context.system)

  def receive = {
    case AddAll(values) =>
      values.foreach(value => replicator ! Update(DdataSetKey, ORSet(), WriteLocal)(_ + value))

    case RemoveAll(values) =>
      values.foreach(value => replicator ! Update(DdataSetKey, ORSet(), WriteLocal)(_ - value))

    case UpdateSuccess(_, _) =>

    case c @ Changed(DdataSetKey) =>
      c.dataValue match {
        case data: ORSet[Position] => context.parent ! LookupResult(Some(data.elements))
        case _ => context.parent ! LookupResult(None)
      }

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