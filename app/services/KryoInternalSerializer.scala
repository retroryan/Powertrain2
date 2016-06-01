package services

import java.util

import com.datastax.demo.vehicle.VehicleLocation
import com.datastax.demo.vehicle.model.Location
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.github.davidmoten.geo.LatLong
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

import java.io.Closeable
import java.util.Map


object  StaticKryoInternalSerializer extends com.esotericsoftware.kryo.Serializer[VehicleLocation]   {
  @Override
  def write(kryo:Kryo, output:Output, vehicleLocation: VehicleLocation) =  {
    //write Lat and Long
    val latLong: LatLong = vehicleLocation.location.getLatLong
    output.writeDouble(latLong.getLat)
    output.writeDouble(latLong.getLon)

    //write elevation
    output.writeDouble(vehicleLocation.location.getElevation)

    //write vehicle location information
    output.writeString(vehicleLocation.vehicle)
    output.writeDouble(vehicleLocation.speed)
    output.writeDouble(vehicleLocation.acceleration)
  }

  override def read(kryo: Kryo, input: Input, `type`: Class[VehicleLocation]): VehicleLocation = {
    //read Lat and Long
    val lat = input.readDouble()
    val long = input.readDouble()
    val latLong = new LatLong(lat,long)

    //read elevation
    val elevation = input.readDouble()
    val location = new Location(latLong,elevation)

    //read vehicle location information
    val vehicle = input.readString()
    val speed = input.readDouble()
    val acceleration = input.readDouble()

    val vehicleLocation = VehicleLocation(vehicle, location, speed,acceleration)

    vehicleLocation
  }
}

class KryoInternalSerializer extends Closeable with AutoCloseable with Serializer[VehicleLocation]  with Deserializer[VehicleLocation] {

  private val kryos: ThreadLocal[Kryo] = new ThreadLocal[Kryo]() {
    protected override def initialValue: Kryo = {
      val kryo: Kryo = new Kryo
      kryo.addDefaultSerializer(classOf[VehicleLocation], StaticKryoInternalSerializer)
      kryo
    }
  }

  override def configure(map: util.Map[String, _], b: Boolean): Unit = {}

  override def serialize(s: String, vehicleLocation: VehicleLocation): Array[Byte] = {
    val output = new ByteBufferOutput(100)
    kryos.get().writeObject(output, vehicleLocation)
    output.toBytes
  }

  override def close(): Unit = {}

  override def deserialize(s: String, bytes: Array[Byte]): VehicleLocation = {
    try {
      kryos.get().readObject(new ByteBufferInput(bytes), classOf[VehicleLocation])
    }
    catch  {
      case exc:Exception => throw new IllegalArgumentException("Error reading bytes", exc);
    }
  }
}
