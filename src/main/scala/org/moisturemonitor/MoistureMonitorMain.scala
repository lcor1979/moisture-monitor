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
 * limitations under the License..
 */

package org.moisturemonitor

import akka.actor.{ActorSystem, Props}
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import akka.contrib.throttle.TimerBasedThrottler
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
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
  val slackBot = system.actorOf(Props(classOf[CustomSlackBotActor], new BotsBundle(eventBus), eventBus, this, None), "slack-bot")

  def main(args: Array[String]): Unit = {

    setupJsonMapper()

    val stats = system.actorOf(Props[StatsActor], "statsActor")
    val messaging = system.actorOf(Props(classOf[MessagingActor], slackBot), "messagingActor")
    val messagingThrottler = system.actorOf(Props(classOf[TimerBasedThrottler],
      3 msgsPer 10.second))

    messagingThrottler ! SetTarget(Option(messaging))

    val coordinator = system.actorOf(Props(classOf[CoordinatorActor], stats, messagingThrottler), "coordinator")

    system.scheduler.scheduleOnce(0 seconds, slackBot, Start)

    SensorMeasuresWebServer.start(coordinator)

    sys.addShutdownHook(shutdown())

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
