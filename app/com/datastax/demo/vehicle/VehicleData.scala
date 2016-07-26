package com.datastax.demo.vehicle

import java.sql.Timestamp

import com.datastax.demo.vehicle.model.Location
import com.github.davidmoten.geo.LatLong
import play.api.libs.json._
import play.api.libs.functional.syntax._


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

