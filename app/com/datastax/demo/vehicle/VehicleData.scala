package com.datastax.demo.vehicle

import java.sql.Timestamp

import com.datastax.demo.vehicle.model.Location
import com.github.davidmoten.geo.LatLong
import play.api.libs.json._
import play.api.libs.functional.syntax._


//TODO FIX THIS
//for now this is the version of VehicleLocation that is used for kafka seralization
case class VehicleLocation(vehicle_id: String, lat_long:String, elevation:String, speed: Double, acceleration: Double, time_period:Timestamp, collect_time:Timestamp, tile2: String) {
  override def toString: String = {
    //when acceleration gets parse
    val speed_str:String = if (speed == 0.0) "0" else speed.toString
    val acc_str = if (acceleration == 0.0)  "0" else acceleration.toString
    val vl_str = s"$vehicle_id,$lat_long,$elevation,$speed_str,$acc_str,${time_period.getTime},${collect_time.getTime},$tile2"
    println(s"vl_str $vl_str")
    vl_str
  }
}

case class NOTVehicleEvent(vehicle_id:String, event_name:String, event_value:String,
                        time_period:Timestamp = new Timestamp(System.currentTimeMillis()),
                        collect_time:Timestamp = new Timestamp(System.currentTimeMillis())) extends VehicleUpdate {
  override def toString:String = {
    s"$vehicle_id,$event_name,$event_value,${time_period.getTime},${collect_time.getTime}"
  }
}


sealed trait VehicleUpdate
object VehicleUpdate {
  implicit val reads: Reads[VehicleUpdate] = (__ \ "type").read[String].flatMap {
    case "location" => implicitly[Reads[InternalVehicleLocation]].map(v => v)
    case "event" => implicitly[Reads[InternalVehicleEvent]].map(v => v)
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

case class InternalVehicleEvent(vehicle_id:String, event_name:String, event_value:String) extends VehicleUpdate

object InternalVehicleEvent {
  implicit val reads: Reads[InternalVehicleEvent] = Json.reads[InternalVehicleEvent]
}
