package streaming
/**
  * Created by sebastianestevez on 6/1/16.
  */

import kafka.serializer.StringDecoder
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext, Time}
import org.apache.spark.{SparkConf, SparkContext}
import kafka.serializer.StringDecoder
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, SaveMode}
import com.datastax.spark.connector.SomeColumns
import org.apache.spark.streaming.dstream.DStream
import org.joda.time.DateTime
import scala.collection.mutable.ArrayBuffer

object StreamVehicleData {
  def main(args: Array[String]){
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

    val topicsArg = "vehicle_events,vehicle_status"
    val brokers =  "localhost:9092"
    val debugOutput = true


    val topics: Set[String] = topicsArg.split(",").map(_.trim).toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers, "value.deserializer" -> classOf[KryoInternalSerializer].getName)


    println(s"connecting to brokers: $brokers")
    println(s"sparkStreamingContext: $sparkStreamingContext")
    println(s"kafkaParams: $kafkaParams")
    println(s"topics: $topics")


    val rawVehicleStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](sparkStreamingContext, kafkaParams, topics)


    val vehicleStream= rawVehicleStream.map { case (key, nxtVehicle) =>
      val parsedRating = nxtVehicle.split("::")
      VehicleEvent(parsedRating(0).toString(), parsedRating(1).toString(), parsedRating(2).toString())
    }

    vehicleStream.foreachRDD {
      (message: RDD[VehicleEvent], batchTime: Time) => {

        // convert each RDD from the batch into a Ratings DataFrame
        //rating data has the format user_id:movie_id:rating:timestamp
        val ratingDF = message.toDF()

        // this can be used to debug dataframes
        if (debugOutput)
          ratingDF.show()

        // save the DataFrame to Cassandra
        // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
        ratingDF.write.format("org.apache.spark.sql.cassandra")
          .mode(SaveMode.Append)
          .options(Map("keyspace" -> "vehicle_tracking_app", "table" -> "vehicle_stats"))
          .save()
      }
    }

    //Kick off
    sparkStreamingContext.start()
    sparkStreamingContext.awaitTermination()

  }

}
