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
 * limitations under the License.
 */

package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.ConfigFactory
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializable
import org.joda.time.DateTime
import org.moisturemonitor.actors.Messages.Measure
import org.moisturemonitor.actors.MessagingMessages.SendMessage

object Messages {

  case object GetLatestMeasure

  case class Measure(timestamp: DateTime, temperature: Double, relativeMoisture: Double, batteryLevel: Double) extends JacksonJsonSerializable {
    override def toString: String = {
      s"Timestamp : ${timestamp} \n" +
        f"Temperature : ${temperature}%.1f° \n" +
        f"Relative moisture : ${relativeMoisture}%.1f%% \n" +
        f"Battery level : ${batteryLevel}%.1f%%"
    }
  }

}

/**
  * Actor responsible of coordination between stats actor and messaging actor
  *
  * It is the actor receiving measures from the SensorMeasuresWebServer.
  *
  * On new measure, it will :
  *   - Record latest measure (non persistent)
  *   - Dispatch it to stats actor for persistence and statistics
  *   - If message or stats has reach monitoring thresholds, dispatch it to messaging actor
  *
  * @param stats
  * @param messaging
  */
class CoordinatorActor(stats: ActorRef, messaging: ActorRef) extends Actor with ActorLogging {

  val config = ConfigFactory.defaultApplication().getConfig("app-settings.alarm-thresholds")

  val batteryLevelThreshold = config.getDouble("batteryLevel")
  val relativeMoistureThreshold = config.getDouble("relativeMoisture")
  val relativeMoistureDeltaFromAverageThreshold = config.getDouble("relativeMoistureDeltaFromAverage")

  var latestMeasure: Option[Measure] = None

  import Messages._
  import StatsMessages._

  def receive = {
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
    case statsState: StatsState if statsState.latestMeasure.isDefined && statsState.deltaFromAverage(statsState.latestMeasure.get.relativeMoisture, statsState.relativeMoistureStats) >= relativeMoistureDeltaFromAverageThreshold => {
      messaging ! SendMessage(Some("Moisture is above accepted average threshold"), Some(statsState))
    }
    case GetLatestMeasure => {
      sender() ! latestMeasure
    }
    case unexpected => log warning (s"${self.path.name} Receive ${unexpected}")
  }

  def addMeasure(measure: Measure) = {
    latestMeasure = Some(measure)
    stats ! AddMeasure(measure)
  }
}

