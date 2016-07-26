dse spark-submit --packages org.apache.spark:spark-streaming-kafka_2.10:1.6.2 --class streaming.StreamVehicleData target/scala-2.10/streaming-vehicle-app_2.10-1.0-SNAPSHOT.jar localhost:9092 ratings true

