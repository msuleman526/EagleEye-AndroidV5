package com.suleman.eagleeye.models;

import java.io.Serializable;

/**
 * WaypointAddress - Represents a waypoint address/location in a flight path
 */
public class WaypointAddress implements Serializable {
    
    public double lat;
    public double lng;
    public WaypointAddress(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

}
