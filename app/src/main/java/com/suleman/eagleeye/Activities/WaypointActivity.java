package com.suleman.eagleeye.Activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import com.suleman.eagleeye.R;
import com.suleman.eagleeye.Services.CommandService_V5SDK;
import com.suleman.eagleeye.Services.TelemetryService;
import com.suleman.eagleeye.util.PermissionHelper;

import dji.sdk.keyvalue.value.common.LocationCoordinate3D;

/**
 * WaypointActivity displays Google Maps with waypoints and real-time drone location
 * Receives status updates from CommandService for waypoint mission progress
 */
public class WaypointActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "WaypointActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    // UI Components
    private GoogleMap googleMap;
    private TextView statusTextView;
    private Button startMissionButton;
    private Button stopMissionButton;
    private Button pauseResumeButton;
    
    // Services
    private CommandService_V5SDK commandService;
    private TelemetryService telemetryService;
    private boolean isCommandServiceBound = false;
    
    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng homeLocation;
    private Marker homeMarker;
    private Marker droneMarker;
    
    // Waypoints from CommandService
    private static final double[][] WAYPOINTS = {
        {33.695843675830965, 73.05188642388882},
        {33.695748383286784, 73.05199694013237},
        {33.69557785952293, 73.0520411466298},
        {33.69544411515762, 73.05189245204755},
        {33.695460833214675, 73.05175782316904},
        {33.69553773623518, 73.051635250608},
        {33.69563804441939, 73.05154683761317},
        {33.6957433678868, 73.05157697840686},
        {33.69587711178632, 73.05167945710544}
    };
    
    // Mission state
    private boolean missionInProgress = false;
    private boolean missionPaused = false;
    private Handler uiHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint);
        
        Log.d(TAG, "WaypointActivity created");
        
        initializeUI();
        initializeServices();
        initializeMap();
        checkPermissions();
        
        uiHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Initialize UI components
     */
    private void initializeUI() {
        statusTextView = findViewById(R.id.statusTextView);
        startMissionButton = findViewById(R.id.startMissionButton);
        stopMissionButton = findViewById(R.id.stopMissionButton);
        pauseResumeButton = findViewById(R.id.pauseResumeButton);
        
        // Initially disable mission control buttons
        stopMissionButton.setEnabled(false);
        pauseResumeButton.setEnabled(false);
        
        setupButtonListeners();
        updateStatus("Ready to start mission");
    }
    
    /**
     * Setup button click listeners
     */
    private void setupButtonListeners() {
        startMissionButton.setOnClickListener(v -> {
            // Check storage permissions before starting mission
            if (!PermissionHelper.hasStoragePermissions(this)) {
                PermissionHelper.showStoragePermissionExplanation(this);
                PermissionHelper.requestStoragePermissions(this);
                return;
            }
            
            if (commandService != null) {
                commandService.startWaypointMission();
                startMissionButton.setEnabled(false);
                stopMissionButton.setEnabled(true);
                pauseResumeButton.setEnabled(true);
                pauseResumeButton.setText("Pause");
                missionPaused = false;
            } else {
                Toast.makeText(this, "Command service not available", Toast.LENGTH_SHORT).show();
            }
        });
        
        stopMissionButton.setOnClickListener(v -> {
            if (commandService != null) {
                commandService.stopMission();
                resetMissionButtons();
            }
        });
        
        pauseResumeButton.setOnClickListener(v -> {
            if (commandService != null) {
                if (missionPaused) {
                    commandService.resumeMission();
                    pauseResumeButton.setText("Pause");
                    missionPaused = false;
                } else {
                    commandService.pauseMission();
                    pauseResumeButton.setText("Resume");
                    missionPaused = true;
                }
            }
        });
    }
    
    /**
     * Check and request all required permissions
     */
    private void checkPermissions() {
        // Check location permission
        checkLocationPermission();
        
        // Check storage permission for KMZ file saving
        if (!PermissionHelper.hasStoragePermissions(this)) {
            Log.d(TAG, "Storage permission not granted, will request when needed");
        }
    }
    
    /**
     * Reset mission control buttons to initial state
     */
    private void resetMissionButtons() {
        startMissionButton.setEnabled(true);
        stopMissionButton.setEnabled(false);
        pauseResumeButton.setEnabled(false);
        pauseResumeButton.setText("Pause");
        missionInProgress = false;
        missionPaused = false;
    }
    
    /**
     * Initialize services
     */
    private void initializeServices() {
        // Bind to CommandService
        Intent commandIntent = new Intent(this, CommandService_V5SDK.class);
        bindService(commandIntent, commandServiceConnection, Context.BIND_AUTO_CREATE);
        
        // Initialize TelemetryService (singleton pattern, not Android Service)
        telemetryService = TelemetryService.getInstance(this);
        telemetryService.initializeTelemetryService();
        
        // Register for broadcast messages from CommandService
        LocalBroadcastManager.getInstance(this).registerReceiver(
            waypointStatusReceiver,
                new IntentFilter(CommandService_V5SDK.ACTION_WAYPOINT_STATUS)
        );
    }
    
    /**
     * Initialize Google Maps
     */
    private void initializeMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }
    
    /**
     * Check and request location permission
     */
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission required for home location", Toast.LENGTH_LONG).show();
                // Use default location if permission denied
                setDefaultHomeLocation();
            }
        } else {
            // Handle storage permission results
            PermissionHelper.handlePermissionResult(this, requestCode, permissions, grantResults);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Handle storage permission results for Android 11+
        if (requestCode == PermissionHelper.MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
            PermissionHelper.handlePermissionResult(this, requestCode, new String[]{}, new int[]{});
        }
    }
    
    /**
     * Get current device location as home location
     */
    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
            
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                homeLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                Log.d(TAG, "Home location set: " + homeLocation.latitude + ", " + homeLocation.longitude);
                                updateMapWithHomeLocation();
                            } else {
                                // Use default location if GPS location not available
                                setDefaultHomeLocation();
                            }
                        }
                    });
        }
    }
    
    /**
     * Set default home location (near the waypoints)
     */
    private void setDefaultHomeLocation() {
        // Use center of waypoints area as default home
        homeLocation = new LatLng(33.69566, 73.0518);
        Log.d(TAG, "Default home location set: " + homeLocation.latitude + ", " + homeLocation.longitude);
        updateMapWithHomeLocation();
    }
    
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        Log.d(TAG, "Google Map ready");
        
        // Configure map
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        
        // Add waypoint markers
        addWaypointMarkers();
        
        // Add home location if available
        if (homeLocation != null) {
            updateMapWithHomeLocation();
        }
        
        // Setup drone location listener
        setupDroneLocationListener();
        
        // Focus on waypoints area
        focusOnWaypoints();
    }
    
    /**
     * Add waypoint markers to the map
     */
    private void addWaypointMarkers() {
        if (googleMap == null) return;
        
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                .width(5);
        
        for (int i = 0; i < WAYPOINTS.length; i++) {
            LatLng waypoint = new LatLng(WAYPOINTS[i][0], WAYPOINTS[i][1]);
            
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(waypoint)
                    .title("Waypoint " + (i + 1))
                    .snippet("Lat: " + WAYPOINTS[i][0] + ", Lng: " + WAYPOINTS[i][1])
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
            
            googleMap.addMarker(markerOptions);
            polylineOptions.add(waypoint);
        }
        
        // Add polyline connecting waypoints
        googleMap.addPolyline(polylineOptions);
        Log.d(TAG, "Added " + WAYPOINTS.length + " waypoint markers to map");
    }
    
    /**
     * Update map with home location marker
     */
    private void updateMapWithHomeLocation() {
        if (googleMap == null || homeLocation == null) return;
        
        // Remove existing home marker
        if (homeMarker != null) {
            homeMarker.remove();
        }
        
        // Add home marker
        MarkerOptions homeMarkerOptions = new MarkerOptions()
                .position(homeLocation)
                .title("Home Location")
                .snippet("Launch/Landing Point")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        
        homeMarker = googleMap.addMarker(homeMarkerOptions);
        Log.d(TAG, "Home location marker added to map");
    }
    
    /**
     * Setup real-time drone location listener
     */
    private void setupDroneLocationListener() {
        // Since TelemetryService is a singleton class (not Android Service), 
        // we'll use it directly to get location updates
        if (telemetryService != null) {
            telemetryService.setLocationChangedListener(new TelemetryService.LocationChangedListener() {
                @Override
                public void onLocationChanged(LocationCoordinate3D location) {
                    uiHandler.post(() -> updateDroneLocationOnMap(location));
                }
            });
        }
        
        Log.d(TAG, "Drone location listener setup complete");
    }
    
    /**
     * Update drone location marker on map
     */
    private void updateDroneLocationOnMap(LocationCoordinate3D location) {
        if (googleMap == null || location == null) return;
        
        LatLng dronePosition = new LatLng(location.getLatitude(), location.getLongitude());
        
        // Remove existing drone marker
        if (droneMarker != null) {
            droneMarker.remove();
        }
        
        // Add/update drone marker
        MarkerOptions droneMarkerOptions = new MarkerOptions()
                .position(dronePosition)
                .title("Drone Location")
                .snippet("Alt: " + String.format("%.1f", location.getAltitude()) + "m")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.drone_new)); // Use drone_new.xml
        
        droneMarker = googleMap.addMarker(droneMarkerOptions);
        
        // Optionally move camera to follow drone during mission
        if (missionInProgress) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(dronePosition, 18));
        }
    }
    
    /**
     * Focus camera on waypoints area
     */
    private void focusOnWaypoints() {
        if (googleMap == null) return;
        
        // Calculate center of waypoints
        double centerLat = 0;
        double centerLng = 0;
        
        for (double[] waypoint : WAYPOINTS) {
            centerLat += waypoint[0];
            centerLng += waypoint[1];
        }
        
        centerLat /= WAYPOINTS.length;
        centerLng /= WAYPOINTS.length;
        
        LatLng center = new LatLng(centerLat, centerLng);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 16));
        Log.d(TAG, "Camera focused on waypoints area");
    }
    
    /**
     * Update status display
     */
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            statusTextView.setText(status);
        });
    }
    
    // Service connection for CommandService
    private ServiceConnection commandServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CommandService_V5SDK.CommandServiceBinder binder = (CommandService_V5SDK.CommandServiceBinder) service;
            commandService = binder.getService();
            isCommandServiceBound = true;
            Log.d(TAG, "CommandService connected");
            
            // Enable start button once service is connected
            runOnUiThread(() -> startMissionButton.setEnabled(true));
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            commandService = null;
            isCommandServiceBound = false;
            Log.d(TAG, "CommandService disconnected");
        }
    };
    
    // Broadcast receiver for waypoint status updates
    private BroadcastReceiver waypointStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String statusType = intent.getStringExtra(CommandService_V5SDK.EXTRA_STATUS_TYPE);
            String message = intent.getStringExtra(CommandService_V5SDK.EXTRA_STATUS_MESSAGE);
            updateStatus(message);

            // Update mission state based on status
            switch (statusType) {
                case CommandService_V5SDK.STATUS_MISSION_STARTED:
                    missionInProgress = true;
                    break;
                case CommandService_V5SDK.STATUS_MISSION_COMPLETED:
                case CommandService_V5SDK.STATUS_RETURNING_HOME:
                case CommandService_V5SDK.STATUS_ERROR:
                    missionInProgress = false;
                    runOnUiThread(() -> resetMissionButtons());
                    break;
            }
            
            Log.d(TAG, "Received status update: " + statusType + " - " + message);
        }
    };
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unbind services
        if (isCommandServiceBound) {
            unbindService(commandServiceConnection);
            isCommandServiceBound = false;
        }
        
        // Cleanup TelemetryService (singleton)
        if (telemetryService != null) {
            telemetryService.setLocationChangedListener(null);
        }
        
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(waypointStatusReceiver);
        
        Log.d(TAG, "WaypointActivity destroyed");
    }
}
