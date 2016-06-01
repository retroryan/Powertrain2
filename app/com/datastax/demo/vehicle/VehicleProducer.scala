package com.datastax.demo.vehicle

import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

import com.datastax.demo.vehicle.model.Location
import org.apache.kafka.clients.producer._
import org.joda.time.DateTime
import play.api.Logger
import services.KafkaConfig

import scala.concurrent.Future

class VehicleProducer @Inject() (kafkaConfig: KafkaConfig) {


  val atomicCounter = new AtomicInteger()

  def updateVehicle (vehicle:VehicleLocation,  vehicleId: String, location: Location, speed: Double, acceleration: Double):Unit =  {

   val timestamp = DateTime.now().getMillis

    val nextInt: Int = atomicCounter.getAndIncrement
    val key = s"$vehicleId:${nextInt}"
    val record = new ProducerRecord[String, String](kafkaConfig.topic, key, nextInt.toString)

    Logger.info(s"sending message $key   $nextInt")

    val future = kafkaConfig.producer.send(record, new Callback {
      override def onCompletion(result: RecordMetadata, exception: Exception) {
        if (exception != null)
          Logger.info("Failed to send record: " + exception)
        else {
          //periodically log the num of messages sent
          //if (ratingsSent % 20987 == 0)
          Logger.info(s"ratingsSent = $vehicle  //  result partition: ${result.partition()}")
        }
      }
    })

    future
  }

  def addVehicleEvent(vehicleId: String, eventName: String, eventValue: String):Future[Any] = ???

}
