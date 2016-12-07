package org.moisturemonitor.actors

import akka.actor.{Actor, ActorRef, Props}

class MainActor(sensor: ActorRef, stats: ActorRef) extends Actor {

  import SensorMessages._
  import StatsMessages._

  def receive = {
    case GetMeasure => {
      sensor ! GetMeasure
    }
    case Measure(temperature, relativeMoisture) if relativeMoisture > 80.0 => {
      println(f"ALERT ${self.path.name} Received ${temperature}%.1f° / ${relativeMoisture}%.1f%%")
      addMeasure(temperature, relativeMoisture)
    }
    case Measure(temperature, relativeMoisture) => {
      println(f"${self.path.name} Received ${temperature}%.1f° / ${relativeMoisture}%.1f%%")
      addMeasure(temperature, relativeMoisture)
    }
    case unexpected => println(s"${self.path.name} Receive ${unexpected}")
  }

  def addMeasure(temperature:Double, relativeMoisture:Double) = {
    stats ! AddMeasure(temperature, relativeMoisture)
  }
}

