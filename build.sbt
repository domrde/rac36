
resolvers += "OSS Sonatype" at "https://repo1.maven.org/maven2/"

lazy val akkaVersion = "2.4.10"

lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.11.8",
  libraryDependencies ++= {
    Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % "2.2.6",
      "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion,
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"
    )
  }
)

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

lazy val messages = (project in file("messages")).
  settings(commonSettings: _*).
  settings(
    name := "messages"
  )

lazy val pipe = (project in file("pipe")).
  settings(commonSettings: _*).
  settings(
    name := "pipe",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.0.13",
      "org.zeromq" % "jeromq" % "0.3.5",
      "com.typesafe.play" %% "play-json" % "2.5.4"
    )
  ).
  dependsOn(messages)

lazy val avatar = (project in file("avatar")).
  settings(commonSettings: _*).
  settings(SbtMultiJvm.multiJvmSettings: _*).
  settings(additionalMultiJvmSettings: _*).
  configs (MultiJvm).
  settings(
    name := "avatar",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test",
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion
    )
  ).
  dependsOn(messages)

lazy val dashboard = (project in file("dashboard")).
  settings(commonSettings: _*).
  settings(
    name := "dashboard",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
      "com.typesafe.play" %% "play-json" % "2.5.4"
    )
  ).
  dependsOn(messages)

lazy val test = (project in file("test")).
  settings(commonSettings: _*).
  settings(SbtMultiJvm.multiJvmSettings: _*).
  settings(additionalMultiJvmSettings: _*).
  configs (MultiJvm).
  settings(
    name := "test",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test",
      "com.typesafe.play" %% "play-json" % "2.5.4"
    )
  ).
  dependsOn(messages, pipe, avatar)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "root"
  ).
  dependsOn(pipe, avatar, dashboard, test)

