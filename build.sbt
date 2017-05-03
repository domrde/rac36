
resolvers += "OSS Sonatype" at "https://repo1.maven.org/maven2/"

lazy val akkaVersion = "2.4.11"

lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.11.8",
  organization := "com.dda"
)


// --------------------------------- MINIMAL DEPENDENCIES SUBPROJECTS -----------------------------------------------


lazy val common = (project in file("common")).
  settings(commonSettings: _*).
  settings(
    name := "common",
    libraryDependencies ++=
      Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion
      )
  )

lazy val brain = (project in file("brain")).
  settings(commonSettings: _*).
  settings(
    name := "brain",
    libraryDependencies ++=
      Seq(
        "com.lihaoyi" %% "upickle" % "0.4.3",
        "com.typesafe.akka" %% "akka-actor" % akkaVersion
      )
  )
  .dependsOn(common)


// ------------------------------- SHARED ANCESTOR WITH COMMON DEPENDENCIES -----------------------------------------


lazy val commonDependencies = Seq(
  libraryDependencies ++= {
    Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "org.scalatest" %% "scalatest" % "2.2.6",
      "io.kamon" % "sigar-loader" % "1.6.6-rev002",
      "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1"
    )
  }
)

lazy val utils = (project in file("utils")).
  settings(commonSettings: _*).
  settings(commonDependencies: _*).
  settings(
    name := "utils",
    libraryDependencies ++= Seq(
      "org.zeromq" % "jeromq" % "0.3.5",

      // Cannot be fully replaced by upickle because provides a way to match json against multiple reads that not
      // a part of sealed trait. It's used to parse classes that use this util but not known to the util.
      "com.typesafe.play" %% "play-json" % "2.5.4"
    )
  )
  .dependsOn(brain)


// -------------------------------------- DOCKERIZED SUBPROJECTS ----------------------------------------------------


lazy val dockerSettings = Seq(
  dockerfile in docker := {
    // The assembly task generates a fat JAR file
    val artifact: File = assembly.value
    val artifactTargetPath = s"/app/${artifact.name}"

    new Dockerfile {
      from("java")
      add(artifact, artifactTargetPath)
      entryPoint("java", "-jar", artifactTargetPath)
      expose(34051)
      expose(34052)
      expose(34053)
      expose(8888)
      expose(34671)
    }
  },
  buildOptions in docker := BuildOptions(
    cache = false,
    removeIntermediateContainers = BuildOptions.Remove.Always,
    pullBaseImage = BuildOptions.Pull.Always
  )
)

lazy val assemblyMergeApplicationConfs = Seq(
  assemblyMergeStrategy in assembly := {
    case "application.conf" =>
      MergeStrategy.first
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val vivarium = (project in file("vivarium")).
  settings(commonSettings: _*).
  settings(dockerSettings: _*).
  settings(assemblyMergeApplicationConfs: _*).
  settings(
    name := "vivarium",
    mainClass in assembly := Some("vivarium.Boot")
  )
  .dependsOn(utils)
  .enablePlugins(DockerPlugin)

lazy val pipe = (project in file("pipe")).
  settings(commonSettings: _*).
  settings(dockerSettings: _*).
  settings(assemblyMergeApplicationConfs: _*).
  settings(
    name := "pipe",
    mainClass in assembly := Some("pipe.Boot")
  )
  .dependsOn(vivarium)
  .enablePlugins(DockerPlugin)

lazy val dashboard = (project in file("dashboard")).
  settings(commonSettings: _*).
  settings(dockerSettings: _*).
  settings(assemblyMergeApplicationConfs: _*).
  settings(
    name := "dashboard",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
    ),
    mainClass in assembly := Some("dashboard.Boot")
  )
  .dependsOn(pipe)
  .enablePlugins(DockerPlugin)


// --------------------------------------------- V-REP PLAYGROUND ---------------------------------------------------


lazy val playground = (project in file("playground")).
  settings(commonSettings: _*).
  settings(
    name := "playground",
    libraryDependencies ++= Seq(
      "com.github.troxid" %% "vrepapiscala" % "0.3.6"
    )
  )
  .dependsOn(dashboard)

// ----------------------------------- EXTERNAL PATHFINDING ROBOT ---------------------------------------------------

lazy val pathfinder = (project in file("pathfinder")).
  settings(commonSettings: _*).
  settings(
    name := "pathfinder",
    libraryDependencies ++= Seq(
      "tw.edu.ntu.csie" % "libsvm" % "3.17"
    )
  )
  .dependsOn(dashboard)

// ----------------------------------- MULTIJVM TESTS SUBPROJECTS ---------------------------------------------------


lazy val additionalMultiJvmSettings = Seq(
  Keys.fork in run := true,
  compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
  parallelExecution in Test := false,
  executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
    case (testResults, multiNodeResults)  =>
      val overall =
        if (testResults.overall.id < multiNodeResults.overall.id)
          multiNodeResults.overall
        else
          testResults.overall
      Tests.Output(overall,
        testResults.events ++ multiNodeResults.events,
        testResults.summaries ++ multiNodeResults.summaries)
  }
)

lazy val test = (project in file("test")).
  settings(commonSettings: _*).
  settings(SbtMultiJvm.multiJvmSettings: _*).
  settings(additionalMultiJvmSettings: _*).
  configs (MultiJvm).
  settings(
    name := "test",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test"
    )
  )
  .dependsOn(dashboard)


// ------------------------------------------------ ROOT ------------------------------------------------------------


lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "root"
  )
  .dependsOn(test)
  .aggregate(common, brain, utils, vivarium, pipe, dashboard, playground, test)
