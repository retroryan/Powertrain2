# Use Case

Today business are striving to create a more personalised relationship with their customers, to increase customer loyalty, up-sell services and expand market share through diferentiation. 
In the motor insurance sector telematics insurance (also known as black box insurance) has emerged as an effective way to acheive a more personalised relationship. Drivers can save money 
on their motor insurance and the insurance compaines can reduce risks and so costs. A telematics insurance policy uses an in-car device or mobile app to monitor your driving habits, 
and the insurer can base your premiums on how safely or regularly a person drives. Using this information, they can then provide tailored quotes specific to an individual 
using vehicle sensor information gathered over time.

A number of insurers already use DSE for this very use case and this example illustrates how the components of DSE can be combined to meet the challenges of this type of application. 

Some sample requirements are;
 
The insurer plans to track up to 10 million vehicles in Europe over the next year and the cars will send information on speed and acceleration every 5 seconds to the REST interface 
which will save the data. They expect that there will be peaks of inserts in the morning and late afternoon of approx 50,000 writes a second. 

The insurance company would like to access this service in DSE via REST to;

1. Query all of a vehicle’s movements for a given day
2. Query all the vehicles within a distance of a certain location
3. Provide typical latency examples which would expected at peak times
4. Obtain an estimate of the number of nodes needed to accommodate peak traffic
5. Understand how requests could be buffered at peak times, in case there are too many for DSE to consume

This example does not contain industry specific logic, but illustrates how a scalable data model and architecture can be built for such an application using DSE.

**The driving game incorporated with this example also provides a fun way to generate sensor readings ;)**

## DSE Setup
DataStax Enterprise supplies built-in enterprise analytics and search functionality on Cassandra data that scales and performs in a way that meets the search analytics requirements of modern 
Internet Enterprise applications. Using this analytics and search functionality will allow the volume of operations to grow without a loss in performance. DSE Analytics and Search also 
allows for near real-time analytics, live indexing for improved index throughput and reduced reader latency. More details about live indexing can be found 
here -  http://docs.datastax.com/en/datastax_enterprise/4.8/datastax_enterprise/srch/srchConfIncrIndexThruPut.html - and near real-time analytics 
here - http://docs.datastax.com/en/datastax_enterprise/4.8/datastax_enterprise/spark/sparkWithDSE.html

We will need to start DSE in Analytics and Search mode to allow us to use the analytics and search functionalities that we need on top on Cassandra. To do this see the following 
http://docs.datastax.com/en/datastax_enterprise/4.8/datastax_enterprise/startStop/refDseStandalone.html. In a production cluster these different workloads could be placed in their own logical Data 
Center to minimise the impact each on the other.

For instance DSE can be started in standalone mode as follows;

$DSE_HOME/bin/dse cassandra -k -s -f

## Data Model 

Here is the data model for the sample application. 

```sql
// ------------------------------------
// Vechicle tracking application schema
// ------------------------------------

// Create keyspace for vehicle tracking

CREATE KEYSPACE IF NOT EXISTS vehicle_tracking_app WITH replication = {'class':'SimpleStrategy', 'replication_factor': 1};

// Create table to capture regular vehicle statistics
// The vehicle stats table holds all the individual vehicle sensor readings.
// Using a time period along with a vehicle's Id for the partition key
// limits the size of a partition (to at most 12 * 60 * 24 = 17280 
// entries, if readings are taken every 5s and a partition holds 
// readings for 1 day). Also setting the time period to 1 day 
// ensures that all the readings for a vehicle can be fetched in a single
// request. Adding the collection time as a clustering colum provides 
// ordering (newest first by specifying DESC) and uniqueness.

CREATE TABLE IF NOT EXISTS vehicle_tracking_app.vehicle_stats (
    vehicle_id text,
    time_period timestamp,
    collect_time timestamp,
    acceleration double,
    fuel_level float,
    lat_long text,
    elevation text,
    mileage float,
    speed double,
    tile2 text,
    PRIMARY KEY ((vehicle_id, time_period), collect_time)
) WITH CLUSTERING ORDER BY (collect_time DESC);

// Note: TTL turned off for the time being. When batch roll-up of metrics enabled it 
// can be re-enabled to reduce the amount of raw metrics stored
// AND default_time_to_live = 86400;

// Clear any previously stored entires

TRUNCATE vehicle_tracking_app.vehicle_stats;

// Create table to store historical vehicle statistics
// The history table stores aggregated raw values rolled up periodically
// using a Spark job to stop the stats table growing too large.
// All rolled up metrics for a single vehicle are held together. Using 
// the vehicle Id as the partition Id

CREATE TABLE IF NOT EXISTS vehicle_tracking_app.vehicle_stats_history (
    vehicle_id text,
    time_period timestamp,
    acceleration_avg double,
    acceleration_min int,
    acceleration_max int,
    fuel_level_avg double,
    fuel_level_min float,
    fuel_level_max float,
    mileage_min float,
    mileage_max float,
    speed_avg double,
    speed_min int,
    speed_max int,
    PRIMARY KEY (vehicle_id, time_period)
) WITH CLUSTERING ORDER BY (time_period DESC);

// Clear any previously stored entires

TRUNCATE vehicle_tracking_app.vehicle_stats_history;

// Create table to store car location information
// These ebale the cars within a location to be identified

CREATE TABLE IF NOT EXISTS vehicle_tracking_app.current_location (
  vehicle_id text,
  tile1 text,
  tile2 text,
  lat_long text,
  collect_time timestamp,
  PRIMARY KEY ((vehicle_id))
);

// Clear any previously stored entires

TRUNCATE vehicle_tracking_app.current_location;

// Captures significant vehicle events, braking hard etc.
// Vehicle events store significant events captured while driving

CREATE TABLE IF NOT EXISTS vehicle_tracking_app.vehicle_events (
  vehicle_id text,
  time_period timestamp,
  collect_time timestamp,
  event_name text,
  event_value text,
  PRIMARY KEY ((vehicle_id, time_period), collect_time)
) WITH CLUSTERING ORDER BY (collect_time DESC);

// Clear any previously stored entires

TRUNCATE vehicle_tracking_app.vehicle_events;
```

To create the schema, run the following command in the project root directory;

```
cqlsh -f  src/main/resources/cql/create_schema.cql
```

## Search setup

To provide geo-spatial searching of vehicle locations the vechicle states and location tables are indexed using DSE Search.
The default schema generated by Solr needs to be modifed slightly to highlight that the lat_long_fields are not text but
Solr LatLonType field types.
  
To create the Solr core for identify which vehicles are at a particular location, run the following command; 

```
dsetool create_core vehicle_tracking_app.current_location reindex=true coreOptions=src/main/resources/solr/rt.yaml schema=src/main/resources/solr/geo.xml solrconfig=src/main/resources/solr/solrconfig.xml
```
  	
If you want to also query on where vehicles where at a certain time;

```
dsetool create_core vehicle_tracking_app.vehicle_stats reindex=true coreOptions=src/main/resources/solr/rt.yaml schema=src/main/resources/solr/geo_vehicle.xml solrconfig=src/main/resources/solr/solrconfig.xml	
```

To index the events to make the values searchable;

```
dsetool create_core vehicle_tracking_app.vehicle_events reindex=true coreOptions=src/main/resources/solr/rt.yaml schema=src/main/resources/solr/events.xml solrconfig=src/main/resources/solr/solrconfig.xml	
```

## Running the example application

To setup the example application;

1. Start DSE with Search and Analytics enabled
2. Create the data model
3. Create the Solr cores for the different tables

There are now a number of options for generating a load. 

A continuous stream of updates to the locations of vehicles cane be generated by running a sample client application as follows;

```
mvn clean compile exec:java -Dexec.mainClass="com.datastax.demo.vehicle.Main" -DcontactPoints=localhost
```
  	
To issue REST requests to query or update the application you first need to start a web server as follows;
```
mvn jetty:run
```
Or on a different port with;
```
mvn  -Djetty.port=8081 jetty:run
```

You also need to start the web server to run the driving game. Having started the webserver, you can hit the game UI by going to:

```
http://localhost:8080/vehicle-tracking-app/game
```

## Sample insert

```sql
// Inserting a simple vehicle sensor reading

USE vehicle_tracking_app;

INSERT INTO vehicle_stats (vehicle_id, time_period, collect_time, lat_long,    elevation, tile2, speed, acceleration, fuel_level, mileage)  values ('Seb', '2016-02-11T00:00:00.000Z',  '2016-02-11T12:32:20.000Z', '0.3869000146484375,-2.2514641738287566',  '-0.996',  'gcx8zjq' ,  2.56125,  0, 0.599259, 0.355472);
```

## Sample queries

To find all the sensor readings for of a vehicle on a specific day;

Issue the REST request http://localhost:8080/vehicle-tracking-app/rest/getmovements/{vehicle}/{date} e.g.

```
curl http://localhost:8080/vehicle-tracking-app/rest/getmovements/FT664PQ/2016-03-11
```
  
Or the CQL request
  
```sql
SELECT * FROM vehicle_tracking_app.vehicle_stats 
WHERE vehicle_id = "FT664PQ" AND time_period = '2016-03-11';
```
  
To find all vehicles in a specific location (tile);
 
Issue the REST request http://localhost:8080/vehicle-tracking-app/rest/getvehicles/{tile} e.g.

```
curl http://localhost:8080/vehicle-tracking-app/rest/getvehicles/gcx8zjq
```
  
Or the CQL request

```sql
SELECT * FROM vehicle_tracking_app.current_location 
WHERE solr_query = '{"q": "tile1:gcx8zjq"}' 
LIMIT 1000;
```

To find all vehicles within a certain distance of a latitude and longitude;

Issue the REST request http://localhost:8080/vehicle-tracking-app/rest/search/{lat}/{long}/{distance} e.g.

```
curl http://localhost:8080/vehicle-tracking-app/rest/search/52.53956077140064/-0.20225833920426117/5
```
  	
Or the CQL request

```sql
SELECT * FROM vehicle_tracking_app.current_location 
WHERE solr_query = '{"q": "*:*", "fq": "{!geofilt sfield=lat_long pt=52.53956077140064,-0.20225833920426117 d=5}"}' 
LIMIT 1000;
```	
 	
If you have created the core on the vehicle stats as well, you can run a query that will allow a user to 
search vehicles in a particular region in a particular time, e.g.

```sql
SELECT * FROM vehicle_tracking_app.vehicle_stats 
WHERE solr_query = '{"q": "*:*", "fq": "time_period:[2016-02-11T12:32:00.000Z TO 2016-03-11T12:34:00.000Z] 
AND {!bbox sfield=lat_long pt=51.404970234124800,-.206445841245690 d=1}"}' 
LIMIT 1000;
```

## Further REST interfaces

To update a vehicle pass its vehicle id, location (lon + lat + elevation), speed and acceleration. This will add one if it doesn't already exist

```
curl -X PUT http://localhost:8080/vehicle-tracking-app/rest/updateVehicleLocation/FT664PQ/22.53956077140064/-0.20225833920426117/10.00/55/5
```
  	
To get a vehicles location (lon + lat)

```
curl http://localhost:8080/vehicle-tracking-app/rest/getvehiclelocation/FT664PQ
```
  	
To add a vehicles event

```
curl -X PUT http://localhost:8080/vehicle-tracking-app/rest/addVehicleEvent/FT664PQ/braking/12.5
```

## Analytics operations

The example application uses a Spark job that can be run periodically to roll-up the raw vehicle sensor readings into a daily aggregated view.
This is stored in the vehcile stats history table and also controls the growth of the vehicle states table. A TTL (tiem to live) is set on the 
vehicle stats table to expire entries after a certain number of days and this roll-up job should be run at intervals shorter than the TTL
to ensure readings are not lost.

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
Although not included Spark Streaming combined with Machine Learning algorithms could easily be added to analyse sensor readings and highlight 
driving patterns that could be linked to claims. This could then be used to adjust online quotes in real-time

## Sample latency testing

These latency tests results are from a Cassandra insert stress test. When testing on Azure with 3 and then 6 nodes the scalability was not linear. There was not time to fully investigate the reason but a couple of observations are;

The driver was run on one of the test nodes
It did not seem that the driver node was CPU or network bound

Here are the latency results;

Operating system: Linux
Size: Standard D5 v2 (16 cores, 56 GB memory)

6 nodes (Running with 406 threadCount)
```
Results:
op rate                   : 9122 [insert:9122]
partition rate            : 9122 [insert:9122]
row rate                  : 278431 [insert:278431]
latency mean              : 20.4 [insert:20.4]
latency median            : 5.3 [insert:5.3]
latency 95th percentile   : 114.9 [insert:114.9]
latency 99th percentile   : 209.1 [insert:209.1]
latency 99.9th percentile : 362.1 [insert:362.1]
latency max               : 638.3 [insert:638.3]
```
3 nodes (Running with 271 threadCount)
```
Results:
op rate                   : 7419 [insert:7419]
partition rate            : 7419 [insert:7419]
row rate                  : 226370 [insert:226370]
latency mean              : 21.8 [insert:21.8]
latency median            : 8.3 [insert:8.3]
latency 95th percentile   : 87.7 [insert:87.7]
latency 99th percentile   : 221.4 [insert:221.4]
latency 99.9th percentile : 326.8 [insert:326.8]
latency max               : 598.5 [insert:598.5]
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

The test results (as mentioned above for latency testing) did not show linear scalability

6 nodes, using dc0vm0 as driver node 

Operating system: Linux
Size: Standard D5 v2 (16 cores, 56 GB memory)

```
cassandra-stress user profile=stress.yaml ops\(insert=1\) cl=LOCAL_ONE -node dc0vm0,dc0vm1,dc0vm2,dc0vm3,dc0vm4,dc0vm5

Running with 406 threadCount
Running [insert] with 406 threads until stderr of mean < 0.02
Failed to connect over JMX; not collecting these stats
type,      total ops,    op/s,    pk/s,   row/s,    mean,     med,     .95,     .99,    .999,     max,   time,   stderr, errors,  gc: #,  max ms,  sum ms,  sdv ms,      mb
total,         11739,   12615,   12615,  386489,    18.7,     8.7,    79.9,   162.8,   275.3,   345.7,    0.9,  0.00000,      0,      0,       0,       0,       0,       0
total,         25287,   10269,   10269,  312090,    16.2,     5.6,   114.9,   189.8,   237.8,   322.8,    2.2,  0.07531,      0,      0,       0,       0,       0,       0
total,         36079,    8441,    8441,  256665,    20.1,     5.5,   125.4,   177.6,   409.2,   503.0,    3.5,  0.09644,      0,      0,       0,       0,       0,       0
total,         47442,    9892,    9892,  301315,    19.7,     7.2,   106.1,   166.2,   227.6,   275.6,    4.7,  0.07426,      0,      0,       0,       0,       0,       0
total,         61060,   10973,   10973,  333131,    14.6,     6.6,    47.5,   163.5,   208.8,   236.6,    5.9,  0.05966,      0,      0,       0,       0,       0,       0
total,         75438,    9728,    9728,  296209,    19.2,     5.7,   132.0,   192.5,   296.1,   354.1,    7.4,  0.05138,      0,      0,       0,       0,       0,       0
total,         85656,    9425,    9425,  289351,    34.1,     8.1,   179.3,   322.5,   368.0,   416.1,    8.5,  0.04579,      0,      0,       0,       0,       0,       0
total,         99202,    9931,    9931,  302570,    19.7,     5.9,   119.9,   238.0,   397.2,   443.8,    9.8,  0.04032,      0,      0,       0,       0,       0,       0
total,        111660,    9730,    9730,  297398,    13.5,     4.5,   104.8,   151.7,   290.5,   296.2,   11.1,  0.03624,      0,      0,       0,       0,       0,       0
total,        124250,   10568,   10568,  321176,    22.2,     6.5,   133.4,   259.5,   339.0,   426.0,   12.3,  0.03272,      0,      0,       0,       0,       0,       0
total,        134411,    7031,    7031,  214730,    42.5,     6.5,   254.6,   396.8,   474.1,   638.3,   13.8,  0.04103,      0,      0,       0,       0,       0,       0
total,        146671,    9376,    9376,  285500,    25.0,     7.4,   139.4,   238.4,   417.3,   465.1,   15.1,  0.03799,      0,      0,       0,       0,       0,       0
total,        159863,   10405,   10405,  318531,    16.4,     6.8,   102.4,   146.7,   198.6,   223.2,   16.3,  0.03521,      0,      0,       0,       0,       0,       0
total,        173348,    9661,    9661,  293662,    14.4,     4.9,   108.0,   160.2,   209.9,   316.8,   17.7,  0.03280,      0,      0,       0,       0,       0,       0
total,        184907,    8467,    8467,  259192,    34.4,     9.6,   162.7,   270.9,   373.1,   428.0,   19.1,  0.03217,      0,      0,       0,       0,       0,       0
total,        199201,    9943,    9943,  302655,    13.9,     4.8,   105.1,   163.2,   185.8,   200.5,   20.5,  0.03014,      0,      0,       0,       0,       0,       0
total,        212188,    9517,    9517,  290404,    16.8,     4.8,   114.0,   171.1,   268.5,   373.8,   21.9,  0.02845,      0,      0,       0,       0,       0,       0
total,        222449,    8973,    8973,  273426,    22.8,     5.4,   153.2,   225.1,   317.0,   410.6,   23.0,  0.02735,      0,      0,       0,       0,       0,       0
total,        236783,    9799,    9799,  298308,    15.0,     4.1,   100.5,   152.3,   219.7,   322.0,   24.5,  0.02591,      0,      0,       0,       0,       0,       0
total,        246414,    8900,    8900,  272842,    24.3,     6.1,   143.4,   251.3,   355.7,   389.7,   25.6,  0.02502,      0,      0,       0,       0,       0,       0
total,        258761,    8651,    8651,  264674,    21.6,     5.8,   156.4,   283.9,   333.0,   414.1,   27.0,  0.02443,      0,      0,       0,       0,       0,       0
total,        271444,    8604,    8604,  264124,    22.5,     4.8,   149.1,   244.9,   328.4,   407.2,   28.5,  0.02385,      0,      0,       0,       0,       0,       0
total,        285765,    9094,    9094,  278494,    19.1,     5.1,   119.0,   213.4,   297.7,   408.2,   30.1,  0.02296,      0,      0,       0,       0,       0,       0
total,        296702,    7384,    7384,  225662,    25.0,     5.1,   147.1,   344.5,   490.8,   561.6,   31.5,  0.02409,      0,      0,       0,       0,       0,       0
total,        310595,    9608,    9608,  292660,    24.1,     5.1,   138.0,   237.3,   301.0,   400.2,   33.0,  0.02312,      0,      0,       0,       0,       0,       0
total,        324047,    9409,    9409,  287183,    18.9,     5.1,   114.1,   220.4,   291.7,   479.8,   34.4,  0.02224,      0,      0,       0,       0,       0,       0
total,        338486,    9194,    9194,  280169,    16.9,     4.1,   104.4,   172.7,   253.2,   311.3,   36.0,  0.02147,      0,      0,       0,       0,       0,       0
total,        350386,    7597,    7597,  232424,    24.6,     5.0,   165.4,   311.8,   369.7,   499.7,   37.6,  0.02196,      0,      0,       0,       0,       0,       0
total,        364541,    8245,    8245,  251471,    15.2,     4.3,   119.6,   161.8,   237.4,   257.1,   39.3,  0.02170,      0,      0,       0,       0,       0,       0
total,        376018,    8570,    8570,  261002,    17.4,     4.8,   112.4,   183.8,   223.0,   302.6,   40.6,  0.02123,      0,      0,       0,       0,       0,       0
total,        387857,    7953,    7953,  243388,    24.3,     5.5,   140.7,   262.4,   349.4,   414.0,   42.1,  0.02116,      0,      0,       0,       0,       0,       0
total,        401119,    8553,    8553,  261471,    12.6,     3.7,   110.2,   141.5,   185.1,   193.5,   43.7,  0.02068,      0,      0,       0,       0,       0,       0
total,        412668,    8154,    8154,  251047,    24.0,     4.5,   135.5,   245.8,   305.0,   370.2,   45.1,  0.02040,      0,      0,       0,       0,       0,       0
total,        423526,    8721,    8721,  265824,    18.6,     4.5,   115.7,   204.3,   295.3,   354.3,   46.3,  0.01990,      0,      0,       0,       0,       0,       0
total,        427284,    7131,    7131,  218773,    18.6,     6.0,   130.5,   174.4,   293.1,   304.2,   46.8,  0.02045,      0,      0,       0,       0,       0,       0


Results:
op rate                   : 9122 [insert:9122]
partition rate            : 9122 [insert:9122]
row rate                  : 278431 [insert:278431]
latency mean              : 20.4 [insert:20.4]
latency median            : 5.3 [insert:5.3]
latency 95th percentile   : 114.9 [insert:114.9]
latency 99th percentile   : 209.1 [insert:209.1]
latency 99.9th percentile : 362.1 [insert:362.1]
latency max               : 638.3 [insert:638.3]
Total partitions          : 427284 [insert:427284]
Total errors              : 0 [insert:0]
total gc count            : 0
total gc mb               : 0
total gc time (s)         : 0
avg gc time(ms)           : NaN
stdev gc time(ms)         : 0
Total operation time      : 00:00:46
Improvement over 271 threadCount: 1%
```

3 nodes using dc0vm0 as driver node 

Operating system: Linux
Size: Standard D5 v2 (16 cores, 56 GB memory)

```
cassandra-stress user profile=stress.yaml ops\(insert=1\) cl=LOCAL_ONE -node dc0vm0,dc0vm1,dc0vm2

Running with 271 threadCount
Running [insert] with 271 threads until stderr of mean < 0.02
Failed to connect over JMX; not collecting these stats
type,      total ops,    op/s,    pk/s,   row/s,    mean,     med,     .95,     .99,    .999,     max,   time,   stderr, errors,  gc: #,  max ms,  sum ms,  sdv ms,      mb
total,          8231,    8229,    8229,  251084,    17.6,     7.9,    89.1,   134.8,   164.6,   221.5,    1.0,  0.00000,      0,      0,       0,       0,       0,       0
total,         15996,    7441,    7441,  226721,    23.8,     9.8,   112.6,   242.5,   285.2,   288.1,    2.0,  0.03606,      0,      0,       0,       0,       0,       0
total,         26084,    7942,    7942,  242383,    24.6,     8.6,    95.1,   305.7,   570.2,   598.5,    3.3,  0.06558,      0,      0,       0,       0,       0,       0
total,         36725,    9281,    9281,  282838,    14.9,     7.9,    66.6,   132.0,   165.6,   170.9,    4.5,  0.05204,      0,      0,       0,       0,       0,       0
total,         47714,    7948,    7948,  241582,    25.1,    10.7,   120.5,   257.5,   359.1,   385.7,    5.8,  0.04521,      0,      0,       0,       0,       0,       0
total,         57667,    8705,    8705,  265215,    17.2,     9.1,    62.3,   109.0,   146.6,   177.8,    7.0,  0.03768,      0,      0,       0,       0,       0,       0
total,         64937,    6188,    6188,  187976,    31.8,    11.1,   188.2,   272.5,   387.5,   396.7,    8.2,  0.05110,      0,      0,       0,       0,       0,       0
total,         73921,    7506,    7506,  228418,    25.9,    11.7,    81.8,   322.7,   384.1,   414.6,    9.4,  0.04654,      0,      0,       0,       0,       0,       0
total,         84071,    8378,    8378,  256195,    15.1,     7.0,    75.5,   124.0,   157.8,   160.4,   10.6,  0.04135,      0,      0,       0,       0,       0,       0
total,         91478,    6156,    6156,  189024,    37.4,    13.7,   269.2,   336.5,   381.6,   421.0,   11.8,  0.04572,      0,      0,       0,       0,       0,       0
total,        100955,    8080,    8080,  246731,    19.3,     8.0,    81.1,   193.2,   311.1,   326.0,   12.9,  0.04188,      0,      0,       0,       0,       0,       0
total,        109813,    6561,    6561,  201107,    25.9,    10.1,   102.0,   221.8,   363.1,   376.2,   14.3,  0.03978,      0,      0,       0,       0,       0,       0
total,        118604,    6963,    6963,  212429,    22.5,     6.8,   116.3,   253.1,   378.0,   401.8,   15.6,  0.03862,      0,      0,       0,       0,       0,       0
total,        128254,    7018,    7018,  212462,    25.4,     9.6,   140.2,   207.6,   306.4,   314.2,   16.9,  0.03737,      0,      0,       0,       0,       0,       0
total,        137423,    7657,    7657,  233763,    20.9,     8.6,    86.5,   221.8,   246.5,   315.9,   18.1,  0.03517,      0,      0,       0,       0,       0,       0
total,        145688,    8148,    8148,  249217,    17.7,     8.6,    86.4,   118.0,   145.2,   191.4,   19.1,  0.03295,      0,      0,       0,       0,       0,       0
total,        153721,    6691,    6691,  205209,    26.1,     9.9,   104.2,   309.2,   337.1,   359.1,   20.3,  0.03100,      0,      0,       0,       0,       0,       0
total,        162325,    7376,    7376,  225365,    28.8,    11.5,   136.9,   210.9,   240.4,   264.2,   21.5,  0.02972,      0,      0,       0,       0,       0,       0
total,        171376,    7269,    7269,  220916,    19.3,     6.9,    83.3,   285.8,   364.7,   391.8,   22.8,  0.02870,      0,      0,       0,       0,       0,       0
total,        182232,    8844,    8844,  270616,    16.5,     8.1,    55.0,   114.8,   279.7,   372.1,   24.0,  0.02768,      0,      0,       0,       0,       0,       0
total,        191647,    7185,    7185,  218514,    20.9,     9.1,    79.3,   263.7,   319.4,   333.3,   25.3,  0.02694,      0,      0,       0,       0,       0,       0
total,        201606,    7616,    7616,  231660,    21.8,     9.0,   120.3,   169.6,   196.9,   272.9,   26.6,  0.02585,      0,      0,       0,       0,       0,       0
total,        213294,    9146,    9146,  278985,    12.8,     5.9,    64.2,   105.4,   126.0,   148.9,   27.9,  0.02538,      0,      0,       0,       0,       0,       0
total,        223801,    6745,    6745,  206052,    27.3,    10.1,   136.1,   285.4,   353.7,   371.7,   29.4,  0.02440,      0,      0,       0,       0,       0,       0
total,        232788,    7201,    7201,  218002,    25.0,     8.6,    94.0,   259.6,   284.4,   357.1,   30.7,  0.02342,      0,      0,       0,       0,       0,       0
total,        240426,    5662,    5662,  172493,    35.8,    11.8,   205.4,   289.3,   336.9,   457.2,   32.0,  0.02535,      0,      0,       0,       0,       0,       0
total,        250150,    8143,    8143,  249680,    14.5,     6.7,    75.3,   141.0,   215.2,   225.5,   33.2,  0.02441,      0,      0,       0,       0,       0,       0
total,        259735,    7646,    7646,  234198,    22.3,     8.2,    87.0,   278.7,   323.0,   391.2,   34.5,  0.02359,      0,      0,       0,       0,       0,       0
total,        269383,    6728,    6728,  205758,    23.4,     9.6,   107.4,   235.6,   269.4,   323.9,   35.9,  0.02342,      0,      0,       0,       0,       0,       0
total,        278865,    7266,    7266,  221813,    18.5,     6.8,   101.5,   185.5,   226.9,   244.2,   37.2,  0.02283,      0,      0,       0,       0,       0,       0
total,        285755,    6116,    6116,  187745,    31.8,    10.8,   197.8,   274.1,   312.2,   351.7,   38.3,  0.02329,      0,      0,       0,       0,       0,       0
total,        295710,    7706,    7706,  234405,    16.1,     6.5,    78.3,   163.4,   263.1,   270.7,   39.6,  0.02257,      0,      0,       0,       0,       0,       0
total,        302215,    6897,    6897,  210970,    21.4,     9.9,   101.1,   159.5,   196.5,   216.6,   40.6,  0.02221,      0,      0,       0,       0,       0,       0
total,        309731,    6268,    6268,  190501,    32.2,    11.5,   211.9,   279.6,   358.5,   374.4,   41.8,  0.02242,      0,      0,       0,       0,       0,       0
total,        321799,    8559,    8559,  260511,    14.0,     7.2,    61.7,   119.3,   188.3,   218.7,   43.2,  0.02192,      0,      0,       0,       0,       0,       0
total,        331479,    7748,    7748,  235896,    17.7,     7.0,    93.9,   175.9,   216.9,   260.4,   44.4,  0.02131,      0,      0,       0,       0,       0,       0
total,        340788,    6618,    6618,  202519,    22.5,     6.8,   104.3,   294.3,   363.5,   404.7,   45.8,  0.02083,      0,      0,       0,       0,       0,       0
total,        349956,    6980,    6980,  213688,    20.6,     6.3,    94.8,   275.8,   312.7,   352.4,   47.2,  0.02029,      0,      0,       0,       0,       0,       0
total,        360528,    7671,    7671,  234807,    19.0,     5.7,    89.6,   220.1,   311.5,   359.8,   48.5,  0.01987,      0,      0,       0,       0,       0,       0
total,        362671,    6292,    6292,  190615,    17.8,     8.6,    79.2,   113.2,   144.9,   147.0,   48.9,  0.02006,      0,      0,       0,       0,       0,       0


Results:
op rate                   : 7419 [insert:7419]
partition rate            : 7419 [insert:7419]
row rate                  : 226370 [insert:226370]
latency mean              : 21.8 [insert:21.8]
latency median            : 8.3 [insert:8.3]
latency 95th percentile   : 87.7 [insert:87.7]
latency 99th percentile   : 221.4 [insert:221.4]
latency 99.9th percentile : 326.8 [insert:326.8]
latency max               : 598.5 [insert:598.5]
Total partitions          : 362671 [insert:362671]
Total errors              : 0 [insert:0]
total gc count            : 0
total gc mb               : 0
total gc time (s)         : 0
avg gc time(ms)           : NaN
stdev gc time(ms)         : 0
Total operation time      : 00:00:48
Improvement over 181 threadCount: 3%
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


### Other information

To buffer requests for DSE if there are peaks in demand a technology like Kafka could be used to store and forward requests.

