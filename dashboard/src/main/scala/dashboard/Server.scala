package dashboard

import akka.NotUsed
import akka.actor._
import akka.cluster.client.ClusterClient.Publish
import akka.cluster.pubsub.DistributedPubSub
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.dda.brain.BrainMessages
import com.typesafe.config.ConfigFactory
import common.Constants.AVATAR_STATE_SUBSCRIPTION
import dashboard.MetricsAggregator.{CollectedMetrics, MetricsAggregationMessage}
import dashboard.ServerClient.LaunchCommand
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import vivarium.Avatar

/**
  * Created by dda on 9/6/16.
  */
object Server {
  case object Join
}

object Protocols extends DefaultJsonProtocol {
  case class ChangeOperationState(newState: String)
  implicit val changeOperationStateFormat: RootJsonFormat[ChangeOperationState] = jsonFormat1(ChangeOperationState)
}

class Server extends Actor with ActorLogging {
  import Directives._
  import Protocols._

  implicit val system = context.system

  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()

  val starter = context.actorOf(Props[OpenstackActor], "OpenstackActor")

  val metrics = context.actorOf(Props[MetricsAggregator], "MetricsAggregator")

  val mediator = DistributedPubSub(context.system).mediator

  def newUser(): Flow[Message, Message, NotUsed] = {
    val userActor = context.actorOf(Props[ServerClient])

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => ServerClient.IncomingMessage(text)
      }.to(Sink.actorRef[ServerClient.IncomingMessage](userActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ServerClient.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          userActor ! ServerClient.Connected(outActor)
          NotUsed
        }.map(
        (outMsg: ServerClient.OutgoingMessage) => TextMessage(outMsg.text))

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  val route: Route = staticFilesRoute ~ avatarsRoute ~ robotsRoute ~ statsRoute

  lazy val staticFilesRoute: Route =
    pathSingleSlash {
      getFromFile("dashboard/src/webapp/index.html")
    } ~
      pathPrefix("") {
        getFromDirectory("dashboard/src/webapp/")
      }

  //todo: refactor
  lazy val state: Route = {
    path("state") {
      post {
        entity(as[ChangeOperationState]) { state =>
          val newState = if (state.newState == "Start") BrainMessages.Start else BrainMessages.Stop
          mediator ! Publish(AVATAR_STATE_SUBSCRIPTION, Avatar.ChangeState(null, newState))
          log.info("[-] dashboard.Server: State changed to [{}]", state)
          complete(StatusCodes.OK)
        }
      }
    }
  }

  lazy val avatarsRoute: Route =
    path("avatar") {
      get {
        complete("Not implemented")
      } ~
        post {
          complete("Not implemented")
        }
    }

  lazy val robotsRoute: Route =
    path("robot") {
      get {
        complete("Not implemented")
      } ~
        post {
          complete("Not implemented")
        }
    }

  lazy val statsRoute: Route =
    path("stats") {
      get {
        handleWebSocketMessages(newUser())
      }
    }

  Http().bindAndHandle(route, "127.0.0.1", config.getInt("application.httpBindingPort"))

  override def receive = receiveWithClients(List.empty)

  def receiveWithClients(clients: List[ActorRef]): Receive = {
    case m: MetricsAggregationMessage =>
      metrics forward m

    case l: LaunchCommand =>
      starter forward l

    case Server.Join =>
      context.become(receiveWithClients(sender() :: clients))

    case c: CollectedMetrics =>
      clients.foreach(_ ! c)

    case other =>
      log.error("[-] dashboard.Server: other {} from {}", other, sender())
  }
}
