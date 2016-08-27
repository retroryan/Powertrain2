package controllers

/**
  * Created by sebastianestevez on 8/26/16.
  */

import java.io.StringWriter
import java.util

import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import play.api._
import play.api.libs.json._
import play.api.mvc._
import sys.process._


class PowertrainRestController extends Controller {
  def populateGraph(username: String)= Action {
    //just change echo for the python script you want to run, username will be passed as an argument.
    val cmd = Seq("echo", username).!!
    Ok(cmd)
  }
  def getUserName(token: String) = Action {
    val cmd = Seq("echo", token).!!
    val url = "https://api.github.com/user";

    val post = new HttpGet(url)
    val client = new DefaultHttpClient
    val params = client.getParams
    params.setParameter("access_token", token)

    // send request
    val response = client.execute(post)
    val result = EntityUtils.toString(response.getEntity());

    Ok(result)
  }
  def getLeaderboard(username: String, filter: String) = Action {
    val cmd = Seq("echo", username,filter).!!
    //TODO - gremlin code to get leaderboard based on filter and username
    Ok(cmd)
  }

}
