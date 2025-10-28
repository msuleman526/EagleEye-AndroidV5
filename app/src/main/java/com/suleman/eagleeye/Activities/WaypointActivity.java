package com.suleman.eagleeye.Activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dji.wpmzsdk.common.utils.kml.model.Location2D;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import com.suleman.eagleeye.R;
import com.suleman.eagleeye.Services.CommandService_V5SDK;
import com.suleman.eagleeye.Services.TelemetryService;
import com.suleman.eagleeye.models.Flight;
import com.suleman.eagleeye.models.FlightAddress;
import com.suleman.eagleeye.models.MissionSetting;
import com.suleman.eagleeye.models.Obstacle;
import com.suleman.eagleeye.models.Project;
import com.suleman.eagleeye.models.WaypointSetting;
import com.suleman.eagleeye.util.Helper;
import com.suleman.eagleeye.util.OtherHelper;
import com.suleman.eagleeye.util.PermissionHelper;
import com.suleman.eagleeye.util.SessionUtils;

import java.util.ArrayList;
import java.util.List;

import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
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
    private ArrayList<Marker> points;
    private Marker pointOfInterest;
    private Button startMissionButton;
    private ImageView stopMissionButton;
    private ImageView pauseResumeButton;
    private ArrayList<WaypointSetting> waypointsList;
    private Polyline waypointPolyline;
    // Services
    private CommandService_V5SDK commandService;
    private TelemetryService telemetryService;
    private boolean isCommandServiceBound = false;
    private Project currentProject;
    
    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng homeLocation;
    private Marker homeMarker;
    private Marker droneMarker;
    private boolean missionInProgress = false;
    private boolean missionPaused = false;
    private Handler uiHandler;
    MissionSetting missionSetting;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint);

        currentProject = (Project) getIntent().getSerializableExtra("project");
        waypointsList = new ArrayList<>();
        points = new ArrayList<>();
        initializeUI();
        initializeServices();
        initializeMap();
        checkPermissions();
        uiHandler = new Handler(Looper.getMainLooper());
    }

    void showMessage(String message){
        runOnUiThread(() -> {
            statusTextView.setVisibility(View.VISIBLE);
            statusTextView.setText(message);
        });

        statusTextView.postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(() -> statusTextView.setVisibility(View.GONE));
            }
        }, 3000);
    }

    private void setUpCurrentProject(){
        if(currentProject != null){
            missionSetting = new MissionSetting();
            missionSetting.poiLocation = new Location2D(Double.parseDouble(currentProject.latitude), Double.parseDouble(currentProject.longitude));

            ArrayList<WaypointSetting> waypointSettingArrayList = new ArrayList<>();
            double maxHeight = currentProject.must_height;
            List<Obstacle> obstacles = currentProject.getObstacles();
            for (Obstacle obstacle : obstacles) {
                if (obstacle.height > maxHeight) {
                    maxHeight = obstacle.height;
                }
            }
            if (currentProject.is_grid) {
                showMessage("This Flight path is not currently implemented.");
                return;
            }

            double projLat = Double.parseDouble(currentProject.latitude);
            double projLng = Double.parseDouble(currentProject.longitude);
            LatLng homePosition = new LatLng(projLat, projLng);
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(homePosition)
                    .title("Home - " + currentProject.name)
                    .snippet("Project Location")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.home_marker));

            pointOfInterest = googleMap.addMarker(markerOptions);

            if (pointOfInterest != null) {
                pointOfInterest.setVisible(true);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(homePosition, 20f));
                uiHandler.postDelayed(() -> {
                    try {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(homePosition, 20f));
                    } catch (Exception e) {}
                }, 500);
            } else {
                showMessage("Failed to create home marker on map");
            }

            // 4. Process non-grid flight
            double subjectPhotoHeight = currentProject.height_of_house + 45;
            List<FlightAddress> flightPath = currentProject.getWaypointList();
            double maxObstacleHeight = maxHeight + 10;

            if (!flightPath.isEmpty()) {
                double[] heights = {150, subjectPhotoHeight,
                        maxObstacleHeight};
                for (int i = 0; i < heights.length; i++) {
                    double height = heights[i];
                    drawWaypoint(Double.parseDouble(currentProject.latitude), Double.parseDouble(currentProject.longitude), currentProject, height);
                }

                // 4. Process remaining waypoints with maxObstacleHeight
                for (int i = 1; i < flightPath.size(); i++) {
                    FlightAddress waypoint = flightPath.get(i);
                    drawWaypoint(waypoint.lat, waypoint.lng,
                            currentProject, maxObstacleHeight);
                }
            }
        }
    }

    public void drawWaypoint(double lat, double lng, Project project, double height) {
        try {
            // Step 1: Create a new waypoint
            WaypointSetting waypointSetting = new WaypointSetting();
            String waypointName = "Waypoint " + (points.size() + 1);
            waypointSetting.name = waypointName;
            if (googleMap != null) {
                LatLng position = new LatLng(lat, lng);
                int markerNumber = points.size() + 1;
                BitmapDescriptor waypointIcon = createWaypointMarker(markerNumber);

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(position)
                        .title(waypointName)
                        .snippet("Waypoint #" + markerNumber + " - Altitude: "+ height + "f")
                        .icon(waypointIcon)
                        .anchor(0.5f, 0.5f);

                Marker marker = googleMap.addMarker(markerOptions);

                if (marker != null) {
                    marker.setVisible(true);
                    points.add(marker);
                } else {
                    showMessage("Failed to create waypoint marker");
                    return;
                }
            } else {
                showMessage("Map not ready - cannot add waypoint");
                return;
            }
            // Step 3: Set coordinates
            waypointSetting.latitude = lat;
            waypointSetting.longitude = lng;
            if (pointOfInterest != null) {Location currentMarkerLocation = new Location("waypoint");
                currentMarkerLocation.setLatitude(lat);
                currentMarkerLocation.setLongitude(lng);
                LatLng poiPosition = pointOfInterest.getPosition();
                Location poiLocation = new Location("poi");
                poiLocation.setLatitude(poiPosition.latitude);
                poiLocation.setLongitude(poiPosition.longitude);
                double angle = OtherHelper.getAngleBetweenPoints(currentMarkerLocation, poiLocation);
                waypointSetting.gimbalPitchAngle = angle;
            } else {
                waypointSetting.gimbalPitchAngle = 0.0;
            }
            if (waypointsList.isEmpty()) {
                waypointSetting.gimbalPitch = 0;
            } else {
                // Check the last waypoint's gimbal pitch
                WaypointSetting lastWaypoint = waypointsList.get(waypointsList.size() - 1);
                if (lastWaypoint.gimbalPitch == 0) {
                    waypointSetting.gimbalPitch = -90;
                } else {
                    waypointSetting.gimbalPitch = 0;
                }
            }
            if (project != null && project.id != 0 && height != 0) {
                waypointSetting.altitude = height * 0.3048;
                float pitch = OtherHelper.calculatePitchAngle(new LocationCoordinate2D(lat, lng), new LocationCoordinate2D(pointOfInterest.getPosition().latitude, pointOfInterest.getPosition().longitude),height * 0.3048,  missionSetting.poiHeight);
            } else {
                waypointSetting.altitude = 30.0;
            }
            if (points.size() > 0) {
                Marker lastMarker = points.get(points.size() - 1);
                if (lastMarker != null) {
                    String newSnippet = "Waypoint #" + points.size() + " - Altitude: " + String.format("%.1fm", waypointSetting.altitude);
                }
            }
            waypointsList.add(waypointSetting);
            SessionUtils.saveWaypoints(waypointsList);
            updateWaypointPolyline();
        } catch (Exception e) {
            showMessage("Error creating waypoint: " + e.getMessage());
        }
    }

    private void updateWaypointPolyline() {
        try {
            if (googleMap == null) {
                return;
            }
            if (waypointPolyline != null) {
                waypointPolyline.remove();
                waypointPolyline = null;
            }
            if (!waypointsList.isEmpty()) {
                PolylineOptions polylineOptions = new PolylineOptions()
                        .color(getResources().getColor(android.R.color.holo_blue_bright))
                        .width(8f)
                        .geodesic(true);
                for (WaypointSetting waypoint : waypointsList) {
                    if (waypoint.latitude != null && waypoint.longitude != null) {
                        polylineOptions.add(new LatLng(waypoint.latitude, waypoint.longitude));
                    }
                }
                if (polylineOptions.getPoints().size() >= 2) {
                    waypointPolyline = googleMap.addPolyline(polylineOptions);
                }
            }
        } catch (Exception e) {
            // Error updating waypoint polyline
        }
    }

    BitmapDescriptor createWaypointMarker(int waypointNumber) {
        try {
            // Use the same map_marker resource as FlightWaypointsActivity
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.map_marker);

            if (originalBitmap == null) {
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
            }

            // Create mutable copy to draw on
            Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);

            // Set up paint for the number text (same as FlightWaypointsActivity)
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(35);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            // Draw the number on the marker
            String numberText = String.valueOf(waypointNumber);
            Rect textBounds = new Rect();
            textPaint.getTextBounds(numberText, 0, numberText.length(), textBounds);

            // Position text in center of marker (same calculation as FlightWaypointsActivity)
            float x = mutableBitmap.getWidth() / 2.0f;
            float y = (mutableBitmap.getHeight() / 3.0f) - textBounds.exactCenterY();
            canvas.drawText(numberText, x, y, textPaint);
            return BitmapDescriptorFactory.fromBitmap(mutableBitmap);

        } catch (Exception e) {
            // Fallback to default green marker if creation fails
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        }
    }
    
    /**
     * Initialize UI components
     */
    private void initializeUI() {
        statusTextView = findViewById(R.id.statusTextView);
        startMissionButton = findViewById(R.id.startMissionButton);
        stopMissionButton = findViewById(R.id.stopMissionButton);
        pauseResumeButton = findViewById(R.id.pauseMissionButton);
        
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
                startMissionButton.setVisibility(View.GONE);
                stopMissionButton.setEnabled(true);
                stopMissionButton.setVisibility(View.VISIBLE);
                pauseResumeButton.setEnabled(true);
                pauseResumeButton.setVisibility(View.VISIBLE);
                stopMissionButton.setVisibility(View.GONE);
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
                    pauseResumeButton.setImageDrawable(getDrawable(R.drawable.pause));
                    pauseResumeButton.setVisibility(View.VISIBLE);
                    stopMissionButton.setVisibility(View.VISIBLE);
                    stopMissionButton.setVisibility(View.GONE);
                    missionPaused = false;
                } else {
                    commandService.pauseMission();
                    pauseResumeButton.setImageDrawable(getDrawable(R.drawable.resume));
                    pauseResumeButton.setVisibility(View.VISIBLE);
                    stopMissionButton.setVisibility(View.VISIBLE);
                    stopMissionButton.setVisibility(View.GONE);
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
        startMissionButton.setVisibility(View.VISIBLE);
        stopMissionButton.setEnabled(false);
        stopMissionButton.setVisibility(View.GONE);
        pauseResumeButton.setEnabled(false);
        pauseResumeButton.setVisibility(View.GONE);
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
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        if (homeLocation != null) {
            updateMapWithHomeLocation();
        }

        setUpCurrentProject();

        setupDroneLocationListener();
    }
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
        Log.d(TAG, "Home location marker added to map.");
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

    private void updateStatus(String status) {
        showMessage(status);
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
