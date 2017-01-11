package org.moisturemonitor.actors

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializable
import org.joda.time.DateTime
import org.moisturemonitor.actors.SensorMessages.Measure

object StatsMessages {

  case class AddMeasure(measure: Measure)

}

case class Stats(average: Double = 0.0, variance: Double = 0.0, stdDeviation: Double = 0.0)

object Stats extends JacksonJsonSerializable {
  def apply(values: List[Double]): Stats = {
    val average = values.sum / values.length.toDouble
    val variance = values.fold(0.0)((result, value) => result + (value - average) * (value - average)) / values.length.toDouble
    val stdDeviation = Math.sqrt(variance)

    apply(average, variance, stdDeviation)
  }
}

case class StatsState(events: List[Measure] = Nil, temperatureStats: Stats = Stats(), relativeMoistureStats: Stats = Stats()) extends JacksonJsonSerializable {
  def update(measure: Measure): StatsState = {
    val allMeasures = measure :: events.filter(event => event.timestamp isAfter DateTime.now.minusSeconds(5));
    val temperatures = allMeasures.map(m => m.temperature)
    val relativeMoistures = allMeasures.map(m => m.relativeMoisture)

    copy(allMeasures, Stats(temperatures), Stats(relativeMoistures))
  }

  def size: Int = events.length
}

class StatsActor extends PersistentActor with ActorLogging {

  import StatsMessages._

  override def persistenceId = "stats-actor"

  var state = StatsState()

  def updateState(measure: Measure): Unit =
    state = state.update(measure)

  def numEvents =
    state.size

  val receiveRecover: Receive = {
    case measure: Measure => updateState(measure)
    case SnapshotOffer(_, snapshot: StatsState) => state = snapshot
  }

  val receiveCommand: Receive = {
    case AddMeasure(measure) => {

      log info s"${self.path.name} Add measures : ${measure}"
      persist(measure)(updateState)

      // Save snapshot
      saveSnapshot(state);

      log info s"${self.path.name} All measures : ${state}"

      if (state.relativeMoistureStats.stdDeviation > 10.0) {
        log.error(s"Standard deviation is too high ${state.relativeMoistureStats.stdDeviation}")
      }
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}