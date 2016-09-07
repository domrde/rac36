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
object ShardingStatsListener {
  case class ShardMetric(shardId: String, entities: Set[String])
  case class ShardMetrics(metrics: Set[ShardMetric], t: String = "ShardMetrics")
}

class ShardingStatsListener extends Actor with ActorLogging {
  import ShardingStatsListener._
  val mediator = DistributedPubSub(context.system).mediator
  val config = ConfigFactory.load()
  val avatarAddress = config.getString("application.avatarAddress")

  context.system.scheduler.schedule(1.seconds, 3.seconds, mediator,
    Send(avatarAddress, ShardRegion.GetShardRegionState, localAffinity = false))

  override def receive: Receive = {
    case stats: ShardRegion.CurrentShardRegionState =>
      context.parent ! ShardMetrics(stats.shards.map { shard =>
        ShardMetric(shard.shardId, shard.entityIds)
      })

    case other => log.error("ShardingStatsListener: other {} from {}", other, sender())
  }
}
