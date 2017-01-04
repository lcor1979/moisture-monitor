package org.moisturemonitor.actors

import akka.actor.{Actor, ActorRef}
import org.moisturemonitor.actors.MessagingMessages.SendMeasureMessage

class MainActor(sensor: ActorRef, stats: ActorRef, messaging: ActorRef) extends Actor {

  import SensorMessages._
  import StatsMessages._

  def receive = {
    case GetMeasure => {
      sensor ! GetMeasure
    }
    case measure: Measure if measure.relativeMoisture > 80.0 => {
      println(f"ALERT ${self.path.name} Received ${measure.temperature}%.1f° / ${measure.relativeMoisture}%.1f%%")
      addMeasure(measure)
      messaging ! SendMeasureMessage(measure)
    }
    case measure: Measure => {
      println(f"${self.path.name} Received ${measure.temperature}%.1f° / ${measure.relativeMoisture}%.1f%%")
      addMeasure(measure)
    }
    case unexpected => println(s"${self.path.name} Receive ${unexpected}")
  }

  def addMeasure(measure: Measure) = {
    stats ! AddMeasure(measure)
  }
}

