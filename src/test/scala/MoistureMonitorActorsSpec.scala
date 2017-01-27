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

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.ConfigFactory
import io.scalac.slack.MessageEventBus
import io.scalac.slack.common.{BaseMessage, Command, HelpRequest, OutboundMessage}
import no.nextgentel.oss.akkatools.serializing.JacksonJsonSerializer
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.moisturemonitor.actors.MessagingMessages.SendMessage
import org.moisturemonitor.actors.SensorMessages.{GetMeasure, Measure}
import org.moisturemonitor.actors.StatsMessages.{AddMeasure, GetStats, Stats, StatsState}
import org.moisturemonitor.actors._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class MoistureMonitorActorsSpec extends TestKit(ActorSystem("testSystem", ConfigFactory.load()))
  with DefaultTimeout
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll = {
    TestKit.shutdownActorSystem(system)
  }

  "Sensor Actor" should {
    val sensor = mock(classOf[Sensor])
    val sensorRef = TestActorRef(Props(classOf[SensorActor], sensor))

    "Send measures" in {
      val expectedMeasure = (DateTime.now, 1.0, 1.0, 1.0)
      doReturn(expectedMeasure).when(sensor).measure

      sensorRef ! GetMeasure

      expectMsg(Measure(expectedMeasure._1, expectedMeasure._2, expectedMeasure._3, expectedMeasure._4))
    }
  }

  "Stats Actor" should {
    def initJsonSerializer = {
      val objectMapper = new ObjectMapper()
      objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      objectMapper.registerModule(new DefaultScalaModule)
      objectMapper.registerModule(new JodaModule)

      JacksonJsonSerializer.setObjectMapper(objectMapper)
    }

    def assertStatsEquals(stats: Stats, expectedStats: Stats) = {
      assert(stats.average === expectedStats.average)
      assert(stats.stdDeviation === expectedStats.stdDeviation +- 0.001)
      assert(stats.variance === expectedStats.variance +- 0.001)
    }

    val statsRef = system.actorOf(Props[StatsActor])

    initJsonSerializer

    "Save measures" in {
      val expectedMeasures = List(
        Measure(DateTime.now, 1.0, 1.0, 1.0),
        Measure(DateTime.now, 2.0, 2.0, 2.0),
        Measure(DateTime.now, 3.0, 3.0, 3.0)
      )
      val expectedStats = Stats(2.0, 0.666, 0.816)

      expectedMeasures.foreach(measure => {
        statsRef ! AddMeasure(measure)

        expectMsgAnyClassOf(classOf[StatsState])
      })

      statsRef ! GetStats

      expectMsgPF() {
        case StatsState(measures, temperatureStats, relativeMoistureStats) => {
          assert(measures.size === 3)
          assertStatsEquals(temperatureStats, expectedStats)
          assertStatsEquals(relativeMoistureStats, expectedStats)
        }
      }

      statsRef ! PoisonPill

      val statsRef2 = system.actorOf(Props[StatsActor])

      statsRef2 ! GetStats

      expectMsgPF() {
        case StatsState(measures, temperatureStats, relativeMoistureStats) => {
          assert(measures.size === 3)
          assertStatsEquals(temperatureStats, expectedStats)
          assertStatsEquals(relativeMoistureStats, expectedStats)
        }
      }

    }
  }

  "Main Actor" should {
    val sensorActorProbe = TestProbe()
    val statsActorProbe = TestProbe()
    val messagingActorProbe = TestProbe()
    val mainActorRef = TestActorRef(Props(classOf[MainActor], sensorActorProbe.ref, statsActorProbe.ref, messagingActorProbe.ref))

    "Send GetMeasure command to sensor actor" in {

      mainActorRef ! GetMeasure

      sensorActorProbe.expectMsg(GetMeasure)
    }

    "Forward received measure to stats actor" in {
      val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 1.0, 50.0)

      mainActorRef ! expectedMeasure

      statsActorProbe.expectMsg(AddMeasure(expectedMeasure))
      messagingActorProbe.expectNoMsg()
    }

    "Send a message to Messaging actor if a moisture measure is above the threshold" in {
      val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 85.0, 50.0)

      mainActorRef ! expectedMeasure

      statsActorProbe.expectMsg(AddMeasure(expectedMeasure))
      messagingActorProbe.expectMsg(SendMessage(Some("Moisture too high"), Some(expectedMeasure)))
    }

    "Send a message to Messaging actor if moisture standard deviation stats is above the threshold" in {
      var expectedStats = StatsState(Nil, Stats(1.0, 1.0, 1.0), Stats(2.0, 2.0, 12.0))

      mainActorRef ! expectedStats

      messagingActorProbe.expectMsg(SendMessage(Some("Moisture Stats standard deviation too high"), Some(expectedStats)))
    }

    "Send a message to Messaging actor if battery is below the threshold" in {
      val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 85.0, 5.0)

      mainActorRef ! expectedMeasure

      statsActorProbe.expectMsg(AddMeasure(expectedMeasure))
      messagingActorProbe.expectMsg(SendMessage(Some("Low battery"), Some(expectedMeasure)))
    }
  }

  "Messaging Actor" should {
    val slackBotActorProbe = TestProbe()
    val messagingActorRef = TestActorRef(Props(classOf[MessagingActor], slackBotActorProbe.ref))

    "Dispatch SendMessage to slackbot actor" in {
      val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 1.0, 1.0)

      messagingActorRef ! SendMessage(Some("message"), Some(expectedMeasure))

      slackBotActorProbe.expectMsg(DispatchCommandMessage("moisture-bot", DispatchCommand("send-message", List(Some("message"), Some(expectedMeasure)), "C3M5DR334")))
    }

  }

  "Moisture Bot" should {

    val expectedMeasure: Measure = Measure(DateTime.now, 1.0, 1.0, 1.0)
    class TestSensorActor(val expectedMeasure: Measure) extends Actor {
      override def receive: Receive = {
        case GetMeasure => sender() ! expectedMeasure
      }
    }

    val sensorActorRef = TestActorRef(new TestSensorActor(expectedMeasure), "sensorActor")
    val messageEventBus = mock(classOf[MessageEventBus])
    val moistureBotRef = TestActorRef(Props(classOf[MoistureBot], messageEventBus, sensorActorRef.path.parent.name + "/" + sensorActorRef.path.name))

    "Publish help commands" in {
      moistureBotRef ! HelpRequest(None, "channel")

      verify(messageEventBus).publish(OutboundMessage("channel", anyString()))
    }

    "Publish measure commands" in {
      val expectedMessage = moistureBotRef.underlyingActor.asInstanceOf[MoistureBot].format(None, expectedMeasure)

      moistureBotRef ! Command("measure", List(), BaseMessage("", "channel", "", "", false))

      verify(messageEventBus).publish(OutboundMessage("channel", expectedMessage))
    }

    "Publish send-message commands with just text" in {
      val text = "text"
      val expectedMessage = moistureBotRef.underlyingActor.asInstanceOf[MoistureBot].format(Some(text))

      moistureBotRef ! DispatchCommand("send-message", List(Some(text), None), "channel")

      verify(messageEventBus).publish(OutboundMessage("channel", expectedMessage))
    }

    "Publish send-message commands with measure" in {
      val text = "text"
      val expectedMessage = moistureBotRef.underlyingActor.asInstanceOf[MoistureBot].format(Some(text), expectedMeasure)

      moistureBotRef ! DispatchCommand("send-message", List(Some(text), Some(expectedMeasure)), "channel")

      verify(messageEventBus).publish(OutboundMessage("channel", expectedMessage))
    }

    "Publish send-message commands with stats" in {
      val text = "text"
      var expectedStats = StatsState(Nil, Stats(1.0, 1.0, 1.0), Stats(2.0, 2.0, 2.0))
      val expectedMessage = moistureBotRef.underlyingActor.asInstanceOf[MoistureBot].format(Some(text), expectedStats)

      moistureBotRef ! DispatchCommand("send-message", List(Some(text), Some(expectedStats)), "channel")

      verify(messageEventBus).publish(OutboundMessage("channel", expectedMessage))
    }
  }
}



