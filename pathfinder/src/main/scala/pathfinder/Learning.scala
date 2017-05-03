package pathfinder

import libsvm._
import pathfinder.Globals._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object InputMapper {
  def mapObstaclesToExamples(obstacles: List[Polygon], height: Double, width: Double,
                             s: Point, f: Point): List[Example] = {

    obstacles.flatMap { obstacle =>
      val centroid = obstacle.centroid
      val c = if ((f.x - s.x) * (centroid.y - s.y) < (f.y - s.y) * (centroid.x - s.x)) 1.0 else -1.0
      obstacle.extractPointsByEdges()
        .map { case Point(y, x) => Example(Point(y / height, x / width), c) }
        .flatMap { a => List(a, a, a) }
    }
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
        val curAngle = Math.atan2(to.y - curPoint.y, to.x - curPoint.x) / Math.PI * 180
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

    Path(buildPath(List(from), 50))
  }

  val defaultParameters = SvmParameters(SvmType.EPSILON_SVR, KernelType.RBF, eps = 0.5, gamma = 30, cost = 100)

  case class RunResults(path: Path, timeMs: Double, isCorrect: Boolean, message: String)

  def runClassificationSVM(obstacles: List[Polygon], dims: Point, start: Point, finish: Point): Future[Option[RunResults]] = {
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

  def runRegressionSVM(obstacles: List[Polygon], dims: Point, start: Point, finish: Point): Future[Option[RunResults]] = {
    runSVM(SvmType.EPSILON_SVR, obstacles, dims, start, finish, 100.0, vals => vals.minBy(pair => Math.abs(pair._2))._1)
  }

  private def runSVM(svmType: SvmType, obstacles: List[Polygon], dims: Point,
                     start: Point, finish: Point, angle: Double, finder: TargetPointFinder): Future[Option[RunResults]] = {
    println("Running SVM")
    val field = InputMapper.mapObstaclesToExamples(obstacles, dims.y, dims.x, start, finish)
    val d = Globals.STEP_OF_PATH

    val results =
      List(0.0001, 0.001, 0.01, 0.1, 0.5, 1).flatMap { eps =>
        List(0.1, 0.5, 1, 5, 10, 20, 30, 40, 75, 100).flatMap { gamma =>
          List(0.001, 0.01, 0.1, 0.5, 1, 10, 20, 30, 40, 75, 100).map { cost => Future {
            var path: Path = null
            val elapsed = time {
              val model = trainSvmModel(field, defaultParameters.copy(svmType = svmType, eps = eps, gamma = gamma, cost = cost))
              path = buildPath(model, d, angle, start, finish, dims, "black", finder)
            }
            val (isPathCorrect, errorMessage) = checkPathCorrect(obstacles, dims, start, finish, path)
            RunResults(path, elapsed, isPathCorrect,
              s"Training model eps=$eps gamma=$gamma cost=$cost. Elapsed $elapsed ms")
          }}
        }
      }

    Future.find(results)(_.isCorrect)
  }

  def checkPathCorrect(obstacles: List[Polygon], dims: Point, start: Point, finish: Point, path: Path): (Boolean, String) = {
    val pointOfPathInsideObstacle =
      path.path.exists { point =>
        obstacles.exists { obstacle =>
          obstacle.isInside(point)
        }
      }

    val pointOutsideField =
      path.path.exists(point => point.x < 0 || point.y < 0 || point.x > dims.x || point.y > dims.y)

    val isAwayFromStart = distance(path.path.last, start) > 5

    val isAwayFromFinish = distance(path.path.head, finish) > 5

    val errorMessage = (if (pointOfPathInsideObstacle) " [intersects obstacle]" else "") +
      (if (isAwayFromStart) " [not near start]" else "") +
      (if (isAwayFromFinish) " [not near finish]" else "")

    (!(pointOfPathInsideObstacle || isAwayFromStart || isAwayFromFinish), errorMessage)
  }

}

object Test extends App {
  val dims = Point(150, 150)
  val start = Point(135, 15)
  val finish = Point(15, 125)
  val pivots = Pivots.getPivotPoints(start, finish).map(point => Polygon(List(point)))
  val noise =
    (10.0 to dims.y by Globals.STEP_OF_NOISE_GRID).flatMap { y =>
      (10.0 to dims.x by Globals.STEP_OF_NOISE_GRID).map { x =>
        Point(y, x)
      }
    }.map(point => Polygon(List(point))).toList

  val obstacles = List(
    Polygon(List(Point(25, 25), Point(25, 75), Point(75, 75), Point(75, 25))),
    Polygon(List(Point(300, 150), Point(300, 250), Point(400, 250), Point(400, 150))),
    Polygon(List(Point(25, 300), Point(25, 400), Point(95, 400), Point(95, 300))),
    Polygon(List(Point(130, 320), Point(130, 380), Point(310, 380), Point(310, 320))),
    Polygon(List(Point(500, 340), Point(500, 400), Point(590, 400), Point(590, 340))),
    Polygon(List(Point(300, 500), Point(300, 575), Point(375, 575), Point(375, 500)))
  ) ::: pivots ::: noise

  Await.result(new Learning().runRegressionSVM(obstacles, dims, start, finish), 1.minute) match {
    case Some(x) =>
      println(x)

    case None =>
  }
}
