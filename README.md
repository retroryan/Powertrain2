#Powertrain

##Kafka setup

Follow the setup instructions in KAFKA SETUP 

## DSE / Cassandra Setup
DSE must be configured with spark and analytics enabled.

$DSE_HOME/bin/dse cassandra -k -s -f

Alternatively you can run open source Spark and Cassandra separately

## Schema

To create the schema, run the following command in the project root directory;

```
cqlsh -f  resources/cql/create_schema.cql
```

##App

To run the app install sbt

http://www.scala-sbt.org/0.13/docs/Installing-sbt-on-Linux.html

and run `sbt run`

The app will be serving on port 9000

##App Production deployment

on your local development environment:
sbt dist

first deployment
    copy entire powertrain2-1.0-SNAPSHOT.zip to server

second deployment do the following:
    cd target/universal
    unzip powertrain2-1.0-SNAPSHOT.zip
    copy the following to the server:
        powertrain2.powertrain2-1.0-SNAPSHOT-assets.jar and powertrain2.powertrain2-1.0-SNAPSHOT-sans-externalized.jar

on the server:
nohup bin/powertrain2 -J-Xms1G -J-Xmx5G -J-server 2>&1 1> powertrain2.log &

##Spark Streaming

The spark streaming app ingests data off of kafka, processes it in micro
batches, and writes to cassandra

This app lives in the PowertrainStreaming repo

## Search setup

Run:

    resources/solr/solr_commands.txt


```
http://localhost:8080/vehicle-tracking-app/game
```

## Analytics operations

The example application uses a Spark job that can be run periodically to roll-up the raw vehicle sensor readings into a daily aggregated view.

To run the Spark roll-up job;

1. Ensure you are in the 'Spark' folder
2. Build the application using command :
  ```
  sbt package
  ```
3. Submit the application using command :
  ```
  dse spark-submit target/scala-2.10/powertrain_2.10-1.0.jar
  ```

## Stress testing and capacity planning
```
keyspace: vehicle_tracking_app
table: vehicle_stats

columnspec:
  - name: vehicle_id
    size: fixed(10)               # Vehicle Id's quite short
    population: uniform(1..100M)  # The range of unique values to select for the field (default is 100Billion)

  - name: time_period

  - name: collect_time
    cluster: gaussian(100..6000) # Number of collections per day, 1 every 5s for a day. Example trips could be 5 min or 5 hrs

  - name: acceleration

  - name: fuel_level

  - name: lat_long
    size: fixed(20)

  - name: mileage

  - name: speed

  - name: tile2
    size: fixed(20)

insert:
  partitions: fixed(1)      # number of unique partitions to update in a single operation
                                  # if batchcount > 1, multiple batches will be used but all partitions will
                                  # occur in all batches (unless they finish early); only the row counts will vary
  batchtype: UNLOGGED             # type of batch to use
  select: fixed(1)/100          # uniform chance any single generated CQL row will be visited in a partition;
                                  # generated for each partition independently, each time we visit it

queries:
   simple1:
      cql: select * from vehicle_tracking_app.vehicle_stats where vehicle_id = ? and time_period = ? LIMIT 100
      fields: samerow             # samerow or multirow (select arguments from the same row, or randomly from all rows in the partition)
   range1:
      cql: select * from vehicle_tracking_app.vehicle_stats where vehicle_id = ? and time_period = ? and collect_time >= ? LIMIT 100
      fields: multirow            # samerow or multirow (select arguments from the same row, or randomly from all rows in the partition)

```
Examples of running the stress tool are (please change node0 to whatever your contact point may be)

An insert example is;
```
cassandra-stress user profile=stress.yaml ops\(insert=1\) cl=LOCAL_ONE n=100000 -rate threads=4 -node node0 
```
A query example is;
```
cassandra-stress user profile=stress.yaml ops\(simple1=1\) cl=LOCAL_ONE n=100000 -rate threads=4 -node node0 
```

You can read more about stress testing a data model here 
http://www.datastax.com/dev/blog/improved-cassandra-2-1-stress-tool-benchmark-any-schema and here 
http://docs.datastax.com/en/cassandra/2.1/cassandra/tools/toolsCStress_t.html

##Silk (DSE Search Dashboarding UI)
Silk is lucidwork's port of Kibana for Solr. Because I had to make some modifications to the Silk source for it to work with DSE Search and because it has some node.js dependencies, the easiest way to run it is to use this neatly packaged docker container. You can run silk ontop of this demo to visualize the vehicle data.

###clone the repo:
    git clone https://github.com/phact/docker-silk-dse

Set up keyspace and table:

    wget https://raw.githubusercontent.com/phact/silk/dev/silkconfig/conf/schema.cql
    
    cqlsh -f schema.cql
    
Set up the DSE Search core before kicking off the container:
    
    chmod +x create_core.sh
    ./create_core.sh

###Docker setup for OSX:

```
#setup
docker-machine start default
eval $(docker-machine env default)
#a bit of cleanup
docker rm -f $(docker ps -aq)
docker rmi -f $(docker images -aq)
#build
docker build -t silk-image .
docker run --net=host -d -p 0.0.0.0:5601:5601  --name silk silk-image
#or for debug
docker run -it --net=host -p 0.0.0.0:5601:5601  --name silk silk-image
docker-machine ip default
```

###Docker setup for linux:
install docker https://docs.docker.com/engine/installation/linux

add your user to the docker group
    sudo gpasswd -a ${USER} docker

and refresh 
   newgrp docker 


````
#start docker
service docker start
docker build -t silk-image .
docker run -d -p 0.0.0.0:5601:5601  --name silk silk-image

#or if it's not working run without detaching to troubeshoot
docker run --net=host -p 0.0.0.0:5601:5601  --name silk silk-image
````
