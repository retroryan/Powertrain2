package services

import java.sql.Timestamp
import java.util

import com.datastax.demo.vehicle.VehicleLocation
import com.datastax.demo.vehicle.model.Location
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.github.davidmoten.geo.{GeoHash, LatLong}
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

import java.io.Closeable
import java.util.Map


object  StaticKryoInternalSerializer extends com.esotericsoftware.kryo.Serializer[VehicleLocation]   {
  @Override
  def write(kryo:Kryo, output:Output, vehicleLocation: VehicleLocation) =  {
    //write vehicle location information
    output.writeString(vehicleLocation.vehicle_id)
    output.writeString(vehicleLocation.lat_long)
    output.writeString(vehicleLocation.elevation)
    output.writeDouble(vehicleLocation.speed)
    output.writeDouble(vehicleLocation.acceleration)
    output.writeString(vehicleLocation.tile2)
  }

  override def read(kryo: Kryo, input: Input, `type`: Class[VehicleLocation]): VehicleLocation = {
    //read vehicle location information
    val vehicle_id = input.readString()
    val lat_long = input.readString()
    val elevation = input.readString()
    val speed = input.readDouble()
    val acceleration = input.readDouble()

    val tile1: String = GeoHash.encodeHash(0, 4)
    val tile2: String = GeoHash.encodeHash(0, 7)

    val vehicleLocation = VehicleLocation(vehicle_id, lat_long, elevation, speed,acceleration,
      new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), tile2)

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
    val toBytes: Array[Byte] = output.toBytes
    println(s"bytes size: ${toBytes.length}  vehicle id: ${vehicleLocation.vehicle_id}")

    toBytes
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
