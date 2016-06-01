
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
