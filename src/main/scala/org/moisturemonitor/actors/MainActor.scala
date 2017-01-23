package org.moisturemonitor.actors

import akka.actor.{Actor, ActorRef}
import org.moisturemonitor.actors.MessagingMessages.SendMessage

class MainActor(sensor: ActorRef, stats: ActorRef, messaging: ActorRef) extends Actor {

  import SensorMessages._
  import StatsMessages._

  def receive = {
    case GetMeasure => {
      sensor ! GetMeasure
    }
    case measure: Measure if measure.batteryLevel < 10.0 => {
      println(f"ALERT ${self.path.name} Low battery : ${measure}")
      addMeasure(measure)
      messaging ! SendMessage(Some("Low battery"), Some(measure))
    }
    case measure: Measure if measure.relativeMoisture > 80.0 => {
      println(f"ALERT ${self.path.name} Moisture too high ${measure}")
      addMeasure(measure)
      messaging ! SendMessage(Some("Moisture too high"), Some(measure))
    }
    case measure: Measure => {
      println(f"${self.path.name} Received ${measure}")
      addMeasure(measure)
    }
    case statsState: StatsState if statsState.relativeMoistureStats.stdDeviation > 10.0 => {
      println(f"ALERT ${self.path.name} Moisture Stats standard deviation too high ${statsState.relativeMoistureStats.stdDeviation}")
      messaging ! SendMessage(Some("Moisture Stats standard deviation too high"), Some(statsState))
    }

    case unexpected => println(s"${self.path.name} Receive ${unexpected}")
  }

  def addMeasure(measure: Measure) = {
    stats ! AddMeasure(measure)
  }
}

