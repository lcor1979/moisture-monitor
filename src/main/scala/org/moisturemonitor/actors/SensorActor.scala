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

import akka.actor.{Actor, ActorLogging}
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializable
import org.joda.time.DateTime

import scala.concurrent.forkjoin.ThreadLocalRandom

object SensorMessages {

  case object GetMeasure

  case class Measure(timestamp: DateTime, temperature: Double, relativeMoisture: Double, batteryLevel: Double) extends JacksonJsonSerializable {
    override def toString: String = {
        s"Timestamp : ${timestamp} \n" +
        f"Temperature : ${temperature}%.1f° \n" +
        f"Relative moisture : ${relativeMoisture}%.1f%% \n" +
        f"Battery level : ${batteryLevel}%.1f%%"
    }
  }

}

class SensorActor(sensor:Sensor) extends Actor with ActorLogging {

  import SensorMessages._

  def receive = {
    case GetMeasure => {
      log info s"${self.path.name} Taking a measures"
      val (timestamp, temperature, relativeMoisture, batteryLevel) = sensor.measure
      sender() ! Measure(timestamp, temperature, relativeMoisture, batteryLevel)
    }
    case unexpected => println(s"${self.path.name} receive ${unexpected}")
  }
}

trait Sensor {
  def measure:(DateTime, Double, Double, Double)
}

object FakeSensor extends Sensor {
  def measure = {
    val temperature = ThreadLocalRandom.current().nextDouble(15, 30)
    val relativeMoisture = ThreadLocalRandom.current().nextDouble(45, 95)
    val batteryLevel = ThreadLocalRandom.current().nextDouble(0, 60)

    (DateTime.now, temperature, relativeMoisture, batteryLevel)
  }
}
/*
object SensorMeasureReceiverServer {


  class Auction extends Actor {
    def receive = {
      case Bid(userId, bid) => println(s"Bid complete: $userId, $bid")
      case _ => println("Invalid message")
    }
  }

  // these are from spray-json
  implicit val measureFormat = akka.http.scaladsl.marshalling.J.SprayJsonSupport.jsonFormat(Bid)
  implicit val bidsFormat = jsonFormat1(Bids)

  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val auction = system.actorOf(Props[Auction], "auction")

    val route =
      path("auction") {
        put {
          parameter("bid".as[Int], "user") { (bid, user) =>
            // place a bid, fire-and-forget
            auction ! Bid(user, bid)
            complete((StatusCodes.Accepted, "bid placed"))
          }
        }
        get {
          implicit val timeout: Timeout = 5.seconds

          // query the actor for the current auction state
          val bids: Future[Bids] = (auction ? GetBids).mapTo[Bids]
          complete(bids)
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}
*/