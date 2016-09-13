package dashboard

import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.cluster.sharding.ShardRegion
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by dda on 9/5/16.
  */
// todo: check it's working
class ShardingStatsListener extends Actor with ActorLogging {
  val mediator = DistributedPubSub(context.system).mediator
  val config = ConfigFactory.load()
  val avatarAddress = config.getString("application.avatarAddress")

  context.system.scheduler.schedule(1.seconds, 3.seconds) {
    mediator ! Send(avatarAddress, ShardRegion.GetShardRegionState, localAffinity = false)
  }

  context.system.scheduler.schedule(1.seconds, 3.seconds) {
    mediator ! Send(avatarAddress, ShardRegion.GetClusterShardingStats, localAffinity = false)
  }

  override def receive: Receive = {
    case stats: ShardRegion.CurrentShardRegionState =>
      context.parent ! MetricsAggregator.RegionMetrics(stats.shards.map { shard =>
        MetricsAggregator.RegionMetric(shard.shardId, shard.entityIds)
      })

    case stats: ShardRegion.ClusterShardingStats =>
      stats.regions.foreach { case (address, shardRegionStats) =>
        context.parent ! MetricsAggregator.ShardingStats(address, shardRegionStats.stats)
      }

    case other => log.error("dashboard.ShardingStatsListener: other {} from {}", other, sender())
  }
}
