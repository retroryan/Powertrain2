package streaming

import java.sql.Timestamp

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

object  VehicleLocation {
  def apply(rawVehicleStr:String):VehicleLocation = {
    val data = rawVehicleStr.split(",")
    VehicleLocation(data(0), data(1), data(2), data(3).toDouble, data(4).toDouble, new Timestamp(data(5).toLong),new Timestamp(data(6).toLong), data(7))
  }
}
case class VehicleEvent(vehicle_id:String, event_name:String, event_value:String, time_period: Timestamp, collect_time: Timestamp)

object VehicleEvent {

}