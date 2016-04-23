package services

import javax.inject.{Provider, Inject, Singleton}

import com.datastax.demo.vehicle.VehicleDao
import com.datastax.driver.core.{Session, Cluster}
import com.datastax.driver.core.policies.{DCAwareRoundRobinPolicy, DefaultRetryPolicy, TokenAwarePolicy}
import play.api.Logger
import play.api.inject.ApplicationLifecycle

@Singleton
class SessionProvider @Inject()(appLifecycle: ApplicationLifecycle) extends Provider[Session] {

  lazy val get = {
    val contactPoints = "localhost"

    //TODO convert between java and scala
    //val split: java.util.Collection[String] = contactPoints.split(",").asJava

    val cluster = Cluster.builder.addContactPoints(contactPoints)
      .withRetryPolicy(DefaultRetryPolicy.INSTANCE)
      .withLoadBalancingPolicy(new TokenAwarePolicy(DCAwareRoundRobinPolicy.builder.build))
      .build

    Logger.info("Built Cluster   " + cluster.toString)
    Logger.info("cluster hosts   " + cluster.getMetadata.getAllHosts)

    val session = cluster.newSession
    Logger.info("Session created " + session.toString)

    session
  }


}
