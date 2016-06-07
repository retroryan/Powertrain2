package com.datastax.demo.vehicle

import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

import com.github.davidmoten.geo.GeoHash
import org.apache.kafka.clients.producer._
import play.api.Logger
import services.KafkaConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VehicleProducer @Inject()(kafkaConfig: KafkaConfig) {


  val atomicCounter = new AtomicInteger()

  def updateVehicle(internalVehicleLocation: VehicleLocation) = {

    val latLong = internalVehicleLocation.location.getLatLong
    val elevation = internalVehicleLocation.location.getElevation.toString

    val tile1: String = GeoHash.encodeHash(latLong.getLat, 4)
    val tile2: String = GeoHash.encodeHash(latLong.getLon, 7)

    val vehicleLocation = ALT_VehicleLocation(internalVehicleLocation.vehicle, s"${latLong.getLat},${latLong.getLon}", elevation,
      internalVehicleLocation.speed, internalVehicleLocation.acceleration,
      new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), tile2)

    val nextInt: Int = atomicCounter.getAndIncrement
    val key = s"${vehicleLocation.vehicle_id}:$nextInt"
    val record = new ProducerRecord[String, String](kafkaConfig.topic, key, "location," + vehicleLocation.toString)

    Logger.info(s"sending location $key $nextInt")

    val future = kafkaConfig.producer.send(record, new Callback {
      override def onCompletion(result: RecordMetadata, exception: Exception) = {
        if (exception != null)
          Logger.info("Failed to send record: " + exception)
        else {
          //periodically log the num of messages sent
          //if (ratingsSent % 20987 == 0)
          Logger.info(s"location sent = $vehicleLocation  //  result partition: ${result.partition()}")
        }
      }
    })

    /*  In the future this can be used to map the results to a message back to client

     val listenableFuture: ListenableFuture[RecordMetadata] = JdkFutureAdapters.listenInPoolThread(future)
     val eventualRecordMetadata: Future[RecordMetadata] = VehicleDao.toCompletionStage(listenableFuture).toScala
     eventualRecordMetadata.map(rslt => rslt.toString)

     */

    Future {
      "Not Used"
    }
  }


  def addVehicleEvent(internalVehicleEvent: VehicleEvent) = {

    val vehicleEvent = ALT__VehicleEvent(internalVehicleEvent.vehicle, internalVehicleEvent.name, internalVehicleEvent.value)

    val nextInt: Int = atomicCounter.getAndIncrement
    val key = s"${vehicleEvent.vehicle_id}:$nextInt"
    val record = new ProducerRecord[String, String](kafkaConfig.topic, key, "event," + vehicleEvent.toString)

    Logger.info(s"sending event $key   $nextInt")

    val future = kafkaConfig.producer.send(record, new Callback {
      override def onCompletion(result: RecordMetadata, exception: Exception) = {
        if (exception != null)
          Logger.info("Failed to send record: " + exception)
        else {
          //periodically log the num of messages sent
          //if (ratingsSent % 20987 == 0)
          Logger.info(s"event sent = $vehicleEvent  //  result partition: ${result.partition()}")
        }
      }
    })

    /*
        val listenableFuture: ListenableFuture[RecordMetadata] = JdkFutureAdapters.listenInPoolThread(future)
        val eventualRecordMetadata: Future[RecordMetadata] = VehicleDao.toCompletionStage(listenableFuture).toScala
        eventualRecordMetadata.map(rslt => rslt.toString)
    */

    Future {
      "Not Used"
    }
  }

}
