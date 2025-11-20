package io.empowerbits.sightflight.util;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import io.empowerbits.sightflight.R;

import java.util.ArrayList;
import java.util.List;

/**
 * MapHelper - Universal Map Helper Class for DJI Smart Controller compatibility
 * Provides both static utility methods and instance methods for map management
 */
public class MapHelper {
    private static final String TAG = "MapHelper";
    
    // Instance variables for map management
    private Context context;
    private GoogleMap googleMap;
    private LatLng centerLocation;
    private List<LatLng> waypoints;
    private List<Marker> waypointMarkers;
    private Polyline waypointPath;
    
    // Static utility methods for device detection
    
    /**
     * Check if Google Play Services is available
     * @param context Application context
     * @return true if Google Play Services available, false otherwise
     */
    public static boolean isGooglePlayServicesAvailable(Context context) {
        try {
            GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
            int result = googleAPI.isGooglePlayServicesAvailable(context);
            boolean isAvailable = result == ConnectionResult.SUCCESS;
            
            Log.d(TAG, "Google Play Services available: " + isAvailable + " (Code: " + result + ")");
            return isAvailable;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Google Play Services: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if we're running on DJI Smart Controller or similar device
     * @param context Application context
     * @return true if DJI device, false otherwise
     */
    public static boolean isDJIDevice(Context context) {
        try {
            String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
            String model = android.os.Build.MODEL.toLowerCase();
            String product = android.os.Build.PRODUCT.toLowerCase();
            String brand = android.os.Build.BRAND.toLowerCase();
            
            boolean isDJI = manufacturer.contains("dji") || 
                           model.contains("smart controller") ||
                           model.contains("dji") ||
                           product.contains("dji") ||
                           brand.contains("dji");
                           
            Log.d(TAG, "Device Info - Manufacturer: " + manufacturer + 
                      ", Model: " + model + ", Product: " + product + 
                      ", Brand: " + brand + ", isDJI: " + isDJI);
                      
            return isDJI;
        } catch (Exception e) {
            Log.e(TAG, "Error checking device type: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Determine which map type to use based on device capabilities
     * @param context Application context
     * @return MAP_TYPE_GOOGLE or MAP_TYPE_OSM
     */
    public static MapType getPreferredMapType(Context context) {
        boolean hasGoogleServices = isGooglePlayServicesAvailable(context);
        boolean isDJI = isDJIDevice(context);
        
        if (hasGoogleServices && !isDJI) {
            Log.d(TAG, "Using Google Maps - Google Play Services available and not DJI device");
            return MapType.GOOGLE_MAPS;
        } else {
            Log.d(TAG, "Using OpenStreetMap (OSM) - " + 
                  (isDJI ? "DJI device detected" : "Google Play Services not available"));
            return MapType.OPEN_STREET_MAP;
        }
    }
    
    /**
     * Force use of specific map type (for testing purposes)
     * @param context Application context
     * @param forceOSM true to force OSM, false for automatic detection
     * @return Selected map type
     */
    public static MapType getMapType(Context context, boolean forceOSM) {
        if (forceOSM) {
            Log.d(TAG, "Forcing OpenStreetMap (OSM) usage");
            return MapType.OPEN_STREET_MAP;
        }
        return getPreferredMapType(context);
    }
    
    // Instance methods for map management
    
    /**
     * Constructor for instance-based map management
     * @param context Application context
     * @param googleMap Google Map instance
     * @param centerLocation Center location for waypoint generation
     */
    public MapHelper(Context context, GoogleMap googleMap, LatLng centerLocation) {
        this.context = context;
        this.googleMap = googleMap;
        this.centerLocation = centerLocation;
        this.waypoints = new ArrayList<>();
        this.waypointMarkers = new ArrayList<>();
        
        Log.d(TAG, "MapHelper instance created with center: " + centerLocation);
    }
    
    /**
     * Update waypoints around the center location
     * @param waypointCount Number of waypoints to generate
     * @param radiusFeet Radius in feet for waypoint circle
     */
    public void updateWaypoints(int waypointCount, int radiusFeet) {
        updateWaypoints(waypointCount, radiusFeet, 1);
    }

    /**
     * Update waypoints around the center location with custom starting number
     * @param waypointCount Number of waypoints to generate
     * @param radiusFeet Radius in feet for waypoint circle
     * @param startNumber Starting number for waypoint numbering (e.g., 9 to start from waypoint 9)
     */
    public void updateWaypoints(int waypointCount, int radiusFeet, int startNumber) {
        if (googleMap == null || centerLocation == null) {
            Log.w(TAG, "Cannot update waypoints - map or center location is null");
            return;
        }

        try {
            // Clear existing waypoints
            clearWaypointMarkers();

            // Generate new waypoints in a circle (using feet directly)
            waypoints = generateCircularWaypoints(centerLocation, radiusFeet, waypointCount);

            // Add markers for each waypoint
            for (int i = 0; i < waypoints.size(); i++) {
                LatLng waypoint = waypoints.get(i);

                // Create numbered marker (starting from startNumber)
                int markerNumber = startNumber + i;

                MarkerOptions markerOptions = new MarkerOptions()
                    .position(waypoint)
                    .title("Waypoint " + markerNumber)
                    .snippet("Lat: " + String.format("%.6f", waypoint.latitude) +
                            "\nLng: " + String.format("%.6f", waypoint.longitude))
                    .icon(createNumberedMarkerIcon(markerNumber))
                    .anchor(0.5f, 0.8f); // Anchor at bottom center for original map_marker shape

                Marker marker = googleMap.addMarker(markerOptions);
                if (marker != null) {
                    waypointMarkers.add(marker);
                }
            }

            // Draw path connecting waypoints
            drawWaypointPath();

            Log.d(TAG, "Updated waypoints: " + waypointCount + " points at " + radiusFeet + " feet, starting from #" + startNumber);

        } catch (Exception e) {
            Log.e(TAG, "Error updating waypoints: " + e.getMessage());
        }
    }
    
    /**
     * Create a numbered marker icon
     * @param number The number to display on the marker (1-based)
     * @return BitmapDescriptor for the numbered marker
     */
    private com.google.android.gms.maps.model.BitmapDescriptor createNumberedMarkerIcon(int number) {
        try {
            // Load the original map_marker drawable
            android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.map_marker);
            if (drawable == null) {
                Log.w(TAG, "Could not load map_marker drawable, using default");
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
            }
            
            // Get drawable dimensions
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            
            // Create bitmap with same dimensions as original marker
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            // Draw the original marker
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            
            // Draw number text on top of the marker
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setColor(android.graphics.Color.WHITE);
            textPaint.setTextSize(number > 99 ? width/4f : (number > 9 ? width/3f : width/2.5f));
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            // Add black shadow for better readability
            android.graphics.Paint shadowPaint = new android.graphics.Paint(textPaint);
            shadowPaint.setColor(android.graphics.Color.BLACK);
            shadowPaint.setStrokeWidth(3);
            shadowPaint.setStyle(android.graphics.Paint.Style.STROKE);
            
            String numberText = String.valueOf(number);
            float textX = width / 2f;
            float textY = height / 2.5f; // Position number in upper part of marker
            
            // Draw shadow first, then white text on top
            canvas.drawText(numberText, textX, textY, shadowPaint);
            canvas.drawText(numberText, textX, textY, textPaint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating numbered marker with original design: " + e.getMessage());
            // Fallback to original map_marker without number
            return BitmapDescriptorFactory.fromResource(R.drawable.map_marker);
        }
    }
    
    /**
     * Generate waypoints in a circular pattern
     * @param radiusFeet Radius in feet
     * @param waypointCount Number of waypoints
     * @return List of waypoint coordinates
     */
    private List<LatLng> generateCircularWaypoints(LatLng center, double radiusFeet, int waypointCount) {
        List<LatLng> points = new ArrayList<>();

        // Convert radius from feet to degrees (1 foot = 0.3048 meters, 111320 meters per degree latitude)
        double radiusInDegrees = (radiusFeet * 0.3048) / 111320.0;
        
        double angleStep = 360.0 / waypointCount;
        
        for (int i = 0; i < waypointCount; i++) {
            double angle = Math.toRadians(i * angleStep);
            
            // Calculate waypoint coordinates
            double deltaLat = radiusInDegrees * Math.cos(angle);
            double deltaLng = radiusInDegrees * Math.sin(angle) / Math.cos(Math.toRadians(center.latitude));
            
            double waypointLat = center.latitude + deltaLat;
            double waypointLng = center.longitude + deltaLng;
            
            points.add(new LatLng(waypointLat, waypointLng));
        }
        
        return points;
    }
    
    /**
     * Draw polyline connecting all waypoints
     */
    private void drawWaypointPath() {
        if (waypointPath != null) {
            waypointPath.remove();
        }
        
        if (waypoints.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(waypoints)
                .color(android.graphics.Color.BLUE)
                .width(3f)
                .geodesic(true);
            
            // Close the circle by connecting last point to first
            if (waypoints.size() > 2) {
                polylineOptions.add(waypoints.get(0));
            }
            
            waypointPath = googleMap.addPolyline(polylineOptions);
        }
    }
    
    /**
     * Get current waypoints
     * @return List of waypoint coordinates
     */
    public List<LatLng> getWaypoints() {
        return new ArrayList<>(waypoints);
    }
    
    /**
     * Clear all waypoint markers from the map
     */
    private void clearWaypointMarkers() {
        for (Marker marker : waypointMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        waypointMarkers.clear();
        
        if (waypointPath != null) {
            waypointPath.remove();
            waypointPath = null;
        }
    }
    
    /**
     * Clear all markers and paths managed by this MapHelper
     */
    public void clearMarkers() {
        clearWaypointMarkers();
        waypoints.clear();
        Log.d(TAG, "All MapHelper markers cleared");
    }
    
    /**
     * Update center location for waypoint generation
     * @param newCenter New center location
     */
    public void setCenterLocation(LatLng newCenter) {
        this.centerLocation = newCenter;
        Log.d(TAG, "Center location updated to: " + newCenter);
    }
    
    /**
     * Get current center location
     * @return Center location
     */
    public LatLng getCenterLocation() {
        return centerLocation;
    }
    
    /**
     * Get number of waypoints currently managed
     * @return Number of waypoints
     */
    public int getWaypointCount() {
        return waypoints.size();
    }
    
    /**
     * Map type enumeration
     */
    public enum MapType {
        GOOGLE_MAPS,
        OPEN_STREET_MAP
    }
}