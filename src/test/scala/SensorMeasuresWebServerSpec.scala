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

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshal, PredefinedToEntityMarshallers}
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.moisturemonitor.actors.Messages.Measure
import org.moisturemonitor.actors.SensorMeasuresWebServer
import org.scalatest.{Matchers, WordSpec}
import spray.json
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, RootJsonFormat}

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

class SensorMeasuresWebServerSpec
  extends WordSpec
    with Matchers
    with ScalatestRouteTest {

  override def createActorSystem(): ActorSystem = {
    return ActorSystem("testSystem", ConfigFactory.load())
  }


  "SensorMeasuresWebServer" should {
    val coordinatorActorProbe = TestProbe()
    val route = SensorMeasuresWebServer.setupRoute(coordinatorActorProbe.ref)

    "Dispatch new measure to Coordinator actor" in {
      val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 2.0, 3.0)

      Get(s"/addMeasure?timestamp=${expectedMeasure.timestamp.getMillis}&temperature=${expectedMeasure.temperature}&relativeMoisture=${expectedMeasure.relativeMoisture}&batteryLevel=${expectedMeasure.batteryLevel}") ~> route ~> check {
        status == StatusCodes.Accepted
        responseAs[String] shouldEqual "measure saved"

        coordinatorActorProbe.expectMsg(expectedMeasure)
      }

    }

    "Dispatch put JSON measure to Coordinator actor" in {
      // Json setup
      import org.moisturemonitor.actors.SensorMeasuresWebServer.SensorMeasuresWebServerJsonProtocol._
      import spray.json._

      val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 2.0, 3.0)

      val json = expectedMeasure.toJson.compactPrint

      Put(s"/addMeasure", HttpEntity(MediaTypes.`application/json`, json)) ~> route ~> check {
        status == StatusCodes.Accepted
        responseAs[String] shouldEqual "measure saved"

        coordinatorActorProbe.expectMsg(expectedMeasure)
      }

    }
  }

}
