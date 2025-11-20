package io.empowerbits.sightflight.Activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.empowerbits.sightflight.Services.ConnectionStateManager;
import io.empowerbits.sightflight.Services.TelemetryService;
import io.empowerbits.sightflight.util.ServiceManager;
import io.empowerbits.sightflight.util.SpeedDisplayManager;
import io.empowerbits.sightflight.util.TelemetryDisplayManager;

/**
 * SAMPLE ACTIVITY - How to use TelemetryService with Display Managers
 * 
 * This demonstrates the proper way to integrate TelemetryService and ConnectionService
 * with TelemetryDisplayManager and SpeedDisplayManager in any Activity.
 * 
 * IMPORTANT: This is a demo file showing the integration pattern.
 * Copy this pattern to your actual activities like:
 * - ProjectInfoActivity
 * - FPVCameraActivity  
 * - MissionPlanner
 * 
 * @author Suleman
 * @version 1.0
 * @date 2025-06-27
 */
public class SampleTelemetryActivity extends AppCompatActivity {
    private static final String TAG = "SampleTelemetryActivity";
    
    // Services
    private TelemetryService telemetryService;
    boolean isDroneConnected = false;
    private ServiceManager serviceManager;
    
    // Display Managers
    private TelemetryDisplayManager telemetryDisplayManager;
    private SpeedDisplayManager speedDisplayManager;
    
    // UI Components for telemetry_view.xml
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
    
    // UI Components for speed_view.xml  
    private TextView distanceTxt;
    private TextView altitudeTxt;
    private TextView horizontalSpeedTxt;
    private TextView verticalSpeedTxt;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set your layout that includes telemetry_view and/or speed_view
        // setContentView(R.layout.activity_sample_telemetry);
        
        Log.d(TAG, "SampleTelemetryActivity created");
        
        // Initialize services and display managers
        initializeServices();
        initializeDisplayManagers();
        setupTelemetryViews();
    }
    
    /**
     * Initialize all services using ServiceManager
     */
    private void initializeServices() {
        try {
            Log.d(TAG, "Initializing services...");
            
            // Get ServiceManager instance and initialize all services
            serviceManager = ServiceManager.getInstance(this);
            serviceManager.initializeAllServices();
            ConnectionStateManager.getInstance().getConnectionState().observe(this, isConnected -> {
                if (isConnected) {
                    isDroneConnected = true;
                    serviceManager.getTelemetryService().startTelemetryMonitoring();
                    serviceManager.getTelemetryService().forceInitialTelemetryUpdate();
                } else {
                    isDroneConnected = false;
                    serviceManager.getTelemetryService().stopTelemetryMonitoring();
                }
            });
            ConnectionStateManager.getInstance().getDroneName().observe(this, droneName -> {
            });
            isDroneConnected = ConnectionStateManager.getInstance().isCurrentlyConnected();
            telemetryService = serviceManager.getTelemetryService();
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing services: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize display managers
     */
    private void initializeDisplayManagers() {
        try {
            Log.d(TAG, "Initializing display managers...");
            
            // Create display managers
            telemetryDisplayManager = new TelemetryDisplayManager(this, "SampleTelemetryActivity");
            speedDisplayManager = new SpeedDisplayManager(this);
            
            Log.d(TAG, "Display managers created");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing display managers: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup telemetry views - Find UI components and configure display managers
     */
    private void setupTelemetryViews() {
        try {
            Log.d(TAG, "Setting up telemetry views...");
            
            // Find UI components from your layout
            findTelemetryViewComponents();
            findSpeedViewComponents();
            
            // Initialize display managers with UI components
            if (telemetryDisplayManager != null) {
                telemetryDisplayManager.initializeComponents(
                    flightModeText, satelliteCountText, batteryText, remoteSignalText,
                    droneIcon, satelliteIcon, gpsSignalIcon, batteryIcon, 
                    remoteIcon, remoteSignalIcon
                );
                
                // Setup services with display manager
                telemetryDisplayManager.setupTelemetryServices(telemetryService);
            }
            
            if (speedDisplayManager != null) {
                speedDisplayManager.initializeComponents(
                    distanceTxt, altitudeTxt, horizontalSpeedTxt, verticalSpeedTxt
                );

                speedDisplayManager.setupTelemetryServices(telemetryService);
            }
            
            Log.d(TAG, "Telemetry views setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up telemetry views: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find UI components for telemetry display
     * Update these IDs to match your actual layout
     */
    private void findTelemetryViewComponents() {
        try {
            // TODO: Update these IDs to match your actual telemetry_view.xml layout
            /*
            flightModeText = findViewById(R.id.flight_mode_text);
            satelliteCountText = findViewById(R.id.satellite_count_text);
            batteryText = findViewById(R.id.battery_text);
            remoteSignalText = findViewById(R.id.remote_signal_text);
            droneIcon = findViewById(R.id.drone_icon);
            satelliteIcon = findViewById(R.id.satellite_icon);
            gpsSignalIcon = findViewById(R.id.gps_signal_icon);
            batteryIcon = findViewById(R.id.battery_icon);
            remoteIcon = findViewById(R.id.remote_icon);
            remoteSignalIcon = findViewById(R.id.remote_signal_icon);
            */
            
            Log.d(TAG, "Telemetry view components found");
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding telemetry view components: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find UI components for speed display
     * Update these IDs to match your actual layout
     */
    private void findSpeedViewComponents() {
        try {
            // TODO: Update these IDs to match your actual speed_view.xml layout
            /*
            distanceTxt = findViewById(R.id.distance_txt);
            altitudeTxt = findViewById(R.id.altitude_txt);
            horizontalSpeedTxt = findViewById(R.id.horizontal_speed_txt);
            verticalSpeedTxt = findViewById(R.id.vertical_speed_txt);
            */
            
            Log.d(TAG, "Speed view components found");
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding speed view components: " + e.getMessage(), e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        Log.d(TAG, "Activity resumed - refreshing telemetry data");
        
        // Force refresh telemetry data when activity resumes
        if (telemetryDisplayManager != null) {
            telemetryDisplayManager.forceRefresh();
        }
        
        if (speedDisplayManager != null) {
            speedDisplayManager.forceRefresh();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        Log.d(TAG, "Activity destroyed - cleaning up");
        
        // Cleanup display managers
        if (telemetryDisplayManager != null) {
            telemetryDisplayManager.cleanup();
            telemetryDisplayManager = null;
        }
        
        if (speedDisplayManager != null) {
            speedDisplayManager.cleanup();
            speedDisplayManager = null;
        }
        
        // Note: Don't cleanup services here as they may be used by other activities
        // Services will be cleaned up by Application class or when app is destroyed
        
        Log.d(TAG, "Activity cleanup completed");
    }
    
    /**
     * EXAMPLE: How to set home location for distance calculation
     * Call this when you know the operator's position or when drone takes off
     */
    private void setHomeLocation(double latitude, double longitude) {
        if (speedDisplayManager != null) {
            speedDisplayManager.setHomeLocation(latitude, longitude);
            Log.d(TAG, "Home location set for distance calculation");
        }
    }
    
    /**
     * EXAMPLE: How to manually update distance (if needed)
     */
    private void updateDroneDistance(double droneLatitude, double droneLongitude) {
        if (speedDisplayManager != null) {
            speedDisplayManager.updateDistance(droneLatitude, droneLongitude);
        }
    }
    
    /**
     * EXAMPLE: How to reset distance counter for new mission
     */
    private void resetDistanceForNewMission() {
        if (speedDisplayManager != null) {
            speedDisplayManager.resetDistance();
            Log.d(TAG, "Distance counter reset for new mission");
        }
    }
    
    /**
     * EXAMPLE: How to get current telemetry values
     */
    private void logCurrentTelemetryValues() {
        if (telemetryService != null) {
            Log.d(TAG, "=== Current Telemetry Values ===");
            Log.d(TAG, "Flight Mode: " + telemetryService.getCurrentFlightMode());
            Log.d(TAG, "GPS Signal: " + telemetryService.getCurrentGpsSignalLevel());
            Log.d(TAG, "Satellites: " + telemetryService.getCurrentSatelliteCount());
            Log.d(TAG, "Remote Signal: " + telemetryService.getCurrentLinkSignalQuality() + "%");
            Log.d(TAG, "Battery: " + telemetryService.getCurrentBatteryPercentage() + "%");
            Log.d(TAG, "Altitude: " + String.format("%.1f", telemetryService.getCurrentAltitude()) + "m");
            Log.d(TAG, "H-Speed: " + String.format("%.1f", telemetryService.getCurrentHorizontalVelocity()) + "m/s");
            Log.d(TAG, "V-Speed: " + String.format("%.1f", telemetryService.getCurrentVerticalVelocity()) + "m/s");
            
            if (telemetryService.getCurrentLocation() != null) {
                Log.d(TAG, "Location: " + 
                      String.format("%.6f", telemetryService.getCurrentLocation().getLatitude()) + ", " +
                      String.format("%.6f", telemetryService.getCurrentLocation().getLongitude()));
            }
            Log.d(TAG, "================================");
        }
    }
}
