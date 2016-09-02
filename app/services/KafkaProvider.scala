package services

import java.util.Properties
import javax.inject.{Provider, Inject, Singleton}
import data.VehicleLocation
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.{ProducerConfig, KafkaProducer}
import play.api.{Configuration, Logger}
import play.api.inject.ApplicationLifecycle

case class KafkaConfig(topic:String, producer: KafkaProducer[String,String])

@Singleton
class KafkaProvider @Inject()(appLifecycle: ApplicationLifecycle, config:Configuration) extends Provider[KafkaConfig] {

  lazy val get = {

    val host: String = config.getString("powertrain.kafkaHost").getOrElse("172.31.14.225:9092")
    val topic: String = config.getString("powertrain.kafkaHost").getOrElse("vehicle_events")

    //val host: String = "localhost:9092"
    //val topic: String = "vehicle_events"

    println(s"kafka host: $host and topic: $topic")

    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    Logger.info(s"created producer on host $host and producer $producer")

    KafkaConfig(topic, producer)

  }
}

