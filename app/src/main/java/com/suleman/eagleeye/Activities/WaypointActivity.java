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
import androidx.fragment.app.FragmentTransaction;
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

import com.suleman.eagleeye.Fragments.CameraFeedFragment;
import com.google.gson.Gson;
import com.suleman.eagleeye.ApiResponse.FlightLogResponse;
import com.suleman.eagleeye.R;
import com.suleman.eagleeye.Retrofit.ApiClient;
import com.suleman.eagleeye.Services.CommandService_V5SDK;
import com.suleman.eagleeye.Services.ConnectionStateManager;
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
import com.suleman.eagleeye.util.SpeedDisplayManager;
import com.suleman.eagleeye.util.TelemetryDisplayManager;
import com.suleman.eagleeye.util.UserSessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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
    private ImageView connectionBar;
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
    private Double currentDroneHeading = 0.0;
    private boolean missionInProgress = false;
    private boolean missionPaused = false;
    private Handler uiHandler;
    MissionSetting missionSetting;
    private CameraFeedFragment cameraFeedFragment;
    private TelemetryDisplayManager telemetryDisplayManager;
    private SpeedDisplayManager speedDisplayManager;

    // Flight logging
    private UserSessionManager userSessionManager;
    private com.suleman.eagleeye.models.Log currentFlightLog;
    private int currentLastWaypoint = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint);

        currentProject = (Project) getIntent().getSerializableExtra("project");
        waypointsList = new ArrayList<>();
        points = new ArrayList<>();
        uiHandler = new Handler(Looper.getMainLooper());

        // Initialize SessionUtils for flight ID caching
        SessionUtils.initialize(this);

        // Initialize user session manager for API token
        userSessionManager = new UserSessionManager(this);

        initializeUI();
        initializeServices();
        initializeDisplayManagers();
        initializeMap();
        checkPermissions();
        initCameraFeedFragment();

        updateConnection(ConnectionStateManager.getInstance().isCurrentlyConnected());
        ConnectionStateManager.getInstance().getConnectionState().observe(this, isConnected -> {
            Log.d(TAG, "🔗 Connection state changed: " + isConnected);
            updateConnection(isConnected);
        });
    }

    void updateConnection(boolean isConnected){
        if(isConnected){
            connectionBar.setImageDrawable(getDrawable(R.drawable.connected_bar));
        }else{
            connectionBar.setImageDrawable(getDrawable(R.drawable.disconnected_bar));
        }
    }

    private void initializeDisplayManagers() {
        try {
            Log.d(TAG, "Initializing display managers...");

            // Create display managers
            telemetryDisplayManager = new TelemetryDisplayManager(this, "WaypointActivity");
            speedDisplayManager = new SpeedDisplayManager(this);

            // Initialize TelemetryDisplayManager UI components from telemetry_view.xml
            TextView flightModeText = findViewById(R.id.flightModeText);
            TextView satelliteCountText = findViewById(R.id.satelliteCountText);
            TextView batteryText = findViewById(R.id.batteryText);
            TextView remoteSignalText = findViewById(R.id.remoteSignalText);
            ImageView droneIcon = findViewById(R.id.droneIcon);
            ImageView satelliteIcon = findViewById(R.id.satelliteIcon);
            ImageView gpsSignalIcon = findViewById(R.id.gpsSignalIcon);
            ImageView batteryIcon = findViewById(R.id.batteryIcon);
            ImageView remoteIcon = findViewById(R.id.remoteIcon);
            ImageView remoteSignalIcon = findViewById(R.id.remoteSignalIcon);

            telemetryDisplayManager.initializeComponents(
                flightModeText, satelliteCountText, batteryText, remoteSignalText,
                droneIcon, satelliteIcon, gpsSignalIcon, batteryIcon, remoteIcon, remoteSignalIcon
            );

            // Initialize SpeedDisplayManager UI components from speed_view.xml
            TextView distanceTxt = findViewById(R.id.distanceTxt);
            TextView altitudeTxt = findViewById(R.id.altitudeTxt);
            TextView horizontalSpeedTxt = findViewById(R.id.horizontalSpeedTxt);
            TextView verticalSpeedTxt = findViewById(R.id.verticalSpeedTxt);

            speedDisplayManager.initializeComponents(
                distanceTxt, altitudeTxt, horizontalSpeedTxt, verticalSpeedTxt
            );

            // Setup telemetry services for both managers
            if (telemetryService != null) {
                telemetryDisplayManager.setupTelemetryServices(telemetryService);
                speedDisplayManager.setupTelemetryServices(telemetryService);

                // Set home location for distance calculation if available
                if (homeLocation != null) {
                    speedDisplayManager.setHomeLocation(homeLocation.latitude, homeLocation.longitude);
                }
            }

            Log.d(TAG, "Display managers initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing display managers: " + e.getMessage(), e);
        }
    }

    private void initCameraFeedFragment() {
        // Check if fragment already exists
        cameraFeedFragment = (CameraFeedFragment) getSupportFragmentManager()
                .findFragmentById(R.id.cameraFeedContainer);
        if (cameraFeedFragment == null) {
            cameraFeedFragment = new CameraFeedFragment();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.cameraFeedContainer, cameraFeedFragment);
            transaction.commit();
        } else {
            Log.d(TAG, "✅ Camera fragment already exists - reusing");
        }
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
        }, 5000);
    }

    private void setUpCurrentProject(){
        if(currentProject != null){
            missionSetting = new MissionSetting();
            missionSetting.poiLocation = new Location2D(Double.parseDouble(currentProject.latitude), Double.parseDouble(currentProject.longitude));

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
                float gimbalPitch = OtherHelper.calculatePitchAngle(new LocationCoordinate2D(lat, lng), new LocationCoordinate2D(pointOfInterest.getPosition().latitude, pointOfInterest.getPosition().longitude),
                        height * 0.3048,
                        missionSetting.poiHeight);
                if (gimbalPitch > 90) {
                    gimbalPitch = 90;
                } else if (gimbalPitch < -90) {
                    gimbalPitch = -90;
                }
                waypointSetting.gimbalPitchAngle = (double) gimbalPitch;
            } else {
                waypointSetting.gimbalPitchAngle = 0.0;
            }

            waypointSetting.altitude = height * 0.3048;
            Log.d("Waypoint Angle", "" + waypointSetting.gimbalPitchAngle);
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
        connectionBar = findViewById(R.id.connectionBar);
        findViewById(R.id.backBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        
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
                PermissionHelper.requestStoragePermissions(this);
                return;
            }
            
            if (commandService != null) {
                commandService.startWaypointMission(missionSetting, waypointsList);
                startMissionButton.setEnabled(false);
                startMissionButton.setVisibility(View.GONE);
                stopMissionButton.setEnabled(true);
                stopMissionButton.setVisibility(View.VISIBLE);
                pauseResumeButton.setEnabled(true);
                pauseResumeButton.setVisibility(View.VISIBLE);
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
                    missionPaused = false;
                } else {
                    commandService.pauseMission();
                    pauseResumeButton.setImageDrawable(getDrawable(R.drawable.resume));
                    pauseResumeButton.setVisibility(View.VISIBLE);
                    stopMissionButton.setVisibility(View.VISIBLE);
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

        // Update SpeedDisplayManager with home location for distance calculation
        if (speedDisplayManager != null) {
            speedDisplayManager.setHomeLocation(homeLocation.latitude, homeLocation.longitude);
        }

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

            // Setup heading listener for drone marker rotation
            telemetryService.setHeadingChangedListener(new TelemetryService.HeadingChangedListener() {
                @Override
                public void onHeadingChanged(Double heading) {
                    currentDroneHeading = heading;
                    uiHandler.post(() -> updateDroneMarkerRotation(heading));
                }
            });
        }

        Log.d(TAG, "Drone location and heading listeners setup complete");
    }
    
    /**
     * Update drone location marker on map
     */
    private void updateDroneLocationOnMap(LocationCoordinate3D location) {
        if (googleMap == null || location == null) return;

        LatLng dronePosition = new LatLng(location.getLatitude(), location.getLongitude());

        // Update existing marker or create new one
        if (droneMarker == null) {
            // Create new drone marker
            MarkerOptions droneMarkerOptions = new MarkerOptions()
                    .position(dronePosition)
                    .title("Drone Location")
                    .snippet("Alt: " + String.format("%.1f", location.getAltitude()) + "m")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.drone_new))
                    .anchor(0.5f, 0.5f) // Set anchor to center for proper rotation
                    .flat(true) // Make marker flat so it rotates with the map
                    .rotation(currentDroneHeading.floatValue()); // Set initial rotation

            droneMarker = googleMap.addMarker(droneMarkerOptions);
        } else {
            // Update existing marker position and snippet
            droneMarker.setPosition(dronePosition);
            droneMarker.setSnippet("Alt: " + String.format("%.1f", location.getAltitude()) + "m");
            droneMarker.setRotation(currentDroneHeading.floatValue());
        }

//        // Optionally move camera to follow drone during mission
//        if (missionInProgress) {
//            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(dronePosition, 18));
//        }
    }

    /**
     * Update drone marker rotation based on heading
     */
    private void updateDroneMarkerRotation(Double heading) {
        if (googleMap == null || droneMarker == null || heading == null) return;
        droneMarker.setRotation(heading.floatValue());
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
                    // Call flight started log API
                    createAndSaveFlightStartedLog();
                    break;

                case CommandService_V5SDK.STATUS_HEADING_TO_WAYPOINT:
                    // Update last waypoint during flight
                    int currentWaypoint = intent.getIntExtra(CommandService_V5SDK.EXTRA_CURRENT_WAYPOINT, 0);
                    if (currentWaypoint > 0) {
                        currentLastWaypoint = currentWaypoint;
                        Log.d(TAG, "Updated last waypoint to: " + currentLastWaypoint);
                    }
                    break;

                case CommandService_V5SDK.STATUS_MISSION_COMPLETED:
                case CommandService_V5SDK.STATUS_RETURNING_HOME:
                case CommandService_V5SDK.STATUS_ERROR:
                    missionInProgress = false;
                    saveFlightEndedLog();
                    runOnUiThread(() -> resetMissionButtons());
                    break;
            }

            Log.d(TAG, "Received status update: " + statusType + " - " + message);
        }
    };

    /**
     * Create flight log and call API when mission starts
     */
    private void createAndSaveFlightStartedLog() {
        try {
            if (currentProject == null) {
                Log.e(TAG, "Cannot create flight log: currentProject is null");
                return;
            }

            // Get current date and time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String startTime = dateFormat.format(new Date());

            // Calculate estimated end time (current time + 13 minutes)
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, 13);
            String estimatedEndTime = dateFormat.format(calendar.getTime());

            // Get drone name from ConnectionStateManager
            String droneName = ConnectionStateManager.getInstance().getCurrentDroneName();
            if (droneName == null || droneName.isEmpty() || droneName.equals("No Drone Connected")) {
                droneName = "Unknown Drone";
            }

            // Get number of waypoints
            int numberOfWaypoints = waypointsList != null ? waypointsList.size() : 0;

            // Get number of obstacles
            int numberOfObstacles = 0;
            List<Obstacle> obstacles = currentProject.getObstacles();
            if (obstacles != null) {
                numberOfObstacles = obstacles.size();
            }
            int waypointHeight = currentProject.must_height + 10;
            int horizontalPathHeight = currentProject.height_of_house + 45;
            int houseHeight = currentProject.height_of_house;
            int maxObstacleHeight = currentProject.must_height;
            int droneSpeed = (int) (missionSetting != null ? missionSetting.autoFlighSpeed : 8);
            int flightStartBattery = 0;
            if (telemetryService != null) {
                Integer battery = telemetryService.getCurrentBatteryPercentage();
                flightStartBattery = battery != null ? battery : 0;
            }
            String finishAction = missionSetting != null ? missionSetting.getFinishActionDisplay() : "Go Home";
            String headingMode = "Towards POI";
            String rotateGimbalPitch = "true";
            String goToFirstWaypointMode = missionSetting != null ? missionSetting.getFlyToWaylineModeDisplay() : "Safely";
            currentLastWaypoint = 0;
            currentFlightLog = new com.suleman.eagleeye.models.Log(
                    startTime,
                    estimatedEndTime,
                    droneName,
                    numberOfWaypoints,
                    ""+numberOfObstacles,
                    waypointHeight,
                    horizontalPathHeight,
                    houseHeight,
                    maxObstacleHeight,
                    droneSpeed,
                    flightStartBattery,
                    finishAction,
                    headingMode,
                    rotateGimbalPitch,
                    goToFirstWaypointMode,
                    currentLastWaypoint
            );
            Gson gson = new Gson();
            String logJson = gson.toJson(currentFlightLog);

            // Get auth token
            String token = userSessionManager.getToken();
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "Cannot save flight log: No auth token found");
                Toast.makeText(this, "Authentication error: Please login again", Toast.LENGTH_SHORT).show();
                return;
            }
            String authToken = "Bearer " + token;
            SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String currentDateTime = apiDateFormat.format(new Date());
            String projectId = String.valueOf(currentProject.id);

            Log.d(TAG, "Calling saveFlightStartedLog API...");
            Log.d(TAG, "Project ID: " + projectId);
            Log.d(TAG, "Date: " + currentDateTime);
            Log.d(TAG, "Flight Log: " + logJson);

            // Call API
            ApiClient.getApiService().
                    saveFlightStartedLog(authToken, projectId, currentDateTime, logJson)
                    .enqueue(new retrofit2.Callback<FlightLogResponse>() {
                        @Override
                        public void onResponse(retrofit2.Call<FlightLogResponse> call, retrofit2.Response<FlightLogResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                Log.d(TAG, "✅ Flight started log saved successfully");

                                // Store the flight ID from response in cache
                                FlightLogResponse flightLogResponse = response.body();
                                if (flightLogResponse.flight != null && flightLogResponse.flight.getId() != null) {
                                    Integer flightId = flightLogResponse.flight.getId();
                                    SessionUtils.saveCurrentFlightId(flightId);
                                    Log.d(TAG, "✅ Stored flight ID in cache for end log: " + flightId);
                                } else {
                                    Log.e(TAG, "⚠️ Warning: Flight ID not found in response");
                                }
                            } else {
                                Log.e(TAG, "❌ Failed to save flight started log. Response code: " + response.code());
                                try {
                                    if (response.errorBody() != null) {
                                        Log.e(TAG, "Error body: " + response.errorBody().string());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error reading error body: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<FlightLogResponse> call, Throwable t) {
                            Log.e(TAG, "❌ Network error saving flight started log: " + t.getMessage(), t);
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating flight started log: " + e.getMessage(), e);
        }
    }

    /**
     * Update flight log and call API when mission ends
     */
    private void saveFlightEndedLog() {
        try {
            if (currentFlightLog == null) {
                Log.e(TAG, "Cannot save flight ended log: currentFlightLog is null");
                return;
            }

            // Get flight ID from cache (persisted from started log response)
            Integer currentFlightId = SessionUtils.getCurrentFlightId();
            if (currentFlightId == null) {
                Log.e(TAG, "Cannot save flight ended log: currentFlightId is null (no started log response in cache)");
                return;
            }

            // Get current date and time as end time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String endTime = dateFormat.format(new Date());

            // Update the log with end time and last waypoint
            currentFlightLog.endTime = endTime;
            currentFlightLog.lastWaypoint = currentLastWaypoint;

            // Convert Log to JSON string
            Gson gson = new Gson();
            String logJson = gson.toJson(currentFlightLog);

            // Get auth token
            String token = userSessionManager.getToken();
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "Cannot save flight ended log: No auth token found");
                return;
            }

            // Prepare token with Bearer prefix
            String authToken = "Bearer " + token;
            SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String currentDateTime = apiDateFormat.format(new Date());

            // Use flight ID instead of project ID
            String flightId = String.valueOf(currentFlightId);

            Log.d(TAG, "Calling saveFlightEndedLog API...");
            Log.d(TAG, "Flight ID: " + flightId);
            Log.d(TAG, "Date: " + currentDateTime);
            Log.d(TAG, "Flight Log: " + logJson);

            // Call API with flight ID (not project ID)
            ApiClient.getApiService().saveFlightEndedLog(authToken, flightId, currentDateTime, logJson)
                    .enqueue(new retrofit2.Callback<FlightLogResponse>() {
                        @Override
                        public void onResponse(retrofit2.Call<FlightLogResponse> call, retrofit2.Response<FlightLogResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                Log.d(TAG, "✅ Flight ended log saved successfully");

                                // Clear the flight ID from cache after successful save
                                SessionUtils.clearCurrentFlightId();
                                Log.d(TAG, "✅ Cleared flight ID from cache");

                                runOnUiThread(() -> Toast.makeText(WaypointActivity.this,
                                        "Flight log completed", Toast.LENGTH_SHORT).show());
                            } else {
                                Log.e(TAG, "❌ Failed to save flight ended log. Response code: " + response.code());
                                try {
                                    if (response.errorBody() != null) {
                                        Log.e(TAG, "Error body: " + response.errorBody().string());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error reading error body: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<FlightLogResponse> call, Throwable t) {
                            Log.e(TAG, "❌ Network error saving flight ended log: " + t.getMessage(), t);
                        }
                    });

            // Clear the current flight log
            currentFlightLog = null;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving flight ended log: " + e.getMessage(), e);
        }
    }

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
            telemetryService.setHeadingChangedListener(null);
        }

        // Cleanup display managers
        if (telemetryDisplayManager != null) {
            telemetryDisplayManager.cleanup();
        }
        if (speedDisplayManager != null) {
            speedDisplayManager.cleanup();
        }

        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(waypointStatusReceiver);

        Log.d(TAG, "WaypointActivity destroyed");
    }
}
