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

case class VehicleLocation(vehicle: String, location: Location, speed: Double, acceleration: Double) extends VehicleUpdate {
  override def toString: String = {
    //when acceleration gets parse
    val speed_str:String = if (speed == 0.0) "0" else speed.toString
    val acc_str = if (acceleration == 0.0)  "0" else acceleration.toString
    //How do we want to format lat long?
    val latLong = s"${location.getLatLong.getLat}"
    val time_period:Timestamp = new Timestamp(System.currentTimeMillis()),
    val collect_time:Timestamp = new Timestamp(System.currentTimeMillis())
    val vl_str = s"$vehicle,$latLong,${location.getElevation},$speed_str,$acc_str,${time_period.getTime},${collect_time.getTime},$tile2"
    println(s"vl_str $vl_str")
    vl_str
  }
}
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

//special version for serialization to kafka -- needs to be merged into above
case class ALT_VehicleLocation(vehicle_id: String, lat_long:String, elevation:String, speed: Double, acceleration: Double, time_period:Timestamp, collect_time:Timestamp, tile2: String, fuel_level:Double =0, mileage:Double =0) {
  override def toString: String = {
    //when acceleration gets parse
    val speed_str:String = if (speed == 0.0) "0" else speed.toString
    val acc_str = if (acceleration == 0.0)  "0" else acceleration.toString
    val vl_str = s"$vehicle_id,$lat_long,$elevation,$speed_str,$acc_str,${time_period.getTime},${collect_time.getTime},$tile2"
    println(s"vl_str $vl_str")
    vl_str
  }
}

case class ALT__VehicleEvent(vehicle_id:String, event_name:String, event_value:String,
                             time_period:Timestamp = new Timestamp(System.currentTimeMillis()),
                             collect_time:Timestamp = new Timestamp(System.currentTimeMillis()))  {
  override def toString:String = {
    s"$vehicle_id,$event_name,$event_value,${time_period.getTime},${collect_time.getTime}"
  }
}