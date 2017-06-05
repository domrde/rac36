package playground

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import playground.complex.Car
import playground.simple.{DirectMovementExperimentRunner, FullKnowledgeExperimentRunner}
import utils.zmqHelpers.ZeroMQHelper
import vrepapiscala.VRepAPI

import scala.collection.JavaConversions._

/**
  * Created by dda on 9/10/16.
  */
object Boot extends App {
  val system = ActorSystem("ClusterSystem")
  val helper = ZeroMQHelper(system)
  val config = ConfigFactory.load()
  val api = VRepAPI.connect("127.0.0.1", 19997).get

  if (config.getString("brain-class").contains("DirectMovementExperiment")) {
    system.actorOf(Props(classOf[DirectMovementExperimentRunner], api))
  } else {
    if (config.getBoolean("playground.full-knowledge")) {
      system.actorOf(Props(classOf[FullKnowledgeExperimentRunner], api))
    } else {
      config.getStringList("playground.car-ids").map(id => system.actorOf(Car(id, api), "Car" + id.substring(1)))
    }
  }

  api.simulation.start()
  system.registerOnTermination(() => api.simulation.stop())
}