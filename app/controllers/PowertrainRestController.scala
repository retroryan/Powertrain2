package controllers

/**
  * Created by sebastianestevez on 8/26/16.
  */

import java.util.concurrent.{CompletableFuture, CompletionStage, ForkJoinPool}
import javax.inject._

import akka.actor.ActorSystem
import com.datastax.driver.dse.graph.{GraphOptions, GraphResultSet, SimpleGraphStatement}
import com.datastax.driver.dse.{DseCluster, DseSession}
import com.datastax.driver.core.Row
import com.google.common.util.concurrent.ListenableFuture
import play.api._
import play.api.libs.json._
import play.api.mvc._
import services.CassandraConfig

import scala.collection.mutable.ListBuffer
import scala.compat.java8.FutureConverters
import scala.sys.process._
import scalaj.http._
import scala.collection.JavaConversions._

case class LeaderboardResults(vehicle_id: String, elapsed_time: String)

class PowertrainRestController @Inject()(configuration: play.api.Configuration, system: ActorSystem, cassandraConfig:CassandraConfig) extends Controller {

  def populateGraph(username: String) = Action {
    val pyPath = getClass.getClassLoader.getResource("networkByUser.py").getPath
    Logger.info(s"pyPath for networkByUser.py $pyPath")
    val cmd = Seq("python", pyPath, configuration.getString("powertrain.dse_graph_host").get, username).!!
    Logger.info(s"cmd output for networkByUser: $cmd")
    Ok("Success")
  }

  def getUserName(token: String) = Action {
    val access_token = get_authorization_code(token)
    val username = get_username(access_token).getOrElse("Username not found")
    Ok(username)
  }

  def getLeaderboard(username: String, filter: String) = Action {
    val cmd = Seq("echo", username, filter).!!
    //TODO - gremlin code to get leaderboard based on filter and username
    Ok(cmd)
  }

  def get_authorization_code(token: String): String = {

    val response = Http("https://github.com/login/oauth/access_token")
      .postForm(Seq(
        "client_id" -> configuration.getString("powertrain.github_client_id").get,
        "client_secret" -> configuration.getString("powertrain.github_client_secret").get,
        "code" -> token))
      .asString
    // send request
    val access_token = response.body.split('&')(0).split('=')(1)
    access_token
  }

  def get_username(access_token: String): Option[String] = {

    val response = Http("https://api.github.com/user")
      .param("access_token", access_token)
      .asString

    val response_json = Json.parse(response.body)

    (response_json \ "login").toOption match {
      case Some(loginStr) => Some(loginStr.toString.replace("\"", ""))
      case None => None
    }
  }


  def global_leaderboard() = Action.async {
    //val session = get_dse_session(configuration.getString("powertrain.dse_graph_host").get, "summitDemo")

    val getGlobalLeaderboard = new SimpleGraphStatement(
      """
        g.V()
         .has('powertrain_events','event_name', 'lap')
          .as('events')
          .order()
          .by("elapsed_time", incr)
          .as('vehicle_id', 'elapsed_time')
          .select('vehicle_id', 'elapsed_time')
          .by('vehicle_id')
          .by('elapsed_time')
          .limit(100)
      """)

    //val results = session.executeGraph(getTopTenGlobal).all()
    //Ok(eventualGraphResultSet)

    implicit val ec = system.dispatcher
    val graphCompletionStage: CompletionStage[GraphResultSet] = PowertrainRestController.toCompletionStage(cassandraConfig.session.executeGraphAsync(getGlobalLeaderboard))
    FutureConverters.toScala(graphCompletionStage).map {
      results => Ok(results.all().toString)
    }
  }
  def coding_leaderboard(language: String) = Action.async {
    //val session = get_dse_session(configuration.getString("powertrain.dse_graph_host").get, "summitDemo")

    val getCodingLeaderboard = new SimpleGraphStatement(
      """
         g.V().has('coding_language', 'name', name)
           .inE('develops_in')
           .outV().dedup()
           .outE('has_events')
           .inV().has('event_name', 'lap')
           .as('vehicle_id', 'elapsed_time')
           .select('vehicle_id', 'elapsed_time')
           .by('vehicle_id')
           .by('elapsed_time')
           .limit(100)
      """).set("name", language)
    //val results = session.executeGraph(getCodingLeaderboard).all()
    //Ok(results.toString)

    implicit val ec = system.dispatcher
    val graphCompletionStage: CompletionStage[GraphResultSet] = PowertrainRestController.toCompletionStage(cassandraConfig.session.executeGraphAsync(getCodingLeaderboard))
    FutureConverters.toScala(graphCompletionStage).map {
      results => Ok(results.all().toString)
    }
  }

  def get_languages() = Action {
    val languages = cassandraConfig.session.execute("select name from cassandra_summit.coding_language_p").all().asInstanceOf[java.util.ArrayList[Row]].toSet
    val results = ListBuffer[String]()
    for(language <- languages){
      if(!language.isNull("name"))
      {
        results += "\"" + language.getString("name") + "\""
      }
    }
    val buffer: java.util.List[String] = results

    Ok(buffer.toString)
  }

/*  def get_dse_session(dse_host: String, graph_name: String): DseSession = {
    val dseCluster = if (graph_name ne "")
      new DseCluster.Builder().addContactPoint(dse_host).withGraphOptions(new GraphOptions().setGraphName(graph_name)).build
    else
      new DseCluster.Builder().addContactPoint(dse_host).build

    dseCluster.connect()
  }*/


}

object PowertrainRestController {

  def createRunnable(f: () => Unit): Runnable =
    new Runnable() {
      def run() = f()
    }

  def toCompletionStage[T](listenableFuture: ListenableFuture[T]): CompletionStage[T] = {
    val completableFuture: CompletableFuture[T] = new CompletableFuture[T]

    val runnable = createRunnable { () =>
      try
        completableFuture.complete(listenableFuture.get)
      catch {
        case e: Exception =>
          completableFuture.completeExceptionally(e)
      }
    }

    listenableFuture.addListener(runnable, ForkJoinPool.commonPool())
    completableFuture
  }
}