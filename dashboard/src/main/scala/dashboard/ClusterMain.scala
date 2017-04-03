package dashboard

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.ddata.DistributedData
import akka.cluster.metrics.ClusterMetricsExtension
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.cluster.{Cluster, ClusterEvent}
import common.messages.NumeratedMessage

class ClusterMain extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  val replicator = DistributedData(context.system).replicator

  val mediator = DistributedPubSub(context.system).mediator

  ClusterMetricsExtension(context.system)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: NumeratedMessage => (m.id, m)
  }
  val numberOfShards = 100 // ten times of expected
  val extractShardId: ShardRegion.ExtractShardId = {
    case m: NumeratedMessage => Math.floorMod(m.id.hashCode, numberOfShards).toString
  }
  val shardProxy = ClusterSharding(context.system).startProxy(
    typeName = "Avatar",
    role = Some("Avatar"),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  cluster.subscribe(self, classOf[ClusterEvent.MemberUp], classOf[ClusterEvent.MemberRemoved])

  override def receive: Receive = initial

  val initial: Receive = {
    case ccs: CurrentClusterState => ccs.members.find(_.address == cluster.selfAddress).foreach(_ => startMainSystem())
    case MemberUp(member) =>
      log.info("[-] DashboardClusterMain: MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      if (cluster.selfAddress == member.address) startMainSystem()
    case MemberRemoved(member, _) => log.info("[-] DashboardClusterMain: MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  def startMainSystem() = {
    context.become(initialised(context.actorOf(Props[Server], "Server")))
    log.info("[-] DashboardClusterMain: Avatar cluster started with mediator [{}] and replicator [{}]",
      mediator, replicator)
  }

  def initialised(server: ActorRef): Receive = {
    case MemberUp(member) =>
      log.info("[-] DashboardClusterMain: MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      server ! MetricsAggregator.NodeUp(member.address, member.roles.head)

    case MemberRemoved(member, _) =>
      log.info("[-] DashboardClusterMain: MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
      server ! MetricsAggregator.NodeDown(member.address)
  }

}
