/*
 * Copyright 2017 Laurent Cornélis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.moisturemonitor

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import akka.contrib.throttle.TimerBasedThrottler
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.ConfigFactory
import io.scalac.slack.MessageEventBus
import io.scalac.slack.api.Start
import io.scalac.slack.common.Shutdownable
import io.scalac.slack.websockets.WebSocket
import org.moisturemonitor.actors._

import scala.concurrent.duration.DurationDouble

object MoistureMonitorMain extends Shutdownable {

  implicit val system = ActorSystem("moistureMonitorSystem")
  implicit val executionContext = system.dispatcher

  val eventBus = new MessageEventBus

  // Create SlackBot actor
  val slackBot = system.actorOf(Props(classOf[DispatchCommandMessageSlackBotActor], new BotsBundle(eventBus), eventBus, this, None), "slack-bot")

  def main(args: Array[String]): Unit = {
    setupJsonMapper()

    val config = ConfigFactory.defaultApplication().getConfig("app-settings")
    val serverPort = config.getInt("serverPort")

    // Create actors
    val stats = system.actorOf(Props[StatsActor], "statsActor")
    val messaging: ActorRef = createMessagingActor
    val coordinator = system.actorOf(Props(classOf[CoordinatorActor], stats, messaging), "coordinator")

    // Start SlackBot and server
    system.scheduler.scheduleOnce(0 seconds, slackBot, Start)
    SensorMeasuresWebServer.start(serverPort, coordinator)

    // Setup shutdown hook
    sys.addShutdownHook(shutdown())

  }

  private def createMessagingActor = {
    val messaging = system.actorOf(Props(classOf[MessagingActor], slackBot), "messagingActor")

    // Setup a throttler for messaging to avoid spamming Slack
    val messagingThrottler = system.actorOf(Props(classOf[TimerBasedThrottler],
      3 msgsPer 10.second))
    messagingThrottler ! SetTarget(Option(messaging))
    messagingThrottler
  }

  override def shutdown(): Unit = {
    slackBot ! WebSocket.Release
    SensorMeasuresWebServer.stop().onComplete(_ => system.terminate())
  }

  private def setupJsonMapper() = {
    val objectMapper = new ObjectMapper()
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    objectMapper.registerModule(new DefaultScalaModule)
    objectMapper.registerModule(new JodaModule)

    no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializer.setObjectMapper(objectMapper)
  }


}
