package avatar
import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import messages.Messages._

object Boot extends App {
  val system = ActorSystem("AvatarManagerSystem")

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: NumeratedMessage => (m.uuid.toString, m)
  }

  val numberOfShards = 100 // ten times of expected
  val extractShardId: ShardRegion.ExtractShardId = {
    case m: NumeratedMessage => Math.floorMod(m.uuid.getMostSignificantBits, numberOfShards).toString
  }

  ClusterSharding(system).start(
    typeName = "Avatar",
    entityProps = Props[Avatar],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

}
