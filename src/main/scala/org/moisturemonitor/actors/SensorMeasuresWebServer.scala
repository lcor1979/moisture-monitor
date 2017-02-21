package org.moisturemonitor.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import org.joda.time.DateTime
import org.moisturemonitor.actors.Messages.Measure
import spray.json
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future};

object SensorMeasuresWebServer {
  // Json protocol supporting Joda DateTime
  object SensorMeasuresWebServerJsonProtocol extends DefaultJsonProtocol {

    implicit object JodaDateTimeJsonFormat extends RootJsonFormat[DateTime] {
      def write(d: DateTime) =
        JsNumber(d.getMillis)

      def read(value: JsValue) = value match {
        case timestamp: JsNumber =>
          new DateTime(timestamp.value.toLong)
        case _ => json.deserializationError("Timestamp expected")
      }
    }

    // JSON Format for measure
    implicit val measureFormat = jsonFormat4(Measure)
  }

  import SensorMeasuresWebServerJsonProtocol._

  var bindingFutureOption:Option[Future[ServerBinding]] = None

  def start(coordinator: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val route = setupRoute(coordinator)

    bindingFutureOption = Some(Http().bindAndHandle(route, "localhost", 8080))

  }

  def setupRoute(coordinator: ActorRef) = {
    path("addMeasure") {
      get {
        parameter("timestamp".as[Long], "temperature".as[Double], "relativeMoisture".as[Double], "batteryLevel".as[Double]) { (timestamp, temperature, relativeMoisture, batteryLevel) =>
          coordinator ! Measure(new DateTime(timestamp), temperature, relativeMoisture, batteryLevel)
          complete((StatusCodes.Accepted, "measure saved"))
        }
      } ~
        put {
          entity(as[Measure]) { (measure) =>
            coordinator ! measure
            complete((StatusCodes.Accepted, "measure saved"))
          }
        }
    }
  }

  def stop()(implicit executionContext: ExecutionContext): Future[Unit] = {
    bindingFutureOption.map(_.flatMap(_.unbind())).getOrElse(Future {})
  }

}
