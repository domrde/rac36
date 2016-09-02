package avatar
import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ddata.DistributedData
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory
import messages.Messages.NumeratedMessage

class ShardMaster extends Actor with ActorLogging {

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: NumeratedMessage => (m.uuid.toString, m)
  }

  val numberOfShards = 100 // ten times of expected
  val extractShardId: ShardRegion.ExtractShardId = {
    case m: NumeratedMessage => Math.floorMod(m.uuid.getMostSignificantBits, numberOfShards).toString
  }

  val replicator = DistributedData(context.system).replicator

  val shard = ClusterSharding(context.system).start(
    typeName = "Avatar",
    entityProps = Props[Avatar],
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Put(self)

  override def receive: Receive = {

    // CreateAvatar is NumeratedMessage
    case nm: NumeratedMessage =>
      log.info("\nShard master received NumeratedMessage [{}]", nm)
      shard ! nm

    case other =>
      log.error("\nShardMaster: other [{}] from [{}]", other, sender())
  }

  val config = ConfigFactory.load()

  log.info("\nShard master started")

}
