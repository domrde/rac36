lazy val akkaVersion = "2.4.8"

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
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    )
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
      "org.zeromq" % "jeromq" % "0.3.5"
    )
  ).
  dependsOn(messages)

lazy val avatar = (project in file("avatar")).
  settings(commonSettings: _*).
  settings(SbtMultiJvm.multiJvmSettings: _*).
  settings(
    Keys.fork in run := true,
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
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
  ).
  configs (MultiJvm).
  settings(
    name := "avatar",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test"
    )
  ).
  dependsOn(messages)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "root"
  ).
  dependsOn(pipe)

