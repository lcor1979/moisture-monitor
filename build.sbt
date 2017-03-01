val akkaVersion = SettingKey[String]("Akka version used")

name := """moisture-monitor"""

version := "0.1"

scalaVersion := "2.11.8"

akkaVersion := "2.4.16"

fork in run := true

javaOptions in run += "-Dconfig.resource=user-settings.conf"

resolvers ++= Seq(
  "scalac repo" at "https://raw.githubusercontent.com/lcor1979/mvn-repo/master/"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion.value,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion.value,
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.17",
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion.value,
  //"com.typesafe.akka" %% "akka-remote" % akkaVersion.value,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value,
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.3",
  "com.typesafe.akka" %% "akka-http" % "10.0.3",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.3",
  "org.scalaj" %% "scalaj-time" % "0.8",
  // Slack
  "io.scalac" %% "slack-scala-bot-core" % "0.2.1-spray-json-1.3.3",
  "no.nextgentel.oss.akka-tools" %% "akka-tools-json-serializing" % "1.1.1",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.4.0"
)