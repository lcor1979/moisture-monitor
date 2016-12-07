package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.forkjoin.ThreadLocalRandom

object StatsMessages {
  case class AddMeasure(temperature: Double, relativeMoisture: Double)

}

class StatsActor extends Actor with ActorLogging {
  val data = ArrayBuffer.empty[(Double, Double)]

  import StatsMessages._

  def receive = {
    case AddMeasure(temperature, relativeMoisture) => {
      var measure = (temperature, relativeMoisture)
      log info s"${self.path.name} Add measures : ${measure}"
      data += measure
      log info s"${self.path.name} All measures : ${data}"

      var sum = data.foldLeft((0.0, 0.0))((result, currentMeasure) => (result._1 + currentMeasure._1, result._2 + currentMeasure._2));
      var (avgTemp, avgMoist) = (sum._1 / data.length, sum._2 / data.length)
      println(f"Avg : ${avgTemp}%.1f / ${avgMoist}%.1f")
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}