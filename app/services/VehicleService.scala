package services

import javax.inject.{Inject, Singleton}

import com.datastax.demo.vehicle.model.Location
import play.api.Logger
import play.api.inject.ApplicationLifecycle

@Singleton
class VehicleService @Inject()(sessionService: SessionService, appLifecycle: ApplicationLifecycle) {


  /*
    def getVehicleMovements(vehicle: String, dateString: String): java.util.List[Vehicle] = {
      dao.getVehicleMovements(vehicle, dateString)
    }

    def getVehiclesByTile(tile: String): java.util.List[Vehicle] = {
      dao.getVehiclesByTile(tile)
    }

    def searchVehiclesByLonLatAndDistance(distance: Int, location: Location): java.util.List[Vehicle] = {
      dao.searchVehiclesByLonLatAndDistance(distance, location)
    }

    def getVehicleLocation(vehicleId: String): Location = {
      dao.getVehiclesLocation(vehicleId)
    }
  */

  def updateVehicleLocation(vehicleId: String, location: Location, speed: Double, acceleration: Double) {
    sessionService.vehicleDao.fold {
      Logger.info("vehicle dao not found")
    } {
      dao => dao.updateVehicle(vehicleId, location, speed, acceleration)
    }

  }


    def addVehicleEvent(vehicleId: String, eventName: String, eventValue: String) {
      sessionService.vehicleDao.fold{
        Logger.info("vehicle dao not found")
      } {
        dao => dao.addVehicleEvent(vehicleId, eventName, eventValue)
      }
    }

}
