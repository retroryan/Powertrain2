package com.datastax.demo.vehicle

import com.datastax.demo.vehicle.model.Location
import com.github.davidmoten.geo.LatLong
import play.api.libs.json._
import play.api.libs.functional.syntax._


//TODO FIX THIS
//for now this is the version of VehicleLocation that is used for kafka kryo seralization
case class VehicleLocation(vehicle: String, location: String, elevation:String, speed: Double, acceleration: Double)


sealed trait VehicleUpdate
object VehicleUpdate {
  implicit val reads: Reads[VehicleUpdate] = (__ \ "type").read[String].flatMap {
    case "location" => implicitly[Reads[InternalVehicleLocation]].map(v => v)
    case "event" => implicitly[Reads[VehicleEvent]].map(v => v)
  }
}

//for now this is the version of VehicleLocation that is used for websocket seralization
case class InternalVehicleLocation(vehicle: String, location: Location, speed: Double, acceleration: Double) extends VehicleUpdate
object InternalVehicleLocation {
  implicit val latLongReads: Reads[LatLong] =
    ((__ \ "lat").read[Double] and
      (__ \ "lon").read[Double]).apply(new LatLong(_, _))
  implicit val locationReads: Reads[Location] =
    ((__ \ "position").read[LatLong] and
      (__ \ "elevation").read[Double]).apply(new Location(_, _))
  implicit val reads: Reads[InternalVehicleLocation] = Json.reads[InternalVehicleLocation]
}

case class VehicleEvent(vehicle:String, name:String, value:String) extends VehicleUpdate
object VehicleEvent {
  implicit val reads: Reads[VehicleEvent] = Json.reads[VehicleEvent]
}
