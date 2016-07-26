package controllers

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Producer
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.stream.scaladsl.{Flow, Sink, Source}
import data.{VehicleEvent, VehicleLocation, VehicleUpdate}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{Controller, WebSocket}
import services.Kafka


@Singleton
class PowertrainController @Inject()(kafka: Kafka, system: ActorSystem) extends Controller {

  val atomicCounter = new AtomicInteger()

  val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[VehicleUpdate, String]


  def vehicleStream = WebSocket.accept[VehicleUpdate, String] { request =>
    val sink = Flow[VehicleUpdate]
      .map(vehicleUpdate => {
          val record = vehicleUpdate match {
            case vehicleLocation@VehicleLocation(vehicle, location, speed, acceleration) => {
              val key = s"${vehicleLocation.vehicle}:$atomicCounter.getAndIncrement"
              new ProducerRecord[String, String]("vehicle_events", key, "location," + vehicleLocation.toString)
            }
            case vehicleEvent@VehicleEvent(vehicle, name, value) => {
              val key = s"${vehicleEvent.vehicle}:$atomicCounter.getAndIncrement"
              new ProducerRecord[String, String]("vehicle_events", key, "event," + vehicleEvent.toString)
            }
          }
          ProducerMessage.Message(record, vehicleUpdate)
        }
      )
      .via(Producer.flow(producerSettings))
      .map { result =>
        val record = result.message.record
        println(s"${record.topic}/${record.partition} ${result.offset}: ${record.value} (${result.message.passThrough}")
        result
      }.to(Sink.ignore)

    Flow.fromSinkAndSource(sink, Source.maybe)
  }



}
