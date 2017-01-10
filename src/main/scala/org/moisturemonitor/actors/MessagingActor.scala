package org.moisturemonitor.actors

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.scalac.slack.bots.AbstractBot
import io.scalac.slack.bots.system.{CommandsRecognizerBot, HelpBot}
import io.scalac.slack.common.actors.SlackBotActor
import io.scalac.slack.common.{BaseMessage, Command, OutboundMessage, Shutdownable}
import io.scalac.slack.{BotModules, MessageEventBus}
import org.joda.time.DateTime
import org.moisturemonitor.actors.SensorMessages.{GetMeasure, Measure}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object MessagingMessages {

  case class SendMeasureMessage(measure: Measure)

}

class MessagingActor(slackBot: ActorRef) extends Actor with ActorLogging {

  import MessagingMessages._

  def receive = {
    case SendMeasureMessage(measure) => {
      log info s"${self.path.name} Send a message"
      slackBot forward DispatchCommandMessage("moisture-bot", DispatchCommand("send-measure", List(measure), "C3M5DR334"))
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}


class BotsBundle(eventBus: MessageEventBus) extends BotModules {
  override def registerModules(context: ActorContext, websocketClient: ActorRef) = {
    context.actorOf(Props(classOf[CommandsRecognizerBot], eventBus), "commandProcessor")
    context.actorOf(Props(classOf[HelpBot], eventBus), "helpBot")
    context.actorOf(Props(classOf[MoistureBot], eventBus), "moisture-bot")
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

class MoistureBot(override val bus: MessageEventBus) extends AbstractBot {

  override def help(channel: String): OutboundMessage =
    OutboundMessage(channel, s"Usage: $$measure")

  override def act: Receive = {
    case Command("measure", _, BaseMessage(_, channel, _, _, _)) =>
      implicit val timeout = Timeout(5 seconds)
      val sensorRef = Await.result(context.system.actorSelection("user/sensorActor").resolveOne(), timeout.duration)
      val measure:Measure = Await.result(ask(sensorRef, GetMeasure), timeout.duration).asInstanceOf[Measure]

      publish(OutboundMessage(channel, format(measure)))
    case DispatchCommand("send-measure", List(measure: Measure), channel) =>
      publish(OutboundMessage(channel, format(measure)))
  }

  def format(measure: Measure): String = {
      s"`Timestamp` : *${measure.timestamp}* \\n" +
      f"`Temperature` : *${measure.temperature}%.1f°* \\n" +
      f"`Relative moisture` : *${measure.relativeMoisture}%.1f%%*"
  }
}