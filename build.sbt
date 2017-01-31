name := """moisture-monitor"""

version := "1.0"

scalaVersion := "2.11.8"

fork in run := true

javaOptions in run += "-Dconfig.resource=user-settings.conf"

resolvers ++= Seq(
  "scalac repo" at "https://raw.githubusercontent.com/ScalaConsultants/mvn-repo/master/"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % "2.4.14",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.14",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.17",
  "com.typesafe.akka" %% "akka-contrib" % "2.4.14",
  //"com.typesafe.akka" %% "akka-remote" % "2.4.14",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.14",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.11.1",
  "com.typesafe.akka" %% "akka-http" % "10.0.3",
  "org.scalaj" %% "scalaj-time" % "0.8",
  // Slack
  "io.scalac" %% "slack-scala-bot-core" % "0.2.1",
  "no.nextgentel.oss.akka-tools" %% "akka-tools-json-serializing" % "1.1.1",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.4.0"
)
