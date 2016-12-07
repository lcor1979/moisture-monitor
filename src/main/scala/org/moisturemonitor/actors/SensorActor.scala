package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging}

import scala.concurrent.forkjoin.ThreadLocalRandom

object SensorMessages {

  case object GetMeasure

  case class Measure(temperature: Double, relativeMoisture: Double)

}

class SensorActor extends Actor with ActorLogging {

  import SensorMessages._

  def receive = {
    case GetMeasure => {
      log info s"${self.path.name} Taking a measures"
      val (temperature, relativeMoisture) = Sensor.measure
      sender() ! Measure(temperature, relativeMoisture)
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}

object Sensor {
  def measure = {
    val temperature = ThreadLocalRandom.current().nextDouble(15, 30)
    val relativeMoisture = ThreadLocalRandom.current().nextDouble(45, 95)

    (temperature, relativeMoisture)
  }
}