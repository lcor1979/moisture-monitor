/*
 * Copyright 2017 Laurent Cornélis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License..
 */

package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.ConfigFactory
import org.moisturemonitor.actors.MessagingMessages.SendMessage

class MainActor(sensor: ActorRef, stats: ActorRef, messaging: ActorRef) extends Actor with ActorLogging{

  val config = ConfigFactory.defaultApplication().getConfig("app-settings.alarm-thresholds")

  val batteryLevelThreshold = config.getDouble("batteryLevel")
  val relativeMoistureThreshold = config.getDouble("relativeMoisture")
  val relativeMoistureDeltaFromAverageThreshold = config.getDouble("relativeMoistureDeltaFromAverage")

  import SensorMessages._
  import StatsMessages._

  def receive = {
    case GetMeasure => {
      sensor ! GetMeasure
    }
    case measure: Measure if measure.batteryLevel < batteryLevelThreshold => {
      addMeasure(measure)
      messaging ! SendMessage(Some("Low battery"), Some(measure))
    }
    case measure: Measure if measure.relativeMoisture > relativeMoistureThreshold => {
      addMeasure(measure)
      messaging ! SendMessage(Some("Moisture too high"), Some(measure))
    }
    case measure: Measure => {
      addMeasure(measure)
    }
    case statsState: StatsState if statsState.deltaFromAverage(statsState.latestMeasure.relativeMoisture, statsState.relativeMoistureStats) >= relativeMoistureDeltaFromAverageThreshold => {
      messaging ! SendMessage(Some("Moisture is above accepted average threshold"), Some(statsState))
    }

    case unexpected => log warning (s"${self.path.name} Receive ${unexpected}")
  }

  def addMeasure(measure: Measure) = {
    stats ! AddMeasure(measure)
  }
}

