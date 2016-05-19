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
