package streaming

case class VehicleLocation(vehicle: String, location: String, elevation: String, speed: Double, acceleration: Double)

case class VehicleEvent(vehicle:String, name:String, value:String)
