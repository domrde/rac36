package pipe

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.metrics.ClusterMetricsExtension
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, ClusterEvent}

class ClusterMain extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  ClusterMetricsExtension(context.system)
  DistributedPubSub(context.system).mediator

  cluster.subscribe(self, classOf[ClusterEvent.MemberUp], classOf[ClusterEvent.MemberRemoved])

  override def receive: Receive = initial

  val initial: Receive = {
    case ccs: CurrentClusterState => ccs.members.find(_.address == cluster.selfAddress).foreach(_ => startMainSystem())
    case MemberUp(member) =>
      log.info("MemberUp {} with roles {}", member.uniqueAddress, member.roles)
      if (cluster.selfAddress == member.address) startMainSystem()
    case MemberRemoved(member, _) => log.info("MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  val initialised: Receive = {
    case MemberUp(member) => log.info("MemberUp {} with roles {}", member.uniqueAddress, member.roles)
    case MemberRemoved(member, _) => log.info("MemberRemoved {} with roles {}", member.uniqueAddress, member.roles)
  }

  def startMainSystem() = {
    context.actorOf(Props[TunnelManager], "TunnelManager")
    context.become(initialised)
    log.info("\n---------------------------------------------------------------------------")
    log.info("\n\nTunnelManager initialised")
    log.info("\n---------------------------------------------------------------------------")
  }
}