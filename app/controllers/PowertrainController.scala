package controllers

import javax.inject.{Inject, Singleton}

import akka.stream.scaladsl.{Source, Sink, Flow}
import com.datastax.demo.vehicle.VehicleDao
import com.datastax.demo.vehicle.model.Location
import com.github.davidmoten.geo.LatLong
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{WebSocket, Action, Controller}

import scala.compat.java8.FutureConverters._

@Singleton
class PowertrainController @Inject() (vehicleDao: VehicleDao) extends Controller {


  def updateVehicleLocation(vehicle:String,lon:String, lat:String, elevation:String, speed:String, acceleration:String) = Action {
    val location: Location = new Location(new LatLong(lat.toDouble, lon.toDouble), elevation.toDouble)
    vehicleDao.updateVehicle(vehicle, location, speed.toDouble, acceleration.toDouble)
    Ok(s"updateVehicleLocation: $vehicle")
  }

  def addVehicleEvent(vehicle:String, name:String, value:String) = Action {
    vehicleDao.addVehicleEvent(vehicle,name,value)
    Ok(s"addVehicleEvent: $vehicle name:$name value:$value ")
  }

  def vehicleStream = WebSocket.accept[VehicleUpdate, String] { request =>
    val sink = Flow[VehicleUpdate].mapAsync(4) {
      case VehicleLocation(vehicle, location, speed, acceleration) =>
        vehicleDao.updateVehicle(vehicle, location, speed, acceleration).toScala
      case VehicleEvent(vehicle, name, value) =>
        vehicleDao.addVehicleEvent(vehicle, name, value).toScala
    }.to(Sink.ignore)

    Flow.fromSinkAndSource(sink, Source.maybe)
  }

  implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[VehicleUpdate, String]


  sealed trait VehicleUpdate
  object VehicleUpdate {
    implicit val reads: Reads[VehicleUpdate] = (__ \ "type").read[String].flatMap {
      case "location" => implicitly[Reads[VehicleLocation]].map(v => v)
      case "event" => implicitly[Reads[VehicleEvent]].map(v => v)
    }
  }

  case class VehicleLocation(vehicle: String, location: Location, speed: Double, acceleration: Double) extends VehicleUpdate
  object VehicleLocation {
    implicit val latLongReads: Reads[LatLong] =
      ((__ \ "lat").read[Double] and
        (__ \ "lon").read[Double]).apply(new LatLong(_, _))
    implicit val locationReads: Reads[Location] =
      ((__ \ "position").read[LatLong] and
        (__ \ "elevation").read[Double]).apply(new Location(_, _))
    implicit val reads: Reads[VehicleLocation] = Json.reads[VehicleLocation]
  }

  case class VehicleEvent(vehicle:String, name:String, value:String) extends VehicleUpdate
  object VehicleEvent {
    implicit val reads: Reads[VehicleEvent] = Json.reads[VehicleEvent]
  }

}
