package pathfinder.pathfinding
import pathfinder.pathfinding.Patcher.Polygon
import pathfinder.Globals._

object InputMapper {

  case class Group(members: List[Obstacle]) {
    def intersects(another: Group): Boolean = {
      members.exists { a =>
        another.members.exists { b =>
          Math.pow(a.y - b.y, 2.0) + Math.pow(a.x - b.x, 2.0) <= Math.pow(a.r + b.r + 0.01, 2.0)
        }
      }
    }

    def merge(another: Group): Group = {
      Group(members ::: another.members)
    }
  }

  def getPivotPoints(start: Point, finish: Point, pointOfInterest: Point, l: Double): (Point, Point) = {
    def solve(a: Double, b: Double, c: Double): (Double, Double) = {
      val d = Math.pow(b, 2.0) - 4 * a * c
      ((-b + Math.sqrt(d)) / 2.0 / a, (-b - Math.sqrt(d)) / 2.0 / a)
    }

    def calculateNeighbourPoints(point: Point, m: Double, b: Double): (Point, Point) = {
      val (first, second) = solve(
        1 + Math.pow(m, 2),
        2 * m * (b - point.y) - 2 * point.x,
        Math.pow(point.x, 2) + Math.pow(b - point.y, 2) - Math.pow(l, 2)
      )

      (Point(m * first + b, first), Point(m * second + b, second))
    }

    val m = (finish.y - start.y + 1e-6) / (finish.x - start.x + 1e-6)
    val mp = -1 / m

    calculateNeighbourPoints(pointOfInterest, mp, pointOfInterest.y - mp * pointOfInterest.x)
  }


  // todo: for now it ignores not grouped obstacles
  def mapObstaclesToPolygons(obstacles: List[Obstacle]): List[Polygon] = {
    val groups =
      obstacles.sortBy(_.y).sortBy(_.x).foldLeft(Set.empty[Group]) { case (listOfGroups, obstacle) =>
        val newGroup = Group(List(obstacle))
        val possibleNeighbour = listOfGroups.find { group => group.intersects(newGroup) }
        if (possibleNeighbour.isDefined) {
          (listOfGroups - possibleNeighbour.get) + possibleNeighbour.get.merge(newGroup)
        } else {
          listOfGroups + newGroup
        }
      }

    groups
      .filter(group => group.members.length > 1)
      .map { group =>
        val maxRadius = group.members.maxBy(_.r).r + 0.2
        val dist = maxRadius / distance(group.members.head, group.members.last)
        val start = pointInBetween(group.members.head, group.members.last)(1.0 + dist)
        val end = pointInBetween(group.members.head, group.members.last)(0.0 - dist)

        val nearStart = getPivotPoints(Point(start.y, start.x), Point(end.y, end.x), Point(start.y, start.x), maxRadius)
        val nearFinish = getPivotPoints(Point(start.y, start.x), Point(end.y, end.x), Point(end.y, end.x), maxRadius)
        Polygon(List(nearStart._1, nearStart._2, nearFinish._2, nearFinish._1))
      }
      .toList
  }
}


