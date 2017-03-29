package vivarium

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.ddata.DistributedData
import akka.cluster.metrics.ClusterMetricsExtension
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.{Cluster, ClusterEvent}
import common.SharedMessages.NumeratedMessage


//todo: get rid of sharding for good
class ClusterMain extends Actor with ActorLogging {
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

  val initialised: Receive = {
    case MemberUp(member) => log.info("MemberUp {} with roles {}", member.uniqueAddress, member.roles)
    case MemberRemoved(member, _) => log.info("MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: NumeratedMessage => (m.id, m)
  }

  val numberOfShards = 100 // ten times of expected
  val extractShardId: ShardRegion.ExtractShardId = {
    case m: NumeratedMessage => Math.floorMod(m.id.hashCode, numberOfShards).toString
  }

  val replicator = DistributedData(context.system).replicator

  val shard = ClusterSharding(context.system).start(
    typeName = "Avatar",
    entityProps = Props[Avatar],
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  ClusterMetricsExtension(context.system)
  DistributedPubSub(context.system)

  def startMainSystem() = {
    context.become(initialised)
    log.info("\n---------------------------------------------------------------------------")
    log.info("\n\nAvatar cluster started with shard [{}] and replicator [{}]\n", shard, replicator)
    log.info("\n---------------------------------------------------------------------------")
  }
}
