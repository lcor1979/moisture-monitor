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

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializable
import org.joda.time.DateTime
import org.moisturemonitor.actors.Messages.Measure

object StatsMessages {

  case class AddMeasure(measure: Measure)
  case class GetStats()
  case class Stats(average: Double = 0.0, variance: Double = 0.0, stdDeviation: Double = 0.0) extends JacksonJsonSerializable
  case class StatsState(events: List[Measure] = Nil, temperatureStats: Stats = Stats(), relativeMoistureStats: Stats = Stats()) extends JacksonJsonSerializable {
    def size: Int = events.length
    def latestMeasure: Option[Measure] = events.lastOption
    def deltaFromAverage(value: Double, stats:Stats): Double = value - stats.average
  }

  object Stats {
    def apply(values: List[Double]): Stats = {
      val average = values.sum / values.length.toDouble
      val variance = values.fold(0.0)((result, value) => result + (value - average) * (value - average)) / values.length.toDouble
      val stdDeviation = Math.sqrt(variance)

      apply(average, variance, stdDeviation)
    }
  }
}

class StatsActor extends PersistentActor with ActorLogging {

  val config = ConfigFactory.defaultApplication().getConfig("app-settings")
  val keepStatsForDays = config.getInt("keep-stats-for-days")

  import StatsMessages._

  override def persistenceId = "stats-actor"

  var state = StatsState()

  def updateState(measure: Measure): Unit = {
    val allMeasures = measure :: state.events.filter(event => event.timestamp isAfter DateTime.now.minusDays(keepStatsForDays));
    val temperatures = allMeasures.map(m => m.temperature)
    val relativeMoistures = allMeasures.map(m => m.relativeMoisture)

    state = state.copy(allMeasures, Stats(temperatures), Stats(relativeMoistures))
  }

  def numEvents =
    state.size

  val receiveRecover: Receive = {
    case measure: Measure => updateState(measure)
    case SnapshotOffer(_, snapshot: StatsState) => state = snapshot
    case SaveSnapshotSuccess => log debug("Snapshot saved")
  }

  val receiveCommand: Receive = {
    case AddMeasure(measure) => {

      log debug s"${self.path.name} Add measure to stats : ${measure}"
      persist(measure) { _ =>
        updateState(measure)

        // Save snapshot
        saveSnapshot(state);

        sender ! state
      }

    }
    case GetStats => sender ! state
    case unexpected => log warning s"${self.path.name} receive ${unexpected}"
  }
}