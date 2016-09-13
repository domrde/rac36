package dashboard

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.metrics.StandardMetrics.{Cpu, HeapMemory}
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsExtension, NodeMetrics}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// based on source from http://doc.akka.io/docs/akka/current/scala/cluster-metrics.html
class ClusterMetricsListener extends Actor with ActorLogging {

  val extension = ClusterMetricsExtension(context.system)

  val cluster = Cluster(context.system)

  override def preStart(): Unit = extension.subscribe(self)

  override def postStop(): Unit = extension.unsubscribe(self)

  def receive = {
    case ClusterMetricsChanged(clusterMetrics) =>
      clusterMetrics.foreach { nodeMetrics =>
        logHeap(nodeMetrics)
        logCpu(nodeMetrics)
      }

    case other => log.error("dashboard.ClusterMetricsListener: other {} from {}", other, sender())
  }

  def logHeap(nodeMetrics: NodeMetrics): Unit = nodeMetrics match {
    case HeapMemory(address, timestamp, used, committed, Some(max)) =>
      context.parent ! MetricsAggregator.MemoryMetrics(address, Math.round(committed.doubleValue / 1024 / 1024), Math.round(max / 1024 / 1024))
    case _ => // No heap info.
  }

  def logCpu(nodeMetrics: NodeMetrics): Unit = nodeMetrics match {
    case Cpu(address, timestamp, Some(systemLoadAverage), cpuCombined, cpuStolen, processors) =>
      context.parent ! MetricsAggregator.CpuMetrics(address, systemLoadAverage, processors)
    case _ => // No cpu info.
  }

  context.system.scheduler.schedule(0.seconds, 3.seconds) {
    context.parent ! MetricsAggregator.Members(
      cluster.state.members.map(member =>
        MetricsAggregator.Member(member.address, member.status.toString, member.roles.head))
    )
  }
}