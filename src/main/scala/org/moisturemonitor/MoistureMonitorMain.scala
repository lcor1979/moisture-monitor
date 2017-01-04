package org.moisturemonitor

import akka.actor.{ActorSystem, Props}
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import akka.contrib.throttle.TimerBasedThrottler
import org.moisturemonitor.actors.SensorMessages.GetMeasure
import org.moisturemonitor.actors.{MainActor, MessagingActor, SensorActor, StatsActor}

import scala.concurrent.duration.DurationDouble

object MoistureMonitorMain {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("moistureMonitorSystem")

    import system.dispatcher

    val sensor = system.actorOf(Props[SensorActor], "sensorActor")
    val stats = system.actorOf(Props[StatsActor], "statsActor")
    val messaging = system.actorOf(Props[MessagingActor], "messagingActor")
    val messagingThrottler = system.actorOf(Props(classOf[TimerBasedThrottler],
      3 msgsPer 10.second))

    messagingThrottler ! SetTarget(Option(messaging))

    var mainActor = system.actorOf(Props(classOf[MainActor], sensor, stats, messagingThrottler), "mainActor")

    system.scheduler.schedule(0 seconds, 2 seconds, mainActor, GetMeasure)


  }

}
