package org.moisturemonitor

import akka.actor.{ActorSystem, Props}
import org.moisturemonitor.actors.SensorMessages.{GetMeasure, Measure}
import org.moisturemonitor.actors.{MainActor, SensorActor, StatsActor}

import scala.concurrent.duration.DurationLong

object MoistureMonitorMain {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("moistureMonitorSystem")

    import system.dispatcher

    val sensor = system.actorOf(Props[SensorActor], "sensorActor")
    val stats = system.actorOf(Props[StatsActor], "statsActor")
    var mainActor = system.actorOf(Props(classOf[MainActor], sensor, stats), "mainActor")

    system.scheduler.schedule(0 seconds, 2 seconds, mainActor, GetMeasure)
  }

}
