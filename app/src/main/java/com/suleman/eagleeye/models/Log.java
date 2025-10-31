package com.suleman.eagleeye.models;

import android.os.Parcel;
import android.os.Parcelable;

public class Log implements Parcelable {

    public String startTime;
    public String estimateEndTime;
    public String endTime;
    public String droneName;
    public int numberOfWaypoints;
    public String numberOfObstcles;
    public int waypointHeight;
    public int horizontalPathHeight;
    public int houseHeight;
    public int maxObstcleHeight;
    public int droneSpeed;
    public int flightStartDroneBettery;
    public String finishAction;
    public String headingMode;
    public String rotateGimblePitch;
    public String goToFirstWaypointMode;
    public int lastWaypoint;

    public Log() {}

    // ✅ Constructor with estimatedEndTime
    public Log(
            String startTime,
            String estimateEndTime,
            String droneName,
            int numberOfWaypoints,
            String numberOfObstcles,
            int waypointHeight,
            int horizontalPathHeight,
            int houseHeight,
            int maxObstcleHeight,
            int droneSpeed,
            int flightStartDroneBettery,
            String finishAction,
            String headingMode,
            String rotateGimblePitch,
            String goToFirstWaypointMode,
            int lastWaypoint
    ) {
        this.startTime = startTime;
        this.estimateEndTime = estimateEndTime;
        this.droneName = droneName;
        this.numberOfWaypoints = numberOfWaypoints;
        this.numberOfObstcles = numberOfObstcles;
        this.waypointHeight = waypointHeight;
        this.horizontalPathHeight = horizontalPathHeight;
        this.houseHeight = houseHeight;
        this.maxObstcleHeight = maxObstcleHeight;
        this.droneSpeed = droneSpeed;
        this.flightStartDroneBettery = flightStartDroneBettery;
        this.finishAction = finishAction;
        this.headingMode = headingMode;
        this.rotateGimblePitch = rotateGimblePitch;
        this.goToFirstWaypointMode = goToFirstWaypointMode;
        this.lastWaypoint = lastWaypoint;
    }

    // ✅ Constructor with endTime instead of estimateEndTime
    public Log(
            String startTime,
            String endTime,
            String droneName,
            int numberOfWaypoints,
            String numberOfObstcles,
            int waypointHeight,
            int horizontalPathHeight,
            int houseHeight,
            int maxObstcleHeight,
            int droneSpeed,
            int flightStartDroneBettery,
            String finishAction,
            String headingMode,
            String rotateGimblePitch,
            String goToFirstWaypointMode,
            int lastWaypoint,
            boolean isActualEndTime // just to differentiate constructor overloads
    ) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.droneName = droneName;
        this.numberOfWaypoints = numberOfWaypoints;
        this.numberOfObstcles = numberOfObstcles;
        this.waypointHeight = waypointHeight;
        this.horizontalPathHeight = horizontalPathHeight;
        this.houseHeight = houseHeight;
        this.maxObstcleHeight = maxObstcleHeight;
        this.droneSpeed = droneSpeed;
        this.flightStartDroneBettery = flightStartDroneBettery;
        this.finishAction = finishAction;
        this.headingMode = headingMode;
        this.rotateGimblePitch = rotateGimblePitch;
        this.goToFirstWaypointMode = goToFirstWaypointMode;
        this.lastWaypoint = lastWaypoint;
    }

    protected Log(Parcel in) {
        startTime = in.readString();
        estimateEndTime = in.readString();
        endTime = in.readString();
        droneName = in.readString();
        numberOfWaypoints = in.readInt();
        numberOfObstcles = in.readString();
        waypointHeight = in.readInt();
        horizontalPathHeight = in.readInt();
        houseHeight = in.readInt();
        maxObstcleHeight = in.readInt();
        droneSpeed = in.readInt();
        flightStartDroneBettery = in.readInt();
        finishAction = in.readString();
        headingMode = in.readString();
        rotateGimblePitch = in.readString();
        goToFirstWaypointMode = in.readString();
        lastWaypoint = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(startTime);
        dest.writeString(estimateEndTime);
        dest.writeString(endTime);
        dest.writeString(droneName);
        dest.writeInt(numberOfWaypoints);
        dest.writeString(numberOfObstcles);
        dest.writeInt(waypointHeight);
        dest.writeInt(horizontalPathHeight);
        dest.writeInt(houseHeight);
        dest.writeInt(maxObstcleHeight);
        dest.writeInt(droneSpeed);
        dest.writeInt(flightStartDroneBettery);
        dest.writeString(finishAction);
        dest.writeString(headingMode);
        dest.writeString(rotateGimblePitch);
        dest.writeString(goToFirstWaypointMode);
        dest.writeInt(lastWaypoint);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Log> CREATOR = new Creator<Log>() {
        @Override
        public Log createFromParcel(Parcel in) {
            return new Log(in);
        }

        @Override
        public Log[] newArray(int size) {
            return new Log[size];
        }
    };

    @Override
    public String toString() {
        return "Log{" +
                "startTime='" + startTime + '\'' +
                ", estimateEndTime='" + estimateEndTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", droneName='" + droneName + '\'' +
                ", numberOfWaypoints=" + numberOfWaypoints +
                ", numberOfObstcles=" + numberOfObstcles +
                ", waypointHeight=" + waypointHeight +
                ", horizontalPathHeight=" + horizontalPathHeight +
                ", houseHeight=" + houseHeight +
                ", maxObstcleHeight=" + maxObstcleHeight +
                ", droneSpeed=" + droneSpeed +
                ", flightStartDroneBettery=" + flightStartDroneBettery +
                ", finishAction='" + finishAction + '\'' +
                ", headingMode='" + headingMode + '\'' +
                ", rotateGimblePitch='" + rotateGimblePitch + '\'' +
                ", goToFirstWaypointMode='" + goToFirstWaypointMode + '\'' +
                ", lastWaypoint=" + lastWaypoint +
                '}';
    }
}
