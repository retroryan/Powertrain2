package streaming

import java.sql.Timestamp

case class VehicleLocation(vehicle: String, location: String, elevation: String, speed: Double, acceleration: Double)

case class VehicleEvent(vehicle_id:String, event_name:String, event_value:String, time_period: Timestamp, collect_time: Timestamp)
