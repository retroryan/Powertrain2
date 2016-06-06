package com.datastax.demo.vehicle

import java.sql.Timestamp
import java.util.concurrent.{CompletionStage, CompletableFuture, ForkJoinPool}
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

import com.github.davidmoten.geo.GeoHash
import com.google.common.util.concurrent.{JdkFutureAdapters, ListenableFuture}
import org.apache.kafka.clients.producer._
import play.api.Logger
import services.KafkaConfig

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class VehicleProducer @Inject()(kafkaConfig: KafkaConfig) {


  val atomicCounter = new AtomicInteger()

  def updateVehicle(internalVehicleLocation: InternalVehicleLocation) = {

    val latLong = internalVehicleLocation.location.getLatLong
    val elevation = internalVehicleLocation.location.getElevation.toString

    val tile1: String = GeoHash.encodeHash(latLong.getLat, 4)
    val tile2: String = GeoHash.encodeHash(latLong.getLon, 7)

    val vehicleLocation = VehicleLocation(internalVehicleLocation.vehicle, s"${latLong.getLat},${latLong.getLon}", elevation,
      internalVehicleLocation.speed, internalVehicleLocation.acceleration,
      new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), tile2)

    val nextInt: Int = atomicCounter.getAndIncrement
    val key = s"${vehicleLocation.vehicle_id}:$nextInt"
    val record = new ProducerRecord[String, String](kafkaConfig.topic, key, "location," + vehicleLocation.toString)

    Logger.info(s"sending message $key   $nextInt")

    val future = kafkaConfig.producer.send(record, new Callback {
      override def onCompletion(result: RecordMetadata, exception: Exception) = {
        if (exception != null)
          Logger.info("Failed to send record: " + exception)
        else {
          //periodically log the num of messages sent
          //if (ratingsSent % 20987 == 0)
          Logger.info(s"ratingsSent = $vehicleLocation  //  result partition: ${result.partition()}")
        }
      }
    })

    val listenableFuture: ListenableFuture[RecordMetadata] = JdkFutureAdapters.listenInPoolThread(future)
    val eventualRecordMetadata: Future[RecordMetadata] = VehicleDao.toCompletionStage(listenableFuture).toScala
    eventualRecordMetadata.map(rslt => rslt.toString)

  }


  def addVehicleEvent(internalVehicleEvent:InternalVehicleEvent): Future[String] = {

    val vehicleEvent = NOTVehicleEvent(internalVehicleEvent.vehicle_id, internalVehicleEvent.event_name, internalVehicleEvent.event_value)

    val nextInt: Int = atomicCounter.getAndIncrement
    val key = s"${vehicleEvent.vehicle_id}:$nextInt"
    val record = new ProducerRecord[String, String](kafkaConfig.topic, key, "event," + vehicleEvent.toString)

    Logger.info(s"sending message $key   $nextInt")

    val future = kafkaConfig.producer.send(record, new Callback {
      override def onCompletion(result: RecordMetadata, exception: Exception) = {
        if (exception != null)
          Logger.info("Failed to send record: " + exception)
        else {
          //periodically log the num of messages sent
          //if (ratingsSent % 20987 == 0)
          Logger.info(s"ratingsSent = $vehicleEvent  //  result partition: ${result.partition()}")
        }
      }
    })

    val listenableFuture: ListenableFuture[RecordMetadata] = JdkFutureAdapters.listenInPoolThread(future)
    val eventualRecordMetadata: Future[RecordMetadata] = VehicleDao.toCompletionStage(listenableFuture).toScala
    eventualRecordMetadata.map(rslt => rslt.toString)
  }

}
