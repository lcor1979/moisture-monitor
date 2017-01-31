import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.joda.time.DateTime
import org.moisturemonitor.actors.SensorMessages.{GetMeasure, Measure}
import spray.json
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, RootJsonFormat}
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object WebServer {
/*
  case class Bid(userId: String, bid: Int)
  case object GetBids
  case class Bids(bids: List[Bid])

  class Auction extends Actor {
    val bids = ArrayBuffer.empty[Bid]

    def receive = {
      case Bid(userId, bid) => {
        println(s"Bid complete: $userId, $bid")
        bids += Bid(userId, bid)
      }
      case GetBids => sender() ! Bids(bids.toList)
      case _ => println("Invalid message")
    }
  }*/

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit object JodaDateTimeJsonFormat extends RootJsonFormat[DateTime] {
      def write(d: DateTime) =
        JsNumber(d.getMillis)

      def read(value: JsValue) = value match {
        case timestamp:JsNumber =>
          new DateTime(timestamp.value.toLong)
        case _ => json.deserializationError("Timestamp expected")
      }
    }
  }

  import MyJsonProtocol._

  case class Measures(measures: List[Measure])

  class MainActor extends Actor {
    val measures = ArrayBuffer.empty[Measure]

    def receive = {
      case measure:Measure => {
        println(s"Measure received: $measure")
        measures += measure
      }
      case GetMeasure => sender() ! Measures(measures.toList)
      case _ => println("Invalid message")
    }
  }

  // these are from spray-json
  implicit val measureFormat = jsonFormat4(Measure)
  implicit val measuresFormat = jsonFormat1(Measures)

  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val mainActor = system.actorOf(Props[MainActor], "mainActor")

    val route =
      path("addMeasure") {
       //path("add") {
          get {
            parameter("timestamp".as[Long], "temperature".as[Double], "moisture".as[Double], "battery".as[Double]) { (timestamp, temperature, moisture, battery) =>
              mainActor ! Measure(new DateTime(timestamp), temperature, moisture, battery)
              complete((StatusCodes.Accepted, "measure saved"))
            }
         // }
        } ~
        put {
          entity(as[Measure]) { (measure) =>
            mainActor ! measure
            complete((StatusCodes.Accepted, "measure saved"))
          }
        }
      }~
        path("all") {
          get {
            implicit val timeout: Timeout = 5.seconds

            // query the actor for the current auction state
            val measures: Future[Measures] = (mainActor ? GetMeasure).mapTo[Measures]
            complete(measures)
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