package dashboard

import akka.NotUsed
import akka.actor._
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.typesafe.config.ConfigFactory
import common.Constants.AvatarsDdataSetKey
import dashboard.MetricsAggregator.{CollectedMetrics, MetricsAggregationMessage}
import dashboard.clients.MetricsClient.LaunchCommand
import dashboard.clients.{AvatarClient, MetricsClient, ServerClient}
import vivarium.ReplicatedSet
import vivarium.ReplicatedSet.LookupResult

/**
  * Created by dda on 9/6/16.
  */
object Server {
  trait ClientType
  case object AvatarClient extends ClientType
  case object MetricsClient extends ClientType

  case class Join(clientType: ClientType)
}

class Server extends Actor with ActorLogging {
  import Directives._

  implicit val system = context.system

  implicit val materializer = ActorMaterializer()

  private val config = ConfigFactory.load()

  private val starter = context.actorOf(Props[OpenstackActor], "OpenstackActor")

  private val metrics = context.actorOf(Props[MetricsAggregator], "MetricsAggregator")

  private val avatarIdStorage = context.actorOf(ReplicatedSet(AvatarsDdataSetKey))

  private val shard = ClusterSharding(system).shardRegion("Avatar")

  def newMetricsUser(): Flow[Message, Message, NotUsed] = {
    newServerUser(context.actorOf(Props[MetricsClient]))
  }

  def newAvatarUser(): Flow[Message, Message, NotUsed] = {
    newServerUser(context.actorOf(Props(classOf[AvatarClient], avatarIdStorage, shard)))
  }

  def newServerUser(actor: ActorRef): Flow[Message, Message, NotUsed] = {
    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => ServerClient.IncomingMessage(text)
      }.to(Sink.actorRef[ServerClient.IncomingMessage](actor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ServerClient.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          actor ! ServerClient.Connected(outActor)
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

  lazy val avatarsRoute: Route =
    path("avatar") {
      get {
        handleWebSocketMessages(newAvatarUser())
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
        handleWebSocketMessages(newMetricsUser())
      }
    }

  Http().bindAndHandle(route, "127.0.0.1", config.getInt("application.httpBindingPort"))

  override def receive = receiveWithClients(List.empty, List.empty)

  def receiveWithClients(metricClients: List[ActorRef], avatarClients: List[ActorRef]): Receive = {
    case m: MetricsAggregationMessage =>
      metrics forward m

    case l: LaunchCommand =>
      starter forward l

    case Server.Join(Server.MetricsClient) =>
      context.become(receiveWithClients(sender() :: metricClients, avatarClients))

    case Server.Join(Server.AvatarClient) =>
      context.become(receiveWithClients(metricClients, sender() :: avatarClients))

    case c: CollectedMetrics =>
      metricClients.foreach(_ ! c)

    case l: LookupResult =>
      avatarClients.foreach(_ ! l)

    case other =>
      log.error("[-] dashboard.Server: other {} from {}", other, sender())
  }
}
