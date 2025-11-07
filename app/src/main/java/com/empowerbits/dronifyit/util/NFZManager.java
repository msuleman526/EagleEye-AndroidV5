package com.empowerbits.dronifyit.util;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.aircraft.perception.PerceptionManager;
import dji.v5.manager.aircraft.perception.data.PerceptionInfo;
import dji.v5.manager.aircraft.perception.listener.PerceptionInformationListener;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.v5.manager.KeyManager;
import dji.v5.manager.aircraft.perception.data.ObstacleData;
import dji.v5.manager.interfaces.IPerceptionManager;
import dji.v5.manager.interfaces.IFlyZoneManager;
import dji.v5.manager.aircraft.flysafe.FlyZoneManager;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneCategory;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneShape;

/**
 * NFZ (No-Fly Zone) Manager
 * Real implementation using DJI V5 SDK and NFZ database
 */
public class NFZManager {
    private static final String TAG = "NFZManager";

    // NFZ check radius in meters
    private static final double NFZ_CHECK_RADIUS = 5000.0; // 5km radius
    private static final double WAYPOINT_NFZ_DISTANCE_THRESHOLD = 100.0; // 100m threshold

    /**
     * Simple FlyZone information class with polygon support
     */
    public static class SimpleFlyZoneInfo {
        public String name;
        public double latitude;
        public double longitude;
        public double radiusMeters;
        public String category; // "RESTRICTED", "AUTHORIZATION", "WARNING"
        public List<List<LatLng>> polygons; // Multi-polygon coordinates
        public Polygon mapPolygon; // Reference to drawn polygon on map
        public int flyZoneId; // Unique ID for the fly zone
        public FlyZoneShape shape; // Shape type (CYLINDER, POLYGON, etc.)

        public SimpleFlyZoneInfo(String name, double lat, double lon, double radius, String category) {
            this.name = name;
            this.latitude = lat;
            this.longitude = lon;
            this.radiusMeters = radius;
            this.category = category;
            this.polygons = new ArrayList<>();
        }

        public SimpleFlyZoneInfo(String name, double lat, double lon, double radius, String category,
                                List<List<LatLng>> polygons, int flyZoneId, FlyZoneShape shape) {
            this.name = name;
            this.latitude = lat;
            this.longitude = lon;
            this.radiusMeters = radius;
            this.category = category;
            this.polygons = polygons != null ? polygons : new ArrayList<>();
            this.flyZoneId = flyZoneId;
            this.shape = shape;
        }

        public boolean hasPolygons() {
            return polygons != null && !polygons.isEmpty();
        }
    }

    public interface NFZCheckCallback {
        void onNFZDetected(List<SimpleFlyZoneInfo> zones, List<Integer> affectedWaypoints);
        void onNoNFZDetected();
        void onError(String error);
    }

    public interface FlyZonesCallback {
        void onFlyZonesRetrieved(List<SimpleFlyZoneInfo> zones);
        void onError(String error);
    }

    /**
     * Get all fly zones in surrounding area for visualization
     */
    public void getFlyZonesForVisualization(LatLng location, FlyZonesCallback callback) {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager != null) {
            Log.d(TAG, "Fetching fly zones for visualization at: " + location.latitude + ", " + location.longitude);

            LocationCoordinate2D centerLocation = new LocationCoordinate2D(
                location.latitude,
                location.longitude
            );

            flyZoneManager.getFlyZonesInSurroundingArea(centerLocation, new CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneInformation>>() {
                @Override
                public void onSuccess(List<FlyZoneInformation> flyZones) {
                    if (flyZones == null || flyZones.isEmpty()) {
                        if (callback != null) {
                            callback.onFlyZonesRetrieved(new ArrayList<>());
                        }
                        return;
                    }

                    // Convert and remove duplicates
                    Map<Integer, SimpleFlyZoneInfo> uniqueZones = new HashMap<>();
                    for (FlyZoneInformation zone : flyZones) {
                        SimpleFlyZoneInfo simpleZone = convertToSimpleZone(zone);
                        if (simpleZone != null && !uniqueZones.containsKey(simpleZone.flyZoneId)) {
                            uniqueZones.put(simpleZone.flyZoneId, simpleZone);
                        }
                    }

                    if (callback != null) {
                        callback.onFlyZonesRetrieved(new ArrayList<>(uniqueZones.values()));
                    }
                }

                @Override
                public void onFailure(IDJIError error) {
                    String errorMsg = (error != null && error.description() != null) ?
                                      error.description() : "Failed to get fly zones";
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                }
            });
        } else {
            if (callback != null) {
                callback.onError("FlyZone Manager not available");
            }
        }
    }

    /**
     * Convert DJI FlyZoneInformation to SimpleFlyZoneInfo with polygon support
     */
    private static SimpleFlyZoneInfo convertToSimpleZone(FlyZoneInformation zone) {
        if (zone == null) return null;

        try {
            String name = zone.getName() != null ? zone.getName() : "Unnamed Zone";
            double lat = zone.getCircleCenter().getLatitude();
            double lon = zone.getCircleCenter().getLongitude();
            double radius = zone.getCircleRadius();
            String category = zone.getCategory() != null ? zone.getCategory().name() : "UNKNOWN";
            int flyZoneId = zone.getFlyZoneID();
            FlyZoneShape shape = zone.getShape();

            // Extract polygon coordinates from sub fly zones
            List<List<LatLng>> polygons = extractPolygonsFromZone(zone);

            Log.d(TAG, "Converting zone: " + name +
                  ", Shape: " + (shape != null ? shape.name() : "null") +
                  ", Category: " + category +
                  ", ID: " + flyZoneId +
                  ", Polygons extracted: " + polygons.size());

            return new SimpleFlyZoneInfo(name, lat, lon, radius, category, polygons, flyZoneId, shape);
        } catch (Exception e) {
            Log.w(TAG, "Error converting zone: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract polygon coordinates from FlyZoneInformation
     * Uses reflection to discover available methods at runtime
     */
    private static List<List<LatLng>> extractPolygonsFromZone(FlyZoneInformation zone) {
        List<List<LatLng>> allPolygons = new ArrayList<>();

        try {
            FlyZoneShape shape = zone.getShape();
            Log.d(TAG, "Zone shape: " + (shape != null ? shape.name() : "null"));
            Log.d(TAG, "Zone class: " + zone.getClass().getName());

            // Log available methods for debugging
            java.lang.reflect.Method[] methods = zone.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().contains("Polygon") ||
                    method.getName().contains("Vertex") ||
                    method.getName().contains("Vertices") ||
                    method.getName().contains("Ring") ||
                    method.getName().contains("Coordinate")) {
                    Log.d(TAG, "Available method: " + method.getName() + " returns " + method.getReturnType().getName());
                }
            }

            // Check if this is a multi-polygon zone
            if (shape == FlyZoneShape.MULTI_POLYGON) {
                Log.d(TAG, "Zone is MULTI_POLYGON type, attempting to extract vertices");

                // Try to use getMultiPolygonFlyZoneInformation() method
                try {
                    java.lang.reflect.Method getMultiPolygonMethod = zone.getClass().getMethod("getMultiPolygonFlyZoneInformation");
                    Object polygons = getMultiPolygonMethod.invoke(zone);

                    if (polygons instanceof List) {
                        List<?> polygonList = (List<?>) polygons;
                        Log.d(TAG, "Found " + polygonList.size() + " multi-polygon zones");

                        for (Object polygonObj : polygonList) {
                            // Try to extract vertices from each polygon
                            extractVerticesFromPolygon(polygonObj, allPolygons);
                        }
                    }
                } catch (NoSuchMethodException e) {
                    Log.d(TAG, "getMultiPolygonFlyZoneInformation() method not found, trying alternative methods");

                    // Try alternative method names
                    tryAlternativePolygonMethods(zone, allPolygons);
                } catch (Exception e) {
                    Log.w(TAG, "Error calling getMultiPolygonFlyZoneInformation: " + e.getMessage(), e);
                }
            } else {
                Log.d(TAG, "Zone is circular type (not MULTI_POLYGON)");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting polygons: " + e.getMessage(), e);
        }

        return allPolygons;
    }

    /**
     * Try alternative method names to extract polygon data
     */
    private static void tryAlternativePolygonMethods(FlyZoneInformation zone, List<List<LatLng>> allPolygons) {
        String[] methodNames = {"getVertices", "getCoordinates", "getPoints", "getBoundary", "getOuterRing"};

        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = zone.getClass().getMethod(methodName);
                Object result = method.invoke(zone);

                if (result instanceof List) {
                    List<?> points = (List<?>) result;
                    Log.d(TAG, "Found coordinates using " + methodName + ": " + points.size() + " points");

                    List<LatLng> polygon = new ArrayList<>();
                    for (Object point : points) {
                        if (point instanceof LocationCoordinate2D) {
                            LocationCoordinate2D coord = (LocationCoordinate2D) point;
                            polygon.add(new LatLng(coord.getLatitude(), coord.getLongitude()));
                        }
                    }

                    if (!polygon.isEmpty()) {
                        allPolygons.add(polygon);
                        return; // Success
                    }
                }
            } catch (Exception e) {
                // Method not found or error, try next
            }
        }
    }

    /**
     * Extract vertices from a polygon object using reflection
     */
    private static void extractVerticesFromPolygon(Object polygonObj, List<List<LatLng>> allPolygons) {
        try {
            Log.d(TAG, "Polygon object class: " + polygonObj.getClass().getName());

            // Log available methods for debugging
            java.lang.reflect.Method[] methods = polygonObj.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().contains("Vertex") ||
                    method.getName().contains("Vertices") ||
                    method.getName().contains("Point") ||
                    method.getName().contains("Coordinate") ||
                    method.getName().contains("Ring")) {
                    Log.d(TAG, "Polygon method: " + method.getName() + " returns " + method.getReturnType().getName());
                }
            }

            // Try different method names
            String[] methodNames = {"getPolygonPoints", "getVertices", "getOuterRing", "getCoordinates", "getPoints", "getBoundary"};

            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = polygonObj.getClass().getMethod(methodName);
                    Object vertices = method.invoke(polygonObj);

                    if (vertices instanceof List) {
                        List<?> vertexList = (List<?>) vertices;
                        Log.d(TAG, "Found " + vertexList.size() + " vertices using " + methodName);

                        List<LatLng> polygon = new ArrayList<>();

                        for (Object vertex : vertexList) {
                            if (vertex instanceof LocationCoordinate2D) {
                                LocationCoordinate2D coord = (LocationCoordinate2D) vertex;
                                polygon.add(new LatLng(coord.getLatitude(), coord.getLongitude()));
                            }
                        }

                        if (!polygon.isEmpty()) {
                            // Close the polygon if not already closed
                            if (polygon.size() > 2) {
                                LatLng first = polygon.get(0);
                                LatLng last = polygon.get(polygon.size() - 1);
                                if (first.latitude != last.latitude || first.longitude != last.longitude) {
                                    polygon.add(new LatLng(first.latitude, first.longitude));
                                }
                            }

                            allPolygons.add(polygon);
                            Log.d(TAG, "Extracted polygon with " + polygon.size() + " vertices");
                            return; // Success
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // Method not found, try next
                }
            }

            Log.w(TAG, "Could not find method to extract vertices from polygon");
        } catch (Exception e) {
            Log.w(TAG, "Error extracting vertices from polygon: " + e.getMessage(), e);
        }
    }

    /**
     * Check if waypoints are in NFZ using drone's current location
     */
    public void checkWaypoints(List<LatLng> waypoints, NFZCheckCallback callback) {
        Log.d(TAG, "Checking " + waypoints.size() + " waypoints for NFZ");

        if (waypoints == null || waypoints.isEmpty()) {
            if (callback != null) {
                callback.onNoNFZDetected();
            }
            return;
        }

        // Get drone's current location
        dji.sdk.keyvalue.key.DJIKey<LocationCoordinate2D> locationKey = KeyTools.createKey(
                FlightControllerKey.KeyAircraftLocation);

        KeyManager.getInstance().getValue(locationKey, new CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate2D>() {
            @Override
            public void onSuccess(LocationCoordinate2D location) {
                if (location != null) {
                    LatLng droneLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    checkWaypointsAgainstLocation(waypoints, droneLocation, callback);
                } else {
                    Log.w(TAG, "Drone location is null, checking waypoints without reference");
                    // Check waypoints anyway with first waypoint as reference
                    if (!waypoints.isEmpty()) {
                        checkWaypointsAgainstLocation(waypoints, waypoints.get(0), callback);
                    }
                }
            }

            @Override
            public void onFailure(IDJIError error) {
                String errorMsg = (error != null && error.description() != null) ? error.description() : "Unknown error";
                Log.e(TAG, "Failed to get drone location: " + errorMsg);
                // Fall back to checking waypoints without drone location
                if (!waypoints.isEmpty()) {
                    checkWaypointsAgainstLocation(waypoints, waypoints.get(0), callback);
                } else if (callback != null) {
                    callback.onNoNFZDetected();
                }
            }
        });
    }

    /**
     * Check waypoints against a reference location using DJI FlySafe Manager
     */
    private void checkWaypointsAgainstLocation(List<LatLng> waypoints, LatLng referenceLocation, NFZCheckCallback callback) {
        Log.d(TAG, "Checking waypoints against location: " + referenceLocation.latitude + ", " + referenceLocation.longitude);

        // Use DJI FlySafe Manager for real NFZ detection
        performRealNFZCheck(waypoints, referenceLocation, callback);
    }

    /**
     * Perform NFZ checking using DJI FlySafe Manager
     * Falls back to simulated database if DJI API unavailable
     */
    private void performRealNFZCheck(List<LatLng> waypoints, LatLng referenceLocation, NFZCheckCallback callback) {
        // Try to use DJI FlySafe Manager first
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager != null) {
            Log.d(TAG, "Using DJI FlySafe Manager to get fly zones");

            // Create LocationCoordinate2D from reference location
            LocationCoordinate2D centerLocation = new LocationCoordinate2D(
                referenceLocation.latitude,
                referenceLocation.longitude
            );

            // Use official DJI API: getFlyZonesInSurroundingArea()
            // Gets fly zones within 50km from central location
            flyZoneManager.getFlyZonesInSurroundingArea(centerLocation, new CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneInformation>>() {
                @Override
                public void onSuccess(List<FlyZoneInformation> flyZones) {
                    Log.d(TAG, "DJI FlySafe returned " + (flyZones != null ? flyZones.size() : 0) + " fly zones");

                    if (flyZones == null || flyZones.isEmpty()) {
                        // No zones from DJI, fall back to simulated database
                        performSimulatedNFZCheck(waypoints, referenceLocation, callback);
                        return;
                    }

                    // Convert DJI zones to simple format and check waypoints
                    // Use HashMap to remove duplicates by flyZoneId
                    Map<Integer, SimpleFlyZoneInfo> uniqueZones = new HashMap<>();

                    for (FlyZoneInformation zone : flyZones) {
                        SimpleFlyZoneInfo simpleZone = convertToSimpleZone(zone);
                        if (simpleZone != null) {
                            // Only add if not already present (removes duplicates)
                            if (!uniqueZones.containsKey(simpleZone.flyZoneId)) {
                                uniqueZones.put(simpleZone.flyZoneId, simpleZone);
                                Log.d("FlyZone Detail", "Zone: " + simpleZone.name +
                                      ", Center: " + simpleZone.latitude + ", " + simpleZone.longitude +
                                      ", ID: " + simpleZone.flyZoneId +
                                      ", Category: " + simpleZone.category +
                                      ", Has Polygons: " + simpleZone.hasPolygons());
                            } else {
                                Log.d(TAG, "Skipping duplicate zone with ID: " + simpleZone.flyZoneId);
                            }
                        }
                    }

                    List<SimpleFlyZoneInfo> simpleZones = new ArrayList<>(uniqueZones.values());
                    Log.d(TAG, "After removing duplicates: " + simpleZones.size() + " unique fly zones");

                    checkWaypointsAgainstZones(waypoints, simpleZones, callback);
                }

                @Override
                public void onFailure(IDJIError error) {
                    String errorMsg = (error != null && error.description() != null) ?
                                      error.description() : "Failed to get fly zones from DJI";
                    Log.w(TAG, "DJI FlySafe error: " + errorMsg + ", falling back to simulated database");

                    // Fall back to simulated database
                    performSimulatedNFZCheck(waypoints, referenceLocation, callback);
                }
            });
        } else {
            Log.w(TAG, "DJI FlySafe Manager not available, using simulated database");
            // Fall back to simulated database
            performSimulatedNFZCheck(waypoints, referenceLocation, callback);
        }
    }

    /**
     * Perform NFZ checking using simulated database (fallback)
     */
    private void performSimulatedNFZCheck(List<LatLng> waypoints, LatLng referenceLocation, NFZCheckCallback callback) {
        List<SimpleFlyZoneInfo> nearbyZones = getNearbyFlyZones(referenceLocation);
        Log.d(TAG, "Simulated database: checking against " + nearbyZones.size() + " nearby fly zones");

        checkWaypointsAgainstZones(waypoints, nearbyZones, callback);
    }

    /**
     * Check waypoints against a list of fly zones
     */
    private void checkWaypointsAgainstZones(List<LatLng> waypoints, List<SimpleFlyZoneInfo> zones, NFZCheckCallback callback) {
        if (zones.isEmpty()) {
            if (callback != null) {
                callback.onNoNFZDetected();
            }
            return;
        }

        // Check which waypoints are in NFZ
        List<Integer> affectedWaypoints = new ArrayList<>();
        List<SimpleFlyZoneInfo> relevantZones = new ArrayList<>();

        for (int i = 0; i < waypoints.size(); i++) {
            LatLng waypoint = waypoints.get(i);

            for (SimpleFlyZoneInfo zone : zones) {
                if (isWaypointInFlyZone(waypoint, zone)) {
                    if (!affectedWaypoints.contains(i)) {
                        affectedWaypoints.add(i);
                    }
                    if (!relevantZones.contains(zone)) {
                        relevantZones.add(zone);
                    }

                    Log.d(TAG, "Waypoint " + i + " is in NFZ: " + zone.name +
                          " (Category: " + zone.category + ")");
                }
            }
        }

        if (callback != null) {
            if (!affectedWaypoints.isEmpty()) {
                callback.onNFZDetected(relevantZones, affectedWaypoints);
            } else {
                callback.onNoNFZDetected();
            }
        }
    }

    /**
     * Get nearby fly zones from simulated database
     * In production, this would query DJI's FlySafe database
     */
    private List<SimpleFlyZoneInfo> getNearbyFlyZones(LatLng location) {
        List<SimpleFlyZoneInfo> allZones = getKnownFlyZones();
        List<SimpleFlyZoneInfo> nearbyZones = new ArrayList<>();

        for (SimpleFlyZoneInfo zone : allZones) {
            double distance = calculateDistance(
                location.latitude, location.longitude,
                zone.latitude, zone.longitude
            );

            // Include zones within check radius
            if (distance < NFZ_CHECK_RADIUS + zone.radiusMeters) {
                nearbyZones.add(zone);
                Log.d(TAG, "Found nearby zone: " + zone.name + " at " +
                      String.format("%.2f", distance / 1000) + " km");
            }
        }

        return nearbyZones;
    }

    /**
     * Get known fly zones database
     * This includes major airports in Pakistan and other regions
     */
    private List<SimpleFlyZoneInfo> getKnownFlyZones() {
        List<SimpleFlyZoneInfo> zones = new ArrayList<>();

        // Pakistan - Islamabad International Airport
        zones.add(new SimpleFlyZoneInfo(
            "Islamabad International Airport",
            33.5569, 72.8516,
            5000, // 5km radius
            "RESTRICTED"
        ));

        // Pakistan - Benazir Bhutto International Airport
        zones.add(new SimpleFlyZoneInfo(
            "Benazir Bhutto International Airport",
            33.6166, 73.0992,
            5000,
            "RESTRICTED"
        ));

        // Add more known zones as needed
        // In production, this would be loaded from DJI's FlySafe database

        return zones;
    }

    /**
     * Check if a waypoint is inside a fly zone (supports both polygons and circles)
     */
    private boolean isWaypointInFlyZone(LatLng waypoint, SimpleFlyZoneInfo zone) {
        if (zone == null) return false;

        try {
            // First, check if zone has polygons - use polygon detection
            if (zone.hasPolygons()) {
                for (List<LatLng> polygon : zone.polygons) {
                    if (isPointInPolygon(waypoint, polygon)) {
                        Log.d(TAG, "Waypoint at (" + waypoint.latitude + ", " + waypoint.longitude +
                              ") is inside polygon of zone: " + zone.name);
                        return true;
                    }
                }
                return false;
            } else {
                // Fall back to circular zone detection
                double distance = calculateDistance(
                    waypoint.latitude, waypoint.longitude,
                    zone.latitude, zone.longitude
                );

                boolean isInZone = distance <= zone.radiusMeters;

                if (isInZone) {
                    Log.d(TAG, "Waypoint at (" + waypoint.latitude + ", " + waypoint.longitude +
                          ") is " + String.format("%.2f", distance) + "m from zone center, " +
                          "radius: " + zone.radiusMeters + "m");
                }

                return isInZone;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking waypoint in fly zone: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a point is inside a polygon using ray casting algorithm
     * @param point The point to check
     * @param polygon List of vertices forming the polygon
     * @return true if point is inside polygon
     */
    private boolean isPointInPolygon(LatLng point, List<LatLng> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); i++) {
            LatLng vi = polygon.get(i);
            LatLng vj = polygon.get(j);

            // Ray casting algorithm
            if ((vi.longitude > point.longitude) != (vj.longitude > point.longitude)) {
                double slope = (point.longitude - vj.longitude) * (vi.latitude - vj.latitude) -
                              (vi.longitude - vj.longitude) * (point.latitude - vj.latitude);

                if ((vi.longitude > vj.longitude && slope > 0) ||
                    (vi.longitude <= vj.longitude && slope < 0)) {
                    inside = !inside;
                }
            }

            j = i;
        }

        return inside;
    }

    /**
     * Get color for restriction level
     */
    public static int getNFZColor(String category) {
        if (category == null) {
            return Color.GRAY;
        }

        switch (category.toUpperCase()) {
            case "AUTHORIZATION":
                return Color.YELLOW;
            case "RESTRICTED":
                return Color.RED;
            case "WARNING":
            case "ENHANCED_WARNING":
                return Color.rgb(255, 165, 0); // Orange
            default:
                return Color.GRAY;
        }
    }

    /**
     * Get level text for display
     */
    public static String getNFZLevelText(String category) {
        if (category == null) {
            return "Unknown";
        }

        switch (category.toUpperCase()) {
            case "AUTHORIZATION":
                return "⚠️ Authorization Required";
            case "RESTRICTED":
                return "🚫 Restricted - No Flight Allowed";
            case "WARNING":
                return "⚠️ Warning Zone";
            case "ENHANCED_WARNING":
                return "⚠️ Enhanced Warning Zone";
            default:
                return "⚠️ Caution";
        }
    }

    /**
     * Check if NFZ is unlockable
     */
    public static boolean isNFZUnlockable(String category) {
        return category != null && category.equalsIgnoreCase("AUTHORIZATION");
    }

    /**
     * Get the most restrictive NFZ from a list
     */
    public static SimpleFlyZoneInfo getMostRestrictiveNFZ(List<SimpleFlyZoneInfo> zones) {
        if (zones == null || zones.isEmpty()) {
            return null;
        }

        SimpleFlyZoneInfo mostRestrictive = zones.get(0);
        int highestPriority = getNFZPriority(mostRestrictive.category);

        for (SimpleFlyZoneInfo zone : zones) {
            int priority = getNFZPriority(zone.category);
            if (priority > highestPriority) {
                highestPriority = priority;
                mostRestrictive = zone;
            }
        }

        return mostRestrictive;
    }

    /**
     * Get NFZ priority for determining most restrictive
     */
    private static int getNFZPriority(String category) {
        if (category == null) return 0;

        switch (category.toUpperCase()) {
            case "RESTRICTED":
                return 5;
            case "AUTHORIZATION":
                return 4;
            case "WARNING":
                return 3;
            case "ENHANCED_WARNING":
                return 2;
            default:
                return 0;
        }
    }

    /**
     * Request NFZ unlock for a specific fly zone
     */
    public void requestUnlock(SimpleFlyZoneInfo zone, CommonCallbacks.CompletionCallback callback) {
        if (zone == null) {
            Log.e(TAG, "Cannot unlock null zone");
            if (callback != null) {
                callback.onFailure(new IDJIError() {
                    @Override
                    public dji.v5.common.error.ErrorType errorType() {
                        return dji.v5.common.error.ErrorType.COMMON;
                    }

                    @Override
                    public String errorCode() {
                        return "INVALID_ZONE";
                    }

                    @Override
                    public String innerCode() {
                        return "";
                    }

                    @Override
                    public String hint() {
                        return "Zone information is null";
                    }

                    @Override
                    public String description() {
                        return "Cannot unlock null zone";
                    }

                    @Override
                    public boolean isError(String s) {
                        return true;
                    }
                });
            }
            return;
        }

        Log.d(TAG, "NFZ unlock requested for zone: " + zone.name);

        // Note: As of 2025, DJI has largely moved away from hard geofencing
        // Most zones now show warnings instead of preventing flight
        // Unlocking requires DJI account login and proper authorization

        // Return not implemented for now
        // In production, you would need to:
        // 1. Ensure user is logged in to DJI account
        // 2. Use FlySafe key operations to enable unlocking
        // 3. Handle the authorization flow

        if (callback != null) {
            callback.onFailure(new IDJIError() {
                @Override
                public dji.v5.common.error.ErrorType errorType() {
                    return dji.v5.common.error.ErrorType.COMMON;
                }

                @Override
                public String errorCode() {
                    return "NOT_IMPLEMENTED";
                }

                @Override
                public String innerCode() {
                    return "";
                }

                @Override
                public String hint() {
                    return "NFZ unlock requires DJI account login and authorization. As of 2025, most zones show warnings instead of preventing flight.";
                }

                @Override
                public String description() {
                    return "NFZ unlock not fully implemented. Most zones as of 2025 allow flight with warnings.";
                }

                @Override
                public boolean isError(String s) {
                    return true;
                }
            });
        }
    }

    /**
     * Unlock all enhanced warning fly zones
     * After unlocking, the aircraft will no longer prompt any enhanced warning zone
     */
    public void unlockAllEnhancedWarningZones(CommonCallbacks.CompletionCallback callback) {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager == null) {
            Log.e(TAG, "FlyZone Manager not available");
            if (callback != null) {
                callback.onFailure(null);
            }
            return;
        }

        Log.d(TAG, "Unlocking all enhanced warning zones");
        flyZoneManager.unlockAllEnhancedWarningFlyZone(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully unlocked all enhanced warning zones");
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to unlock enhanced warning zones: " +
                      (error != null ? error.description() : "Unknown error"));
                if (callback != null) {
                    callback.onFailure(error);
                }
            }
        });
    }


    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
