package com.suleman.eagleeye.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.suleman.eagleeye.R;
import com.suleman.eagleeye.Services.TelemetryService;

import java.util.Locale;

/**
 * TelemetryDisplayManager - Centralized telemetry UI management
 * 
 * This class handles all telemetry display functionality for consistent UI updates
 * across ProjectInfoActivity, FPVCameraActivity, and MissionPlanner activities.
 * 
 * Features:
 * - Automatic telemetry listener setup
 * - Consistent icon selection for satellite, GPS, remote signals
 * - Thread-safe UI updates
 * - Proper resource cleanup
 * - Standardized telemetry display formatting
 * - Uses correct drawable resources as specified:
 *   - satelliteIcon: settlite_25, settlite_50, settlite_75, settlite_full
 *   - gpsSignalIcon: signal_0, signal_25, signal_50, signal_75, signal_full
 *   - remoteSignalIcon: remote_0, remote_25, remote_50, remote_75, remote_full
 * 
 * @author Suleman
 * @version 2.0 - Updated for DJI SDK V5 with TelemetryService integration
 * @date 2025-06-27
 */
public class TelemetryDisplayManager {
    private static final String TAG = "TelemetryDisplayManager";
    
    // Context and UI thread handler
    private final Context context;
    private final Handler uiHandler;
    
    // Services
    private TelemetryService telemetryService;
    
    // UI Components
    private TextView flightModeText;
    private TextView satelliteCountText;
    private TextView batteryText;
    private TextView remoteSignalText;
    private ImageView droneIcon;
    private ImageView satelliteIcon;
    private ImageView gpsSignalIcon;
    private ImageView batteryIcon;
    private ImageView remoteIcon;
    private ImageView remoteSignalIcon;
    
    // Additional telemetry components (optional)
    private TextView altitudeText;
    private TextView horizontalSpeedText;
    private TextView verticalSpeedText;
    private TextView distanceText;
    
    // State tracking
    private boolean isInitialized = false;
    private String activityName;
    
    /**
     * Constructor
     * @param context Activity context
     * @param activityName Name of the activity for logging purposes
     */
    public TelemetryDisplayManager(Context context, String activityName) {
        this.context = context;
        this.activityName = activityName;
        this.uiHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "TelemetryDisplayManager created for " + activityName);
    }

    public void initializeComponents(TextView flightModeText, TextView satelliteCountText, 
                                   TextView batteryText, TextView remoteSignalText,
                                   ImageView droneIcon, ImageView satelliteIcon, 
                                   ImageView gpsSignalIcon, ImageView batteryIcon,
                                   ImageView remoteIcon, ImageView remoteSignalIcon) {
        
        this.flightModeText = flightModeText;
        this.satelliteCountText = satelliteCountText;
        this.batteryText = batteryText;
        this.remoteSignalText = remoteSignalText;
        this.droneIcon = droneIcon;
        this.satelliteIcon = satelliteIcon;
        this.gpsSignalIcon = gpsSignalIcon;
        this.batteryIcon = batteryIcon;
        this.remoteIcon = remoteIcon;
        this.remoteSignalIcon = remoteSignalIcon;
        
        // Set initial default values
        resetTelemetryDisplay();
        
        isInitialized = true;
        Log.d(TAG, "Components initialized for " + activityName);
    }
    
    /**
     * Initialize additional telemetry components (for activities that support them)
     */
    public void initializeAdditionalComponents(TextView altitudeText, TextView horizontalSpeedText,
                                             TextView verticalSpeedText, TextView distanceText) {
        this.altitudeText = altitudeText;
        this.horizontalSpeedText = horizontalSpeedText;
        this.verticalSpeedText = verticalSpeedText;
        this.distanceText = distanceText;
        
        Log.d(TAG, "Additional telemetry components initialized for " + activityName);
    }
    public void setupTelemetryServices(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
        if (!isInitialized) {
            Log.w(TAG, "Components not initialized yet for " + activityName);
            return;
        }
        setupTelemetryListeners();
    }
    
    /**
     * Setup telemetry data listeners
     */
    private void setupTelemetryListeners() {
        if (telemetryService == null) {
            Log.w(TAG, "TelemetryService is null");
            return;
        }
        
        try {
            // Flight mode listener
            telemetryService.setFlightModeChangedListener(flightMode -> {
                uiHandler.post(() -> updateFlightModeUI(flightMode));
            });

            // GPS signal status listener
            telemetryService.setGpsSignalStatusChangedListener(signalLevel -> {
                uiHandler.post(() -> updateGpsSignalUI(signalLevel));
            });

            // GPS satellite count listener
            telemetryService.setGpsSatCountChangedListener(satCount -> {
                uiHandler.post(() -> updateSatelliteCountUI(satCount));
            });

            // Link signal quality listener (for remote signal)
            telemetryService.setLinkSignalQualityChangedListener(signalQuality -> {
                uiHandler.post(() -> updateRemoteSignalUI(signalQuality));
            });

            // Battery charge listener
            telemetryService.setBatteryChargeChangedListener(batteryPercentage -> {
                uiHandler.post(() -> updateBatteryUI(batteryPercentage));
            });
            
            // Dynamic telemetry listeners (if additional components are available)
            if (altitudeText != null) {
                telemetryService.setAltitudeChangedListener(altitude -> {
                    uiHandler.post(() -> updateAltitudeUI(altitude));
                });
            }
            
            if (horizontalSpeedText != null) {
                telemetryService.setHorizontalVelocityChangedListener(speed -> {
                    uiHandler.post(() -> updateHorizontalSpeedUI(speed));
                });
            }
            
            if (verticalSpeedText != null) {
                telemetryService.setVerticalVelocityChangedListener(speed -> {
                    uiHandler.post(() -> updateVerticalSpeedUI(speed));
                });
            }

            Log.d(TAG, "Telemetry listeners setup successfully for " + activityName);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up telemetry listeners: " + e.getMessage());
        }
    }

    private void updateFlightModeUI(String flightMode) {
        try {
            if (flightModeText != null) {
                flightModeText.setText(flightMode != null ? flightMode : "N/A");
                Log.v(TAG, "Flight mode updated: " + flightMode + " (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating flight mode UI: " + e.getMessage());
        }
    }
    
    /**
     * Update GPS signal strength display
     */
    private void updateGpsSignalUI(Integer signalLevel) {
        try {
            if (gpsSignalIcon != null && signalLevel != null) {
                int iconResource = getGpsSignalIcon(signalLevel);
                gpsSignalIcon.setImageResource(iconResource);
                Log.v(TAG, "GPS signal updated: " + signalLevel + " (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating GPS signal UI: " + e.getMessage());
        }
    }
    
    /**
     * Update satellite count display
     */
    private void updateSatelliteCountUI(Integer satCount) {
        try {
            if (satelliteCountText != null) {
                String countText = satCount != null ? String.format(Locale.getDefault(), "%03d", satCount) : "000";
                satelliteCountText.setText(countText);
                
                // Update satellite icon based on count
                if (satelliteIcon != null) {
                    int iconResource = getSatelliteIcon(satCount);
                    satelliteIcon.setImageResource(iconResource);
                }
                
                Log.v(TAG, "Satellite count updated: " + satCount + " (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating satellite count UI: " + e.getMessage());
        }
    }
    
    /**
     * Update remote signal display
     */
    private void updateRemoteSignalUI(Integer signalQuality) {
        try {
            // Update remote signal text if available
            if (remoteSignalText != null && signalQuality != null) {
                String qualityText = String.format(Locale.getDefault(), "%03d", signalQuality);
                remoteSignalText.setText(qualityText);
            }
            
            // Update remote signal icon
            if (remoteSignalIcon != null && signalQuality != null) {
                int iconResource = getRemoteSignalIcon(signalQuality);
                remoteSignalIcon.setImageResource(iconResource);
            }
            
            // Update remote icon based on signal quality
            if (remoteIcon != null && signalQuality != null) {
                int iconResource = getRemoteIcon(signalQuality);
                remoteIcon.setImageResource(iconResource);
            }
            
            Log.v(TAG, "Remote signal updated: " + signalQuality + " (" + activityName + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error updating remote signal UI: " + e.getMessage());
        }
    }
    
    /**
     * Update battery level display
     */
    private void updateBatteryUI(Integer batteryPercentage) {
        try {
            if (batteryText != null && batteryIcon != null && batteryPercentage != null) {
                batteryText.setText(batteryPercentage + "%");
                
                // Update battery icon based on level
                int iconResource = getBatteryIcon(batteryPercentage);
                batteryIcon.setImageResource(iconResource);
                
                Log.v(TAG, "Battery level updated: " + batteryPercentage + "% (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery UI: " + e.getMessage());
        }
    }
    
    /**
     * Update altitude display
     */
    private void updateAltitudeUI(Double altitude) {
        try {
            if (altitudeText != null && altitude != null) {
                String altitudeStr = String.format(Locale.getDefault(), "%.1f m", altitude.floatValue());
                altitudeText.setText(altitudeStr);
                Log.v(TAG, "Altitude updated: " + altitude + " (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating altitude UI: " + e.getMessage());
        }
    }
    
    /**
     * Update horizontal speed display
     */
    private void updateHorizontalSpeedUI(Double speed) {
        try {
            if (horizontalSpeedText != null && speed != null) {
                String speedStr = String.format(Locale.getDefault(), "%.1f m/s", speed);
                horizontalSpeedText.setText(speedStr);
                Log.v(TAG, "Horizontal speed updated: " + speed + " (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating horizontal speed UI: " + e.getMessage());
        }
    }
    
    /**
     * Update vertical speed display
     */
    private void updateVerticalSpeedUI(Double speed) {
        try {
            if (verticalSpeedText != null && speed != null) {
                String speedStr = String.format(Locale.getDefault(), "%.1f m/s", speed);
                verticalSpeedText.setText(speedStr);
                Log.v(TAG, "Vertical speed updated: " + speed + " (" + activityName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating vertical speed UI: " + e.getMessage());
        }
    }
    
    /**
     * Reset all telemetry displays to default values
     */
    public void resetTelemetryDisplay() {
        try {
            updateFlightModeUI("N/A");
            updateSatelliteCountUI(0);
            updateGpsSignalUI(0);
            updateBatteryUI(0);
            updateRemoteSignalUI(0);
            
            // Reset additional components if available
            if (altitudeText != null) {
                altitudeText.setText("0.0 m");
            }
            if (horizontalSpeedText != null) {
                horizontalSpeedText.setText("0.0 m/s");
            }
            if (verticalSpeedText != null) {
                verticalSpeedText.setText("0.0 m/s");
            }
            if (distanceText != null) {
                distanceText.setText("0.0 m");
            }
            
            Log.d(TAG, "Telemetry display reset to default values (" + activityName + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting telemetry display: " + e.getMessage());
        }
    }
    
    // Helper methods for icon selection - UPDATED TO USE CORRECT DRAWABLE NAMES
    
    /**
     * Get GPS signal icon resource based on signal level (0-5)
     * Uses: signal_0, signal_25, signal_50, signal_75, signal_full
     */
    private int getGpsSignalIcon(int signalLevel) {
        switch (Math.min(signalLevel, 5)) {
            case 0: return R.drawable.signal_0;
            case 1: return R.drawable.signal_25;
            case 2: return R.drawable.signal_50;
            case 3: return R.drawable.signal_75;
            case 4: return R.drawable.signal_75;
            case 5: return R.drawable.signal_full;
            default: return R.drawable.signal_0;
        }
    }
    
    /**
     * Get satellite icon resource based on satellite count
     * Uses: settlite_25, settlite_50, settlite_75, settlite_full
     */
    private int getSatelliteIcon(Integer satCount) {
        if (satCount == null || satCount <= 0) {
            return R.drawable.settlite_25;
        } else if (satCount < 4) {
            return R.drawable.settlite_25;
        } else if (satCount < 8) {
            return R.drawable.settlite_50;
        } else if (satCount < 12) {
            return R.drawable.settlite_75;
        } else {
            return R.drawable.settlite_full;
        }
    }
    
    /**
     * Get remote signal icon resource based on signal quality (0-100)
     * Uses: signal_0, signal_25, signal_50, signal_75, signal_full
     */
    private int getRemoteSignalIcon(int signalQuality) {
        if (signalQuality >= 80) {
            return R.drawable.signal_full;
        } else if (signalQuality >= 60) {
            return R.drawable.signal_75;
        } else if (signalQuality >= 40) {
            return R.drawable.signal_50;
        } else if (signalQuality >= 20) {
            return R.drawable.signal_25;
        } else {
            return R.drawable.signal_0;
        }
    }
    
    /**
     * Get remote controller icon resource based on signal quality (0-100)
     * Uses: remote_0, remote_25, remote_50, remote_75, remote_full
     */
    private int getRemoteIcon(int signalQuality) {
        if (signalQuality >= 80) {
            return R.drawable.remote_full;
        } else if (signalQuality >= 60) {
            return R.drawable.remote_75;
        } else if (signalQuality >= 40) {
            return R.drawable.remote_50;
        } else if (signalQuality >= 20) {
            return R.drawable.remote_25;
        } else {
            return R.drawable.remote_0;
        }
    }
    
    /**
     * Get battery icon resource based on battery percentage
     */
    private int getBatteryIcon(int batteryPercentage) {
        if (batteryPercentage > 75) {
            return R.drawable.bettery_full;
        } else if (batteryPercentage > 50) {
            return R.drawable.bettery_high;
        } else if (batteryPercentage > 25) {
            return R.drawable.bettery_medium;
        } else if (batteryPercentage > 10) {
            return R.drawable.bettery_low;
        } else {
            return R.drawable.bettery_empty;
        }
    }
    
    /**
     * Force refresh of all telemetry data (useful when activity resumes)
     */
    public void forceRefresh() {
        if (telemetryService != null) {
            telemetryService.forceInitialTelemetryUpdate();
            Log.d(TAG, "Forced telemetry refresh for " + activityName);
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        try {
            // Clear UI references
            flightModeText = null;
            satelliteCountText = null;
            batteryText = null;
            remoteSignalText = null;
            droneIcon = null;
            satelliteIcon = null;
            gpsSignalIcon = null;
            batteryIcon = null;
            remoteIcon = null;
            remoteSignalIcon = null;
            
            // Clear additional component references
            altitudeText = null;
            horizontalSpeedText = null;
            verticalSpeedText = null;
            distanceText = null;
            
            // Clear service references
            telemetryService = null;
            
            isInitialized = false;
            
            Log.d(TAG, "TelemetryDisplayManager cleanup completed for " + activityName);
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Check if manager is properly initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Get activity name for logging
     */
    public String getActivityName() {
        return activityName;
    }
}
