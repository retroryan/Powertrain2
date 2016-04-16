package com.datastax.demo.vehicle;

import com.datastax.demo.utils.AsyncWriterWrapper;
import com.datastax.demo.vehicle.model.Location;
import com.datastax.demo.vehicle.model.Vehicle;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Map.Entry;

public class VehicleDao {

    private static final Logger logger = LoggerFactory.getLogger(VehicleDao.class);

    private static final String keyspaceName = "vehicle_tracking_app";
    private static final String vehicleTable = keyspaceName + ".vehicle_stats";
    private static final String INSERT_INTO_VEHICLE = "insert into " + vehicleTable + " (vehicle_id, time_period, collect_time, lat_long, elevation, tile2, speed, acceleration, fuel_level, mileage) values (?,?,?,?,?,?,?,?,?,?);";
    private static final String QUERY_BY_VEHICLE = "select * from " + vehicleTable + " where vehicle_id = ? and time_period = ?";
    private static final String vehicleEventsTable = keyspaceName + ".vehicle_events";
    private static final String INSERT_INTO_VEHICLE_EVENT = "insert into " + vehicleEventsTable + " (vehicle_id, time_period, collect_time, event_name, event_value) values (?,?,?,?,?);";
    private static final String currentLocationTable = keyspaceName + ".current_location";
    private static final String INSERT_INTO_CURRENTLOCATION = "insert into " + currentLocationTable + "(vehicle_id, tile1, tile2, lat_long, collect_time) values (?,?,?,?,?)";
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    // Session and PreparedStatement are both thread-safe and one Session per application is usually fine
    private Session session;
    private PreparedStatement insertVehicle;
    private PreparedStatement insertVehicleEvent;
    private PreparedStatement insertCurrentLocation;
    private PreparedStatement queryVehicle;

    public VehicleDao(Session session) {
        this.session = session;

        this.insertVehicle = session.prepare(INSERT_INTO_VEHICLE);
        this.insertVehicleEvent = session.prepare(INSERT_INTO_VEHICLE_EVENT);
        this.insertCurrentLocation = session.prepare(INSERT_INTO_CURRENTLOCATION);

        this.queryVehicle = session.prepare(QUERY_BY_VEHICLE);
        logger.debug("Creating new DAO in thread: " + Thread.currentThread());
    }

    public void insertVehicleLocation(Map<String, Location> newLocations) {
        long day = 24 * 60 * 60 * 1000;
        Date today = new Date((System.currentTimeMillis() / day) * day);
        AsyncWriterWrapper wrapper = new AsyncWriterWrapper();
        Random random = new Random();
        Set<Entry<String, Location>> entrySet = newLocations.entrySet();

        // Update time for reading
        LocalDateTime now = LocalDateTime.now();
        now = now.plusSeconds(10);
        Instant instant = now.atZone(ZoneId.systemDefault()).toInstant();
        Date nowDate = Date.from(instant);

        for (Entry<String, Location> entry : entrySet) {

            String tile1 = GeoHash.encodeHash(entry.getValue().getLatLong(), 4);
            String tile2 = GeoHash.encodeHash(entry.getValue().getLatLong(), 7);

            double speed = Math.abs(random.nextInt() % 100);
            float fuelLevel = Math.abs(random.nextFloat() % 50);
            float mileage = Math.abs(random.nextFloat() % 50000);
            double acceleration = Math.abs(random.nextInt() % 100);

            wrapper.addStatement(insertVehicle.bind(entry.getKey(), today, nowDate,
                    entry.getValue().getLatLong().getLat() + "," + entry.getValue().getLatLong().getLon(), Double.toString(entry.getValue().getElevation()), tile2, speed, acceleration, fuelLevel, mileage));

            wrapper.addStatement(insertCurrentLocation.bind(entry.getKey(), tile1, tile2,
                    entry.getValue().getLatLong().getLat() + "," + entry.getValue().getLatLong().getLon(), nowDate));
        }
        wrapper.executeAsync(this.session);
    }

    public void updateVehicle(String vehicleId, Location location, double speed, double acceleration) {
        long day = 24 * 60 * 60 * 1000;
        Date today = new Date((System.currentTimeMillis() / day) * day);
        AsyncWriterWrapper wrapper = new AsyncWriterWrapper();
        Random random = new Random();

        // Update time for reading
        LocalDateTime now = LocalDateTime.now();
        now = now.plusSeconds(10);
        Instant instant = now.atZone(ZoneId.systemDefault()).toInstant();
        Date nowDate = Date.from(instant);

        String tile1 = GeoHash.encodeHash(location.getLatLong(), 4);
        String tile2 = GeoHash.encodeHash(location.getLatLong(), 7);

        float fuelLevel = Math.abs(random.nextFloat() % 50);
        float mileage = Math.abs(random.nextFloat() % 50000);

        wrapper.addStatement(insertVehicle.bind(vehicleId, today, nowDate,
                location.getLatLong().getLat() + "," + location.getLatLong().getLon(),
                Double.toString(location.getElevation()), tile2, speed,
                acceleration, fuelLevel, mileage));

        wrapper.addStatement(insertCurrentLocation.bind(vehicleId, tile1, tile2,
                location.getLatLong().getLat() + "," + location.getLatLong().getLon(), nowDate));

        wrapper.executeAsync(this.session);
    }

    public void addVehicleEvent(String vehicleId, String eventName, String eventValue) {
        long day = 24 * 60 * 60 * 1000;
        Date today = new Date((System.currentTimeMillis() / day) * day);
        AsyncWriterWrapper wrapper = new AsyncWriterWrapper();
        Random random = new Random();

        // Update time for reading
        LocalDateTime now = LocalDateTime.now();
        now = now.plusSeconds(10);
        Instant instant = now.atZone(ZoneId.systemDefault()).toInstant();
        Date nowDate = Date.from(instant);

        wrapper.addStatement(insertVehicleEvent.bind(vehicleId, today, nowDate, eventName, eventValue));
        wrapper.executeAsync(this.session);
    }

    public List<Vehicle> getVehicleMovements(String vehicleId, String dateString) {
        ResultSet resultSet = null;

        try {
            // Example date time is 2001-07-04T12:08:56.235-0700
            resultSet = session.execute(this.queryVehicle.bind(vehicleId, dateFormatter.parse(dateString + "T00:00:00.000-0000")));
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        List<Vehicle> vehicleMovements = new ArrayList<Vehicle>();
        List<Row> all = resultSet.all();

        for (Row row : all) {
            Date timePeriod = row.getTimestamp("time_period");
            Date collectTime = row.getTimestamp("collect_time");
            Integer acceleration = row.getInt("acceleration");
            Float fuelLevel = row.getFloat("fuel_level");
            Float mileage = row.getFloat("mileage");
            Integer speed = row.getInt("speed");
            String lat_long = row.getString("lat_long");
            String elevation = row.getString("elevation");
            String lastTile = row.getString("tile2");
            Double lat = Double.parseDouble(lat_long.substring(0, lat_long.lastIndexOf(",")));
            Double lng = Double.parseDouble(lat_long.substring(lat_long.lastIndexOf(",") + 1));
            Double el = Double.parseDouble(elevation);

            Vehicle vehicle = new Vehicle(vehicleId, timePeriod, collectTime, acceleration, fuelLevel, new Location(new LatLong(lat, lng), el), mileage, speed, lastTile, "");
            vehicleMovements.add(vehicle);
        }

        return vehicleMovements;
    }

    public List<Vehicle> searchVehiclesByLonLatAndDistance(int distance, Location location) {

        String cql = "select * from " + currentLocationTable
                + " where solr_query = '{\"q\": \"*:*\", \"fq\": \"{!geofilt sfield=lat_long pt="
                + location.getLatLong().getLat() + "," + location.getLatLong().getLon() + " d=" + distance + "}\"}'  limit 1000";
        ResultSet resultSet = session.execute(cql);

        List<Vehicle> vehicleMovements = new ArrayList<Vehicle>();
        List<Row> all = resultSet.all();

        for (Row row : all) {
            vehicleMovements.add(getVehicleFromLocation(row.getString("vehicle_id"), row));
        }

        return vehicleMovements;
    }

    public List<Vehicle> getVehiclesByTile(String tile) {
        String cql = "select * from " + currentLocationTable + " where solr_query = '{\"q\": \"tile1: " + tile + "\"}' limit 1000";
        ResultSet resultSet = session.execute(cql);

        List<Vehicle> vehicleMovements = new ArrayList<Vehicle>();
        List<Row> all = resultSet.all();

        for (Row row : all) {
            vehicleMovements.add(getVehicleFromLocation(row.getString("vehicle_id"), row));
        }

        return vehicleMovements;
    }

    public Location getVehiclesLocation(String vehicleId) {
        String cql = "select lat_long, height from " + currentLocationTable + " where vehicle_id = '" + vehicleId + "'";
        ResultSet resultSet = session.execute(cql);
        List<Row> all = resultSet.all();
        int rows = all.size();

        switch (rows) {
            case 0:
                return null;
            case 1:
                String elevation = all.get(0).getString("elevation");
                String lat_long = all.get(0).getString("lat_long");
                Double lat = Double.parseDouble(lat_long.substring(0, lat_long.lastIndexOf(",")));
                Double lng = Double.parseDouble(lat_long.substring(lat_long.lastIndexOf(",") + 1));
                Double el = Double.parseDouble(elevation);
                return new Location(new LatLong(lng, lat), el);
            default:
                throw new RuntimeException(String.format("Not expecting %d rows\n", rows));
        }
    }

    private Vehicle getVehicleFromLocation(String vehicleId, Row row) {
        Date collectTime = row.getTimestamp("collect_time");
        String lat_long = row.getString("lat_long");
        String elevation = row.getString("elevation");
        String tile1 = row.getString("tile1");
        Double lat = Double.parseDouble(lat_long.substring(0, lat_long.lastIndexOf(",")));
        Double lng = Double.parseDouble(lat_long.substring(lat_long.lastIndexOf(",") + 1));
        Double el = Double.parseDouble(elevation);

        return new Vehicle(vehicleId, null, collectTime, 0, 0.0f, new Location(new LatLong(lat, lng), el), 0.0f, 0, tile1, "");
    }

}
