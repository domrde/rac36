package dashboard

import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.cluster.sharding.ShardRegion
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

/**
  * Created by dda on 9/5/16.
  */
// todo: check it's working
class ShardingStatsListener extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private val mediator = DistributedPubSub(context.system).mediator
  private val config = ConfigFactory.load()
  private val avatarAddress =
    if (config.hasPath("application.avatarAddress")) Some(config.getString("application.avatarAddress")) else None

  context.system.scheduler.schedule(1.seconds, 3.seconds) {
    avatarAddress.foreach(addr => mediator ! Send(addr, ShardRegion.GetShardRegionState, localAffinity = false))
  }

  context.system.scheduler.schedule(1.seconds, 3.seconds) {
    avatarAddress.foreach(addr => mediator ! Send(addr, ShardRegion.GetClusterShardingStats, localAffinity = false))
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

    case other => log.error("[-] dashboard.ShardingStatsListener: other {} from {}", other, sender())
  }
}
