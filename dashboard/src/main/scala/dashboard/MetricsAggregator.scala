package dashboard

import akka.actor.{Actor, ActorLogging, Address, Props}
import com.typesafe.config.ConfigFactory
import common.SharedMessages.Position
import dashboard.ServerClient.LaunchCommand

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by dda on 9/6/16.
  */
object MetricsAggregator {
  // From ClusterMain
  case class NodeUp(address: Address, role: String)
  case class NodeDown(address: Address)

  // From ClusterMetricsListener
  case class MemoryMetrics(address: Address, usedHeap: Long, maxHeap: Long)
  case class CpuMetrics(address: Address, average: Double, processors: Int)
  case class Member(address: Address, status: String, role: String)
  case class Members(members: Set[Member])

  // From DdataListener
  case class DdataStatus(data: Set[Position])

  //From ShardingStatsListener
  case class RegionMetric(shardId: String, entities: Set[String])
  case class RegionMetrics(metrics: Set[RegionMetric])
  case class ShardingStats(address: Address, stats: Map[String, Int])

  case class NodeMetrics(address: Address,
                         cpuCur:  Option[Double],
                         cpuMax:  Option[Double],
                         memCur:  Option[Long],
                         memMax:  Option[Long],
                         role:    Option[String],
                         status:  Option[String],
                         clients: Option[Int])

  case object SendMetricsToServer
  case class CollectedMetrics(metrics: List[NodeMetrics], t: String = "CollectedMetrics")
}

class MetricsAggregator extends Actor with ActorLogging {
  import MetricsAggregator._

  val config = ConfigFactory.load()

  val listeners = if (config.getBoolean("application.testData")) {
    Set(context.actorOf(Props[TestMetrics], "TestMetrics"))
  } else {
    Set(
      context.actorOf(Props[ClusterMetricsListener], "ClusterMetricsListener"),
      context.actorOf(Props[DdataListener], "DdataListener"),
      context.actorOf(Props[ShardingStatsListener], "ShardingStatsListener")
    )
  }

  val server = context.actorOf(Props[Server], "Server")

  val starter = context.actorOf(Props[OpenstackActor], "OpenstackActor")

  val updatePeriod = FiniteDuration(config.getDuration("application.updatePeriod").getSeconds, SECONDS)
  context.system.scheduler.schedule(1.second, updatePeriod, self, SendMetricsToServer)

  override def receive: Receive = receiveWithNodesMetrics(Map.empty)

  def receiveWithNodesMetrics(metrics: Map[Address, NodeMetrics]): Receive = {
    case l: LaunchCommand =>
      starter forward l

    case SendMetricsToServer =>
      server ! CollectedMetrics(metrics.values.toList.sortBy(_.address.host))

    case anything if listeners.contains(sender()) =>
      aggregateMetrics(anything, metrics)

    case anything if sender() == context.parent =>
      aggregateMetrics(anything, metrics)

    case other =>
      log.error("MetricsAggregator: other [{}] from [{}]", other, sender())
  }

  def aggregateMetrics(msg: Any, nodes: Map[Address, NodeMetrics]) = msg match {
    case c: Members =>
      val currentNodes = c.members.map { member =>
        val cur = nodes.getOrElse(member.address, NodeMetrics(member.address, None, None, None, None, None, None, None))
        val updated = cur.copy(role = Some(member.role), status = Some(member.status))
        member.address -> updated
      }.toMap
      context.become(receiveWithNodesMetrics(currentNodes))

    case c: NodeUp =>
      val cur = nodes.getOrElse(c.address, NodeMetrics(c.address, None, None, None, None, None, None, None))
      val updated = cur.copy(role = Some(c.role))
      context.become(receiveWithNodesMetrics(nodes + (c.address -> updated)))

    case c: NodeDown =>
      context.become(receiveWithNodesMetrics(nodes - c.address))

    case c: ShardingStats =>
    //todo

    case c: DdataStatus =>
    //todo

    case c: RegionMetrics =>
    //todo

    case c: MemoryMetrics =>
    val cur = nodes.getOrElse(c.address, NodeMetrics(c.address, None, None, None, None, None, None, None))
    val updated = cur.copy(memCur = Some(c.usedHeap), memMax = Some(c.maxHeap))
    context.become(receiveWithNodesMetrics(nodes + (c.address -> updated)))

    case c: CpuMetrics =>
    val cur = nodes.getOrElse(c.address, NodeMetrics(c.address, None, None, None, None, None, None, None))
    val updated = cur.copy(cpuCur = Some(c.average), cpuMax = Some(c.processors))
    context.become(receiveWithNodesMetrics(nodes + (c.address -> updated)))

    case other =>
      log.error("MetricsAggregator: unknown metrics [{}] from [{}]", other, sender())
  }
}
