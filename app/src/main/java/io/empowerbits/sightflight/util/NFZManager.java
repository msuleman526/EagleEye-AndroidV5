package io.empowerbits.sightflight.util;

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
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.v5.manager.KeyManager;
import dji.v5.manager.interfaces.IFlyZoneManager;
import dji.v5.manager.aircraft.flysafe.FlyZoneManager;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneShape;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneLicenseInfo;

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
        public double upperLimit; // Altitude limit in meters (null if no limit)
        public double lowerLimit;

        public SimpleFlyZoneInfo(String name, double lat, double lon, double radius, String category) {
            this.name = name;
            this.latitude = lat;
            this.longitude = lon;
            this.radiusMeters = radius;
            this.category = category;
            this.polygons = new ArrayList<>();
            this.upperLimit = 0.0;
            this.lowerLimit = 0.0;
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
            this.upperLimit = 0.0;
            this.lowerLimit = 0.0;
        }

        public SimpleFlyZoneInfo(String name, double lat, double lon, double radius, String category,
                                List<List<LatLng>> polygons, int flyZoneId, FlyZoneShape shape, double upperLimit, double lowerLimit) {
            this.name = name;
            this.latitude = lat;
            this.longitude = lon;
            this.radiusMeters = radius;
            this.category = category;
            this.polygons = polygons != null ? polygons : new ArrayList<>();
            this.flyZoneId = flyZoneId;
            this.shape = shape;
            this.lowerLimit = lowerLimit;
            this.upperLimit = upperLimit;
        }

        public boolean hasPolygons() {
            return polygons != null && !polygons.isEmpty();
        }

        public boolean hasUpperLimit() {
            return upperLimit > 0;
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

                    List<SimpleFlyZoneInfo> resultZones = new ArrayList<>(uniqueZones.values());
                    Log.d(TAG, ">>> getFlyZonesForVisualization: Returning " + resultZones.size() + " zones to callback");

                    if (callback != null) {
                        callback.onFlyZonesRetrieved(resultZones);
                    } else {
                        Log.w(TAG, ">>> getFlyZonesForVisualization: Callback is NULL - zones will not be displayed!");
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
        if (zone == null) {
            Log.w(TAG, "convertToSimpleZone: zone is null");
            return null;
        }

        try {
            String name = zone.getName() != null ? zone.getName() : "Unnamed Zone";
            double lat = zone.getCircleCenter().getLatitude();
            double lon = zone.getCircleCenter().getLongitude();
            double radius = zone.getCircleRadius();

            // CRITICAL: Get raw category from DJI
            dji.v5.manager.aircraft.flysafe.info.FlyZoneCategory rawCategory = zone.getCategory();
            String category = rawCategory != null ? rawCategory.name() : "UNKNOWN";

            int flyZoneId = zone.getFlyZoneID();
            FlyZoneShape shape = zone.getShape();

            double lowerLimit = zone.getLowerLimit();
            double upperLimit = zone.getUpperLimit();
            List<List<LatLng>> polygons = extractPolygonsFromZone(zone);
            // IMPORTANT: If radius is 0 or very small AND no polygons, use a default radius
            if (radius < 10.0 && (polygons == null || polygons.isEmpty())) {
                Log.w(TAG, "Zone '" + name + "' has invalid radius (" + radius + "m), using default 500m");
                radius = 500.0;
            }
            return new SimpleFlyZoneInfo(name, lat, lon, radius, category, polygons, flyZoneId, shape, upperLimit, lowerLimit);
        } catch (Exception e) {
            Log.e(TAG, "Error converting zone: " + e.getMessage(), e);
            e.printStackTrace();
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
     * Extract altitude limit from FlyZoneInformation using reflection
     * Tries multiple method names to find height/altitude limit information
     *
     * @param zone The FlyZoneInformation to extract altitude limit from
     * @return Altitude limit in meters, or null if no limit or method not found
     */
    private static Integer extractAltitudeLimit(FlyZoneInformation zone) {
        if (zone == null) {
            return null;
        }

        try {
            // List of method names to try (in order of preference)
            String[] methodNames = {
                "getHeightLimit",      // Most likely method name
                "getUpperLimit",       // Alternative name
                "getMaxFlightHeight",  // Another possibility
                "getMaximumHeight",    // Generic name
                "getAltitudeLimit"     // Direct name
            };

            Log.d(TAG, "Attempting to extract altitude limit from zone: " + zone.getName());

            // Try each method
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = zone.getClass().getMethod(methodName);
                    Object result = method.invoke(zone);

                    // Check if result is a valid altitude limit
                    if (result instanceof Integer) {
                        int limit = (Integer) result;
                        if (limit > 0) {
                            Log.d(TAG, "Found altitude limit using " + methodName + "(): " + limit + "m");
                            return limit;
                        }
                    } else if (result instanceof Double) {
                        double limit = (Double) result;
                        if (limit > 0) {
                            Log.d(TAG, "Found altitude limit using " + methodName + "(): " + limit + "m (converted to int)");
                            return (int) limit;
                        }
                    } else if (result instanceof Float) {
                        float limit = (Float) result;
                        if (limit > 0) {
                            Log.d(TAG, "Found altitude limit using " + methodName + "(): " + limit + "m (converted to int)");
                            return (int) limit;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // Method doesn't exist, try next
                } catch (Exception e) {
                    Log.w(TAG, "Error calling " + methodName + ": " + e.getMessage());
                }
            }

            // Also try getLowerLimit and check if it's different from default
            try {
                java.lang.reflect.Method lowerMethod = zone.getClass().getMethod("getLowerLimit");
                Object lowerResult = lowerMethod.invoke(zone);

                if (lowerResult instanceof Integer) {
                    int lowerLimit = (Integer) lowerResult;
                    if (lowerLimit > 0) {
                        Log.d(TAG, "Found lower altitude limit using getLowerLimit(): " + lowerLimit + "m");
                        return lowerLimit;
                    }
                }
            } catch (Exception e) {
                // Method doesn't exist or error, ignore
            }

            // Check if zone has hasAltitudeLimit() method
            try {
                java.lang.reflect.Method hasLimitMethod = zone.getClass().getMethod("hasAltitudeLimit");
                Object hasLimitResult = hasLimitMethod.invoke(zone);

                if (hasLimitResult instanceof Boolean && (Boolean) hasLimitResult) {
                    Log.d(TAG, "Zone reports hasAltitudeLimit() = true, but couldn't find limit value");
                    // If zone category is ALTITUDE and hasAltitudeLimit is true, use default
                    if (zone.getCategory() != null && zone.getCategory().name().equals("ALTITUDE")) {
                        Log.d(TAG, "Using default altitude limit of 120m for ALTITUDE category zone");
                        return 120; // Default altitude limit for ALTITUDE zones
                    }
                }
            } catch (Exception e) {
                // Method doesn't exist or error, ignore
            }

            // Fallback: If category is ALTITUDE but no method worked, use default
            if (zone.getCategory() != null && zone.getCategory().name().equals("ALTITUDE")) {
                Log.d(TAG, "ALTITUDE zone detected but no altitude methods found - using default 120m");
                return 120;
            }

            Log.d(TAG, "No altitude limit found for zone: " + zone.getName());
            return null;

        } catch (Exception e) {
            Log.w(TAG, "Error extracting altitude limit: " + e.getMessage(), e);
            return null;
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
     * Get color for restriction level - ALL 8 DJI GEO Zone Types
     * Based on official DJI GEO system colors
     */
    public static int getNFZColor(String category) {
        Log.d(TAG, ">>> getNFZColor called with category: '" + category + "'");

        if (category == null) {
            Log.w(TAG, ">>> Category is NULL - returning GRAY");
            return Color.GRAY;
        }

        String upperCategory = category.toUpperCase();
        Log.d(TAG, ">>> Category uppercase: '" + upperCategory + "'");

        int color;
        switch (upperCategory) {
            // 1. Restricted Zones - RED (Flight prevented)
            case "RESTRICTED":
                color = Color.rgb(222, 67, 41); // DJI Red #de4329
                Log.d(TAG, ">>> Matched RESTRICTED - returning RED");
                return color;

            // 2. Altitude Zones - GRAY (Altitude limited)
            case "ALTITUDE":
                color = Color.rgb(151, 151, 151); // DJI Gray #979797
                Log.d(TAG, ">>> Matched ALTITUDE - returning GRAY");
                return color;

            // 3. Authorization Zones - BLUE (Requires unlocking)
            case "AUTHORIZATION":
                color = Color.rgb(16, 136, 242); // DJI Blue #1088f2
                Log.d(TAG, ">>> Matched AUTHORIZATION - returning BLUE");
                return color;

            // 4. Warning Zones - YELLOW (Warning message only)
            case "WARNING":
                color = Color.rgb(255, 204, 0); // DJI Yellow #ffcc00
                Log.d(TAG, ">>> Matched WARNING - returning YELLOW");
                return color;

            // 5. Enhanced Warning Zones - ORANGE (Can be unlocked via app)
            case "ENHANCED_WARNING":
                color = Color.rgb(238, 136, 21); // DJI Orange #ee8815
                Log.d(TAG, ">>> Matched ENHANCED_WARNING - returning ORANGE");
                return color;

            // 6. Regulatory Restricted Zones - DARK RED (Prohibited by law)
            case "REGULATORY_RESTRICTED":
            case "REGULATORY":
                color = Color.rgb(139, 0, 0); // Dark Red #8B0000
                Log.d(TAG, ">>> Matched REGULATORY - returning DARK RED");
                return color;

            // 7. Approved Zones for Light UAVs - GREEN (China only, approved flight)
            case "APPROVED_FOR_LIGHT_UAV":
            case "APPROVED":
                color = Color.rgb(76, 175, 80); // Green #4CAF50
                Log.d(TAG, ">>> Matched APPROVED - returning GREEN");
                return color;

            // 8. Special zones (airports, military, etc.)
            case "AIRPORT":
            case "MILITARY":
            case "SPECIAL_USE":
                color = Color.rgb(222, 67, 41); // Red like restricted
                Log.d(TAG, ">>> Matched SPECIAL ZONE - returning RED");
                return color;

            default:
                Log.w(TAG, ">>> NO MATCH FOUND for category '" + upperCategory + "' - returning DEFAULT GRAY");
                return Color.GRAY;
        }
    }

    /**
     * Get level text for display - ALL 8 DJI GEO Zone Types
     */
    public static String getNFZLevelText(String category) {
        if (category == null) {
            return "Unknown Zone";
        }

        switch (category.toUpperCase()) {
            // 1. Restricted Zones
            case "RESTRICTED":
                return "🚫 Restricted Zone - Flight Prevented";

            // 2. Altitude Zones
            case "ALTITUDE":
                return "📏 Altitude Zone - Height Limited";

            // 3. Authorization Zones
            case "AUTHORIZATION":
                return "🔐 Authorization Zone - Unlock Required";

            // 4. Warning Zones
            case "WARNING":
                return "⚠️ Warning Zone";

            // 5. Enhanced Warning Zones
            case "ENHANCED_WARNING":
                return "⚠️ Enhanced Warning - Can Unlock";

            // 6. Regulatory Restricted Zones
            case "REGULATORY_RESTRICTED":
            case "REGULATORY":
                return "🚨 Regulatory Restricted - Prohibited by Law";

            // 7. Approved Zones (China)
            case "APPROVED_FOR_LIGHT_UAV":
            case "APPROVED":
                return "✅ Approved Zone for Light UAVs";

            // 8. Special zones
            case "AIRPORT":
                return "✈️ Airport Zone - No Flight";
            case "MILITARY":
                return "🎖️ Military Zone - Prohibited";
            case "SPECIAL_USE":
                return "⚠️ Special Use Airspace";

            default:
                return "⚠️ Caution Area";
        }
    }

    /**
     * Check if NFZ is unlockable
     * AUTHORIZATION zones can be unlocked with DJI account
     * ENHANCED_WARNING zones can be unlocked via app
     */
    public static boolean isNFZUnlockable(String category) {
        if (category == null) return false;

        String categoryUpper = category.toUpperCase();
        return categoryUpper.equals("AUTHORIZATION") ||
               categoryUpper.equals("ENHANCED_WARNING");
    }

    /**
     * Check if zone can be unlocked via self-unlocking (Enhanced Warning)
     */
    public static boolean isSelfUnlockable(String category) {
        return category != null && category.equalsIgnoreCase("ENHANCED_WARNING");
    }

    /**
     * Check if zone requires DJI account authorization
     */
    public static boolean requiresAuthorization(String category) {
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
     * Get NFZ priority for determining most restrictive (ALL 8 zone types)
     * Higher number = more restrictive
     */
    private static int getNFZPriority(String category) {
        if (category == null) return 0;

        switch (category.toUpperCase()) {
            // Most restrictive
            case "REGULATORY_RESTRICTED":
            case "REGULATORY":
                return 10; // Prohibited by law - highest priority

            case "RESTRICTED":
                return 9; // Flight prevented

            case "MILITARY":
                return 8; // Military zones

            case "AIRPORT":
                return 7; // Airport zones

            case "AUTHORIZATION":
                return 6; // Requires authorization

            case "ALTITUDE":
                return 5; // Altitude limited

            case "SPECIAL_USE":
                return 4; // Special use airspace

            case "WARNING":
                return 3; // Warning only

            case "ENHANCED_WARNING":
                return 2; // Enhanced warning

            case "APPROVED_FOR_LIGHT_UAV":
            case "APPROVED":
                return 1; // Approved zones (least restrictive)

            default:
                return 0;
        }
    }

    /**
     * Request NFZ unlock for a specific fly zone using DJI FlySafe API
     */
    public void requestUnlock(SimpleFlyZoneInfo zone, CommonCallbacks.CompletionCallback callback) {
        if (zone == null) {
            Log.e(TAG, "Cannot unlock null zone");
            if (callback != null) {
                callback.onFailure(createError("INVALID_ZONE", "Cannot unlock null zone"));
            }
            return;
        }

        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();
        if (flyZoneManager == null) {
            Log.e(TAG, "FlyZone Manager not available");
            if (callback != null) {
                callback.onFailure(createError("MANAGER_UNAVAILABLE", "FlyZone Manager not available"));
            }
            return;
        }

        Log.d(TAG, "Requesting unlock for zone: " + zone.name + " (ID: " + zone.flyZoneId + ")");

        // Check if this zone type can be unlocked
        if (!isNFZUnlockable(zone.category)) {
            Log.w(TAG, "Zone category " + zone.category + " cannot be unlocked");
            if (callback != null) {
                callback.onFailure(createError("NOT_UNLOCKABLE",
                    "This zone type cannot be unlocked. Category: " + zone.category));
            }
            return;
        }

        // For Enhanced Warning zones, use the dedicated unlock method
        if (isSelfUnlockable(zone.category)) {
            Log.d(TAG, "Using enhanced warning unlock for zone");
            unlockAllEnhancedWarningZones(callback);
            return;
        }

        // For Authorization zones, use the DJI V5 license download/push flow
        if (requiresAuthorization(zone.category)) {
            Log.d(TAG, "Unlocking authorization zone: " + zone.flyZoneId);

            // Step 1: Download fly zone licenses from server
            // Note: User must have applied for authorization on DJI official website first
            flyZoneManager.downloadFlyZoneLicensesFromServer(new CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneLicenseInfo>>() {
                @Override
                public void onSuccess(List<FlyZoneLicenseInfo> licenses) {
                    Log.d(TAG, "Downloaded " + (licenses != null ? licenses.size() : 0) + " fly zone licenses successfully");

                    // Step 2: Push licenses to aircraft
                    flyZoneManager.pushFlyZoneLicensesToAircraft(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Successfully pushed authorization licenses to aircraft for zone: " + zone.name);
                            if (callback != null) {
                                callback.onSuccess();
                            }
                        }

                        @Override
                        public void onFailure(IDJIError error) {
                            String errorMsg = error != null && error.description() != null ?
                                             error.description() : "Failed to push licenses to aircraft";
                            Log.e(TAG, "Failed to push licenses: " + errorMsg);

                            if (callback != null) {
                                callback.onFailure(error != null ? error :
                                    createError("PUSH_FAILED", errorMsg));
                            }
                        }
                    });
                }

                @Override
                public void onFailure(IDJIError error) {
                    String errorMsg = error != null && error.description() != null ?
                                     error.description() : "Failed to download licenses";
                    Log.e(TAG, "Failed to download fly zone licenses: " + errorMsg);

                    // Check if error is due to not having authorization
                    if (error != null && error.description() != null) {
                        if (error.description().contains("login") || error.description().contains("account")) {
                            errorMsg = "Please log in to your DJI account first";
                        } else if (error.description().contains("authorization") || error.description().contains("license")) {
                            errorMsg = "Please apply for authorization on DJI official website first (https://www.dji.com/flysafe/geo-map)";
                        }
                    }

                    if (callback != null) {
                        callback.onFailure(error != null ? error :
                            createError("DOWNLOAD_FAILED", errorMsg));
                    }
                }
            });
        } else {
            Log.w(TAG, "Zone category not supported for unlocking: " + zone.category);
            if (callback != null) {
                callback.onFailure(createError("UNSUPPORTED_CATEGORY",
                    "Unlocking not supported for zone category: " + zone.category));
            }
        }
    }

    /**
     * Create a custom IDJIError for error handling
     */
    private IDJIError createError(final String code, final String description) {
        return new IDJIError() {
            @Override
            public dji.v5.common.error.ErrorType errorType() {
                return dji.v5.common.error.ErrorType.COMMON;
            }

            @Override
            public String errorCode() {
                return code;
            }

            @Override
            public String innerCode() {
                return "";
            }

            @Override
            public String hint() {
                return description;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public boolean isError(String s) {
                return true;
            }
        };
    }

    /**
     * Get detailed zone description for user display
     */
    public static String getZoneDescription(String category) {
        if (category == null) return "Unknown zone type";

        switch (category.toUpperCase()) {
            case "RESTRICTED":
                return "Flight is prevented in this zone. If you have authorization to operate here, please contact flysafe@dji.com or request Online Unlocking.";

            case "ALTITUDE":
                return "Flight altitude is limited in this zone. You will receive warnings in the app about maximum altitude restrictions.";

            case "AUTHORIZATION":
                return "This zone is limited by default. You can unlock it using your DJI verified account if you are authorized to fly here.";

            case "WARNING":
                return "You will receive a warning message when flying in this zone (e.g., Class E airspace). Flight is allowed but proceed with caution.";

            case "ENHANCED_WARNING":
                return "You can unlock this zone directly in the app without internet connection or verified account. The zone will be unlocked for current flight session.";

            case "REGULATORY_RESTRICTED":
            case "REGULATORY":
                return "Flight is prohibited by local regulations and policies. This restriction cannot be unlocked (e.g., prison, government facilities).";

            case "APPROVED_FOR_LIGHT_UAV":
            case "APPROVED":
                return "This zone is approved for light UAV operations (China only). Flight is permitted under local regulations.";

            case "AIRPORT":
                return "Airport zone - Flight is restricted near airports for safety. Contact airport authority for permission.";

            case "MILITARY":
                return "Military restricted area - Flight is prohibited. This is enforced by law and cannot be unlocked.";

            case "SPECIAL_USE":
                return "Special use airspace - Check local regulations and NOTAM before flying.";

            default:
                return "Please check local regulations and proceed with caution.";
        }
    }

    /**
     * Check if flight is completely blocked in this zone
     */
    public static boolean isFlightBlocked(String category) {
        if (category == null) return false;

        String categoryUpper = category.toUpperCase();
        return categoryUpper.equals("RESTRICTED") ||
               categoryUpper.equals("REGULATORY_RESTRICTED") ||
               categoryUpper.equals("REGULATORY") ||
               categoryUpper.equals("MILITARY") ||
               categoryUpper.equals("AIRPORT");
    }

    /**
     * Check if zone only limits altitude (not blocks flight)
     */
    public static boolean isAltitudeLimited(String category) {
        return category != null && category.equalsIgnoreCase("ALTITUDE");
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

    public void unlockAuthorizationZone(int id, CommonCallbacks.CompletionCallback callback) {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager == null) {
            Log.e(TAG, "FlyZone Manager not available");
            if (callback != null) {
                callback.onFailure(null);
            }
            return;
        }

        Log.d(TAG, "Unlocking all enhanced warning zones");
        flyZoneManager.unlockAuthorizationFlyZone(id, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully unlocked all enhanced warning zones");
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.e(TAG, "Failed to unlock authorizationzones: " +
                        (idjiError != null ? idjiError.description() : "Unknown error"));
                if (callback != null) {
                    callback.onFailure(idjiError);
                }
            }
        });
    }


    // ============================================================================================
    // LICENSE MANAGEMENT (DJI SDK V5)
    // ============================================================================================

    /**
     * Pull (retrieve) fly zone licenses currently stored on aircraft
     * This shows what authorization zones are currently unlocked
     */
    public void pullLicensesFromAircraft(CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneLicenseInfo>> callback) {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager == null) {
            Log.e(TAG, "FlyZone Manager not available");
            if (callback != null) {
                callback.onFailure(createError("MANAGER_UNAVAILABLE", "FlyZone Manager not available"));
            }
            return;
        }

        Log.d(TAG, "Pulling fly zone licenses from aircraft...");
        flyZoneManager.pullFlyZoneLicensesFromAircraft(new CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneLicenseInfo>>() {
            @Override
            public void onSuccess(List<FlyZoneLicenseInfo> licenses) {
                Log.d(TAG, "Successfully pulled " + (licenses != null ? licenses.size() : 0) + " licenses from aircraft");
                if (callback != null) {
                    callback.onSuccess(licenses);
                }
            }

            @Override
            public void onFailure(IDJIError error) {
                String errorMsg = error != null && error.description() != null ?
                                 error.description() : "Failed to pull licenses from aircraft";
                Log.e(TAG, "Failed to pull licenses: " + errorMsg);
                if (callback != null) {
                    callback.onFailure(error);
                }
            }
        });
    }

    /**
     * Delete all fly zone licenses from aircraft
     * WARNING: This will remove all authorization unlocks!
     */
    public void deleteLicensesFromAircraft(CommonCallbacks.CompletionCallback callback) {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager == null) {
            Log.e(TAG, "FlyZone Manager not available");
            if (callback != null) {
                callback.onFailure(createError("MANAGER_UNAVAILABLE", "FlyZone Manager not available"));
            }
            return;
        }

        Log.d(TAG, "Deleting all fly zone licenses from aircraft...");
        flyZoneManager.deleteFlyZoneLicensesFromAircraft(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully deleted all licenses from aircraft");
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(IDJIError error) {
                String errorMsg = error != null && error.description() != null ?
                                 error.description() : "Failed to delete licenses";
                Log.e(TAG, "Failed to delete licenses: " + errorMsg);
                if (callback != null) {
                    callback.onFailure(error);
                }
            }
        });
    }

    /**
     * Get fly zones associated with a specific area ID (from license)
     * Useful for visualizing which zones a license unlocks
     */
    public List<FlyZoneInformation> getFlyZonesByAreaID(int areaID) {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();

        if (flyZoneManager == null) {
            Log.e(TAG, "FlyZone Manager not available");
            return new ArrayList<>();
        }

        try {
            List<FlyZoneInformation> zones = flyZoneManager.getFlyZonesByAreaID(areaID);
            Log.d(TAG, "Retrieved " + (zones != null ? zones.size() : 0) + " zones for area ID: " + areaID);
            return zones != null ? zones : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error getting fly zones for area ID " + areaID + ": " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // ============================================================================================
    // EU GEOZONE SUPPORT (ED-269 Standard)
    // ============================================================================================

    /**
     * Import EU GeoZone database to MSDK
     * File must be in ED-269 JSON format
     * @param databaseFilePath Absolute path to ED-269 JSON file
     * @param callback Progress callback (0.0 to 1.0)
     */

    /**
     * Push imported EU GeoZone database to aircraft
     * Must be called after successful import
     * @param callback Progress callback (0.0 to 1.0)
     */

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
