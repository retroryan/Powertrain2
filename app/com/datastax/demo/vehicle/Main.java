package com.datastax.demo.vehicle;

import com.datastax.demo.vehicle.model.Location;
import com.datastax.demo.vehicle.model.Vehicle;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.github.davidmoten.geo.LatLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.KryoInternalSerializer;

import java.util.HashMap;
import java.util.Map;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);
    private static int TOTAL_VEHICLES = 10000;
    private static int BATCH = 10000;
    private static Map<String, Location> vehicleLocations = new HashMap<>();

    private VehicleDao dao;

    public Main() {
        String contactPointsStr = System.getProperty("contactPoints", "127.0.0.1");
        this.dao = new VehicleDao(new OldSessionService(contactPointsStr).getSession());

        logger.info("Creating Locations");
        createStartLocations();

        // TODO move this into a background thread in the app
        while (true) {
            //logger.info("Updating Locations");
            updateLocations();
            sleep(5);
        }
    }

    public static void main(String[] args) {
        new Main();
    }

    private void updateLocations() {
        Map<String, Location> newLocations = new HashMap<String, Location>();

        for (int i = 0; i < BATCH; i++) {
            String random = new Double(Math.random() * TOTAL_VEHICLES).intValue() + 1 + "";

            Location location = vehicleLocations.get(random);
            Location update = update(location);
            vehicleLocations.put(random, update);
            newLocations.put(random, update);
        }

        dao.insertVehicleLocation(newLocations);
    }



    private Location update(Location location) {
        double lon = location.getLatLong().getLon();
        double lat = location.getLatLong().getLat();
        double elevation = location.getElevation();

        if (Math.random() < .1)
            return location;

        if (Math.random() < .5)
            lon += .0001d;
        else
            lon -= .0001d;

        if (Math.random() < .5)
            lat += .0001d;
        else
            lat -= .0001d;

        if (Math.random() < .5)
            elevation += .0001d;
        else
            elevation -= .0001d;

        return new Location(new LatLong(lat, lon), elevation);
    }

    private void createStartLocations() {

        for (int i = 0; i < TOTAL_VEHICLES; i++) {
            double lat = getRandomLat();
            double lon = getRandomLng();
            double el = getRandomElevation();

            this.vehicleLocations.put("" + (i + 1), new Location(new LatLong(lat, lon), el));
        }
        dao.insertVehicleLocation(vehicleLocations);
    }

    private double getRandomElevation() {
        return (Math.random() < .5) ? Math.random() : -1 * Math.random();
    }

    /**
     * Between 1 and -1
     *
     * @return
     */
    private double getRandomLng() {
        return (Math.random() < .5) ? Math.random() : -1 * Math.random();
    }

    /**
     * Between 50 and 55
     */
    private double getRandomLat() {

        return Math.random() * 5 + 50;
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
