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
  /**
    * Json protocol supporting Joda DateTime and Measure format
    */
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

  /**
    * Start the webserver that will dispatch received measures to CoordinatorActor
    *
    * @param port Port on which the server will listen
    * @param coordinator CoordinatorActor reference
    * @param system implicit ActorSystem
    */
  def start(port: Int, coordinator: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val route = setupRoute(coordinator)

    bindingFutureOption = Some(Http().bindAndHandle(route, "localhost", port))

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

  /**
    * Stop the server and return a Future indicating the server is unbound.
    *
    * @param executionContext implicit ExecutionContext
    * @return Future indicating the server is unbound
    */
  def stop()(implicit executionContext: ExecutionContext): Future[Unit] = {
    bindingFutureOption.map(_.flatMap(_.unbind())).getOrElse(Future {})
  }

}
