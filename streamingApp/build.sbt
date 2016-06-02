name := """PowertrainStreamingApp"""

val globalSettings = Seq(
  version := "0.1",
  scalaVersion := "2.10.5"
)

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.5"
val kafkaVersion = "0.10.0.0"
val sparkVersion = "1.6.0"
val sparkCassandraConnectorVersion = "1.6.0-M2"

lazy val streamingapp = (project in file("src"))
  .settings(name := "src")
  .settings(globalSettings:_*)
  .settings(libraryDependencies ++= streamingDeps)

lazy val streamingDeps = Seq(
  "com.datastax.spark" % "spark-cassandra-connector_2.10" % sparkCassandraConnectorVersion,
  "org.apache.spark"  %% "spark-mllib"           % sparkVersion % "provided",
  "org.apache.spark"  %% "spark-graphx"          % sparkVersion % "provided",
  "org.apache.spark"  %% "spark-sql"             % sparkVersion % "provided",
  "org.apache.spark"  %% "spark-streaming"       % sparkVersion % "provided",
  "org.apache.spark"  %% "spark-streaming-kafka" % sparkVersion % "provided",
  "com.esotericsoftware" % "kryo" % "3.0.3"
)

