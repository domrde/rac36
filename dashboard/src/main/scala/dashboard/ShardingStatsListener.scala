package dashboard

import akka.actor.{Actor, ActorLogging, Address}
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
object ShardingStatsListener {
  case class RegionMetric(shardId: String, entities: Set[String])
  case class RegionMetrics(metrics: Set[RegionMetric], t: String = "RegionMetrics")

  case class ShardingStats(address: Address, stats: Map[String, Int], t: String = "ShardingStats")
}

class ShardingStatsListener extends Actor with ActorLogging {
  import ShardingStatsListener._
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
      context.parent ! RegionMetrics(stats.shards.map { shard =>
        RegionMetric(shard.shardId, shard.entityIds)
      })

    case stats: ShardRegion.ClusterShardingStats =>
      stats.regions.foreach { case (address, shardRegionStats) =>
        context.parent ! ShardingStats(address, shardRegionStats.stats)
      }

    case other => log.error("dashboard.ShardingStatsListener: other {} from {}", other, sender())
  }
}
