package dashboard

import akka.actor.{Actor, Address}
import dashboard.MetricsAggregator._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by dda on 9/12/16.
  */
class TestMetrics extends Actor {

  val ips = (1 to 10).map(_.toString)

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
  def randomCpuMetrics(): CpuMetrics =
    CpuMetrics(randomAddress(), Random.nextInt(80) / 100 + 0.01, 1 + Random.nextInt(3))

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
