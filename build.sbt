name := """Powertrain2"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"
//val kafkaVersion = "0.9.0.1"
val kafkaVersion = "0.10.0.0"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "com.datastax.cassandra" % "cassandra-driver-core" % "3.0.0-rc1",
  "com.datastax.cassandra" % "cassandra-driver-mapping" % "3.0.0-rc1",
  "com.github.davidmoten" % "geo" % "0.7.1",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.11-M3",
  "com.github.jkutner" % "env-keystore" % "0.1.2"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

routesGenerator := InjectedRoutesGenerator