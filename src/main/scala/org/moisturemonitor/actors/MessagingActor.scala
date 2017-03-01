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

package org.moisturemonitor.actors

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.scalac.slack.bots.AbstractBot
import io.scalac.slack.bots.system.{CommandsRecognizerBot, HelpBot}
import io.scalac.slack.common.actors.SlackBotActor
import io.scalac.slack.common.{BaseMessage, Command, OutboundMessage, Shutdownable}
import io.scalac.slack.{BotModules, MessageEventBus}
import org.moisturemonitor.actors.Messages.{GetLatestMeasure, Measure}
import org.moisturemonitor.actors.StatsMessages.{Stats, StatsState}
import org.scala_tools.time.StaticDateTimeFormat

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt


object MessagingMessages {

  /**
    * Use this message class to ask MessagingActor to send a message through Slackbot
    * @param message Optional message text
    * @param data Optional message data
    */
  case class SendMessage(message: Option[String], data: Option[Any])

}

/**
  * Actor which transform SendMessage requests to DispatchCommandMessage and dispatch them to Slakbot
  * @param slackBot
  */
class MessagingActor(slackBot: ActorRef) extends Actor with ActorLogging {

  import MessagingMessages._

  val config = ConfigFactory.defaultApplication().getConfig("app-settings.messaging")
  val channel = config.getString("channel")

  def receive = {
    case SendMessage(message: Option[String], data: Option[Any]) => {
      slackBot forward DispatchCommandMessage("moisture-bot", DispatchCommand("send-message", List(message, data), channel))
    }
    case unexpected => log warning (s"${self.path.name} receive ${unexpected}")
  }
}

/**
  * BotModules configuration
  * @param eventBus
  */
class BotsBundle(eventBus: MessageEventBus) extends BotModules {
  val config = ConfigFactory.defaultApplication().getConfig("app-settings.messaging")
  val coordinatorPath = config.getString("coordinatorPath")

  override def registerModules(context: ActorContext, websocketClient: ActorRef) = {
    context.actorOf(Props(classOf[CommandsRecognizerBot], eventBus), "commandProcessor")
    context.actorOf(Props(classOf[HelpBot], eventBus), "helpBot")
    context.actorOf(Props(classOf[MoistureBot], eventBus, coordinatorPath), "moisture-bot")
  }
}

/**
  * Case class wrapping a DispatchCommand that should be dispatched to specified bot
  *
  * It is used to simulate "real" slack commands but sent as Akka message from the application
  *
  * @param botName target bot name
  * @param command dispatch command
  */
case class DispatchCommandMessage(botName: String, command: DispatchCommand)

/**
  * Case class wrapping a Slack bot command, its parameters and the target channel
  *
  * @param command command text
  * @param params params list of the command
  * @param channel target channel
  */
case class DispatchCommand(command: String, params: List[Any], channel: String)

/**
  * SlackBotActor which support dispatching of DispatchCommandMessage from application
  */
class DispatchCommandMessageSlackBotActor(modules: BotModules, eventBus: MessageEventBus, master: Shutdownable, usersStorageOpt: Option[ActorRef] = None) extends SlackBotActor(modules, eventBus, master, usersStorageOpt) {
  override def receive: Receive = super.receive orElse {
    case DispatchCommandMessage(botName, command) => {
      log info (s"Dispatch command ${command.command} to $botName")
      context.actorSelection(botName).forward(command)
    }
  }
}

/**
  * SlackBot implementation which support "send-message" DispatchCommand to publish messages on specified channel
  *
  * It also support the "measure" Command which will get the latest measure received by CoordinatorActor and publish it
  *
  * @param bus Event bus
  * @param coordinatorPath path of the CoordinatorActor
  */
class MoistureBot(override val bus: MessageEventBus, coordinatorPath: String) extends AbstractBot {

  override def help(channel: String): OutboundMessage =
    OutboundMessage(channel, s"Usage: $$measure")

  override def act: Receive = {
    case Command("measure", _, BaseMessage(_, channel, _, _, _)) =>
      implicit val timeout = Timeout(5 seconds)
      //TODO I'm sure there is a better way to connect the bot and CoordinatorActor without the need to hardcode the path
      val coordinator = Await.result(context.system.actorSelection(coordinatorPath).resolveOne(), timeout.duration)
      val measure: Option[Measure] = Await.result(ask(coordinator, GetLatestMeasure), timeout.duration).asInstanceOf[Option[Measure]]

      if (measure.isDefined) {
        publish(OutboundMessage(channel, format(Some("Latest measure"), measure.get)))
      }
      else {
        publish(OutboundMessage(channel, format(Some("No measure at this moment"))))
      }
    case DispatchCommand("send-message", List(message: Some[String], None), channel) =>
      publish(OutboundMessage(channel, format(message)))
    case DispatchCommand("send-message", List(message: Option[String], Some(measure: Measure)), channel) =>
      publish(OutboundMessage(channel, format(message, measure)))
    case DispatchCommand("send-message", List(message: Option[String], Some(statsState: StatsState)), channel) =>
      publish(OutboundMessage(channel, format(message, statsState)))
  }

  def format(message: Option[String]): String = message match {
    case Some(content) => s"`Message` : *${content}* \\n"
    case None => ""
  }

  def format(message: Option[String], statsState: StatsState): String = {
    return format(message) +
      format(Some("Latest measure"), statsState.latestMeasure.get) + "\\n" +
      format("temperature", statsState.temperatureStats, "°") + "\\n" +
      format("moisture", statsState.relativeMoistureStats, "%")
  }

  def format(statsName: String, stats: Stats, unit: String): String = {
    return s"*Statistics for `${statsName}`* \\n" +
      f"    `Average` : *${stats.average}%.1f${unit}* \\n" +
      f"    `Standard deviation` : *${stats.stdDeviation}%.1f${unit}* \\n" +
      f"    `Variance` : *${stats.variance}%.1f*"
  }

  def format(message: Option[String], measure: Measure): String = {
    format(message) +
      s"`Timestamp` : *${StaticDateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss").print(measure.timestamp)}* \\n" +
      f"`Temperature` : *${measure.temperature}%.1f°* \\n" +
      f"`Relative moisture` : *${measure.relativeMoisture}%.1f%%* \\n" +
      f"`Battery level` : *${measure.batteryLevel}%.1f%%*"
  }
}