package org.moisturemonitor.actors

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.scalac.slack.bots.AbstractBot
import io.scalac.slack.bots.system.{CommandsRecognizerBot, HelpBot}
import io.scalac.slack.common.actors.SlackBotActor
import io.scalac.slack.common.{BaseMessage, Command, OutboundMessage, Shutdownable}
import io.scalac.slack.{BotModules, MessageEventBus}
import org.moisturemonitor.actors.SensorMessages.{GetMeasure, Measure}
import org.moisturemonitor.actors.StatsMessages.{Stats, StatsState}
import org.scala_tools.time.StaticDateTimeFormat

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object MessagingMessages {

  case class SendMessage(message: Option[String], data: Option[Any])

}

class MessagingActor(slackBot: ActorRef) extends Actor with ActorLogging {

  import MessagingMessages._

  def receive = {
    case SendMessage(message: Option[String], data: Option[Any]) => {
      slackBot forward DispatchCommandMessage("moisture-bot", DispatchCommand("send-message", List(message, data), "C3M5DR334"))
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}


class BotsBundle(eventBus: MessageEventBus) extends BotModules {
  override def registerModules(context: ActorContext, websocketClient: ActorRef) = {
    context.actorOf(Props(classOf[CommandsRecognizerBot], eventBus), "commandProcessor")
    context.actorOf(Props(classOf[HelpBot], eventBus), "helpBot")
    context.actorOf(Props(classOf[MoistureBot], eventBus, "user/sensorActor"), "moisture-bot")
  }
}

case class DispatchCommandMessage(botName: String, command: DispatchCommand)

case class DispatchCommand(command: String, params: List[Any], channel: String)

class CustomSlackBotActor(modules: BotModules, eventBus: MessageEventBus, master: Shutdownable, usersStorageOpt: Option[ActorRef] = None) extends SlackBotActor(modules, eventBus, master, usersStorageOpt) {
  override def receive: Receive = super.receive orElse {
    case DispatchCommandMessage(botName, command) => {
      log.info(s"Dispatch command ${command.command} to $botName")
      context.actorSelection(botName).forward(command)
    }
  }
}

class MoistureBot(override val bus: MessageEventBus, sensorActorName: String) extends AbstractBot {

  override def help(channel: String): OutboundMessage =
    OutboundMessage(channel, s"Usage: $$measure")

  override def act: Receive = {
    case Command("measure", _, BaseMessage(_, channel, _, _, _)) =>
      implicit val timeout = Timeout(5 seconds)
      val sensorRef = Await.result(context.system.actorSelection(sensorActorName).resolveOne(), timeout.duration)
      val measure: Measure = Await.result(ask(sensorRef, GetMeasure), timeout.duration).asInstanceOf[Measure]

      publish(OutboundMessage(channel, format(None, measure)))
    case DispatchCommand("send-message", List(message: Option[String], Some(measure: Measure)), channel) =>
      publish(OutboundMessage(channel, format(message, measure)))
    case DispatchCommand("send-message", List(message: Option[String], Some(statsState: StatsState)), channel) =>
      publish(OutboundMessage(channel, format(message, statsState)))
    case DispatchCommand("send-message", List(message: Some[String], None), channel) =>
      publish(OutboundMessage(channel, format(message)))
  }

  def format(message: Option[String]): String = message match {
    case Some(content) => s"`Message` : *${content}* \\n"
    case None => ""
  }

  def format(message: Option[String], statsState: StatsState): String = {
    return format(message) +
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
    return format(message) +
      s"`Timestamp` : *${StaticDateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss").print(measure.timestamp)}* \\n" +
      f"`Temperature` : *${measure.temperature}%.1f°* \\n" +
      f"`Relative moisture` : *${measure.relativeMoisture}%.1f%%* \\n" +
      f"`Battery level` : *${measure.batteryLevel}%.1f%%*"
  }
}