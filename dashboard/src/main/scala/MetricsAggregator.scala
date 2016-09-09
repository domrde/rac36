import ServerClient.LaunchCommand
import akka.actor.ActorDSL._
import akka.actor.{Actor, ActorLogging, Address, Props}

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by dda on 9/6/16.
  */
class MetricsAggregator extends Actor with ActorLogging {

  val ips = List.fill(12)(Random.nextString(10))
  def randomAddress() = Address.apply("tcp", "ClusterSystem", ips(Random.nextInt(ips.length)), 9999)
  val roles = List("Avatar", "Pipe", "Dashboard")
  def randomRole() = roles(Random.nextInt(roles.length))
  def randomNodeStatus(): ClusterMetricsListener.NodesStatus =
    ClusterMetricsListener.NodesStatus(Set(ClusterMetricsListener.NodeInfo(randomAddress(), "Up", roles(Random.nextInt(roles.length)))))
  def randomMemMetrics(): ClusterMetricsListener.MemoryMetrics =
    ClusterMetricsListener.MemoryMetrics(randomAddress(), Random.nextDouble())
  def randomCpuMetrics(): ClusterMetricsListener.CpuMetrics =
    ClusterMetricsListener.CpuMetrics(randomAddress(), Random.nextDouble() % 1000, Random.nextInt(4))
  val randomMessageGenerators = List(() => randomCpuMetrics(), () => randomMemMetrics(), () => randomNodeStatus())

  val randomizer = actor (context) (new Act {
    case object SendNextRandomMessage
    context.system.scheduler.schedule(1.second, 1.second, self, SendNextRandomMessage)
    become {
      case SendNextRandomMessage =>
        context.parent ! randomMessageGenerators(Random.nextInt(randomMessageGenerators.length))()
    }
  })

  val listeners = Set (
    context.actorOf(Props[ClusterMetricsListener], "ClusterMetricsListener"),
    context.actorOf(Props[DdataListener], "DdataListener"),
    context.actorOf(Props[ShardingStatsListener], "ShardingStatsListener"),
    randomizer
  )

  val server = context.actorOf(Props[Server], "Server")

  val starter = context.actorOf(Props[OpenstackActor], "OpenstackActor")

  override def receive: Receive = {
    case l: LaunchCommand =>
      starter forward l

    case anything if listeners.contains(sender()) =>
      server ! anything

    case other =>
      log.error("MetricsAggregator: other {} from {}", other, sender())
  }
}
