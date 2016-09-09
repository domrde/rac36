import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.ddata.DistributedData
import akka.cluster.metrics.ClusterMetricsExtension
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, ClusterEvent}

object ClusterMain {
  case class NodeUp(address: Address, role: String, t: String = "NodeUp")
  case class NodeDown(address: Address, t: String = "NodeDown")
}

class ClusterMain extends Actor with ActorLogging {
  import ClusterMain._

  val cluster = Cluster(context.system)

  cluster.subscribe(self, classOf[ClusterEvent.MemberUp], classOf[ClusterEvent.MemberRemoved])

  override def receive: Receive = initial

  val initial: Receive = {
    case ccs: CurrentClusterState => ccs.members.find(_.address == cluster.selfAddress).foreach(_ => startMainSystem())
    case MemberUp(member) =>
      log.info("MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      if (cluster.selfAddress == member.address) startMainSystem()
    case MemberRemoved(member, _) => log.info("MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  def startMainSystem() = {
    context.become(initialised(context.actorOf(Props[MetricsAggregator], "MetricsAggregator")))
    log.info("\nAvatar cluster started with mediator [{}] and replicator [{}]",
      mediator, replicator)
  }

  def initialised(aggregator: ActorRef): Receive = {
    case MemberUp(member) =>
      log.info("MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      aggregator ! NodeUp(member.address, member.roles.head)

    case MemberRemoved(member, _) =>
      log.info("MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
      aggregator ! NodeDown(member.address)
  }

  val replicator = DistributedData(context.system).replicator

  val mediator = DistributedPubSub(context.system).mediator

  ClusterMetricsExtension(context.system)
}
