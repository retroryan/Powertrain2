package controllers

/**
  * Created by sebastianestevez on 8/26/16.
  */

import java.io.StringWriter
import java.util
import play.api._
import play.api.libs.json._
import play.api.mvc._
import javax.inject._
import play.api.libs.ws._
import sys.process._
import scalaj.http._



class PowertrainRestController @Inject() (configuration: play.api.Configuration) extends Controller {
  def populateGraph(username: String)= Action {
    val pyPath = getClass.getClassLoader.getResource("networkByUser.py").getPath
    Logger.info(s"pyPath for networkByUser.py $pyPath")
    val cmd = Seq("python", pyPath, configuration.getString("powertrain.dse_graph_host").get, username).!!
    Logger.info(s"cmd output for networkByUser: $cmd")
    Ok("Success")
  }
  def getUserName(token: String) = Action {
    val access_token = get_authorization_code(token)
    val username = get_username(access_token)
    Ok(username)
  }
  def getLeaderboard(username: String, filter: String) = Action {
    val cmd = Seq("echo", username,filter).!!
    //TODO - gremlin code to get leaderboard based on filter and username
    Ok(cmd)
  }

  def get_authorization_code(token: String): String =
  {

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
  def get_username(access_token: String): String ={

    val response = Http("https://api.github.com/user")
      .param("access_token",access_token)
      .asString

    val response_json = Json.parse(response.body)

    (response_json \ "login").get.toString.replace("\"","")
  }

}
