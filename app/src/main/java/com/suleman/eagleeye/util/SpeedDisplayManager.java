package com.suleman.eagleeye.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.suleman.eagleeye.Services.TelemetryService;

import java.util.Locale;

import dji.sdk.keyvalue.value.common.LocationCoordinate3D;

/**
 * SpeedDisplayManager handles real-time speed and telemetry data display
 * Updates the speed_view layout components with telemetry data
 * 
 * Updated for DJI SDK V5 with TelemetryService integration
 * 
 * @author Suleman
 * @version 2.0 - Updated for TelemetryService integration
 * @date 2025-06-27
 */
public class SpeedDisplayManager {
    private static final String TAG = "SpeedDisplayManager";

    // UI Components
    private TextView distanceTxt;
    private TextView altitudeTxt;
    private TextView horizontalSpeedTxt;
    private TextView verticalSpeedTxt;

    // Services
    private TelemetryService telemetryService;

    // Context and Handler
    private Context context;
    private Handler uiHandler;

    // Current values
    private double currentAltitude = 0.0;
    private double currentHorizontalSpeed = 0.0;
    private double currentVerticalSpeed = 0.0;
    private double totalDistance = 0.0;

    // Home/Operator location for distance calculation
    private double homeLatitude = 0.0;
    private double homeLongitude = 0.0;
    private boolean hasHomeLocation = false;

    public SpeedDisplayManager(Context context) {
        this.context = context;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Initialize UI components from speed_view layout
     */
    public void initializeComponents(TextView distanceTxt, TextView altitudeTxt,
                                     TextView horizontalSpeedTxt, TextView verticalSpeedTxt) {
        this.distanceTxt = distanceTxt;
        this.altitudeTxt = altitudeTxt;
        this.horizontalSpeedTxt = horizontalSpeedTxt;
        this.verticalSpeedTxt = verticalSpeedTxt;

        // Initialize with default values
        resetDisplay();

        Log.d(TAG, "SpeedDisplayManager components initialized");
    }

    /**
     * Setup telemetry services and register listeners
     */
    public void setupTelemetryServices(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
        registerTelemetryListeners();
    }

    private void registerTelemetryListeners() {
        if (telemetryService == null) {
            Log.w(TAG, "TelemetryService is null, skipping listener registration");
            return;
        }

        try {
            // Altitude listener
            telemetryService.setAltitudeChangedListener(new TelemetryService.AltitudeChangedListener() {
                @Override
                public void onAltitudeChanged(Double altitude) {
                    if (altitude != null) {
                        currentAltitude = altitude;
                        updateAltitudeDisplay(altitude);
                    }
                }
            });

            // Horizontal velocity listener
            telemetryService.setHorizontalVelocityChangedListener(new TelemetryService.HorizontalVelocityChangedListener() {
                @Override
                public void onHorizontalVelocityChanged(Double velocity) {
                    if (velocity != null) {
                        currentHorizontalSpeed = velocity;
                        updateHorizontalSpeedDisplay(velocity);
                    }
                }
            });

            // Vertical velocity listener
            telemetryService.setVerticalVelocityChangedListener(new TelemetryService.VerticalVelocityChangedListener() {
                @Override
                public void onVerticalVelocityChanged(Double velocity) {
                    if (velocity != null) {
                        currentVerticalSpeed = velocity;
                        updateVerticalSpeedDisplay(velocity);
                    }
                }
            });

            // Location listener for distance calculation
            telemetryService.setLocationChangedListener(new TelemetryService.LocationChangedListener() {
                @Override
                public void onLocationChanged(LocationCoordinate3D location) {
                    if (location != null) {
                        updateDistanceFromLocation(location);
                    }
                }
            });

            Log.d(TAG, "Telemetry listeners registered successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error registering telemetry listeners: " + e.getMessage(), e);
        }
    }

    /**
     * Set home location for distance calculation
     * This should be called when the operator's position is known or when drone takes off
     */
    public void setHomeLocation(double latitude, double longitude) {
        this.homeLatitude = latitude;
        this.homeLongitude = longitude;
        this.hasHomeLocation = true;
        
        Log.d(TAG, "Home location set: " + String.format("%.6f", latitude) + 
                  ", " + String.format("%.6f", longitude));
        
        // Reset distance when home location changes
        totalDistance = 0.0;
        updateDistanceDisplay(0.0);
    }

    /**
     * Update distance based on current drone location
     */
    private void updateDistanceFromLocation(LocationCoordinate3D droneLocation) {
        try {
            if (droneLocation == null || !hasHomeLocation) {
                return;
            }
            
            double droneLatitude = droneLocation.getLatitude();
            double droneLongitude = droneLocation.getLongitude();
            
            if (isValidGpsCoordinate(droneLatitude, droneLongitude)) {
                double distanceToHome = calculateDistance(homeLatitude, homeLongitude,
                        droneLatitude, droneLongitude);
                updateDistanceDisplay(distanceToHome);
                
                Log.v(TAG, "Distance from home to drone: " + String.format("%.2f", distanceToHome) + "m");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating distance from location: " + e.getMessage(), e);
        }
    }

    /**
     * Update distance based on GPS coordinates
     * Call this method when drone location changes (for manual updates)
     */
    public void updateDistance(double droneLatitude, double droneLongitude) {
        try {
            if (isValidGpsCoordinate(droneLatitude, droneLongitude) && hasHomeLocation) {
                double distanceToHome = calculateDistance(homeLatitude, homeLongitude,
                        droneLatitude, droneLongitude);
                updateDistanceDisplay(distanceToHome);
                Log.d(TAG, "Manual distance update: " + String.format("%.2f", distanceToHome) + "m");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating distance: " + e.getMessage(), e);
        }
    }

    /**
     * Update distance from home/operator position to drone
     */
    public void updateDistanceFromHome(double droneLatitude, double droneLongitude,
                                       double homeLatitude, double homeLongitude) {
        try {
            if (isValidGpsCoordinate(droneLatitude, droneLongitude) &&
                    isValidGpsCoordinate(homeLatitude, homeLongitude)) {

                // Update home location if provided
                if (homeLatitude != this.homeLatitude || homeLongitude != this.homeLongitude) {
                    setHomeLocation(homeLatitude, homeLongitude);
                }

                double distanceFromHome = calculateDistance(homeLatitude, homeLongitude,
                        droneLatitude, droneLongitude);
                updateDistanceDisplay(distanceFromHome);
                Log.d(TAG, "Distance from home to drone: " + String.format("%.2f", distanceFromHome) + "m");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating distance from home: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371000; // meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Check if GPS coordinates are valid
     */
    private boolean isValidGpsCoordinate(double latitude, double longitude) {
        return latitude != 0.0 && longitude != 0.0 &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180;
    }

    // UI Update Methods
    private void updateDistanceDisplay(double distance) {
        if (distanceTxt == null) return;

        uiHandler.post(() -> {
            try {
                String distanceText;
                if (distance < 1000) {
                    distanceText = String.format(Locale.getDefault(), "%.0fm", distance);
                } else {
                    distanceText = String.format(Locale.getDefault(), "%.2fkm", distance / 1000);
                }
                distanceTxt.setText(distanceText);
            } catch (Exception e) {
                Log.e(TAG, "Error updating distance display: " + e.getMessage(), e);
            }
        });
    }

    private void updateAltitudeDisplay(double altitude) {
        if (altitudeTxt == null) return;

        uiHandler.post(() -> {
            try {
                String altitudeText = String.format(Locale.getDefault(), "%.1fm", altitude);
                altitudeTxt.setText(altitudeText);
            } catch (Exception e) {
                Log.e(TAG, "Error updating altitude display: " + e.getMessage(), e);
            }
        });
    }

    private void updateHorizontalSpeedDisplay(double speed) {
        if (horizontalSpeedTxt == null) return;

        uiHandler.post(() -> {
            try {
                String speedText = String.format(Locale.getDefault(), "%.1fm/s", speed);
                horizontalSpeedTxt.setText(speedText);
            } catch (Exception e) {
                Log.e(TAG, "Error updating horizontal speed display: " + e.getMessage(), e);
            }
        });
    }

    private void updateVerticalSpeedDisplay(double speed) {
        if (verticalSpeedTxt == null) return;

        uiHandler.post(() -> {
            try {
                String speedText;
                if (speed > 0.1) {
                    speedText = String.format(Locale.getDefault(), "↑%.1fm/s", Math.abs(speed));
                } else if (speed < -0.1) {
                    speedText = String.format(Locale.getDefault(), "↓%.1fm/s", Math.abs(speed));
                } else {
                    speedText = "0.0m/s";
                }
                verticalSpeedTxt.setText(speedText);
            } catch (Exception e) {
                Log.e(TAG, "Error updating vertical speed display: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Reset all displays to default values
     */
    public void resetDisplay() {
        uiHandler.post(() -> {
            try {
                if (distanceTxt != null) distanceTxt.setText("0m");
                if (altitudeTxt != null) altitudeTxt.setText("0.0m");
                if (horizontalSpeedTxt != null) horizontalSpeedTxt.setText("0.0m/s");
                if (verticalSpeedTxt != null) verticalSpeedTxt.setText("0.0m/s");

                // Reset internal values
                currentAltitude = 0.0;
                currentHorizontalSpeed = 0.0;
                currentVerticalSpeed = 0.0;
                totalDistance = 0.0;

                Log.d(TAG, "Speed display reset to default values");
            } catch (Exception e) {
                Log.e(TAG, "Error resetting display: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Force refresh all displays with current telemetry values
     */
    public void forceRefresh() {
        try {
            if (telemetryService != null) {
                // Get current values from telemetry service
                double altitude = telemetryService.getCurrentAltitude();
                double horizontalVel = telemetryService.getCurrentHorizontalVelocity();
                double verticalVel = telemetryService.getCurrentVerticalVelocity();

                updateAltitudeDisplay(altitude);
                updateHorizontalSpeedDisplay(horizontalVel);
                updateVerticalSpeedDisplay(verticalVel);

                // Update current values
                currentAltitude = altitude;
                currentHorizontalSpeed = horizontalVel;
                currentVerticalSpeed = verticalVel;

                // Update distance if we have location data
                LocationCoordinate3D currentLocation = telemetryService.getCurrentLocation();
                if (currentLocation != null) {
                    updateDistanceFromLocation(currentLocation);
                }
            }

            Log.d(TAG, "Speed display force refresh completed");
        } catch (Exception e) {
            Log.e(TAG, "Error during force refresh: " + e.getMessage(), e);
        }
    }

    /**
     * Reset distance counter (useful for new missions)
     */
    public void resetDistance() {
        totalDistance = 0.0;
        hasHomeLocation = false;
        updateDistanceDisplay(0.0);
        Log.d(TAG, "Distance counter reset");
    }

    /**
     * Get current telemetry values
     */
    public double getCurrentAltitude() {
        return currentAltitude;
    }

    public double getCurrentHorizontalSpeed() {
        return currentHorizontalSpeed;
    }

    public double getCurrentVerticalSpeed() {
        return currentVerticalSpeed;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public boolean hasHomeLocation() {
        return hasHomeLocation;
    }

    public double getHomeLatitude() {
        return homeLatitude;
    }

    public double getHomeLongitude() {
        return homeLongitude;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        try {
            if (uiHandler != null) {
                uiHandler.removeCallbacksAndMessages(null);
            }

            // Clear references
            distanceTxt = null;
            altitudeTxt = null;
            horizontalSpeedTxt = null;
            verticalSpeedTxt = null;
            telemetryService = null;

            Log.d(TAG, "SpeedDisplayManager cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage(), e);
        }
    }
}
