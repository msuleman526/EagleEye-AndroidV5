package com.suleman.eagleeye.models;

import java.io.Serializable;

//import dji.common.mission.waypoint.WaypointTurnMode;

public class WaypointSetting implements Serializable {

    public String name;
    public Double latitude;
    public Double longitude;
    public Double altitude = 10.0;
    public Double heading = 0.0;
    public Integer actionRepeatTimes = 1;
    public Integer actionTimeoutInSeconds = 60;
    public Integer cornerRadiusInMeters = 5;

    //public Integer turnMode = WaypointTurnMode.CLOCKWISE.value();

    public Integer gimbalPitch = 0;
}