package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.flyberrycapital.slack.SlackClient
import org.moisturemonitor.actors.SensorMessages.Measure

object MessagingMessages {

  case class SendMeasureMessage(measure: Measure)

}

class MessagingActor extends Actor with ActorLogging {

  import MessagingMessages._

  def receive = {
    case SendMeasureMessage(measure) => {
      log info s"${self.path.name} Send a message"
      SlackBot sendMessage (f"New measure: ${measure.temperature}%.1f° / ${measure.relativeMoisture}%.1f%%")
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}

object SlackBot {

  val token = "xoxb-122513415617-lTq9y3KL7DGiT8mrmCW010qY"
  val client = new SlackClient(token)

  def sendMessage(message: String): Unit = {
    client.chat.postMessage("#general", message)
  }

}