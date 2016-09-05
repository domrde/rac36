import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.cluster.sharding.ShardRegion
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by dda on 9/5/16.
  */
// todo: check it's working
class ShardingStatsListener extends Actor with ActorLogging {
  val mediator = DistributedPubSub(context.system).mediator
  val config = ConfigFactory.load()
  val avatarAddress = config.getString("application.avatarAddress")

  context.system.scheduler.schedule(1.seconds, 3.seconds, mediator,
    Send(avatarAddress, ShardRegion.GetShardRegionState, localAffinity = false))

  override def receive: Receive = {
    case stats: ShardRegion.CurrentShardRegionState =>
      stats.shards.foreach { shard =>
        log.info("\n\n---------------SHARD-{}-------------------", shard.shardId)
        log.info(shard.entityIds.toString())
      }
    case other => log.error("ShardingStatsListener: other {} from {}", other, sender())
  }
}
