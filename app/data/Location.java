package data;

import com.github.davidmoten.geo.LatLong;

/**
 * Created by davidfelcey on 05/04/2016.
 */
public class Location {
    private LatLong latLong;
    private Double elevation;

    public Location(LatLong latLong, Double elevation) {
        this.latLong = latLong;
        this.elevation = elevation;
    }

    public LatLong getLatLong() {
        return latLong;
    }

    public void setLatLong(LatLong latLong) {
        this.latLong = latLong;
    }

    public Double getElevation() {
        return elevation;
    }

    public void setElevation(Double elevation) {
        this.elevation = elevation;
    }

    @Override
    public String toString() {
        return "Location{" +
                "latLong=" + latLong +
                ", elevation=" + elevation +
                '}';
    }
}
