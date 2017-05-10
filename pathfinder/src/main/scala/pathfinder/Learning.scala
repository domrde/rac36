package pathfinder

import libsvm._
import pathfinder.Globals._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object Learning {
  final case class Example(p: Point, c: Double)
  final case class Point(y: Double, x: Double)
  final case class Obstacle(y: Double, x: Double, r: Double) {
    def intersects(l1: Point, l2: Point): Boolean = {
      val a = l1.y - l2.y
      val b = l2.x - l1.x
      val c = (l1.x - l2.x) * l1.y + l1.x * (l2.y - l1.y)
      val value = Math.abs(b * x + a * y + c) / Math.sqrt(b * b + a * a)
      value <= (r + 1.5)
    }
  }
  final case class Path(path: List[Point])
  case class RunResults(path: Path, timeMs: Double, isCorrect: Boolean, message: String)
}

object InputMapper {
  import Learning._

  case class Group(members: List[Obstacle]) {
    def intersects(another: Group): Boolean = {
      members.exists { a =>
        another.members.exists { b =>
          Math.pow(a.y - b.y, 2.0) + Math.pow(a.x - b.x, 2.0) <= Math.pow(a.r + b.r + 0.1, 2.0)
        }
      }
    }

    def merge(another: Group): Group = {
      Group(members ::: another.members)
    }
  }

  def mapPivotsToExamples(pivots: List[Point], height: Double, width: Double,
                          s: Point, f: Point): List[Example] = {
    pivots.map { point =>
      val c = if ((f.x - s.x) * (point.y - s.y) < (f.y - s.y) * (point.x - s.x)) 1.0 else -1.0
      Example(Point(point.y / height, point.x / width), c)
    }
  }

  def mapObstaclesToExamples(obstacles: List[Obstacle], height: Double, width: Double,
                             s: Point, f: Point): List[Example] = {
    val widthRange = 0.01 * width
    val heightRange = 0.01 * height
    def randomDoubleInRange(rangeMin: Double, rangeMax: Double) = {
      rangeMin + (rangeMax - rangeMin) * Random.nextDouble()
    }

    val groups =
      obstacles.sortBy(_.y).foldLeft(Set.empty[Group]) { case (listOfGroups, obstacle) =>
        val newGroup = Group(List(obstacle))
        val possibleNeighbour = listOfGroups.find { group => group.intersects(newGroup) }
        if (possibleNeighbour.isDefined) {
          (listOfGroups - possibleNeighbour.get) + possibleNeighbour.get.merge(newGroup)
        } else {
          listOfGroups + newGroup
        }
      }

    groups.flatMap { group =>
      val sum = group.members.map(point => if ((f.x - s.x) * (point.y - s.y) < (f.y - s.y) * (point.x - s.x)) 1.0 else -1.0).sum
      val c = if (sum > 0) 1.0 else -1.0
      group.members.flatMap { point =>
        val y = point.y / height
        val x = point.x / width
        (0 to 5).map(_ => Example(Point(
          y + randomDoubleInRange(y - heightRange, y + heightRange),
          x  + randomDoubleInRange(x - widthRange, x + widthRange)), c))
      }
    }.toList
  }
}

object SvmType {
  sealed abstract class SvmType(val id: Int)
  case object C_SVC extends SvmType(svm_parameter.C_SVC)
  case object NU_SVC extends SvmType(svm_parameter.NU_SVC)
  case object ONE_CLASS extends SvmType(svm_parameter.ONE_CLASS)
  case object EPSILON_SVR extends SvmType(svm_parameter.EPSILON_SVR)
  case object NU_SVR extends SvmType(svm_parameter.NU_SVR)
}

object KernelType {
  sealed abstract class KernelType(val id: Int)
  case object LINEAR extends KernelType(svm_parameter.LINEAR)
  case object POLY extends KernelType(svm_parameter.POLY)
  case object RBF extends KernelType(svm_parameter.RBF)
  case object SIGMOID extends KernelType(svm_parameter.SIGMOID)
  case object PRECOMPUTED extends KernelType(svm_parameter.PRECOMPUTED)
}

class Learning {
  import KernelType._
  import Learning._
  import SvmType._

  svm.svm_set_print_string_function(new svm_print_interface {
    override def print(s: String): Unit = {
      // do nothing
    }
  })

  case class SvmParameters(svmType: SvmType, kernelType: KernelType, gamma: Double, cost: Double, eps: Double) {
    def toSvmParameter: svm_parameter = {
      val param = new svm_parameter
      param.svm_type = svmType.id
      param.kernel_type = kernelType.id
      param.degree = 3
      param.gamma = gamma
      param.coef0 = 0
      param.nu = 0.5
      param.cache_size = 40
      param.C = cost
      param.eps = eps
      param.p = 0.1
      param.shrinking = 0
      param.probability = 0
      param.nr_weight = 0
      param.weight_label = new Array[Int](0)
      param.weight = new Array[Double](0)
      param
    }
  }

  private def time[A](f: => A) = {
    val s = System.nanoTime
    f
    (System.nanoTime - s) / 1e6
  }

  private def trainSvmModel(field: List[Example], params: SvmParameters) = {
    val prob = new svm_problem
    prob.l = field.size
    prob.y = new Array[Double](prob.l)

    prob.x = Array.fill(prob.l, 2)(new svm_node)
    field.indices.foreach { i =>
      val p = field(i)
      prob.x(i)(0).index = 1
      prob.x(i)(0).value = p.p.x
      prob.x(i)(1).index = 2
      prob.x(i)(1).value = p.p.y
      prob.y(i) = p.c
    }

    svm.svm_train(prob, params.toSvmParameter)
  }

  type TargetPointFinder = (List[(Point, Double)]) => Point

  private def buildPath(model: svm_model, d: Double, angleDelta: Double, from: Point, to: Point,
                        dims: Point, color: String, finder: TargetPointFinder): Path = {
    val par = Array.fill(2)(new svm_node)
    par(0) = new svm_node
    par(1) = new svm_node
    par(0).index = 1
    par(1).index = 2

    def buildPath(accumulator: List[Point], limit: Int): List[Point] =
      if (limit < 0 || distance(to, accumulator.head) < d) accumulator else {
        val curPoint = accumulator.head
        val curAngle = Math.atan2(to.y - curPoint.y, to.x - curPoint.x) * 180.0 / Math.PI
        val vals = (curAngle - angleDelta to curAngle + angleDelta by 0.5).map { angle =>
          val y = Math.sin(Math.toRadians(angle)) * d
          val x = Math.sqrt(Math.pow(d, 2.0) - Math.pow(y, 2.0))
          par(0).value = (curPoint.x + x) / dims.x
          par(1).value = (curPoint.y + y) / dims.y
          (Point(curPoint.y + y, curPoint.x + x), svm.svm_predict(model, par))
        }.toList
        val targetPoint = finder.apply(vals)
        buildPath(targetPoint :: accumulator, limit - 1)
      }

    Path(buildPath(List(from), 50).reverse)
  }

  val defaultParameters = SvmParameters(SvmType.EPSILON_SVR, KernelType.RBF, eps = 0.5, gamma = 30, cost = 100)

  def runClassificationSVM(obstacles: List[Obstacle], dims: Point, start: Point, finish: Point): Future[Option[RunResults]] = {
    @tailrec
    def findElementOfSignChange(elements: List[(Point, Double)], initialSign: Boolean): Point =
      elements match {
        case head :: Nil =>
          head._1

        case head :: tail =>
          if ((head._2 < 0.0) == initialSign) findElementOfSignChange(tail, initialSign) else head._1
      }

    runSVM(SvmType.C_SVC, obstacles, dims, start, finish, 90.0, vals => findElementOfSignChange(vals, vals.head._2 < 0))
  }

  def runRegressionSVM(obstacles: List[Obstacle], dims: Point, start: Point, finish: Point): Future[Option[RunResults]] = {
    runSVM(SvmType.EPSILON_SVR, obstacles, dims, start, finish, 100.0, vals => vals.minBy(pair => Math.abs(pair._2))._1)
  }

  private def runSVM(svmType: SvmType, obstacles: List[Obstacle], dims: Point,
                     start: Point, finish: Point, angle: Double, finder: TargetPointFinder): Future[Option[RunResults]] = {
    val field = InputMapper.mapObstaclesToExamples(obstacles, dims.y, dims.x, start, finish)

    val results =
      List(0.5, 0.2).flatMap { d =>
        List(0.25, 0.5, 0.75).flatMap { robotSize =>
          List(0.0001, 0.01, 0.1, 0.5, 1.0).flatMap { eps =>
            List(0.1, 0.5, 1, 5, 10, 20, 40, 100).flatMap { gamma =>
              List(0.001, 0.01, 0.5, 1, 10, 40, 100).map { cost =>
                Future {
                  val pivots = InputMapper.mapPivotsToExamples(Pivots.getPivotPoints(start, finish, robotSize), dims.y, dims.x, start, finish)
                  var path: Path = null
                  val elapsed = time {
                    val model = trainSvmModel(field ::: pivots, defaultParameters.copy(svmType = svmType, eps = eps, gamma = gamma, cost = cost))
                    path = buildPath(model, d, angle, start, finish, dims, "black", finder)
                  }
                  val (isPathCorrect, errorMessage) = checkPathCorrect(obstacles, dims, start, finish, path)
                  val message = s"Training model pathStep=$d robotSize=$robotSize eps=$eps gamma=$gamma cost=$cost. Elapsed $elapsed ms. $errorMessage"
                  RunResults(path, elapsed, isPathCorrect, message)
                }
              }
            }
          }
        }
      }

//        def lengthOfPath(path: Path) = {
//          path.path.sliding(2).foldLeft(0.0) { case (accumulator, a :: b :: Nil) => accumulator + distance(a, b) }
//        }
//
//        Future.sequence(results).map { results =>
//          results.foreach(res => println(res.message))
//          val filtered = results.filter(_.isCorrect)
//          filtered.foreach(println)
//          if (filtered.isEmpty) {
//            None
//          } else {
//            Some(filtered.minBy(result => lengthOfPath(result.path)))
//          }
//        }
    Future.find(results)(_.isCorrect)
  }

  def checkPathCorrect(obstacles: List[Obstacle], dims: Point, start: Point, finish: Point, path: Path): (Boolean, String) = {
    val pointOfPathInsideObstacle = false
      path.path.sliding(2).exists { case a :: b :: Nil =>
        obstacles.exists { obstacle =>
          obstacle.intersects(a, b)
        }
      }

    val pointOutsideField =
      path.path.exists(point => point.x < 0 || point.y < 0 || point.x > dims.x || point.y > dims.y)

    val isAwayFromStart = distance(path.path.head, start) > 1.0 && distance(path.path.last, start) > 1.0

    val isAwayFromFinish = distance(path.path.head, finish) > 1.0 && distance(path.path.last, finish) > 1.0

    val errorMessage = (if (pointOfPathInsideObstacle) " [intersects obstacle]" else "") +
      (if (isAwayFromStart) " [not near start]" else "") +
      (if (isAwayFromFinish) " [not near finish]" else "") +
      (if (pointOutsideField)  " [outside field]" else "")

    (!(pointOfPathInsideObstacle || isAwayFromStart || isAwayFromFinish || pointOutsideField), errorMessage)
  }

}
