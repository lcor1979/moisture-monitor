package org.moisturemonitor.actors

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializable
import org.joda.time.DateTime
import org.moisturemonitor.actors.SensorMessages.Measure

object StatsMessages {

  case class AddMeasure(measure: Measure)
  case class GetStats()
  case class Stats(average: Double = 0.0, variance: Double = 0.0, stdDeviation: Double = 0.0) extends JacksonJsonSerializable
  case class StatsState(events: List[Measure] = Nil, temperatureStats: Stats = Stats(), relativeMoistureStats: Stats = Stats()) extends JacksonJsonSerializable {
    def size: Int = events.length
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

  import StatsMessages._

  override def persistenceId = "stats-actor"

  var state = StatsState()

  def updateState(measure: Measure): Unit = {
    val allMeasures = measure :: state.events.filter(event => event.timestamp isAfter DateTime.now.minusDays(1));
    val temperatures = allMeasures.map(m => m.temperature)
    val relativeMoistures = allMeasures.map(m => m.relativeMoisture)

    state = state.copy(allMeasures, Stats(temperatures), Stats(relativeMoistures))
  }

  def numEvents =
    state.size

  val receiveRecover: Receive = {
    case measure: Measure => updateState(measure)
    case SnapshotOffer(_, snapshot: StatsState) => state = snapshot
  }

  val receiveCommand: Receive = {
    case AddMeasure(measure) => {

      log info s"${self.path.name} Add measures : ${measure}"
      persist(measure) { event =>
        updateState(measure)


        // Save snapshot
        saveSnapshot(state);

        log info s"${self.path.name} All measures : ${state}"

        if (state.relativeMoistureStats.stdDeviation > 10.0) {
          log.error(s"Standard deviation is too high ${state.relativeMoistureStats.stdDeviation}")
        }
      }

    }
    case GetStats => sender ! state
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}