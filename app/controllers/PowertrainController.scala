package controllers

import javax.inject.{Inject, Singleton}

import com.datastax.demo.vehicle.model.Location
import com.github.davidmoten.geo.LatLong
import play.api.mvc.{Action, Controller}
import services.VehicleService

@Singleton
class PowertrainController @Inject() (vehicleService:VehicleService) extends Controller {


  def updateVehicleLocation(vehicle:String,lon:String, lat:String, elevation:String, speed:String, acceleration:String) = Action {
    val location: Location = new Location(new LatLong(lat.toDouble, lon.toDouble), elevation.toDouble)
    vehicleService.updateVehicleLocation(vehicle, location, speed.toDouble, acceleration.toDouble)
    Ok(s"updateVehicleLocation: $vehicle")
  }

  def addVehicleEvent(vehicle:String, name:String, value:String) = Action {
    vehicleService.addVehicleEvent(vehicle,name,value)
    Ok(s"addVehicleEvent: $vehicle name:$name value:$value ")
  }

}
