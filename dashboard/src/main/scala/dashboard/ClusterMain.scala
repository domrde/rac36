package dashboard

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.ddata.DistributedData
import akka.cluster.metrics.ClusterMetricsExtension
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, ClusterEvent}

class ClusterMain extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  val replicator = DistributedData(context.system).replicator

  val mediator = DistributedPubSub(context.system).mediator

  ClusterMetricsExtension(context.system)

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
    context.become(initialised(context.actorOf(Props[MetricsAggregator], "dashboard.MetricsAggregator")))
    log.info("\nAvatar cluster started with mediator [{}] and replicator [{}]",
      mediator, replicator)
  }

  def initialised(aggregator: ActorRef): Receive = {
    case MemberUp(member) =>
      log.info("MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      aggregator ! MetricsAggregator.NodeUp(member.address, member.roles.head)

    case MemberRemoved(member, _) =>
      log.info("MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
      aggregator ! MetricsAggregator.NodeDown(member.address)
  }

}
