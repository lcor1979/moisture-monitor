name := """moisture-monitor"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % "2.4.14",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.14",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.17",
  "com.typesafe.akka" %% "akka-contrib" % "2.4.14",
  //"com.typesafe.akka" %% "akka-remote" % "2.4.14",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.14",
  "org.scalaj" %% "scalaj-time" % "0.8",
  // Slack
  "com.flyberrycapital" %% "scala-slack" % "0.3.0"
)
