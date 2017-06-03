package pathfinder.pathfinding

import pathfinder.Globals._

/**
  * Created by dda on 11.05.17.
  */
object Pathfinder {
  def findPath(dims: Point, start: Point, finish: Point, obstacles: List[Obstacle]): List[Point] = {
    val patches: List[MapPatch] = Patcher.preparePatches(dims, obstacles)
    val roughPath: List[Point] = AStar.findPath(start, finish, patches)
    Learning.smoothPath(dims, start, finish, roughPath)
  }
}
