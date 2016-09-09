import ServerClient.LaunchCommand
import akka.actor.ActorDSL._
import akka.actor.{Actor, ActorLogging, Address, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by dda on 9/6/16.
  */
class MetricsAggregator extends Actor with ActorLogging {

  val config = ConfigFactory.load()

  val listeners = if (config.getBoolean("application.testData")) {
    val ips = List.fill(12)(Random.nextString(10))
    def randomAddress() = Address.apply("tcp", "ClusterSystem", ips(Random.nextInt(ips.length)), 9999)
    val roles = List("Avatar", "Pipe")
    def randomNodeUp(): ClusterMain.NodeUp =
      ClusterMain.NodeUp(randomAddress(), roles(Random.nextInt(roles.length)))
    def randomNodeDown(): ClusterMain.NodeDown =
      ClusterMain.NodeDown(randomAddress())
    def randomMemMetrics(): ClusterMetricsListener.MemoryMetrics =
      ClusterMetricsListener.MemoryMetrics(randomAddress(), Random.nextInt(1000))
    def randomCpuMetrics(): ClusterMetricsListener.CpuMetrics =
      ClusterMetricsListener.CpuMetrics(randomAddress(), 0.01 + Random.nextInt(40) / 100, 1 + Random.nextInt(3))
    val randomMessageGenerators = List(() => randomCpuMetrics(), () => randomMemMetrics(),
      () => randomNodeDown(), () => randomNodeUp())

    Set(actor (context) (new Act {
      case object SendNextRandomMessage
      context.system.scheduler.schedule(1.second, 500.millis, self, SendNextRandomMessage)
      become {
        case SendNextRandomMessage =>
          context.parent ! randomMessageGenerators(Random.nextInt(randomMessageGenerators.length))()
      }
    }))
  } else {
    Set(
      context.actorOf(Props[ClusterMetricsListener], "ClusterMetricsListener"),
      context.actorOf(Props[DdataListener], "DdataListener"),
      context.actorOf(Props[ShardingStatsListener], "ShardingStatsListener")
    )
  }

  val server = context.actorOf(Props[Server], "Server")

  val starter = context.actorOf(Props[OpenstackActor], "OpenstackActor")

  override def receive: Receive = {
    case l: LaunchCommand =>
      starter forward l

    case anything if listeners.contains(sender()) =>
      server ! anything

    case anything if sender() == context.parent =>
      server ! anything

    case other =>
      log.error("MetricsAggregator: other {} from {}", other, sender())
  }
}
