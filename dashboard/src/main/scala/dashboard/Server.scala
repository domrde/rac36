package dashboard

import akka.actor._
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import common.Constants.{AvatarsDdataSetKey, PositionDdataSetKey}
import dashboard.MetricsAggregator.{CollectedMetrics, MetricsAggregationMessage}
import dashboard.clients.{AvatarClient, MetricsClient, PositionsClient, ServerClient}
import vivarium.ReplicatedSet

/**
  * Created by dda on 9/6/16.
  */
object Server {
  trait ClientType
  case object AvatarClient extends ClientType
  case object MetricsClient extends ClientType
  case object PositionsClient extends ClientType

  case class Join(clientType: ClientType)
}

class Server extends Actor with ActorLogging {
  import Directives._

  private implicit val system = context.system
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext = context.dispatcher

  private val config = ConfigFactory.load()
  private val starter = context.actorOf(Props[OpenstackActor], "OpenstackActor")
  private val metrics = context.actorOf(Props[MetricsAggregator], "MetricsAggregator")
  private val shard = ClusterSharding(system).shardRegion("Avatar")

  val route: Route = staticFilesRoute ~ avatarsRoute ~ statsRoute ~ ddataRoute

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
        handleWebSocketMessages(ServerClient.newServerUser(context.actorOf(AvatarClient(shard))))
      } ~ post {
        fileUpload("jar") { case (metadata, byteSource) =>
          complete(HttpResponse())
        }
      }
    }

  lazy val statsRoute: Route =
    path("stats") {
      get {
        handleWebSocketMessages(ServerClient.newServerUser(context.actorOf(MetricsClient())))
      }
    }

  lazy val ddataRoute: Route =
    path("ddata") {
      get {
        handleWebSocketMessages(ServerClient.newServerUser(context.actorOf(PositionsClient())))
      }
    }

  Http().bindAndHandle(route, "127.0.0.1", config.getInt("application.httpBindingPort"))

  override def receive: Receive = receiveWithClients(List.empty)

  def receiveWithClients(metricsClients: List[ActorRef]): Receive = {
    case m: MetricsAggregationMessage =>
      metrics forward m

    case l: ServerClient.LaunchCommand =>
      starter forward l

    case Server.Join(Server.MetricsClient) =>
      context.become(receiveWithClients(sender() :: metricsClients))

    case Server.Join(_) =>

    case c: CollectedMetrics =>
      metricsClients.foreach(_ ! c)

    case other =>
      log.error("[-] dashboard.Server: other [{}] from [{}]", other, sender())
  }
}
