package com.empowerbits.dronifyit.util;
import android.location.Location;
import android.util.Log;

import dji.sdk.keyvalue.value.common.LocationCoordinate2D;

public class OtherHelper {
    public static double getAngleBetweenPoints(Location point1, Location point2) {
        double lat1 = Math.toRadians(point1.getLatitude());
        double lon1 = Math.toRadians(point1.getLongitude());
        double lat2 = Math.toRadians(point2.getLatitude());
        double lon2 = Math.toRadians(point2.getLongitude());

        double dLon = lon2 - lon1;

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double radiansBearing = Math.atan2(y, x);
        double degreesBearing = Math.toDegrees(radiansBearing);

        // Normalize to 0..360
        degreesBearing = (degreesBearing + 360.0) % 360.0;

        return degreesBearing;
    }


    public static double radiansToDegrees(double radians) {
        return radians * 180.0 / Math.PI;
    }

    /**
     * Calculate pitch angle between waypoint and POI - matches iOS Utils.calculatePitchAngle()
     */
    public static double calculateDistance(
            double lat1, double lon1,
            double lat2, double lon2) {

        final int R = 6371000; // Earth radius in meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // Distance in meters
    }

    public static float calculatePitchAngle(LocationCoordinate2D waypointCoordinate,
                                            LocationCoordinate2D poiCoordinate,
                                            double waypointHeight,
                                            double poiHeight) {
        double heightDifference = poiHeight - waypointHeight;
        Log.d("EAGEELYYEE", "Height Difference - " + heightDifference);

        // Distance between waypoint and POI
        double distance = calculateDistance(poiCoordinate.getLatitude(), poiCoordinate.getLongitude(), waypointCoordinate.getLatitude(), waypointCoordinate.getLongitude());
        System.out.println("Distance: " + distance);
        Log.d("EAGEELYYEE", "Distance" + distance);

        // Avoid divide by zero if same location
        if (distance < 5 ) {
            return (float) -90.0;
        }
        // Pitch angle in radians
        double pitchAngleInRadians = Math.atan(heightDifference / distance);
        // Convert to degrees
        return (float) radiansToDegrees(pitchAngleInRadians);
    }
}
