package data

import java.sql.Timestamp

import com.github.davidmoten.geo.{GeoHash, LatLong}
import play.api.libs.functional.syntax._
import play.api.libs.json._


sealed trait VehicleUpdate
object VehicleUpdate {
  implicit val reads: Reads[VehicleUpdate] = (__ \ "type").read[String].flatMap {
    case "location" => implicitly[Reads[VehicleLocation]].map(v => v)
    case "event" => implicitly[Reads[VehicleEvent]].map(v => v)
  }
}

case class VehicleLocation(vehicle: String, location: Location, speed: Double, acceleration: Double, elapsed_time: Int) extends VehicleUpdate {
  override def toString: String = {
    //when acceleration gets parse
    val speed_str:String = if (speed == 0.0) "0" else speed.toString
    val acc_str = if (acceleration == 0.0)  "0" else acceleration.toString
    //How do we want to format lat long?
    val latLong = s"${location.getLatLong.getLat}"
    val time_period:Timestamp = new Timestamp(System.currentTimeMillis())
    val collect_time:Timestamp = new Timestamp(System.currentTimeMillis())

    //what this means has been lost from to much copying and pasting
    val tile1: String = GeoHash.encodeHash(location.getLatLong, 4)
    val tile2: String = GeoHash.encodeHash(location.getLatLong, 7)

    val vl_str = s"$vehicle,$latLong,${location.getElevation},$speed_str,$acc_str,${time_period.getTime},${collect_time.getTime},$tile2,${elapsed_time}"
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

case class VehicleEvent(vehicle:String, name:String, value:String, elapsed_time: Int) extends VehicleUpdate {
  override def toString:String = {
    val time_period:Timestamp = new Timestamp(System.currentTimeMillis())
    val collect_time:Timestamp = new Timestamp(System.currentTimeMillis())
    s"$vehicle,$name,$value,${time_period.getTime},${collect_time.getTime},${elapsed_time}"
  }
}
object VehicleEvent {
  implicit val reads: Reads[VehicleEvent] = Json.reads[VehicleEvent]
}