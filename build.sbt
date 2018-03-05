import sbt._
import BuildConfig.Dependencies

lazy val commonSettings = BuildConfig.commonSettings()

commonSettings

name := "ecsv"

lazy val versions = new {
  val finatra = "1.0.4"
  val scalaGlobal = "1.1"
  val tasks = "1.5"
  val scalaGuice = "4.1.1"
  val guava = "24.0-jre"
  val logback = "1.2.3"
}

def aws(name: String) = {
  "com.amazonaws" % s"aws-java-sdk-${name}" % "1.11.289"
}

libraryDependencies ++= Seq(
  "io.paradoxical" %% "finatra-server" % versions.finatra,
  "io.paradoxical" %% "finatra-swagger" % versions.finatra,
  "io.paradoxical" %% "paradox-scala-global" % versions.scalaGlobal,
  "com.google.guava" % "guava" % versions.guava,
  "ch.qos.logback" % "logback-classic" % versions.logback,
  "io.paradoxical" %% "tasks" % versions.tasks,
  "net.codingwell" %% "scala-guice" % versions.scalaGuice,
  aws("ecs"),
  aws("ec2"),
  "io.paradoxical" %% "finatra-test" % versions.finatra % "test"
) ++ Dependencies.testDeps

lazy val showVersion = taskKey[Unit]("Show version")

showVersion := {
  println(version.value)
}

// custom alias to hook in any other custom commands
addCommandAlias("build", "; compile")
