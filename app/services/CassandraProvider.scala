package services

import javax.inject.{Inject, Provider}

import com.datastax.driver.dse.graph.GraphOptions
import com.datastax.driver.dse.{DseCluster, DseSession}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle


case class CassandraConfig(session:DseSession)

class CassandraProvider @Inject()(appLifecycle: ApplicationLifecycle, config:Configuration) extends Provider[CassandraConfig] {

  lazy val get = {
    val graphName = config.getString("powertrain.graph_name").getOrElse("summitDemo")
    val dse_host = config.getString("powertrain.dse_graph_host").getOrElse("127.0.0.1")
    println(s"DSE config $graphName  and  $dse_host")
    val cluster = new DseCluster.Builder().addContactPoint(dse_host).withGraphOptions(new GraphOptions().setGraphName(graphName)).build
    CassandraConfig(cluster.connect())
  }
}


