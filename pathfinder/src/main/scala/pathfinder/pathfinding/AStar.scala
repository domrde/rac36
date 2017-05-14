package pathfinder.pathfinding

import pathfinder.{FutureO, Globals}
import pathfinder.Globals._
import pathfinder.pathfinding.Patcher.Polygon

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.Breaks._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by dda on 11.05.17.
  */
object AStar {

  def extractStartAndFinish(start: Point, finish: Point, patches: List[MapPatch]): (Option[MapPatch], Option[MapPatch]) = {
    val polygons = patches.map(patch => (patch, Polygon(patch.coordinates)))

    val startPoly = polygons.find { case (_, polygon) =>
      val (min, max) = polygon.getBoundingBox
      min.y <= start.y && start.y <= max.y &&
        min.x <= start.x && start.x <= max.x
    }.map(_._1)

    val finishPoly = polygons.find { case (_, polygon) =>
      val (min, max) = polygon.getBoundingBox
      min.y <= finish.y && finish.y <= max.y &&
        min.x <= finish.x && finish.x <= max.x
    }.map(_._1)

    (startPoly, finishPoly)
  }

  def heuristicEstimate(a: MapPatch, b: MapPatch): Double = {
    Globals.distance(a.centroid, b.centroid)
  }

  def distance(a: MapPatch, b: MapPatch): Double = {
    Globals.distance(a.centroid, b.centroid)
  }

  def reconstructPath(mappedPatches: Map[Int, MapPatch], cameFrom: Map[Int, Int], current: Int, startId: Int): List[Point] = {
    @tailrec
    def reconstructPath(cameFrom: Map[Int, Int], current: Int, path: List[Point]): List[Point] = {
      if (current == startId) mappedPatches(current).centroid :: path
      else reconstructPath(cameFrom - current, cameFrom(current), mappedPatches(current).centroid :: path)
    }

    reconstructPath(cameFrom, current, List.empty)
  }

  def findPath(start: Point, finish: Point, patches: Future[List[MapPatch]]): FutureO[List[Point]] = {
    FutureO(
      patches.map { patches =>
        findPath(start, finish, patches)
      }
    )
  }

  def findPath(start: Point, finish: Point, patches: List[MapPatch]): Option[List[Point]] = {
    extractStartAndFinish(start, finish, patches) match {
      case (Some(startPoly), Some(finishPoly)) =>
        doFindPath(startPoly, finishPoly, patches)

      case _ =>
        println("A*: Start or finish patches not found")
        None
    }
  }

  private def doFindPath(start: MapPatch, finish: MapPatch, patches: List[MapPatch]): Option[List[Point]] = {
    val mappedPatches: Map[Int, MapPatch] = patches.map(patch => patch.id -> patch).toMap

    // The set of nodes already evaluated.
    var closedSet: Set[Int] = Set.empty

    // The set of currently discovered nodes that are not evaluated yet.
    // Initially, only the start node is known.
    var openSet: Set[Int] = Set(start.id)

    // For each node, which node it can most efficiently be reached from.
    // If a node can be reached from many nodes, cameFrom will eventually contain the
    // most efficient previous step.
    var cameFrom: Map[Int, Int] = Map.empty

    // For each node, the cost of getting from the start node to that node.
    var gScore: Map[Int, Double] = Map.empty.withDefaultValue(Double.PositiveInfinity)

    // The cost of going from start to start is zero.
    gScore = gScore + (start.id -> 0)

    // For each node, the total cost of getting from the start node to the goal
    // by passing by that node. That value is partly known, partly heuristic.
    var fScore: Map[Int, Double] = Map.empty.withDefaultValue(Double.PositiveInfinity)

    // For the first node, that value is completely heuristic.
    fScore = fScore + (start.id -> heuristicEstimate(start, finish))

    while (openSet.nonEmpty) {
      val current = mappedPatches(openSet.minBy(patch => fScore(patch)))
      if (current == finish) {
        // end
        return Some(reconstructPath(mappedPatches, cameFrom, current.id, start.id))
      }

      openSet = openSet - current.id
      closedSet = closedSet + current.id

      current.exits.foreach { idOfPatch =>
        breakable {
          val neighbour = idOfPatch

          if (closedSet.contains(neighbour)) {
            // Ignore the neighbor which is already evaluated.
            break
          }

          // The distance from start to a neighbor
          val tentativeGScore = gScore(current.id) + distance(current, mappedPatches(neighbour))

          if (!openSet.contains(neighbour)) {
            openSet = openSet + neighbour
          } else if (tentativeGScore >= gScore(neighbour)) {
            // This is not a better path.
            break
          }

          // This path is the best until now. Record it!
          cameFrom = cameFrom + (neighbour -> current.id)
          gScore = gScore + (neighbour -> tentativeGScore)
          fScore = fScore + (neighbour -> (tentativeGScore + heuristicEstimate(mappedPatches(neighbour), finish)))
        }
      }
    }

    // Path not found
    println("A*: path not found")
    None
  }

}
