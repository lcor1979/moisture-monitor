import akka.actor.{ActorSystem, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.moisturemonitor.actors.SensorActor
import org.moisturemonitor.actors.SensorMessages.{GetMeasure, Measure}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration.DurationLong

class MoistureMonitorActorsSpec extends TestKit(ActorSystem(
  "MoistureMonitorActorsSpec",
  ConfigFactory.parseString(MoistureMonitorActorsSpec.config)))
with DefaultTimeout with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  val sensorRef = system.actorOf(Props(classOf[SensorActor]), "sensor")

  override def afterAll = {
    shutdown()
  }

  "Sensor Actor" should {
    "Give measures in expected range" in {
      within(500 millis) {
        sensorRef ! GetMeasure

        expectMsgPF(500 millis) {
          case Measure(_, temperature, relativeMoisture) => {
            temperature should (be >= 15.0 and be <= 30.0)
            relativeMoisture should (be >= 60.0 and be <= 100.0)
          }
        }
      }
    }
  }

}

object MoistureMonitorActorsSpec {
  // Define your test specific configuration here
  val config =
    """
    akka {
      loglevel = "WARNING"
    }
    """
}