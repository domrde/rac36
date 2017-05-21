package pathfinder.pathfinding

import pathfinder.FutureO
import pathfinder.Globals._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by dda on 11.05.17.
  */
object Pathfinder {
  def findPath(dims: Point, start: Point, finish: Point, obstacles: List[Obstacle]): FutureO[RunResults] = {
    val patches: Future[List[MapPatch]] = Patcher.preparePatches(dims, obstacles)
    val roughPath: FutureO[List[Point]] = AStar.findPath(start, finish, patches)

//    roughPath.flatMap { roughPathInner =>
//      FutureO(Future.successful(Some(RunResults(roughPathInner, ""))))
//    }

    Learning.smoothPath(dims, start, finish, roughPath)
  }
}
