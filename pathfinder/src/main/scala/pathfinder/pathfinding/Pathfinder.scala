package pathfinder.pathfinding

import pathfinder.FutureO
import pathfinder.Globals._

/**
  * Created by dda on 11.05.17.
  */
object Pathfinder {
  def findPath(dims: Point, start: Point, finish: Point, obstacles: List[Obstacle]): FutureO[SvmResult] = {
    val patches: List[MapPatch] = Patcher.preparePatches(dims, obstacles)
    val roughPath: Option[List[Point]] = AStar.findPath(start, finish, patches)
    Learning.smoothPath(dims, start, finish, roughPath)
  }
}
