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

