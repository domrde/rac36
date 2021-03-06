package pipe

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.ddata.DistributedData
import akka.cluster.metrics.ClusterMetricsExtension
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.cluster.{Cluster, ClusterEvent}
import common.messages.NumeratedMessage

class ClusterMain extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  ClusterMetricsExtension(context.system)
  DistributedPubSub(context.system)
  DistributedData(context.system)

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
      log.info("[-] PipeClusterMain: MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      if (cluster.selfAddress == member.address) startMainSystem()
    case MemberRemoved(member, _) => log.info("[-] PipeClusterMain: MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  val initialised: Receive = {
    case MemberUp(member) => log.info("[-] PipeClusterMain: MemberUp {} with roles {}", member.uniqueAddress, member.roles)
    case MemberRemoved(member, _) => log.info("[-] PipeClusterMain: MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  def startMainSystem() = {
    context.actorOf(Props[TunnelManager], "TunnelManager")
    context.become(initialised)
    log.info("[-] PipeClusterMain: TunnelManager initialised")
  }
}
