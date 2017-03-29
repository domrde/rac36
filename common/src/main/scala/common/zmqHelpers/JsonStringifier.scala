package common.zmqHelpers

import akka.actor.{Actor, ActorLogging}
import common.SharedMessages._
import play.api.libs.json.Json

/**
  * Created by dda on 9/16/16.
  */
object JsonStringifier {
  case class Stringify(msg: AnyRef, request: Option[AnyRef])
  case class StringifyResult(msg: String, request: Option[AnyRef])
}

class JsonStringifier extends Actor with ActorLogging {
  import JsonStringifier._
  import common.Implicits._

  override def receive: Receive = {
    case Stringify(msg: Control, request) =>
      sender() ! StringifyResult(Json.stringify(Json.toJson(msg)), request)

    case Stringify(msg: TunnelCreated, request) =>
      sender() ! StringifyResult(Json.stringify(Json.toJson(msg)), request)

    case Stringify(msg: FailedToCreateTunnel, request) =>
      sender() ! StringifyResult(Json.stringify(Json.toJson(msg)), request)

    case Stringify(msg: CreateAvatar, request) =>
      sender() ! StringifyResult(Json.stringify(Json.toJson(msg)), request)

    case Stringify(msg: Sensory, request) =>
      sender() ! StringifyResult(Json.stringify(Json.toJson(msg)), request)

    case other =>
      log.error("JsonStringifier: other [{}] from [{}]", other, sender())
  }

}
