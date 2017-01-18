package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging}
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializable
import org.joda.time.DateTime

import scala.concurrent.forkjoin.ThreadLocalRandom

object SensorMessages {

  case object GetMeasure

  case class Measure(timestamp:DateTime, temperature: Double, relativeMoisture: Double) extends JacksonJsonSerializable

}

class SensorActor(sensor:Sensor) extends Actor with ActorLogging {

  import SensorMessages._

  def receive = {
    case GetMeasure => {
      log info s"${self.path.name} Taking a measures"
      val (timestamp, temperature, relativeMoisture) = sensor.measure
      sender() ! Measure(timestamp, temperature, relativeMoisture)
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}

trait Sensor {
  def measure:(DateTime, Double, Double)
}

object FakeSensor extends Sensor{
  def measure = {
    val temperature = ThreadLocalRandom.current().nextDouble(15, 30)
    val relativeMoisture = ThreadLocalRandom.current().nextDouble(45, 95)

    (DateTime.now, temperature, relativeMoisture)
  }
}