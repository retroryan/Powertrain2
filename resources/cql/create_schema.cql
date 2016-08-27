CREATE KEYSPACE vehicle_tracking_app WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE vehicle_tracking_app.vehicle_stats2 (
    vehicle_id text,
    time_period timestamp,
    collect_time timestamp,
    acceleration double,
    elevation text,
    fuel_level float,
    lat_long text,
    mileage float,
    solr_query text,
    speed double,
    tile2 text,
    PRIMARY KEY ((vehicle_id, time_period), collect_time)
) WITH CLUSTERING ORDER BY (collect_time DESC);

CREATE TABLE vehicle_tracking_app.vehicle_stats (
    vehicle_id text,
    time_period timestamp,
    collect_time timestamp,
    acceleration double,
    elevation text,
    fuel_level float,
    lat_long text,
    mileage float,
    solr_query text,
    speed double,
    tile2 text,
    elapsed_time int,
    PRIMARY KEY ((vehicle_id, time_period), collect_time)
) WITH CLUSTERING ORDER BY (collect_time DESC);

CREATE TABLE vehicle_tracking_app.vehicle_events (
    vehicle_id text,
    time_period timestamp,
    collect_time timestamp,
    event_name text,
    event_value text,
    solr_query text,
    elapsed_time int,
    PRIMARY KEY ((vehicle_id, time_period), collect_time)
) WITH CLUSTERING ORDER BY (collect_time DESC);

CREATE TABLE vehicle_tracking_app.vehicle_stats_history (
    vehicle_id text,
    time_period timestamp,
    acceleration_avg double,
    acceleration_max int,
    acceleration_min int,
    fuel_level_avg double,
    fuel_level_max float,
    fuel_level_min float,
    mileage_max float,
    mileage_min float,
    speed_avg double,
    speed_max int,
    speed_min int,
    PRIMARY KEY (vehicle_id, time_period)
) WITH CLUSTERING ORDER BY (time_period DESC);

CREATE TABLE vehicle_tracking_app.current_location (
    vehicle_id text PRIMARY KEY,
    collect_time timestamp,
    lat_long text,
    solr_query text,
    tile1 text,
    tile2 text
);
