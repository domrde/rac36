package common.zmqHelpers

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 9/14/16.
  */
object JsonValidator {
  case class Validate(bytes: Array[Byte])
  case class ValidationResult(msg: AnyRef)
}

class JsonValidator extends Actor with ActorLogging {
  import JsonValidator._
  import common.Implicits._

  lazy val allReads = List(createAvatarReads, sensoryReads)

  override def receive: Receive = {
    case Validate(bytes) =>
      processBytes(bytes)

    case other =>
      log.error("JsonValidator: other [{}] from [{}]", other, sender())
  }

  def processBytes(bytes: Array[Byte]) = {
    val bytesAsString = ByteString(bytes).utf8String
    val (_, data) = bytesAsString.splitAt(bytesAsString.indexOf("|"))
    Try(Json.parse(data.drop(1))) match {
      case Success(parsedJson) =>
        validateJson(parsedJson)
      case Failure(exception) =>
        log.error("Malformed message [{}] caused exception [{}]", bytesAsString, exception.getMessage)
    }
  }

  //todo: come up with a better way
  def validateJson(json: JsValue) = {
    allReads.map { reads =>
      json.validate(reads) match {
        case JsSuccess(value, path) =>
          Some(ValidationResult(value))

        case JsError(_) =>
          None
      }
    } find { // only one result needed, so traversing all list not necessary
      case Some(_) => true
      case _ => false
    } match {
      case Some(Some(x)) => sender() ! x
      case None => log.error("Failed to validate json [{}]", json)
    }
  }
}
