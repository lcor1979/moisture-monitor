name := """moisture-monitor"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % "2.4.14",
  //"com.typesafe.akka" %% "akka-remote" % "2.4.14",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.14"
)
