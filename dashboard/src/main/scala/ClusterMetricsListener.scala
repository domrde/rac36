import akka.actor.{Actor, ActorLogging, Address}
import akka.cluster.metrics.StandardMetrics.{Cpu, HeapMemory}
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsExtension, NodeMetrics}

// based on source from http://doc.akka.io/docs/akka/current/scala/cluster-metrics.html
object ClusterMetricsListener {
  case class MemoryMetrics(address: Address, usedHeap: Double, t: String = "MemoryMetrics")
  case class CpuMetrics(address: Address, average: Double, processors: Int, t: String = "CpuMetrics")
}

class ClusterMetricsListener extends Actor with ActorLogging {
  import ClusterMetricsListener._
  val extension = ClusterMetricsExtension(context.system)

  override def preStart(): Unit = extension.subscribe(self)

  override def postStop(): Unit = extension.unsubscribe(self)

  def receive = {
    case ClusterMetricsChanged(clusterMetrics) =>
      clusterMetrics.foreach { nodeMetrics =>
        logHeap(nodeMetrics)
        logCpu(nodeMetrics)
      }

    case other => log.error("ClusterMetricsListener: other {} from {}", other, sender())
  }

  def logHeap(nodeMetrics: NodeMetrics): Unit = nodeMetrics match {
    case HeapMemory(address, timestamp, used, committed, max) =>
      context.parent ! MemoryMetrics(address, committed.doubleValue / 1024 / 1024)
    case _ => // No heap info.
  }

  def logCpu(nodeMetrics: NodeMetrics): Unit = nodeMetrics match {
    case Cpu(address, timestamp, Some(systemLoadAverage), cpuCombined, cpuStolen, processors) =>
      context.parent ! CpuMetrics(address, systemLoadAverage, processors)
    case _ => // No cpu info.
  }
}