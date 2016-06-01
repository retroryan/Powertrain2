#!/bin/bash 

DIRECTORY='kafka_2.10-0.10.0.0'
if [ ! -d "$DIRECTORY" ]; then
  # Control will enter here if $DIRECTORY exists.
  wget http://apache.mirrors.hoobly.com/kafka/0.10.0.0/kafka_2.10-0.10.0.0.tgz
  tar -xvf kafka_2.10-0.10.0.0.tgz
fi

#start zookeeper
cd $PWD/$DIRECTORY && nohup bin/zookeeper-server-start.sh config/zookeeper.properties 2>&1 1> zookeeper.log &

#start kafka
cd $PWD/$DIRECTORY && nohup bin/kafka-server-start.sh config/server.properties 2>&1 1> kafka.log &

cd $PWD

#create topic
$DIRECTORY/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 5 --topic vehicle_events

#topic info
$DIRECTORY/bin/kafka-topics.sh --list --zookeeper localhost:2181
$DIRECTORY/bin/kafka-topics.sh --describe --topic vehicle_events --zookeeper localhost:2181

#consumer play from the beginning
$DIRECTORY/bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic vehicle_events --from-beginning

#delete topic
#$DIRECTORY/bin/kafka-topics.sh --zookeeper localhost:2181 --delete --topic vehicle_events
