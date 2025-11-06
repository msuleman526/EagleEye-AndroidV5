package com.empowerbits.dronifyit.models;

import java.io.Serializable;

/**
 * WaypointAddress - Represents a waypoint address/location in a flight path
 */
public class WaypointAddress implements Serializable {
    
    public double latitude;
    public double longitude;
    public double altitude;

    public WaypointAddress() {

    }

}
