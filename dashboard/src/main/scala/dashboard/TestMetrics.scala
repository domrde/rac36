package dashboard

import akka.actor.{Actor, Address}
import dashboard.MetricsAggregator._

import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by dda on 9/12/16.
  */
class TestMetrics extends Actor {
  private implicit val executionContext = context.dispatcher

  private val ips = (1 to 10).map(_ => Random.nextInt(256) + "." + Random.nextInt(256) +
    "." + Random.nextInt(256) + "." + Random.nextInt(256))

  def randomAddress() = Address.apply("akka.tcp", "ClusterSystem", ips(Random.nextInt(ips.length)), 9999)

  val roles = List("Avatar", "Pipe")

  def randomNodeUp(): NodeUp =
    NodeUp(randomAddress(), roles(Random.nextInt(roles.length)))

  def randomNodeDown(): NodeDown =
    NodeDown(randomAddress())

  def randomMemMetrics(): MemoryMetrics = {
    val lower = Random.nextInt(1000)
    MemoryMetrics(randomAddress(), lower, lower + Random.nextInt(1000))
  }

  def randomCpuMetrics(): CpuMetrics = {
    val maxCpu = 1.0 + Random.nextInt(9)
    val curCpu = 0.01 + (maxCpu - 0.01) * Random.nextDouble()
    CpuMetrics(randomAddress(), Math.round(curCpu * 100.0) / 100.0, maxCpu)
  }

  def randomMember(): Members =
    Members(ips.map(ip =>
      Member(Address("akka.tcp", "ClusterSystem", ip, 9999), "Up", roles(Random.nextInt(roles.length)))
    ).toSet)

  val randomMessageGenerators = List(() => randomCpuMetrics(), () => randomMemMetrics(),
    () => randomMember())

  case object SendNextRandomMessage
  context.system.scheduler.schedule(1.second, 50.millis, self, SendNextRandomMessage)

  override def receive: Receive = {
    case SendNextRandomMessage =>
      context.parent ! randomMessageGenerators(Random.nextInt(randomMessageGenerators.length))()
    case _ =>
  }
}
