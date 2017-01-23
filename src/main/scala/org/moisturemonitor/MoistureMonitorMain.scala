package org.moisturemonitor

import java.util.concurrent.TimeUnit

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import akka.contrib.throttle.TimerBasedThrottler
import akka.util.Timeout
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.util.ConfigLoader
import io.scalac.slack.{BotModules, MessageEventBus}
import io.scalac.slack.api.{BotInfo, Start}
import io.scalac.slack.bots.AbstractBot
import io.scalac.slack.bots.system.{CommandsRecognizerBot, HelpBot}
import io.scalac.slack.common.{BaseMessage, Command, OutboundMessage, Shutdownable}
import io.scalac.slack.common.actors.SlackBotActor
import io.scalac.slack.websockets.WebSocket
import org.moisturemonitor.actors.SensorMessages.GetMeasure
import org.moisturemonitor.actors._

import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.util.{Failure, Success}

object MoistureMonitorMain extends Shutdownable {

  val system = ActorSystem("moistureMonitorSystem")
  val eventBus = new MessageEventBus
  val slackBot = system.actorOf(Props(classOf[CustomSlackBotActor], new BotsBundle(eventBus), eventBus, this, None), "slack-bot")

  def main(args: Array[String]): Unit = {

    import system.dispatcher

    val objectMapper = new ObjectMapper()
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    objectMapper.registerModule(new DefaultScalaModule)
    objectMapper.registerModule(new JodaModule)

    no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializer.setObjectMapper(objectMapper)

    val sensor = system.actorOf(Props(classOf[SensorActor], FakeSensor), "sensorActor")
    val stats = system.actorOf(Props[StatsActor], "statsActor")
    val messaging = system.actorOf(Props(classOf[MessagingActor], slackBot), "messagingActor")
    val messagingThrottler = system.actorOf(Props(classOf[TimerBasedThrottler],
      3 msgsPer 10.second))

    messagingThrottler ! SetTarget(Option(messaging))

    var mainActor = system.actorOf(Props(classOf[MainActor], sensor, stats, messagingThrottler), "mainActor")

    system.scheduler.scheduleOnce(0 seconds, slackBot, Start)
    system.scheduler.schedule(0 seconds, 10 seconds, mainActor, GetMeasure)

    sys.addShutdownHook(shutdown())

  }

  override def shutdown(): Unit = {
    slackBot ! WebSocket.Release
    system.terminate();
  }

}
