package avatar
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import messages.Messages.CoordinateWithType

// todo: extend example so it may be used with different sensor types
object ReplicatedSet {

  def apply() = Props[ReplicatedSet]

  case class AddAll(values: Set[CoordinateWithType])
  case object Lookup
  case class LookupResult(result: Option[Set[CoordinateWithType]])
}

class ReplicatedSet extends Actor with ActorLogging {
  import ReplicatedSet._

  val replicator = DistributedData(context.system).replicator
  implicit val cluster = Cluster(context.system)
  val Key = ORSetKey[CoordinateWithType]("SensoryInfoSet")

  def receive = {
    case AddAll(values) =>
      values.foreach(value => replicator ! Update(Key, ORSet(), WriteLocal)(_ + value))

    case Lookup =>
      replicator ! Get(Key, ReadLocal, Some(sender()))

    case g @ GetSuccess(Key, Some(replyTo: ActorRef)) =>
      g.dataValue match {
        case data: ORSet[CoordinateWithType] => replyTo ! LookupResult(Some(data.elements))
        case _ => replyTo ! LookupResult(None)
      }

    case NotFound(_, Some(replyTo: ActorRef)) =>
      replyTo ! LookupResult(None)

    case other =>
      log.error("\nReplicatedSet: other [{}] from [{}]", other, sender())
  }

}