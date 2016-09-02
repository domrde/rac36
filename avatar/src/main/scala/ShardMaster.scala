package avatar
import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ddata.DistributedData
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory
import messages.Constants._
import messages.Messages.{AvatarCreated, NumeratedMessage}

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

  // todo: do something that only one mediator gets message
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(ACTOR_CREATION_SUBSCRIPTION, self)

  override def receive: Receive = {
    case nm: NumeratedMessage =>
      log.info("\nShard master received NumeratedMessage [{}]", nm)
      shard ! nm

    case SubscribeAck(Subscribe(ACTOR_CREATION_SUBSCRIPTION, None, `self`)) =>
      log.info("\nShard master successfully subscribed")

    // todo: replace this horrible condition to a smooth message flow
    case a: AvatarCreated if sender != self =>
      mediator ! Publish(ACTOR_CREATION_SUBSCRIPTION, a)
      log.info("\nResending [{}]", a)

    case a: AvatarCreated =>

    case other =>
      log.error("ShardMaster: other [{}] from [{}]", other, sender())
  }

  val config = ConfigFactory.load()

  log.info("Shard master started")

}
