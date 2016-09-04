package avatar
import akka.actor.{Actor, ActorLogging}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.ddata.DistributedData
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.{Cluster, ClusterEvent}
import messages.Messages.NumeratedMessage

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
    case m: NumeratedMessage => (m.uuid.toString, m)
  }

  val numberOfShards = 100 // ten times of expected
  val extractShardId: ShardRegion.ExtractShardId = {
    case m: NumeratedMessage => Math.floorMod(m.uuid.getMostSignificantBits, numberOfShards).toString
  }

  val replicator = DistributedData(context.system).replicator

  val cache = context.actorOf(ReplicatedSet())

  val shard = ClusterSharding(context.system).start(
    typeName = "Avatar",
    entityProps = Avatar(cache),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  val mediator = DistributedPubSub(context.system).mediator

  def startMainSystem() = {
    mediator ! Put(shard)
    context.become(initialised)
    log.info("\nAvatar cluster started with mediator [{}], shard [{}] and replicator [{}]",
      mediator, shard, replicator)
  }
}
