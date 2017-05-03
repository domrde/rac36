package vivarium

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._

object ReplicatedSet {
  trait ReplicatedSetMessage

  def apply[T](SetKey: ORSetKey[T]) = Props(classOf[ReplicatedSet[T]], SetKey, None)
  def apply[T](SetKey: ORSetKey[T], target: ActorRef) = Props(classOf[ReplicatedSet[T]], SetKey, Some(target))

  case class AddAll(values: Set[_ <: AnyRef]) extends ReplicatedSetMessage
  case class RemoveAll(values: Set[_ <: AnyRef]) extends ReplicatedSetMessage
  case object Lookup extends ReplicatedSetMessage
  case class LookupResult(result: Option[Set[_ <: AnyRef]]) extends ReplicatedSetMessage
}

class ReplicatedSet[ItemType <: AnyRef](SetKey: ORSetKey[ItemType], targetOpt: Option[ActorRef]) extends Actor with ActorLogging {
  import ReplicatedSet._

  private val target = targetOpt.getOrElse(context.parent)
  log.info("[-] ReplicatedSet initialised [{}] for target [{}]", SetKey, target)

  private val replicator = DistributedData(context.system).replicator
  replicator ! Subscribe(SetKey, self)
  implicit val cluster = Cluster(context.system)

  def receive: Receive = {
    case AddAll(values: Set[ItemType]) =>
      values.foreach(value => replicator ! Update(SetKey, ORSet(), WriteLocal)(_ + value))

    case RemoveAll(values: Set[ItemType]) =>
      values.foreach(value => replicator ! Update(SetKey, ORSet(), WriteLocal)(_ - value))

    case UpdateSuccess(_, _) =>

    case c @ Changed(SetKey) =>
      c.dataValue match {
        case data: ORSet[ItemType] => target ! LookupResult(Some(data.elements))
        case _ =>
      }

    case Lookup =>
      replicator ! Get(SetKey, ReadLocal, Some(sender()))

    case g @ GetSuccess(SetKey, Some(replyTo: ActorRef)) =>
      g.dataValue match {
        case data: ORSet[ItemType] => replyTo ! LookupResult(Some(data.elements))
        case _ => replyTo ! LookupResult(None)
      }

    case NotFound(_, Some(replyTo: ActorRef)) =>
      replyTo ! LookupResult(None)

    case other =>
      log.error("[-] ReplicatedSet: other [{}] from [{}]", other, sender())
  }

}