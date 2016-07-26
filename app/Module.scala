import com.datastax.driver.core.Session
import com.google.inject.AbstractModule
import java.time.Clock

import play.api.inject.ConfigurationProvider
import play.api.{ Configuration, Environment }
import services._

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.

 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module(environment: Environment,
              configuration: Configuration) extends AbstractModule {

  override def configure() = {

    val kafkaConfiguration: Configuration =
      configuration.getConfig("sparkAtScale.kafkaHost").getOrElse(Configuration.empty)

    // Use the system clock as the default implementation of Clock
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    // Ask Guice to create an instance of ApplicationTimer when the
    // application starts.
    bind(classOf[ApplicationTimer]).asEagerSingleton()
    // Set AtomicCounter as the implementation for Counter.
    bind(classOf[Counter]).to(classOf[AtomicCounter])

    bind(classOf[KafkaConfig]).toProvider(classOf[KafkaProvider])

    bind(classOf[Kafka]).to(classOf[KafkaImpl])

    //bind(classOf[Configuration]).toProvider(classOf[ConfigurationProvider])

  }

}
