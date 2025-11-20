package io.empowerbits.sightflight.models;

import java.io.Serializable;
import java.util.ArrayList;

public class Obstacle implements Serializable {
    public ArrayList<WaypointAddress> polygon;
    public int height;
    public WaypointAddress center_point;
    public int number;

    public Obstacle(ArrayList<WaypointAddress> polygon, int height, WaypointAddress center_point, int number) {
        this.polygon = polygon;
        this.height = height;
        this.center_point = center_point;
        this.number = number;
    }
}
