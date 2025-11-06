package com.empowerbits.dronifyit.models;

import java.io.Serializable;

//import dji.common.mission.waypoint.WaypointTurnMode;

public class WaypointSetting implements Serializable {

    public String name;
    public Double latitude;
    public Double longitude;
    public Double altitude = 10.0;
    public Double waypointSpeed = 5.0;
    public Double gimbalPitchAngle = 1.0;
}