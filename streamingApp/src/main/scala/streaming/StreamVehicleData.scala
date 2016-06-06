package streaming

/**
 * Created by sebastianestevez on 6/1/16.
 */

import java.sql.Timestamp

import kafka.serializer.StringDecoder
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext, Time}
import org.apache.spark.{SparkConf, SparkContext}

//import kafka.serializer.StringDecoder

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, SaveMode}

object StreamVehicleData {
  def main(args: Array[String]) {
    val sparkConf = new SparkConf()
    val contextDebugStr: String = sparkConf.toDebugString
    System.out.println("contextDebugStr = " + contextDebugStr)

    def createStreamingContext(): StreamingContext = {
      @transient val newSsc = new StreamingContext(sparkConf, Seconds(1))
      println(s"Creating new StreamingContext $newSsc")
      newSsc
    }

    val sparkStreamingContext = StreamingContext.getActiveOrCreate(createStreamingContext)

    val sc = SparkContext.getOrCreate(sparkConf)
    val sqlContext = SQLContext.getOrCreate(sc)
    import sqlContext.implicits._

    //not checkpointing
    //ssc.checkpoint("/ratingsCP")

    //val topicsArg = "vehicle_events,vehicle_status"
    val topicsArg = "vehicle_events"
    val brokers = "192.168.99.100:9092"
    val debugOutput = true


    val topics: Set[String] = topicsArg.split(",").map(_.trim).toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    //val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)


    println(s"connecting to brokers: $brokers")
    println(s"sparkStreamingContext: $sparkStreamingContext")
    println(s"kafkaParams: $kafkaParams")
    println(s"topics: $topics")


    val rawVehicleStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](sparkStreamingContext, kafkaParams, topics)

    val vehicleStream = rawVehicleStream.map { case (key, rawVehicleStr) =>
      System.out.println("HERE " + rawVehicleStr)
      val data = rawVehicleStr.split(",")
      val vehicleUpdate:VehicleUpdate = data(0) match {
        case "location" =>
          VehicleLocation(data(1), data(2), data(3), data(4).toDouble, data(5).toDouble, new Timestamp(data(6).toLong),new Timestamp(data(7).toLong), data(8))
        case "event" =>
          VehicleEvent(data(1), data(2), data(3), new Timestamp(data(4).toLong),new Timestamp(data(5).toLong))
      }

     vehicleUpdate

    }

    vehicleStream.map {
      case (message: RDD[VehicleLocation], batchTime: Time) => {
        // convert each RDD from the batch into a Vehicle Location DataFrame
        //rating data has the format user_id:movie_id:rating:timestamp
        val vehicleLocationDF = message.toDF()

        // this can be used to debug dataframes
        if (debugOutput) {
          println("vehicle location:")
          vehicleLocationDF.show()
        }

        // save the DataFrame to Cassandra
        // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
        vehicleLocationDF.write.format("org.apache.spark.sql.cassandra")
          .mode(SaveMode.Append)
          .options(Map("keyspace" -> "vehicle_tracking_app", "table" -> "vehicle_stats"))
          .save()
      }
      case (message: RDD[VehicleEvent], batchTime: Time) => {
        // convert each RDD from the batch into a Vehicle Location DataFrame
        //rating data has the format user_id:movie_id:rating:timestamp
        val vehicleEventDF = message.toDF()

        // this can be used to debug dataframes
        if (debugOutput) {
          println("vehicle event:")
          vehicleEventDF.show()
        }

        // save the DataFrame to Cassandra
        // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
        vehicleEventDF.write.format("org.apache.spark.sql.cassandra")
          .mode(SaveMode.Append)
          .options(Map("keyspace" -> "vehicle_tracking_app", "table" -> "vehicle_events"))
          .save()
      }
    }

    //Kick off
    sparkStreamingContext.start()
    sparkStreamingContext.awaitTermination()

  }
}
