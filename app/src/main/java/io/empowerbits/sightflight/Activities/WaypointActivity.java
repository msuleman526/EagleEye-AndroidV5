package io.empowerbits.sightflight.Activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

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
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.HashMap;
import java.util.Map;

import dji.sdk.keyvalue.value.camera.CameraStorageLocation;
import dji.sdk.keyvalue.value.camera.SDCardLoadState;
import dji.v5.manager.account.LoginInfoUpdateListener;
import dji.v5.manager.account.UserAccountManager;
import dji.v5.manager.interfaces.IUserAccountManager;
import io.empowerbits.sightflight.Fragments.CameraFeedFragment;
import io.empowerbits.sightflight.Fragments.ProjectDialogFragment;
import com.google.gson.Gson;
import com.trinnguyen.SegmentView;
import io.empowerbits.sightflight.ApiResponse.FlightLogResponse;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.Retrofit.ApiClient;
import io.empowerbits.sightflight.Services.ButtonsListenerService;
import io.empowerbits.sightflight.Services.CommandService_V5SDK;
import io.empowerbits.sightflight.Services.ConnectionStateManager;
import io.empowerbits.sightflight.Services.TelemetryService;
import io.empowerbits.sightflight.models.FlightAddress;
import io.empowerbits.sightflight.models.MissionSetting;
import io.empowerbits.sightflight.models.Obstacle;
import io.empowerbits.sightflight.models.Project;
import io.empowerbits.sightflight.models.WaypointSetting;
import io.empowerbits.sightflight.util.OtherHelper;
import io.empowerbits.sightflight.util.PermissionHelper;
import io.empowerbits.sightflight.util.SessionUtils;
import io.empowerbits.sightflight.util.SpeedDisplayManager;
import io.empowerbits.sightflight.util.TelemetryDisplayManager;
import io.empowerbits.sightflight.util.UserSessionManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

import dji.sdk.keyvalue.key.CameraKey;
import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.camera.CameraStorageInfo;
import dji.sdk.keyvalue.value.camera.CameraStorageInfos;
import dji.sdk.keyvalue.value.common.EmptyMsg;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;
import dji.v5.manager.aircraft.flysafe.FlySafeNotificationListener;
import dji.v5.manager.aircraft.flysafe.FlyZoneManager;
import dji.v5.manager.aircraft.flysafe.info.FlySafeReturnToHomeInformation;
import dji.v5.manager.aircraft.flysafe.info.FlySafeSeriousWarningInformation;
import dji.v5.manager.aircraft.flysafe.info.FlySafeTipInformation;
import dji.v5.manager.aircraft.flysafe.info.FlySafeWarningInformation;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation;
import dji.v5.manager.interfaces.IFlyZoneManager;
import io.empowerbits.sightflight.util.NFZManager;

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
    private ImageView startMissionButton;
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
    private Marker homeLocationMarker;
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
    private io.empowerbits.sightflight.models.Log currentFlightLog;
    private int currentLastWaypoint = 0;

    // Mission Settings UI
    private PopupWindow missionSettingsPopup;
    private PopupWindow missionStartConfirmationPopup;
    private ImageView btnMissionSetting;
    private boolean isManualWaypointMode = false;

    // Manual Mode UI
    private ImageView btnManualWaypoint;
    private ImageView btnProjectList;
    private TextView manualModeInstructionText;
    private boolean isDrawingPOI = false;
    private boolean isDrawingWaypoints = false;
    private Marker manualPOIMarker;

    // PiP Mode UI
    private FrameLayout cameraFeedContainer;
    private FrameLayout mapContainer;
    private FrameLayout cameraMainContainer;
    private FrameLayout mapPiPContainer;
    private ImageView cameraExpandIcon;
    private ImageView mapExpandIcon;
    private boolean isCameraFullScreen = false;

    // NFZ Management
    private NFZManager nfzManager;
    private RelativeLayout nfzInfoPanel;
    private TextView nfzNameText;
    private TextView nfzLevelText;
    private View nfzColorIndicator;
    private LinearLayout altitudeLimitContainer;
    private TextView nfzAltitudeLimit;
    private TextView nfzDescription;
    private TextView nfzAffectedWaypoints;
    private Button btnUnlockNFZ;
    private Button btnUnlockAllEnhancedWarnings;
    private List<Integer> waypointsInNFZ = new ArrayList<>();

    // Storage Check Popup
    private PopupWindow storageCheckPopup;
    private NFZManager.SimpleFlyZoneInfo currentNFZ;
    private Runnable nfzCheckRunnable;
    private long lastNFZCheckTime = 0;
    private static final long NFZ_CHECK_DEBOUNCE_MS = 2000; // 2 seconds minimum between checks
    private List<com.google.android.gms.maps.model.Polygon> nfzPolygons = new ArrayList<>();
    private Map<com.google.android.gms.maps.model.Polygon, NFZManager.SimpleFlyZoneInfo> polygonToZoneMap = new HashMap<>();
    private PopupWindow nfzWarningPopup;
    private boolean isDroneInNFZ = false;
    private long lastDroneNFZCheckTime = 0;
    private static final long DRONE_NFZ_CHECK_INTERVAL = 5000; // Check every 5 seconds
    private FlySafeNotificationListener flySafeNotificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint);

        if (!isManualWaypointMode){
                currentProject = (Project) getIntent().getSerializableExtra("project");
        }
        isManualWaypointMode = getIntent().getBooleanExtra("mode", false);

        waypointsList = new ArrayList<>();
        points = new ArrayList<>();
        uiHandler = new Handler(Looper.getMainLooper());
        SessionUtils.initialize(this);
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
            startMissionButton.setImageDrawable(getDrawable(R.drawable.go));
            connectionBar.setImageDrawable(getDrawable(R.drawable.connected_bar));
        }else{
            connectionBar.setImageDrawable(getDrawable(R.drawable.disconnected_bar));
            startMissionButton.setImageDrawable(getDrawable(R.drawable.go_red));
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
            double horizonPathHeight = currentProject.height_of_house + 15;
            List<FlightAddress> flightPath = currentProject.getWaypointList();
            double maxObstacleHeight = maxHeight + 10;

            if (!flightPath.isEmpty()) {
                double[] heights = {150, subjectPhotoHeight,
                        maxObstacleHeight, horizonPathHeight};

                for (int i = 0; i < heights.length; i++) {
                    double height = heights[i];
                    drawWaypoint(Double.parseDouble(currentProject.latitude), Double.parseDouble(currentProject.longitude), currentProject, height, false);
                }

                boolean containsInnerPath = false;
                int noOfInspectionWaypoints = 8;
                if (currentProject.flight_setting != null) {
                    String flightSettingStr = currentProject.getFlightSettingAsString();
                    if (flightSettingStr != null && !flightSettingStr.isEmpty() && !flightSettingStr.equals("null")) {
                        try {
                            JSONObject flightSettings = new JSONObject(flightSettingStr);
                            if (flightSettings.has("noOfWaypoints")) {
                                if(flightPath.size() > (flightSettings.getInt("noOfWaypoints"))){
                                    containsInnerPath = true;
                                }
                            }
                            if (flightSettings.has("noOfInspectionWaypoints")) {
                                noOfInspectionWaypoints = flightSettings.getInt("noOfInspectionWaypoints");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing flight settings: " + e.getMessage());
                        }
                    }
                }

                // 4. Process remaining waypoints with maxObstacleHeight
                for (int i = 0; i < flightPath.size(); i++) {
                    FlightAddress waypoint = flightPath.get(i);
                    if(containsInnerPath && i < noOfInspectionWaypoints){
                        drawWaypoint(waypoint.lat, waypoint.lng,
                                currentProject, horizonPathHeight, true);
                    }else{
                        drawWaypoint(waypoint.lat, waypoint.lng,
                                currentProject, maxObstacleHeight, false);
                    }
                }
            }
        }
    }

    public void drawWaypoint(double lat, double lng, Project project, double height, boolean isIspectionMarker) {
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
                if(isIspectionMarker){
                    waypointSetting.gimbalPitchAngle = (double) -90;
                }else{
                    float gimbalPitch = OtherHelper.calculatePitchAngle(new LocationCoordinate2D(lat, lng), new LocationCoordinate2D(pointOfInterest.getPosition().latitude, pointOfInterest.getPosition().longitude),
                            height * 0.3048,
                            missionSetting.poiHeight);
                    if (gimbalPitch > 90) {
                        gimbalPitch = 90;
                    } else if (gimbalPitch < -90) {
                        gimbalPitch = -90;
                    }
                    waypointSetting.gimbalPitchAngle = (double) gimbalPitch;
                }
            } else {
                waypointSetting.gimbalPitchAngle = 0.0;
            }

            waypointSetting.altitude = height * 0.3048;
            waypointsList.add(waypointSetting);
            SessionUtils.saveWaypoints(waypointsList);
            updateWaypointPolyline();

            // Check NFZ after adding waypoint
            checkWaypointsForNFZ();
        } catch (Exception e) {
            Log.d("Error creating waypoint: " , e.getMessage());
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
        btnMissionSetting = findViewById(R.id.btn_mission_setting);
        btnProjectList = findViewById(R.id.btn_project_list);
        btnManualWaypoint = findViewById(R.id.btn_manual_waypoint);
        manualModeInstructionText = findViewById(R.id.manualModeInstructionText);

        // Initialize PiP containers
        cameraFeedContainer = findViewById(R.id.cameraFeedContainer);
        mapContainer = findViewById(R.id.mapContainer);
        cameraMainContainer = findViewById(R.id.cameraMainContainer);
        mapPiPContainer = findViewById(R.id.mapPiPContainer);
        cameraExpandIcon = findViewById(R.id.cameraExpandIcon);
        mapExpandIcon = findViewById(R.id.mapExpandIcon);

        findViewById(R.id.backBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.btn_current_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveToCurrentLocation();
            }
        });

        findViewById(R.id.btn_media_manager).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(WaypointActivity.this, MediaManagerActivity.class);
                startActivity(intent);
            }
        });

        // Setup mission settings button
        btnMissionSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!missionInProgress) {
                    showMissionSettingsDialog();
                } else {
                    Toast.makeText(WaypointActivity.this,
                        "Cannot change settings during flight",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Setup project list button
        btnProjectList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showProjectSelectionDialog();
            }
        });

        // Setup manual waypoint mode button
        btnManualWaypoint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleManualWaypointMode();
            }
        });

        // Setup expand icon click listener for camera (tap plus icon on camera -> make camera full screen)
        cameraExpandIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePiPMode();
            }
        });

        // Setup expand icon click listener for map (tap plus icon on map -> make map full screen)
        mapExpandIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePiPMode();
            }
        });

        // Show manual waypoint button only in manual mode
        if (isManualWaypointMode) {
            btnManualWaypoint.setVisibility(View.VISIBLE);
        }

        // Initially disable mission control buttons
        stopMissionButton.setEnabled(false);
        pauseResumeButton.setEnabled(false);

        setupButtonListeners();
        updateStatus("Ready to start mission");

        // Initialize NFZ panel
        initializeNFZPanel();
    }
    
    /**
     * Setup button click listeners
     */
    private void setupButtonListeners() {
        startMissionButton.setOnClickListener(v -> {
            showStorageCheckPopup();
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

        // Register for RC button press events from ButtonsListenerService
        LocalBroadcastManager.getInstance(this).registerReceiver(
            rcButtonPressReceiver,
                new IntentFilter(ButtonsListenerService.ACTION_RC_BUTTON_PRESSED)
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
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        // Enable blue dot for current location
        enableMyLocation();

        // Setup marker click listener for waypoint settings
        setupWaypointMarkerClickListener();

        if (homeLocation != null) {
            updateMapWithHomeLocation();
            // Load NFZ polygons for home location
            loadNFZPolygons(homeLocation);
        }
        if(currentProject != null) {
            setUpCurrentProject();
        }
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

                @Override
                public void onHomeLocationChanged(LocationCoordinate2D location) {
                    uiHandler.post(() -> {
                        if (googleMap == null || location == null) return;
                        LatLng dronePosition = new LatLng(location.getLatitude(), location.getLongitude());
                        if (homeLocationMarker == null) {
                            MarkerOptions droneMarkerOptions = new MarkerOptions()
                                    .position(dronePosition)
                                    .title("Home Location")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.home));
                            homeLocationMarker = googleMap.addMarker(droneMarkerOptions);
                        } else {
                            homeLocationMarker.setPosition(dronePosition);
                        }
                    });
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

        // Check if drone is in NFZ (throttled to avoid excessive checks)
        checkDroneLocationForNFZ(dronePosition);

//        // Optionally move camera to follow drone during mission
//        if (missionInProgress) {
//            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(dronePosition, 18));
//        }
    }

    /**
     * Check if drone's current location is in an NFZ
     */
    private void checkDroneLocationForNFZ(LatLng droneLocation) {
        // Throttle checks to avoid excessive processing
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDroneNFZCheckTime < DRONE_NFZ_CHECK_INTERVAL) {
            return;
        }
        lastDroneNFZCheckTime = currentTime;

        if (nfzManager == null || droneLocation == null) return;

//        // Get all fly zones at drone's location
//        nfzManager.getFlyZonesForVisualization(droneLocation, new util.dronifyit.empowerbits.io.NFZManager.FlyZonesCallback() {
//            @Override
//            public void onFlyZonesRetrieved(List<util.dronifyit.empowerbits.io.NFZManager.SimpleFlyZoneInfo> zones) {
//                boolean droneInNFZ = false;
//                util.dronifyit.empowerbits.io.NFZManager.SimpleFlyZoneInfo detectedZone = null;
//
//                // Check if drone is inside any NFZ polygon
//                for (util.dronifyit.empowerbits.io.NFZManager.SimpleFlyZoneInfo zone : zones) {
//                    if (zone.hasPolygons()) {
//                        for (List<LatLng> polygon : zone.polygons) {
//                            if (isPointInPolygon(droneLocation, polygon)) {
//                                droneInNFZ = true;
//                                detectedZone = zone;
//                                break;
//                            }
//                        }
//                    } else {
//                        double distance = util.dronifyit.empowerbits.io.NFZManager.calculateDistance(
//                                droneLocation.latitude, droneLocation.longitude,
//                                zone.latitude, zone.longitude);
//                        if (distance <= zone.radiusMeters) {
//                            droneInNFZ = true;
//                            detectedZone = zone;
//                            break;
//                        }
//                    }
//                    if (droneInNFZ) break;
//                }
//                if (droneInNFZ && !isDroneInNFZ) {
//                    isDroneInNFZ = true;
//                } else if (!droneInNFZ && isDroneInNFZ) {
//                    // Drone left NFZ
//                    isDroneInNFZ = false;
//                    runOnUiThread(() -> {
//                        Toast.makeText(WaypointActivity.this,
//                                "✅ Drone has left the no-fly zone",
//                                Toast.LENGTH_SHORT).show();
//                    });
//                }
//            }
//            @Override
//            public void onError(String error) {
//                Log.e(TAG, "Error checking drone NFZ: " + error);
//            }
//        });
    }
    private boolean isPointInPolygon(LatLng point, List<LatLng> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); i++) {
            LatLng vi = polygon.get(i);
            LatLng vj = polygon.get(j);

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

    // Broadcast receiver for RC button press events
    private BroadcastReceiver rcButtonPressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String buttonName = intent.getStringExtra(ButtonsListenerService.EXTRA_BUTTON_NAME);
            if (buttonName != null) {
                String message = "⚠️ " + buttonName + " pressed - Mission paused for safety";
                // Mission is already paused by ButtonsListenerService
                showMessage(message);
            }
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
            currentFlightLog = new io.empowerbits.sightflight.models.Log(
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

    /**
     * Show mission settings dialog based on orientation
     * Landscape: Right drawer (230dp width)
     * Portrait: Popup dialog (scrollable)
     */
    private void showMissionSettingsDialog() {
        // Inflate the popup layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_mission_setting, null);

        // Check orientation
        int orientation = getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        // Create PopupWindow based on orientation
        if (isLandscape) {
            // Landscape: Show as right drawer (230dp width)
            int width = (int) (280 * getResources().getDisplayMetrics().density);
            missionSettingsPopup = new PopupWindow(popupView, width,
                    DrawerLayout.LayoutParams.MATCH_PARENT, true);
            missionSettingsPopup.setAnimationStyle(android.R.style.Animation_Dialog);
            missionSettingsPopup.showAtLocation(findViewById(android.R.id.content),
                    Gravity.END | Gravity.TOP, 0, 0);
        } else {
            // Portrait: Show as popup dialog
            int width = (int) (350 * getResources().getDisplayMetrics().density);
            popupView.setBackground(getDrawable(R.drawable.secondary_gradient_curve));
            missionSettingsPopup = new PopupWindow(popupView,
                    width,
                    DrawerLayout.LayoutParams.WRAP_CONTENT, true);
            missionSettingsPopup.setAnimationStyle(android.R.style.Animation_Dialog);
            missionSettingsPopup.showAtLocation(findViewById(android.R.id.content),
                    Gravity.CENTER, 0, 0);
        }

        // Set background to allow dismissing on outside click
        missionSettingsPopup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.dialog_holo_light_frame));
        missionSettingsPopup.setOutsideTouchable(true);

        // Load current values into form
        loadMissionSettingsIntoForm(popupView);

        // Setup button listeners
        Button closeBtn = popupView.findViewById(R.id.close);
        Button saveBtn = popupView.findViewById(R.id.saveBtn);

        closeBtn.setOnClickListener(v -> {
            if (missionSettingsPopup != null && missionSettingsPopup.isShowing()) {
                missionSettingsPopup.dismiss();
            }
        });

        saveBtn.setOnClickListener(v -> {
            saveMissionSettingsFromForm(popupView);
            if (missionSettingsPopup != null && missionSettingsPopup.isShowing()) {
                missionSettingsPopup.dismiss();
            }
        });
    }

    /**
     * Load current missionSetting values into form fields
     */
    private void loadMissionSettingsIntoForm(View popupView) {
        if (missionSetting == null) {
            missionSetting = new MissionSetting();
        }

        // 1. Fly To Wayline Mode Segment (Safely / Point to Point)
        SegmentView flyWaylineModeSegment = popupView.findViewById(R.id.flyWaylineModeSegment);
        flyWaylineModeSegment.setText(0, "Safely");
        flyWaylineModeSegment.setText(1, "Point to Point");
        flyWaylineModeSegment.setSelectedIndex(missionSetting.getFlyToWaylineModeIndex());

        // 2. Finish Action Spinner
        Spinner finishActionSpinner = popupView.findViewById(R.id.finishActionSpinner);
        ArrayAdapter<String> finishAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MissionSetting.getFinishActions());
        finishAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        finishActionSpinner.setAdapter(finishAdapter);
        finishActionSpinner.setSelection(missionSetting.getFinishActionIndex());

        // 3. Exit On RC Lost Behavior Segment (Execute RC Lost Action / Go ON)
        SegmentView exitRCLostBehaviorSegment = popupView.findViewById(R.id.exitRCLostBehaviorSegment);
        exitRCLostBehaviorSegment.setText(0, "RC Lost Action");
        exitRCLostBehaviorSegment.setText(1, "Go ON");
        exitRCLostBehaviorSegment.setSelectedIndex(missionSetting.getExitOnRCLostBehaviorIndex());

        // 4. Execute RC Lost Action Spinner
        Spinner executeRCLostActionSpinner = popupView.findViewById(R.id.executeRCLostActionSpinner);
        ArrayAdapter<String> rcLostActionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MissionSetting.getExecuteRCLostActions());
        rcLostActionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        executeRCLostActionSpinner.setAdapter(rcLostActionAdapter);
        executeRCLostActionSpinner.setSelection(missionSetting.getExecuteRCLostActionIndex());

        // 5. Take Off Height SeekBar (10-80 meters)
        TextView takeOffHeightLbl = popupView.findViewById(R.id.takeOffHeightLbl);
        SeekBar takeOffHeightSlider = popupView.findViewById(R.id.takeOffHeightSlider);
        // Convert feet to meters for display
        double takeOffHeightMeters = missionSetting.getTakeOffSecurityHeightInMeters();
        int takeOffProgress = (int) (takeOffHeightMeters - 10); // 10-80 range, seekbar 0-70
        if (takeOffProgress < 0) takeOffProgress = 40; // Default to 50m
        if (takeOffProgress > 70) takeOffProgress = 70; // Max 80m
        takeOffHeightSlider.setProgress(takeOffProgress);
        takeOffHeightLbl.setText(String.format("Take Off Height: %.0fm", takeOffHeightMeters));
        takeOffHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double heightMeters = progress + 10; // Convert 0-70 to 10-80
                takeOffHeightLbl.setText(String.format("Take Off Height: %.0fm", heightMeters));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 6. Global Transitional Speed SeekBar (3-15 m/s)
        TextView globalTransitionSpeedLbl = popupView.findViewById(R.id.globalTransitionSpeedLbl);
        SeekBar globalTransitionSpeedSlider = popupView.findViewById(R.id.globalTransitionSpeedSlider);
        int globalSpeedProgress = (int) ((missionSetting.globalTransitionalSpeed - 3.0) * 10); // 3-15 range, seekbar 0-120
        globalTransitionSpeedSlider.setProgress(globalSpeedProgress);
        globalTransitionSpeedLbl.setText(String.format("Global Transitional Speed: %.1f m/s", missionSetting.globalTransitionalSpeed));
        globalTransitionSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double speed = 3.0 + (progress / 10.0); // Convert 0-120 to 3.0-15.0
                globalTransitionSpeedLbl.setText(String.format("Global Transitional Speed: %.1f m/s", speed));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 7. Auto Flight Speed SeekBar (3-15 m/s)
        TextView autoFlightSpeedLbl = popupView.findViewById(R.id.autoFlightSpeedLbl);
        SeekBar autoFlightSpeedSlider = popupView.findViewById(R.id.autoFlightSpeedSlider);
        int autoFlightProgress = (int) ((missionSetting.autoFlighSpeed - 3.0) * 10); // 3-15 range, seekbar 0-120
        autoFlightSpeedSlider.setProgress(autoFlightProgress);
        autoFlightSpeedLbl.setText(String.format("Auto Flight Speed: %.1f m/s", missionSetting.autoFlighSpeed));
        autoFlightSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double speed = 3.0 + (progress / 10.0); // Convert 0-120 to 3.0-15.0
                autoFlightSpeedLbl.setText(String.format("Auto Flight Speed: %.1f m/s", speed));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 8. POI Height SeekBar (2-10 meters)
        TextView poiHeightLbl = popupView.findViewById(R.id.poiHeightLbl);
        SeekBar poiHeightSlider = popupView.findViewById(R.id.poiHeightSlider);
        int poiHeightProgress = (int) ((missionSetting.poiHeight - 2.0) * 10); // 2-10 range, seekbar 0-80
        poiHeightSlider.setProgress(poiHeightProgress);
        poiHeightLbl.setText(String.format("POI Height: %.1f m", missionSetting.poiHeight));
        poiHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double height = 2.0 + (progress / 10.0); // Convert 0-80 to 2.0-10.0
                poiHeightLbl.setText(String.format("POI Height: %.1f m", height));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 9. Height Mode Spinner
        Spinner heightModeSpinner = popupView.findViewById(R.id.heightModeSpinner);
        ArrayAdapter<String> heightModeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MissionSetting.getHeightModes());
        heightModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        heightModeSpinner.setAdapter(heightModeAdapter);
        heightModeSpinner.setSelection(missionSetting.getHeightModeIndex());
    }

    /**
     * Save form values back to missionSetting
     */
    private void saveMissionSettingsFromForm(View popupView) {
        if (missionSetting == null) {
            missionSetting = new MissionSetting();
        }

        // 1. Save Fly To Wayline Mode
        SegmentView flyWaylineModeSegment = popupView.findViewById(R.id.flyWaylineModeSegment);
        missionSetting.setFlyToWaylineModeFromIndex(flyWaylineModeSegment.getSelectedIndex());

        // 2. Save Finish Action
        Spinner finishActionSpinner = popupView.findViewById(R.id.finishActionSpinner);
        missionSetting.setFinishActionFromIndex(finishActionSpinner.getSelectedItemPosition());

        // 3. Save Exit On RC Lost Behavior
        SegmentView exitRCLostBehaviorSegment = popupView.findViewById(R.id.exitRCLostBehaviorSegment);
        missionSetting.setExitOnRCLostBehaviorFromIndex(exitRCLostBehaviorSegment.getSelectedIndex());

        // 4. Save Execute RC Lost Action
        Spinner executeRCLostActionSpinner = popupView.findViewById(R.id.executeRCLostActionSpinner);
        missionSetting.setExecuteRCLostActionFromIndex(executeRCLostActionSpinner.getSelectedItemPosition());

        // 5. Save Take Off Height (convert from meters to feet for storage)
        SeekBar takeOffHeightSlider = popupView.findViewById(R.id.takeOffHeightSlider);
        double heightMeters = takeOffHeightSlider.getProgress() + 10; // Convert 0-70 to 10-80
        missionSetting.setTakeOffSecurityHeightFromMeters(heightMeters);

        // 6. Save Global Transitional Speed
        SeekBar globalTransitionSpeedSlider = popupView.findViewById(R.id.globalTransitionSpeedSlider);
        missionSetting.globalTransitionalSpeed = 3.0 + (globalTransitionSpeedSlider.getProgress() / 10.0);

        // 7. Save Auto Flight Speed
        SeekBar autoFlightSpeedSlider = popupView.findViewById(R.id.autoFlightSpeedSlider);
        missionSetting.autoFlighSpeed = 3.0 + (autoFlightSpeedSlider.getProgress() / 10.0);

        // 8. Save POI Height
        SeekBar poiHeightSlider = popupView.findViewById(R.id.poiHeightSlider);
        missionSetting.poiHeight = 2.0 + (poiHeightSlider.getProgress() / 10.0);

        // 9. Save Height Mode
        Spinner heightModeSpinner = popupView.findViewById(R.id.heightModeSpinner);
        int heightModeIndex = heightModeSpinner.getSelectedItemPosition();
        switch (heightModeIndex) {
            case 0:
                missionSetting.heightMode = com.dji.wpmzsdk.common.data.HeightMode.RELATIVE;
                break;
            case 1:
                missionSetting.heightMode = com.dji.wpmzsdk.common.data.HeightMode.WGS84;
                break;
            case 2:
                missionSetting.heightMode = com.dji.wpmzsdk.common.data.HeightMode.EGM96;
                break;
        }
        Log.d(TAG, "Mission settings saved: " + missionSetting.getMissionSettingsSummary());
    }

    /**
     * Show ProjectDialogFragment to select a project
     */
    private void showProjectSelectionDialog() {
        ProjectDialogFragment dialog = new ProjectDialogFragment();
        dialog.setProjectSelectionListener(new ProjectDialogFragment.ProjectSelectionListener() {
            @Override
            public void onProjectSelected(Project project) {
                currentProject = project;
                clearMapAndWaypoints();
                setUpCurrentProject();
            }

            @Override
            public void onProjectSelectionCancelled() {
                // User cancelled selection
                Log.d(TAG, "Project selection cancelled");
            }
        });
        dialog.show(getSupportFragmentManager(), "ProjectDialog");
    }

    /**
     * Clear all waypoints and markers from the map
     */
    private void clearMapAndWaypoints() {
        if (googleMap != null) {
            // Clear waypoint markers
            for (Marker marker : points) {
                marker.remove();
            }
            points.clear();

            // Clear waypoint list
            waypointsList.clear();

            // Clear polyline
            if (waypointPolyline != null) {
                waypointPolyline.remove();
                waypointPolyline = null;
            }

            // Clear POI marker
            if (pointOfInterest != null) {
                pointOfInterest.remove();
                pointOfInterest = null;
            }

            // Clear manual POI marker
            if (manualPOIMarker != null) {
                manualPOIMarker.remove();
                manualPOIMarker = null;
            }
        }
    }

    /**
     * Toggle manual waypoint drawing mode
     */
    private void toggleManualWaypointMode() {
        if (!isDrawingPOI && !isDrawingWaypoints) {
            // Start POI drawing mode
            isDrawingPOI = true;
            isDrawingWaypoints = false;
            manualModeInstructionText.setText("Tap to draw POI");
            manualModeInstructionText.setVisibility(View.VISIBLE);
            setupMapClickListener();
        } else if (isDrawingPOI) {
            // Switch to waypoint drawing mode
            isDrawingPOI = false;
            isDrawingWaypoints = true;
            manualModeInstructionText.setText("Tap to draw waypoints");
        } else {
            // Turn off manual mode
            isDrawingPOI = false;
            isDrawingWaypoints = false;
            manualModeInstructionText.setVisibility(View.GONE);
            removeMapClickListener();
        }
    }

    /**
     * Setup map click listener for manual waypoint placement
     */
    private void setupMapClickListener() {
        if (googleMap != null) {
            googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    handleManualMapClick(latLng);
                }
            });
        }
    }

    /**
     * Remove map click listener
     */
    private void removeMapClickListener() {
        if (googleMap != null) {
            googleMap.setOnMapClickListener(null);
        }
    }

    /**
     * Handle map click for manual POI and waypoint placement
     */
    private void handleManualMapClick(LatLng latLng) {
        if (isDrawingPOI) {
            // Place POI marker (same style as home marker)
            if (manualPOIMarker != null) {
                manualPOIMarker.remove();
            }

            MarkerOptions poiMarkerOptions = new MarkerOptions()
                    .position(latLng)
                    .title("Point of Interest")
                    .snippet("Manual POI")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.home_marker));

            manualPOIMarker = googleMap.addMarker(poiMarkerOptions);
            pointOfInterest = manualPOIMarker; // Set as point of interest for mission

            // Automatically switch to waypoint mode
            isDrawingPOI = false;
            isDrawingWaypoints = true;
            manualModeInstructionText.setText("Tap to draw waypoints");
        } else if (isDrawingWaypoints) {
            // Place waypoint marker (same style as existing waypoints)
            int markerNumber = points.size() + 1;
            BitmapDescriptor waypointIcon = createWaypointMarker(markerNumber);

            MarkerOptions waypointMarkerOptions = new MarkerOptions()
                    .position(latLng)
                    .title("Waypoint " + markerNumber)
                    .snippet("Waypoint #" + markerNumber)
                    .icon(waypointIcon)
                    .anchor(0.5f, 0.5f);

            Marker waypointMarker = googleMap.addMarker(waypointMarkerOptions);

            if (waypointMarker != null) {
                points.add(waypointMarker);

                // Create WaypointSetting for the mission
                WaypointSetting waypointSetting = new WaypointSetting();
                waypointSetting.name = "Waypoint " + markerNumber;
                waypointSetting.latitude = latLng.latitude;
                waypointSetting.longitude = latLng.longitude;
                waypointSetting.altitude = missionSetting != null ? missionSetting.getTakeOffSecurityHeightInMeters() : 50;
                waypointsList.add(waypointSetting);
                // Update polyline
                updateWaypointPolyline();
                checkWaypointsForNFZ();
            }
        }
    }

    /**
     * Show mission start confirmation popup with swipe button
     */
    private void showMissionStartConfirmationPopup() {
        // Inflate the popup layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_mission_start_confirmation, null);

        // Create popup window
        int width = (int) (320 * getResources().getDisplayMetrics().density);
        missionStartConfirmationPopup = new PopupWindow(popupView,
                width,
                DrawerLayout.LayoutParams.WRAP_CONTENT, true);
        missionStartConfirmationPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        missionStartConfirmationPopup.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent));
        missionStartConfirmationPopup.setOutsideTouchable(true);

        // Get swipe button and cancel button
        com.ebanx.swipebtn.SwipeButton swipeButton = popupView.findViewById(R.id.swipeButton);
        Button cancelButton = popupView.findViewById(R.id.cancelButton);

        // Setup swipe button listener
        swipeButton.setOnStateChangeListener(new com.ebanx.swipebtn.OnStateChangeListener() {
            @Override
            public void onStateChange(boolean active) {
                if (active) {
                    // Swipe completed - start mission
                    startMissionExecution();
                    // Dismiss popup
                    if (missionStartConfirmationPopup != null && missionStartConfirmationPopup.isShowing()) {
                        missionStartConfirmationPopup.dismiss();
                    }
                }
            }
        });

        // Setup cancel button
        cancelButton.setOnClickListener(v -> {
            if (missionStartConfirmationPopup != null && missionStartConfirmationPopup.isShowing()) {
                missionStartConfirmationPopup.dismiss();
            }
        });

        // Show popup at center
        missionStartConfirmationPopup.showAtLocation(findViewById(android.R.id.content),
                Gravity.CENTER, 0, 0);
    }

    /**
     * Execute mission start (called after swipe confirmation)
     */
    private void startMissionExecution() {
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
        }
    }

    /**
     * Enable My Location (blue dot) on Google Maps
     */
    private void enableMyLocation() {
        if (googleMap == null) {
            return;
        }

        // Check if location permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                googleMap.setMyLocationEnabled(true);
                Log.d(TAG, "My Location (blue dot) enabled on map");
            } catch (SecurityException e) {
                Log.e(TAG, "Error enabling my location: " + e.getMessage());
            }
        }
    }

    /**
     * Move map camera to user's current location
     */
    private void moveToCurrentLocation() {
        if (googleMap == null) {
            return;
        }

        // Check if location permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }

        // Get current location using FusedLocationProviderClient
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 18f));
                            Log.d(TAG, "Moved to current location: " + location.getLatitude() + ", " + location.getLongitude());
                        }
                    }
                });
    }

    /**
     * Toggle Picture-in-Picture mode between Camera and Map
     * When camera is small (PiP): Tap makes camera full screen, map becomes PiP
     * When map is small (PiP): Tap makes map full screen, camera becomes PiP
     */
    private void togglePiPMode() {
        if (!isCameraFullScreen) {
            switchToCameraFullScreen();
        } else {
            switchToMapFullScreen();
        }
    }

    /**
     * Switch to Camera Full Screen mode
     * Camera takes full screen, Map moves to small PiP
     */
    private void switchToCameraFullScreen() {
        try {
            // Show main camera container first
            cameraMainContainer.setVisibility(View.VISIBLE);

            // Move camera fragment from PiP to main container
            if (cameraFeedFragment != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(cameraFeedFragment);
                transaction.commit();
                getSupportFragmentManager().executePendingTransactions();

                cameraFeedFragment = new CameraFeedFragment();
                transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraMainContainer, cameraFeedFragment);
                transaction.commit();
            }

            // Hide camera PiP container and its expand icon
            cameraFeedContainer.setVisibility(View.GONE);
            cameraExpandIcon.setVisibility(View.GONE);
            RelativeLayout.LayoutParams mapParams = new RelativeLayout.LayoutParams(
                    (int) (230 * getResources().getDisplayMetrics().density),
                    (int) (140 * getResources().getDisplayMetrics().density)
            );
            mapParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            mapParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            mapContainer.setLayoutParams(mapParams);
            mapExpandIcon.setVisibility(View.VISIBLE);
            mapContainer.bringToFront();
            mapContainer.invalidate();
            bringUIControlsToFront();

            isCameraFullScreen = true;
            Log.d(TAG, "Switched to Camera Full Screen mode");

        } catch (Exception e) {
            Log.e(TAG, "Error switching to camera full screen: " + e.getMessage(), e);
        }
    }

    /**
     * Switch to Map Full Screen mode (default state)
     * Map takes full screen, Camera moves to small PiP
     */
    private void switchToMapFullScreen() {
        try {
            // Restore map container to full screen
            RelativeLayout.LayoutParams mapParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
            );
            mapContainer.setLayoutParams(mapParams);

            // Move camera fragment back to PiP container
            if (cameraFeedFragment != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(cameraFeedFragment);
                transaction.commit();
                getSupportFragmentManager().executePendingTransactions();

                cameraFeedFragment = new CameraFeedFragment();
                transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraFeedContainer, cameraFeedFragment);
                transaction.commit();
            }
            cameraFeedContainer.setVisibility(View.VISIBLE);
            cameraMainContainer.setVisibility(View.GONE);
            cameraExpandIcon.setVisibility(View.VISIBLE);
            mapExpandIcon.setVisibility(View.GONE);
            cameraFeedContainer.bringToFront();
            cameraExpandIcon.bringToFront();
            cameraFeedContainer.invalidate();
            // Bring all UI controls to front so they appear above the map
            bringUIControlsToFront();
            isCameraFullScreen = false;
            Log.d(TAG, "Switched to Map Full Screen mode");

        } catch (Exception e) {
            Log.e(TAG, "Error switching to map full screen: " + e.getMessage(), e);
        }
    }

    /**
     * Brings all UI control containers to front so they appear above map/camera
     * This ensures buttons, telemetry, speed view, etc. are always visible
     */
    private void bringUIControlsToFront() {
        try {
            // Bring top bar to front (back button, connection status, telemetry)
            ViewParent backBtnParent = findViewById(R.id.backBtn).getParent();
            if (backBtnParent instanceof View) ((View)backBtnParent).bringToFront();

            // Explicitly bring telemetry container to front (it's inside the top bar)
            View telemetryContainer = findViewById(R.id.telemetryView);
            if (telemetryContainer != null && telemetryContainer.getParent() instanceof View) {
                ((View)telemetryContainer.getParent()).bringToFront();
                telemetryContainer.bringToFront();
            }

            // Bring top-right buttons to front (current location, media manager)
            ViewParent topRightParent = findViewById(R.id.btn_current_location).getParent();
            if (topRightParent instanceof View) ((View)topRightParent).bringToFront();

            // Bring top-left buttons to front (mission settings, project list)
            ViewParent topLeftParent = findViewById(R.id.btn_mission_setting).getParent();
            if (topLeftParent instanceof View) ((View)topLeftParent).bringToFront();

            if (manualModeInstructionText != null) manualModeInstructionText.bringToFront();

            // Bring status text to front
            if (statusTextView != null) statusTextView.bringToFront();

            // Bring mission buttons to front (GO, STOP, PAUSE, RESUME)
            ViewParent missionButtonsParent = findViewById(R.id.startMissionButton).getParent();
            if (missionButtonsParent instanceof View) ((View)missionButtonsParent).bringToFront();

        } catch (Exception e) {
            Log.e(TAG, "Error bringing UI controls to front: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister FlySafe Notification Listener (CRITICAL - prevents memory leaks)
        unregisterFlySafeNotificationListener();

        // Cancel any pending NFZ checks
        if (nfzCheckRunnable != null && uiHandler != null) {
            uiHandler.removeCallbacks(nfzCheckRunnable);
            nfzCheckRunnable = null;
        }

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

        // Clean up DJI login listener
        try {
            if (loginInfoUpdateListener != null) {
                IUserAccountManager accountManager = UserAccountManager.getInstance();
                if (accountManager != null) {
                    accountManager.removeLoginInfoUpdateListener(loginInfoUpdateListener);
                    loginInfoUpdateListener = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing login listener: " + e.getMessage());
        }

        // Unregister broadcast receivers
        LocalBroadcastManager.getInstance(this).unregisterReceiver(waypointStatusReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(rcButtonPressReceiver);

        Log.d(TAG, "WaypointActivity destroyed");
    }

    // ============================================================================================
    // NFZ (No-Fly Zone) INTEGRATION
    // ============================================================================================

    /**
     * Initialize NFZ panel
     */
    private void initializeNFZPanel() {
        // Inflate NFZ panel layout
        View nfzPanel = getLayoutInflater().inflate(R.layout.nfz_info_panel, null);
        ViewGroup rootLayout = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);

        // Add panel to root layout
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        params.setMargins(12, 0, 0, 260);
        rootLayout.addView(nfzPanel, params);
        Log.d(TAG, ">>> NFZ Panel added to root layout");

        // Get references
        nfzInfoPanel = nfzPanel.findViewById(R.id.nfzInfoPanel);
        nfzNameText = nfzPanel.findViewById(R.id.nfzNameText);
        nfzLevelText = nfzPanel.findViewById(R.id.nfzLevelText);
        nfzColorIndicator = nfzPanel.findViewById(R.id.nfzColorIndicator);
        altitudeLimitContainer = nfzPanel.findViewById(R.id.altitudeLimitContainer);
        nfzAltitudeLimit = nfzPanel.findViewById(R.id.nfzAltitudeLimit);
        nfzDescription = nfzPanel.findViewById(R.id.nfzDescription);
        nfzAffectedWaypoints = nfzPanel.findViewById(R.id.nfzAffectedWaypoints);
        btnUnlockNFZ = nfzPanel.findViewById(R.id.btnUnlockNFZ);
        btnUnlockAllEnhancedWarnings = nfzPanel.findViewById(R.id.btnUnlockAllEnhancedWarnings);

        Log.d(TAG, ">>> NFZ Panel initialization:");
        Log.d(TAG, ">>>   nfzInfoPanel: " + (nfzInfoPanel != null ? "OK" : "NULL"));
        Log.d(TAG, ">>>   btnUnlockNFZ: " + (btnUnlockNFZ != null ? "OK" : "NULL"));
        Log.d(TAG, ">>>   btnUnlockAllEnhancedWarnings: " + (btnUnlockAllEnhancedWarnings != null ? "OK" : "NULL"));

        // Start with panel hidden
        if (nfzInfoPanel != null) {
            nfzInfoPanel.setVisibility(View.GONE);
            Log.d(TAG, ">>> NFZ Panel initially hidden");
        }

        // Initialize NFZ manager
        nfzManager = new NFZManager();

        // Setup unlock buttons
        btnUnlockNFZ.setOnClickListener(v -> handleNFZUnlock());
        btnUnlockAllEnhancedWarnings.setOnClickListener(v -> handleUnlockAllEnhancedWarnings());
        registerFlySafeNotificationListener();

        Log.d(TAG, "NFZ Panel initialized");
    }

    /**
     * Register FlySafe Notification Listener (DJI SDK V5 RECOMMENDED APPROACH)
     * This provides real-time notifications when aircraft enters/exits fly zones
     * Reference: https://developer.dji.com/api-reference-v5/android-api/Components/IFlyZoneManager/IFlyZoneManager.html#iflyzonemanager_addflysafenotificationlistener_inline
     */
    private void registerFlySafeNotificationListener() {
        IFlyZoneManager flyZoneManager = FlyZoneManager.getInstance();
        flySafeNotificationListener = new FlySafeNotificationListener() {
            @Override
            public void onTipNotificationUpdate(@NonNull FlySafeTipInformation info) {
                runOnUiThread(() -> {
                    try {
                        String tipMessage = info.getDescription();
                        int limit = info.getHeightLimit();
                        if(tipMessage != null && !tipMessage.isEmpty()) {
                            if (limit != 0) {
                                showMessage(info + " and flight limit is " + limit);
                            } else {
                                showMessage(tipMessage);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling tip notification: " + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onWarningNotificationUpdate(@NonNull FlySafeWarningInformation info) {
                runOnUiThread(() -> {
                    try {
                        String tipMessage = info.getDescription();
                        int limit = info.getHeightLimit();
                        if(tipMessage != null && !tipMessage.isEmpty()) {
                            if (limit != 0) {
                                showMessage(info + " and flight limit is " + limit);
                            } else {
                                showMessage(tipMessage);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling tip notification: " + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onSeriousWarningNotificationUpdate(@NonNull FlySafeSeriousWarningInformation info) {
                runOnUiThread(() -> {
                    try {
                        String tipMessage = info.getDescription();
                        int limit = info.getHeightLimit();
                        if(tipMessage != null && !tipMessage.isEmpty()) {
                            if (limit != 0) {
                                showMessage(info + " and flight limit is " + limit);
                            } else {
                                showMessage(tipMessage);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling tip notification: " + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onReturnToHomeNotificationUpdate(@NonNull FlySafeReturnToHomeInformation info) {
                runOnUiThread(() -> {
                    try {
                        String tipMessage = info.getDescription();
                        if(tipMessage != null && !tipMessage.isEmpty()) {
                            showMessage(tipMessage);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling tip notification: " + e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onSurroundingFlyZonesUpdate(@NonNull List<FlyZoneInformation> infos) {

            }
        };
        flyZoneManager.addFlySafeNotificationListener(flySafeNotificationListener);
        Log.d(TAG, "✅ FlySafe Notification Listener registered successfully");
    }

    /**
     * Show FlySafe alert dialog
     */
    private void showFlySafeAlert(String title, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show();
    }

    /**
     * Highlight NFZ polygons on map with animation
     */
    private void highlightNFZOnMap() {
        // Visual feedback - could add pulsing animation to NFZ polygons
        for (com.google.android.gms.maps.model.Polygon polygon : nfzPolygons) {
            // Make NFZ polygons more visible
            polygon.setStrokeWidth(8);
        }

        // Reset after 3 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            for (com.google.android.gms.maps.model.Polygon polygon : nfzPolygons) {
                polygon.setStrokeWidth(4);
            }
        }, 3000);
    }

    /**
     * Convert DJI FlyZoneInformation to SimpleFlyZoneInfo
     * Reuses existing conversion logic from NFZManager
     */
    private NFZManager.SimpleFlyZoneInfo convertDJIZoneToSimpleZone(
            dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation zone) {
        if (zone == null) return null;

        try {
            String name = zone.getName() != null ? zone.getName() : "Unnamed Zone";
            double lat = zone.getCircleCenter().getLatitude();
            double lon = zone.getCircleCenter().getLongitude();
            double radius = zone.getCircleRadius();
            String category = zone.getCategory() != null ? zone.getCategory().name() : "UNKNOWN";
            int flyZoneId = zone.getFlyZoneID();
            dji.v5.manager.aircraft.flysafe.info.FlyZoneShape shape = zone.getShape();

            // Create SimpleFlyZoneInfo (polygon extraction will be done by NFZManager if needed)
            return new NFZManager.SimpleFlyZoneInfo(
                name, lat, lon, radius, category, new ArrayList<>(), flyZoneId, shape
            );

        } catch (Exception e) {
            Log.w(TAG, "Error converting DJI zone to simple zone: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Unregister FlySafe Notification Listener
     * MUST be called in onDestroy() to prevent memory leaks
     */
    private void unregisterFlySafeNotificationListener() {
        if (flySafeNotificationListener == null) {
            return;
        }

        dji.v5.manager.interfaces.IFlyZoneManager flyZoneManager = dji.v5.manager.aircraft.flysafe.FlyZoneManager.getInstance();

        if (flyZoneManager != null) {
            try {
                flyZoneManager.removeFlySafeNotificationListener(flySafeNotificationListener);
                Log.d(TAG, "✅ FlySafe Notification Listener unregistered successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering FlySafe listener: " + e.getMessage(), e);
            }
        }

        flySafeNotificationListener = null;
    }

    /*
     * Check waypoints for NFZ with debouncing to prevent spam
     */
    private void checkWaypointsForNFZ() {
        // Check if nfzManager is initialized
        if (nfzManager == null) {
            Log.w(TAG, "NFZ Manager not initialized, skipping waypoint check");
            return;
        }

        // Cancel any pending NFZ check
        if (nfzCheckRunnable != null) {
            uiHandler.removeCallbacks(nfzCheckRunnable);
        }

        // Debounce: skip if checked recently
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNFZCheckTime < NFZ_CHECK_DEBOUNCE_MS) {
            Log.d(TAG, "NFZ check debounced - too soon since last check");
            return;
        }

        if (waypointsList == null || waypointsList.isEmpty()) {
            hideNFZPanel();
            return;
        }

        lastNFZCheckTime = currentTime;

        // Convert waypoints to LatLng list
        List<LatLng> waypointPositions = new ArrayList<>();
        for (WaypointSetting wp : waypointsList) {
            waypointPositions.add(new LatLng(wp.latitude, wp.longitude));
        }

        nfzManager.checkWaypoints(waypointPositions, new NFZManager.NFZCheckCallback() {
            @Override
            public void onNFZDetected(List<NFZManager.SimpleFlyZoneInfo> zones, List<Integer> affectedWaypoints) {
                runOnUiThread(() -> {
                    waypointsInNFZ = affectedWaypoints;
                    currentNFZ = NFZManager.getMostRestrictiveNFZ(zones);

                    // Draw NFZ polygons on map
                    drawNFZPolygons(zones);

                    // Update waypoint markers to show red for NFZ waypoints
                    //updateWaypointMarkersWithNFZ(affectedWaypoints);

                    // Log NFZ detection (panel will only show when user taps on polygon)
                    if (currentNFZ != null) {
                        String zoneName = currentNFZ.name != null ? currentNFZ.name : "Restricted Area";
                        Log.d(TAG, "NFZ Detected: " + zoneName + " (" + currentNFZ.category + "), " +
                              affectedWaypoints.size() + " waypoints affected");
                    }
                });
            }

            @Override
            public void onNoNFZDetected() {
                runOnUiThread(() -> {
                    waypointsInNFZ.clear();
                    currentNFZ = null;
                    hideNFZPanel();
                    //updateWaypointMarkersWithNFZ(new ArrayList<>());
                    Log.d(TAG, "No NFZ detected");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "NFZ check error: " + error);
                });
            }
        });
    }

    /**
     * Update waypoint markers with NFZ indicator
     */
    private void updateWaypointMarkersWithNFZ(List<Integer> affectedIndices) {
        if (points == null || points.isEmpty()) return;

        for (int i = 0; i < points.size(); i++) {
            Marker marker = points.get(i);
            if (affectedIndices.contains(i)) {
                marker.setIcon(createCustomMarkerIcon(i + 1, Color.RED));
            } else {
                marker.setIcon(createCustomMarkerIcon(i + 1, Color.BLUE));
            }
        }
    }

    /**
     * Create custom marker icon with number and color
     */
    private BitmapDescriptor createCustomMarkerIcon(int number, int color) {
        Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw circle
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(40, 40, 35, paint);

        // Draw white border
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        canvas.drawCircle(40, 40, 35, paint);

        // Draw number
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(35);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        Rect textBounds = new Rect();
        String text = String.valueOf(number);
        paint.getTextBounds(text, 0, text.length(), textBounds);
        canvas.drawText(text, 40, 40 + textBounds.height()/2, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Show NFZ panel with SimpleFlyZoneInfo
     */
    private void showNFZPanel(NFZManager.SimpleFlyZoneInfo zone, int affectedCount) {
        if (nfzInfoPanel == null || zone == null) return;

        nfzInfoPanel.setVisibility(View.VISIBLE);

        // Set zone name
        String zoneName = zone.name != null ? zone.name : "Restricted Area";
        nfzNameText.setText(zoneName);

        // Set level and color
        String category = zone.category;
        nfzLevelText.setText(NFZManager.getNFZLevelText(category));

        int color = NFZManager.getNFZColor(category);
        nfzColorIndicator.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);

        // Show altitude limit if available
        if (zone.hasUpperLimit() && altitudeLimitContainer != null && nfzAltitudeLimit != null) {
            altitudeLimitContainer.setVisibility(View.VISIBLE);
            nfzAltitudeLimit.setText(zone.upperLimit + "m");
        } else if (altitudeLimitContainer != null) {
            altitudeLimitContainer.setVisibility(View.GONE);
        }

        // Show description
        if (nfzDescription != null) {
            String description = NFZManager.getZoneDescription(category);
            nfzDescription.setText(description);
            nfzDescription.setVisibility(View.VISIBLE);
        }

        // Show affected waypoints count
        if (affectedCount > 0) {
            nfzAffectedWaypoints.setVisibility(View.VISIBLE);
            nfzAffectedWaypoints.setText("Waypoints in zone: " + affectedCount);
        } else {
            nfzAffectedWaypoints.setVisibility(View.GONE);
        }

        // Show unlock button if zone is unlockable
        if (NFZManager.isNFZUnlockable(category)) {
            btnUnlockNFZ.setVisibility(View.VISIBLE);
        } else {
            btnUnlockNFZ.setVisibility(View.GONE);
        }
    }

    /**
     * Show NFZ panel with all detected zones (enhanced version)
     * Shows "Unlock All Enhanced Warnings" button only if Enhanced Warning zones are present
     */
    private void showNFZPanel(NFZManager.SimpleFlyZoneInfo zone,
                              int affectedCount,
                              List<NFZManager.SimpleFlyZoneInfo> allZones) {
        if (nfzInfoPanel == null || zone == null) return;

        nfzInfoPanel.setVisibility(View.VISIBLE);

        // Set zone name
        String zoneName = zone.name != null ? zone.name : "Restricted Area";
        nfzNameText.setText(zoneName);

        // Set level and color
        String category = zone.category;
        nfzLevelText.setText(NFZManager.getNFZLevelText(category));

        int color = NFZManager.getNFZColor(category);
        nfzColorIndicator.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);

        // Show altitude limit if available
        if (zone.hasUpperLimit() && altitudeLimitContainer != null && nfzAltitudeLimit != null) {
            altitudeLimitContainer.setVisibility(View.VISIBLE);
            nfzAltitudeLimit.setText(zone.upperLimit + "m");
        } else if (altitudeLimitContainer != null) {
            altitudeLimitContainer.setVisibility(View.GONE);
        }

        // Show description
        if (nfzDescription != null) {
            String description = NFZManager.getZoneDescription(category);
            nfzDescription.setText(description);
            nfzDescription.setVisibility(View.VISIBLE);
        }

        // Show affected waypoints count
        if (affectedCount > 0) {
            nfzAffectedWaypoints.setVisibility(View.VISIBLE);
            nfzAffectedWaypoints.setText("Waypoints in zone: " + affectedCount);
        } else {
            nfzAffectedWaypoints.setVisibility(View.GONE);
        }

        // Show unlock button if zone is unlockable
        if (NFZManager.isNFZUnlockable(category)) {
            btnUnlockNFZ.setVisibility(View.VISIBLE);
        } else {
            btnUnlockNFZ.setVisibility(View.GONE);
        }

        // Show "Unlock All Enhanced Warnings" button ONLY if there's at least one Enhanced Warning zone
        Log.d(TAG, ">>> Checking for Enhanced Warning zones...");
        Log.d(TAG, ">>> allZones is " + (allZones != null ? "NOT NULL (" + allZones.size() + " zones)" : "NULL"));

        boolean hasEnhancedWarning = false;
        if (allZones != null) {
            for (NFZManager.SimpleFlyZoneInfo z : allZones) {
                Log.d(TAG, ">>> Checking zone: " + z.name + ", Category: '" + z.category + "'");

                if (z.category != null && z.category.equalsIgnoreCase("ENHANCED_WARNING")) {
                    Log.d(TAG, ">>> FOUND Enhanced Warning zone: " + z.name);
                    hasEnhancedWarning = true;
                    break;
                }
            }
        }

        Log.d(TAG, ">>> hasEnhancedWarning = " + hasEnhancedWarning);
        Log.d(TAG, ">>> btnUnlockAllEnhancedWarnings is " + (btnUnlockAllEnhancedWarnings != null ? "NOT NULL" : "NULL"));

        if (btnUnlockAllEnhancedWarnings != null) {
            btnUnlockAllEnhancedWarnings.setVisibility(hasEnhancedWarning ? View.VISIBLE : View.GONE);
            Log.d(TAG, ">>> Button visibility set to: " + (hasEnhancedWarning ? "VISIBLE" : "GONE"));
        } else {
            Log.e(TAG, ">>> BUTTON IS NULL - Cannot set visibility!");
        }
    }

    /**
     * Hide NFZ panel
     */
    private void hideNFZPanel() {
        if (nfzInfoPanel != null) {
            nfzInfoPanel.setVisibility(View.GONE);
        }
        // Also hide the "Unlock All Enhanced Warnings" button
        if (btnUnlockAllEnhancedWarnings != null) {
            btnUnlockAllEnhancedWarnings.setVisibility(View.GONE);
        }
    }

    /**
     * Handle NFZ unlock
     */
    private void handleNFZUnlock() {
        if (currentNFZ == null) {
            Toast.makeText(this, "No NFZ to unlock", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NFZManager.isNFZUnlockable(currentNFZ.category)) {
            Toast.makeText(this,
                "This zone cannot be unlocked. It is permanently restricted.",
                Toast.LENGTH_LONG).show();
            return;
        }

        // Show loading
        btnUnlockNFZ.setEnabled(false);
        btnUnlockNFZ.setText("Unlocking...");

        nfzManager.requestUnlock(currentNFZ, new dji.v5.common.callback.CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(WaypointActivity.this,
                        "✅ NFZ Unlocked Successfully!\n\nYou can now fly in this area.",
                        Toast.LENGTH_LONG).show();
                    btnUnlockNFZ.setText("Unlocked ✓");
                    btnUnlockNFZ.setBackgroundColor(Color.GREEN);

                    // Re-check waypoints
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        checkWaypointsForNFZ();
                    }, 1500);
                });
            }

            @Override
            public void onFailure(dji.v5.common.error.IDJIError error) {
                runOnUiThread(() -> {
                    Toast.makeText(WaypointActivity.this,
                        "❌ Failed to unlock NFZ:\n" + error.description(),
                        Toast.LENGTH_LONG).show();

                    btnUnlockNFZ.setEnabled(true);
                    btnUnlockNFZ.setText("Unlock Zone");
                });
            }
        });
    }

    /**
     * Handle unlocking all enhanced warning zones
     */
    private void handleUnlockAllEnhancedWarnings() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Unlock All Enhanced Warnings")
            .setMessage("This will unlock ALL enhanced warning fly zones.\n\nThe aircraft will no longer prompt enhanced warning zones after unlocking.\n\nDo you want to proceed?")
            .setPositiveButton("Unlock All", (dialog, which) -> {
                // Show progress
                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                progressDialog.setMessage("Unlocking all enhanced warnings...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                nfzManager.unlockAllEnhancedWarningZones(new dji.v5.common.callback.CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(WaypointActivity.this,
                                "✅ All Enhanced Warning Zones Unlocked!\n\nThe aircraft will no longer show enhanced warning prompts.",
                                Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onFailure(dji.v5.common.error.IDJIError error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            String errorMsg = error != null && error.description() != null ?
                                error.description() : "Failed to unlock enhanced warnings";
                            Toast.makeText(WaypointActivity.this,
                                "❌ Failed to unlock: " + errorMsg,
                                Toast.LENGTH_LONG).show();
                        });
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Show storage check popup before mission start
     * Checks drone storage capacity and estimates required storage based on waypoints
     */
    private void showStorageCheckPopup() {
        if (waypointsList == null || waypointsList.isEmpty()) {
            Toast.makeText(this, "Please add waypoints first", Toast.LENGTH_SHORT).show();
            return;
        }

        DJIKey<CameraStorageInfos> key = KeyTools.createKey(CameraKey.KeyCameraStorageInfos);
        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<CameraStorageInfos>() {
            @Override
            public void onSuccess(CameraStorageInfos storageInfos) {
                runOnUiThread(() -> {
                    try {
                        List<CameraStorageInfo> storageList = storageInfos.getCameraStorageInfoList();
                        if (storageList == null || storageList.isEmpty()) {
                            proceedToNFZOrMissionCheck();
                            return;
                        }
                        CameraStorageInfo storage = storageList.get(0);
                        if(storage.getStorageState() == SDCardLoadState.NOT_INSERTED && storageList.size() > 1){
                            storage = storageList.get(1);
                        }
                        Integer totalCapacity = storage.getStorageCapacity();
                        Integer availableCapacity = storage.getStorageLeftCapacity();
                        Log.d("Storage Dataa", storage.toString());
                        if (totalCapacity == null || availableCapacity == null) {
                            proceedToNFZOrMissionCheck();
                            return;
                        }
                        int estimatedRequiredMB = (waypointsList.size()-3) * 8;
                        showStorageCheckPopupDialog(totalCapacity, availableCapacity, estimatedRequiredMB);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing storage info: " + e.getMessage(), e);
                        proceedToNFZOrMissionCheck();
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get storage info: " + error.description());
                runOnUiThread(() -> {
                    // On failure, show warning but allow to proceed
                    Toast.makeText(WaypointActivity.this,
                        "Could not check storage. Proceeding...",
                        Toast.LENGTH_SHORT).show();
                    proceedToNFZOrMissionCheck();
                });
            }
        });
    }

    /**
     * Show the storage check popup dialog with storage information
     */
    private void showStorageCheckPopupDialog(int totalCapacityMB, int availableCapacityMB, int estimatedRequiredMB) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_storage_check, null);

        // Create popup window
        int width = (int) (350 * getResources().getDisplayMetrics().density);
        storageCheckPopup = new PopupWindow(popupView,
                width,
                DrawerLayout.LayoutParams.WRAP_CONTENT, true);
        storageCheckPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        storageCheckPopup.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent));
        storageCheckPopup.setOutsideTouchable(true);

        // Get views
        TextView tvTotalStorage = popupView.findViewById(R.id.tvTotalStorage);
        TextView tvAvailableStorage = popupView.findViewById(R.id.tvAvailableStorage);
        TextView tvEstimatedStorage = popupView.findViewById(R.id.tvEstimatedStorage);
        TextView tvStorageWarning = popupView.findViewById(R.id.tvStorageWarning);
        Button btnCancel = popupView.findViewById(R.id.btnCancelStorageCheck);
        Button btnProceed = popupView.findViewById(R.id.btnProceedMission);

        // Convert MB to GB for display
        float totalGB = totalCapacityMB / 1024.0f;
        float availableGB = availableCapacityMB / 1024.0f;

        // Set storage values
        tvTotalStorage.setText(String.format(Locale.US, "%.2f GB", totalGB));
        tvAvailableStorage.setText(String.format(Locale.US, "%.2f GB", availableGB));
        tvEstimatedStorage.setText(String.format(Locale.US, "%d MB", estimatedRequiredMB));

        // Check if sufficient storage
        boolean sufficientStorage = availableCapacityMB >= estimatedRequiredMB;

        if (!sufficientStorage) {
            tvStorageWarning.setVisibility(View.VISIBLE);
            btnProceed.setEnabled(true);
            btnProceed.setText("Format & Proceed");
        } else {
            btnProceed.setText("Proceed");
            tvStorageWarning.setVisibility(View.GONE);
            btnProceed.setEnabled(true);
            btnProceed.setAlpha(1.0f);
        }

        // Cancel button
        btnCancel.setOnClickListener(v -> {
            if (storageCheckPopup != null && storageCheckPopup.isShowing()) {
                storageCheckPopup.dismiss();
            }
        });

        // Proceed button
        btnProceed.setOnClickListener(v -> {
            if(btnProceed.getText().toString().equals("Format & Proceed")) {
                formatBothStorages(() -> {
                    runOnUiThread(() -> {
                        if (storageCheckPopup != null && storageCheckPopup.isShowing()) {
                            storageCheckPopup.dismiss();
                        }
                        proceedToNFZOrMissionCheck();
                    });
                });
            }else{
                if (storageCheckPopup != null && storageCheckPopup.isShowing()) {
                    storageCheckPopup.dismiss();
                }
                proceedToNFZOrMissionCheck();
            }
        });
        // Show popup
        storageCheckPopup.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER, 0, 0);
    }

    /**
     * Format both SD card and internal storage
     * @param onComplete Callback to execute after successful formatting
     */
    private void formatBothStorages(Runnable onComplete) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Formatting storage, please wait...", Toast.LENGTH_LONG).show();
        });

        // Format SD Card first
        formatStorage(CameraStorageLocation.SDCARD, () -> {
            Log.d(TAG, "SD Card formatted successfully");

            // Then format Internal Storage
            formatStorage(CameraStorageLocation.INTERNAL, () -> {
                Log.d(TAG, "Internal storage formatted successfully");

                runOnUiThread(() -> {
                    Toast.makeText(this, "Storage formatted successfully", Toast.LENGTH_SHORT).show();
                });

                // Execute completion callback
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    /**
     * Format specific storage location (SD Card or Internal Storage)
     * @param location Storage location to format
     * @param onSuccess Callback to execute after successful formatting
     */
    private void formatStorage(CameraStorageLocation location, Runnable onSuccess) {
        new Thread(() -> {
            try {
                String storageName = (location == CameraStorageLocation.SDCARD) ? "SD Card" : "Internal Storage";

                // Perform the format action with storage location as parameter
                KeyManager.getInstance().performAction(KeyTools.createKey(CameraKey.KeyFormatStorage), location, new CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>() {
                    @Override
                    public void onSuccess(EmptyMsg emptyMsg) {
                        Log.d(TAG, storageName + " formatted successfully");

                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to format " + storageName + ": " + error.description());

                        runOnUiThread(() -> {
                            Toast.makeText(WaypointActivity.this,
                                "Failed to format " + storageName + ": " + error.description(),
                                Toast.LENGTH_LONG).show();
                        });

                        // Still call onSuccess to continue with the flow even if one storage fails
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error formatting storage: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(WaypointActivity.this,
                        "Error formatting storage: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });

                // Still call onSuccess to continue with the flow
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        }).start();
    }

    /**
     * Proceed to NFZ check or mission confirmation after storage check
     */
    private void proceedToNFZOrMissionCheck() {
        // Check for NFZ first, then show appropriate popup
        if (!waypointsInNFZ.isEmpty() && currentNFZ != null) {
            // Show NFZ warning popup
            showNFZWarningPopup();
        } else {
            // No NFZ, proceed directly to mission confirmation
            showMissionStartConfirmationPopup();
        }
    }

    /**
     * Show NFZ warning popup before mission start
     */
    private void showNFZWarningPopup() {
        if (currentNFZ == null) {
            showMissionStartConfirmationPopup();
            return;
        }

        runOnUiThread(() -> {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.popup_nfz_warning, null);

            // Create popup window
            int width = (int) (340 * getResources().getDisplayMetrics().density);
            nfzWarningPopup = new PopupWindow(popupView,
                    width,
                    DrawerLayout.LayoutParams.WRAP_CONTENT, true);
            nfzWarningPopup.setAnimationStyle(android.R.style.Animation_Dialog);
            nfzWarningPopup.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent));
            nfzWarningPopup.setOutsideTouchable(true);

            TextView description = popupView.findViewById(R.id.nfzWarningDescription);
            Button btnCancel = popupView.findViewById(R.id.btnCancelMission);
            Button btnUnlock = popupView.findViewById(R.id.btnUnlockNFZWarning);
            Button btnContinue = popupView.findViewById(R.id.btnContinueAnyway);

            // Set description based on category
            String desc = "";
            boolean showUnlock = false;
            boolean showContinue = false;
            
            String zoneName = currentNFZ.name != null ? currentNFZ.name : "";
            String altitudeStr = "";
            if(currentNFZ.hasUpperLimit()){
                altitudeStr = " Maximum altitude allowed is " + currentNFZ.upperLimit + " meters";
            }
            if (currentNFZ.category != null) {
                switch (currentNFZ.category.toUpperCase()) {
                    // 1. Restricted Zones - Cannot fly
                    case "RESTRICTED":
                        desc = zoneName + " 🚫 RESTRICTED ZONE\n\nFlight is prevented in this area. Please remove waypoints from this zone or contact flysafe@dji.com for authorization." + altitudeStr;
                        btnUnlock.setVisibility(View.GONE);
                        btnContinue.setVisibility(View.VISIBLE);
                        break;
                    // 2. Altitude Zones - Height limited
                    case "ALTITUDE":
                        desc = zoneName + " 📏 ALTITUDE ZONE\n\nFlight altitude is limited in this area. Check the maximum allowed altitude and adjust your waypoints accordingly." + altitudeStr;
                        showContinue = true;
                        break;

                    // 3. Authorization Zones - Requires unlock
                    case "AUTHORIZATION":
                        desc = zoneName + " 🔐 AUTHORIZATION ZONE\n\nThis zone requires unlocking with your DJI verified account. You must unlock before starting the mission." + altitudeStr;
                        showUnlock = true;
                        btnUnlock.setText("Unlock Authorization Zone");
                        break;

                    // 4. Warning Zones - Warning only
                    case "WARNING":
                        desc = zoneName + " ⚠️ WARNING ZONE\n\nYou will receive warnings when flying here (e.g., Class E airspace). Flight is allowed but proceed with caution." + altitudeStr;
                        showContinue = true;
                        break;

                    // 5. Enhanced Warning Zones - Self-unlockable
                    case "ENHANCED_WARNING":
                        desc = zoneName + " ⚠️ ENHANCED WARNING ZONE\n\nYou can unlock this zone directly in the app without internet or verified account. You may also continue with caution." + altitudeStr;
                        showUnlock = true;
                        showContinue = true;
                        btnUnlock.setText("Unlock All Enhanced Warnings");
                        break;

                    // 6. Regulatory Restricted - Prohibited by law
                    case "REGULATORY_RESTRICTED":
                    case "REGULATORY":
                        desc = zoneName + " 🚨 REGULATORY RESTRICTED\n\nFlight is prohibited by local regulations (e.g., prison, government facility). This restriction CANNOT be unlocked." + altitudeStr;
                        btnUnlock.setVisibility(View.GONE);
                        btnContinue.setVisibility(View.GONE);
                        break;

                    // 7. Approved Zones - Allowed (China)
                    case "APPROVED_FOR_LIGHT_UAV":
                    case "APPROVED":
                        desc = zoneName + " ✅ APPROVED ZONE\n\nThis zone is approved for light UAV operations under local regulations. You may proceed with your flight." + altitudeStr;
                        showContinue = true;
                        break;

                    // 8. Special zones (Airport, Military, etc.)
                    case "AIRPORT":
                        desc = zoneName + " ✈️ AIRPORT ZONE\n\nFlight is restricted near airports. Contact airport authority for permission. Remove waypoints from this area." + altitudeStr;
                        btnUnlock.setVisibility(View.GONE);
                        btnContinue.setVisibility(View.GONE);
                        break;

                    case "MILITARY":
                        desc = zoneName + " 🎖️ MILITARY ZONE\n\nFlight is prohibited in military areas. This is enforced by law and CANNOT be unlocked." + altitudeStr;
                        btnUnlock.setVisibility(View.GONE);
                        btnContinue.setVisibility(View.GONE);
                        break;

                    case "SPECIAL_USE":
                        desc = zoneName + " ⚠️ SPECIAL USE AIRSPACE\n\nCheck local regulations and NOTAM before flying. Proceed with extreme caution." + altitudeStr;
                        showContinue = true;
                        break;
                    default:
                        desc = zoneName + " ⚠️ NO-FLY ZONE\n\nYour mission intersects with a no-fly zone. Please review your waypoints." + altitudeStr;
                        break;
                }
            }

            description.setText(desc);

            // Always show unlock button, but disable if not unlockable
            btnUnlock.setVisibility(View.VISIBLE);
            if (showUnlock) {
                btnUnlock.setEnabled(true);
                btnUnlock.setAlpha(1.0f);
                btnUnlock.setVisibility(View.VISIBLE);
            } else {
                btnUnlock.setEnabled(false);
                btnUnlock.setAlpha(0.5f);
                btnUnlock.setText("Not Unlockable");
                btnUnlock.setVisibility(View.GONE);
            }

            if (showContinue) {
                btnContinue.setVisibility(View.VISIBLE);
            }

            // Cancel button
            btnCancel.setOnClickListener(v -> {
                if (nfzWarningPopup != null && nfzWarningPopup.isShowing()) {
                    nfzWarningPopup.dismiss();
                }
            });

            // Unlock button
            btnUnlock.setOnClickListener(v -> {
                unlockNFZForMission();
            });

            // Continue anyway button
            btnContinue.setOnClickListener(v -> {
                if (nfzWarningPopup != null && nfzWarningPopup.isShowing()) {
                    nfzWarningPopup.dismiss();
                }
                // Proceed to mission confirmation
                showMissionStartConfirmationPopup();
            });

            // Show popup
            nfzWarningPopup.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER, 0, 0);
        });
    }

    /**
     * Unlock NFZ for mission using DJI FlySafe unlock mechanism
     */
    private void unlockNFZForMission() {
        if (currentNFZ == null) {
            Toast.makeText(this, "No NFZ to unlock", Toast.LENGTH_SHORT).show();
            return;
        }

        Button btnUnlock = nfzWarningPopup.getContentView().findViewById(R.id.btnUnlockNFZWarning);
        btnUnlock.setEnabled(false);
        btnUnlock.setText("Unlocking...");

        // Check if this is an authorization zone - REQUIRES DJI LOGIN
        if (currentNFZ.category != null && currentNFZ.category.equalsIgnoreCase("AUTHORIZATION")) {
            if (!isDJIAccountLoggedIn()) {
                btnUnlock.setEnabled(true);
                btnUnlock.setText("Unlock Authorization Zone");
                loginToDJIAccount();
                return;
            }else{
                nfzManager.unlockAuthorizationZone(currentNFZ.flyZoneId, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            if (nfzWarningPopup != null && nfzWarningPopup.isShowing()) {
                                nfzWarningPopup.dismiss();
                            }
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                showMissionStartConfirmationPopup();
                            }, 500);
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError idjiError) {
                        runOnUiThread(() -> {
                            String errorMsg = idjiError != null && idjiError.description() != null ?
                                    idjiError.description() : "Failed to unlock enhanced warnings";
                            Toast.makeText(WaypointActivity.this,
                                    "❌ Unlock failed: " + errorMsg,
                                    Toast.LENGTH_LONG).show();
                            btnUnlock.setEnabled(true);
                            btnUnlock.setText("Unlock All Enhanced Warnings");
                        });
                    }
                });
            }
        }

        // Check if this is an enhanced warning zone
        if (currentNFZ.category != null && currentNFZ.category.equalsIgnoreCase("ENHANCED_WARNING")) {
            nfzManager.unlockAllEnhancedWarningZones(new dji.v5.common.callback.CommonCallbacks.CompletionCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(WaypointActivity.this,
                                "✅ All enhanced warning zones unlocked!\n\nThe aircraft will no longer show enhanced warning prompts.",
                                Toast.LENGTH_LONG).show();

                        if (nfzWarningPopup != null && nfzWarningPopup.isShowing()) {
                            nfzWarningPopup.dismiss();
                        }
                        // Proceed to mission confirmation
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            showMissionStartConfirmationPopup();
                        }, 500);
                    });
                }

                @Override
                public void onFailure(dji.v5.common.error.IDJIError error) {
                    runOnUiThread(() -> {
                        String errorMsg = error != null && error.description() != null ?
                                error.description() : "Failed to unlock enhanced warnings";
                        Toast.makeText(WaypointActivity.this,
                                "❌ Unlock failed: " + errorMsg,
                                Toast.LENGTH_LONG).show();

                        btnUnlock.setEnabled(true);
                        btnUnlock.setText("Unlock All Enhanced Warnings");
                    });
                }
            });
        } else if (NFZManager.isNFZUnlockable(currentNFZ.category)) {
            // Regular authorization zone unlock
            nfzManager.requestUnlock(currentNFZ, new dji.v5.common.callback.CommonCallbacks.CompletionCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(WaypointActivity.this,
                                "✅ Zone unlocked successfully!",
                                Toast.LENGTH_LONG).show();
                        if (nfzWarningPopup != null && nfzWarningPopup.isShowing()) {
                            nfzWarningPopup.dismiss();
                        }
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            checkWaypointsForNFZ();
                            showMissionStartConfirmationPopup();
                        }, 1500);
                    });
                }

                @Override
                public void onFailure(dji.v5.common.error.IDJIError error) {
                    runOnUiThread(() -> {
                        String errorMsg = error != null && error.description() != null ?
                                error.description() : "Failed to unlock zone";
                        Toast.makeText(WaypointActivity.this,
                                "❌ Unlock failed: " + errorMsg,
                                Toast.LENGTH_LONG).show();

                        btnUnlock.setEnabled(true);
                        btnUnlock.setText("Unlock Zone");
                    });
                }
            });
        } else {
            // Cannot unlock this zone type
            Toast.makeText(this,
                    "This zone cannot be unlocked. It is permanently restricted.",
                    Toast.LENGTH_LONG).show();
            btnUnlock.setEnabled(true);
            btnUnlock.setText("Unlock Zone");
        }
    }

    /**
     * Draw NFZ polygons on the map
     */
    private void drawNFZPolygons(List<NFZManager.SimpleFlyZoneInfo> zones) {
        Log.d(TAG, ">>> drawNFZPolygons: CALLED with " + (zones != null ? zones.size() : "NULL") + " zones");

        if (googleMap == null) {
            Log.w(TAG, ">>> drawNFZPolygons: Cannot draw - googleMap is NULL!");
            return;
        }

        if (zones == null || zones.isEmpty()) {
            Log.w(TAG, ">>> drawNFZPolygons: No zones to draw");
            return;
        }

        Log.d(TAG, ">>> drawNFZPolygons: Starting to draw " + zones.size() + " zones on map");

        runOnUiThread(() -> {
            // Clear existing polygons and circles
            clearNFZPolygons();

            for (NFZManager.SimpleFlyZoneInfo zone : zones) {
                Log.d(TAG, "=== DRAWING ZONE: " + zone.name + " ===");
                Log.d(TAG, "Zone Category: '" + zone.category + "'");
                Log.d(TAG, "Has Polygons: " + zone.hasPolygons());
                Log.d(TAG, "Radius: " + zone.radiusMeters + "m");

                int color = NFZManager.getNFZColor(zone.category);
                Log.d(TAG, "Color returned: RGB(" + Color.red(color) + ", " + Color.green(color) + ", " + Color.blue(color) + ")");

                int fillColor = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color));

                if (zone.hasPolygons()) {
                    Log.d(TAG, "Drawing as POLYGON zone");

                    // Draw polygon zones WITHOUT labels
                    for (int i = 0; i < zone.polygons.size(); i++) {
                        List<LatLng> polygonPoints = zone.polygons.get(i);

                        PolygonOptions polygonOptions = new PolygonOptions()
                                .addAll(polygonPoints)
                                .strokeColor(color)
                                .strokeWidth(3f)
                                .fillColor(fillColor)
                                .clickable(true);

                        Polygon polygon = googleMap.addPolygon(polygonOptions);
                        nfzPolygons.add(polygon);
                        polygonToZoneMap.put(polygon, zone);

                        // Label removed - users will tap to see info
                        Log.d(TAG, "Drew polygon " + i + " for zone: " + zone.name + " with " +
                              polygonPoints.size() + " vertices, color: " + zone.category);
                    }
                } else {
                    Log.d(TAG, "Drawing as CIRCULAR zone");
                    // Draw circular zones WITHOUT labels (Authorization, etc.)
                    LatLng center = new LatLng(zone.latitude, zone.longitude);

                    // Skip zones with invalid radius
                    if (zone.radiusMeters <= 0) {
                        Log.w(TAG, "Skipping circular zone '" + zone.name + "' with invalid radius: " + zone.radiusMeters + "m");
                        continue;
                    }

                    Log.d(TAG, "Drawing circular zone: " + zone.name +
                          " (Category: " + zone.category + ")" +
                          " at (" + center.latitude + ", " + center.longitude + ")" +
                          " with radius " + zone.radiusMeters + "m");

                    // Create circle options
                    com.google.android.gms.maps.model.CircleOptions circleOptions =
                        new com.google.android.gms.maps.model.CircleOptions()
                            .center(center)
                            .radius(zone.radiusMeters)
                            .strokeColor(color)
                            .strokeWidth(3f)
                            .fillColor(fillColor)
                            .clickable(true);

                    com.google.android.gms.maps.model.Circle circle = googleMap.addCircle(circleOptions);

                    // Create a circular polygon approximation for click handling
                    List<LatLng> circleAsPolygon = generateCirclePoints(center, zone.radiusMeters, 32);

                    PolygonOptions polygonOptions = new PolygonOptions()
                            .addAll(circleAsPolygon)
                            .strokeColor(color)
                            .strokeWidth(3f)
                            .fillColor(fillColor)
                            .clickable(true);

                    Polygon polygon = googleMap.addPolygon(polygonOptions);
                    nfzPolygons.add(polygon);
                    polygonToZoneMap.put(polygon, zone);

                    Log.d(TAG, "Successfully drew circular zone: " + zone.name + " with " + zone.category + " category");
                }
            }

            // Setup polygon click listener - show info popup when tapped
            googleMap.setOnPolygonClickListener(polygon -> {
                NFZManager.SimpleFlyZoneInfo zone = polygonToZoneMap.get(polygon);
                if (zone != null) {
                    showNFZInfoPopup(zone);
                }
            });
        });
    }

    /**
     * Generate circle points for visualization
     */
    private List<LatLng> generateCirclePoints(LatLng center, double radiusMeters, int numPoints) {
        List<LatLng> points = new ArrayList<>();
        double earthRadius = 6371000; // meters

        for (int i = 0; i <= numPoints; i++) {
            double angle = 2 * Math.PI * i / numPoints;

            // Calculate offset in meters
            double dx = radiusMeters * Math.cos(angle);
            double dy = radiusMeters * Math.sin(angle);

            // Convert to lat/lng offsets
            double deltaLat = dy / earthRadius * (180 / Math.PI);
            double deltaLng = dx / (earthRadius * Math.cos(Math.toRadians(center.latitude))) * (180 / Math.PI);

            points.add(new LatLng(center.latitude + deltaLat, center.longitude + deltaLng));
        }

        return points;
    }

    /**
     * Add a label marker at a specific position (for circular zones)
     */
    private void addNFZLabelAtPosition(NFZManager.SimpleFlyZoneInfo zone, LatLng position) {
        if (googleMap == null) return;

        try {
            // Create label text
            String labelText = zone.category != null ? zone.category : "NFZ";

            // Increased dimensions for larger label
            int width = 400;
            int height = 100;

            // Create a custom marker with text
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Background
            Paint bgPaint = new Paint();
            bgPaint.setColor(NFZManager.getNFZColor(zone.category));
            bgPaint.setAlpha(200);
            bgPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(10, 10, width - 10, height - 10, 15, 15, bgPaint);

            // Border
            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3);
            canvas.drawRoundRect(10, 10, width - 10, height - 10, 15, 15, borderPaint);

            // Text - increased font size
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(36);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(labelText, width / 2, height / 2 + 12, textPaint);

            BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bitmap);

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(100);

            googleMap.addMarker(markerOptions);

            Log.d(TAG, "Added label for circular zone " + zone.name + " at " + position.latitude + ", " + position.longitude);
        } catch (Exception e) {
            Log.e(TAG, "Error adding zone label: " + e.getMessage());
        }
    }

    /**
     * Add a label marker for a specific polygon
     */
    private void addNFZLabelForPolygon(NFZManager.SimpleFlyZoneInfo zone, List<LatLng> polygonPoints) {
        if (googleMap == null || polygonPoints == null || polygonPoints.isEmpty()) return;

        try {
            // Calculate center of polygon
            double sumLat = 0, sumLng = 0;
            for (LatLng point : polygonPoints) {
                sumLat += point.latitude;
                sumLng += point.longitude;
            }
            LatLng center = new LatLng(sumLat / polygonPoints.size(), sumLng / polygonPoints.size());

            // Create label text
            String labelText = zone.category != null ? zone.category : "NFZ";

            // Increased dimensions for larger label
            int width = 400;
            int height = 100;

            // Create a custom marker with text
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Background
            Paint bgPaint = new Paint();
            bgPaint.setColor(NFZManager.getNFZColor(zone.category));
            bgPaint.setAlpha(200);
            bgPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(10, 10, width - 10, height - 10, 15, 15, bgPaint);

            // Border
            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3);
            canvas.drawRoundRect(10, 10, width - 10, height - 10, 15, 15, borderPaint);

            // Text - increased font size
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(36);  // Increased from 24 to 36
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(labelText, width / 2, height / 2 + 12, textPaint);

            BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bitmap);

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(center)
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(100);

            googleMap.addMarker(markerOptions);

            Log.d(TAG, "Added label for polygon of " + zone.name + " at " + center.latitude + ", " + center.longitude);
        } catch (Exception e) {
            Log.e(TAG, "Error adding polygon label: " + e.getMessage());
        }
    }

    /**
     * Show detailed NFZ information popup using nfz_info_panel.xml
     */
    private void showNFZInfoPopup(NFZManager.SimpleFlyZoneInfo zone) {
        runOnUiThread(() -> {
            // Inflate the custom popup layout
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.nfz_info_panel, null);

            // Create popup window
            PopupWindow infoPopup = new PopupWindow(popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true);
            infoPopup.setAnimationStyle(android.R.style.Animation_Dialog);
            infoPopup.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent));
            infoPopup.setOutsideTouchable(true);

            // Set zone information
            TextView nameText = popupView.findViewById(R.id.nfzNameText);
            TextView levelText = popupView.findViewById(R.id.nfzLevelText);
            View colorIndicator = popupView.findViewById(R.id.nfzColorIndicator);
            LinearLayout altitudeContainer = popupView.findViewById(R.id.altitudeLimitContainer);
            TextView altitudeText = popupView.findViewById(R.id.nfzAltitudeLimit);
            ImageView closeButton = popupView.findViewById(R.id.btnCloseNFZPanel);

            // Set name
            nameText.setText(zone.name != null ? zone.name : "Unnamed Zone");

            // Set category with color
            levelText.setText(NFZManager.getNFZLevelText(zone.category));
            int color = NFZManager.getNFZColor(zone.category);
            colorIndicator.setBackgroundColor(color);

            // Show altitude limit if available
            if (zone.hasUpperLimit()) {
                altitudeContainer.setVisibility(View.VISIBLE);
                altitudeText.setText(zone.upperLimit + " meters");
            } else {
                altitudeContainer.setVisibility(View.GONE);
            }

            // Close button
            closeButton.setOnClickListener(v -> infoPopup.dismiss());

            // Show popup at center of screen
            infoPopup.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER, 0, 0);

            Log.d(TAG, "Showing NFZ info popup for: " + zone.name);
        });
    }

    /**
     * Clear all NFZ polygons from map
     */
    private void clearNFZPolygons() {
        runOnUiThread(() -> {
            for (Polygon polygon : nfzPolygons) {
                polygon.remove();
            }
            nfzPolygons.clear();
            polygonToZoneMap.clear();
            Log.d(TAG, "Cleared all NFZ polygons");
        });
    }

    /**
     * Load and display NFZ polygons when map is ready
     */
    private void loadNFZPolygons(LatLng location) {
        if (nfzManager == null) {
            Log.w(TAG, "NFZ Manager not initialized");
            return;
        }

        nfzManager.getFlyZonesForVisualization(location, new NFZManager.FlyZonesCallback() {
            @Override
            public void onFlyZonesRetrieved(List<NFZManager.SimpleFlyZoneInfo> zones) {
                Log.d(TAG, ">>> loadNFZPolygons: Received " + zones.size() + " fly zones for visualization");

                if (zones == null || zones.isEmpty()) {
                    Log.w(TAG, ">>> loadNFZPolygons: No zones received - map will be empty");
                    return;
                }

                for (NFZManager.SimpleFlyZoneInfo zone : zones) {
                    Log.d(TAG, ">>> Zone to draw: " + zone.name + " (Category: " + zone.category + ", Has Polygons: " + zone.hasPolygons() + ")");
                }

                Log.d(TAG, ">>> loadNFZPolygons: Calling drawNFZPolygons with " + zones.size() + " zones");
                drawNFZPolygons(zones);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, ">>> loadNFZPolygons: Error loading fly zones: " + error);
            }
        });
    }

    /**
     * Setup marker click listener to show waypoint settings popup
     */
    private void setupWaypointMarkerClickListener() {
        if (googleMap == null) {
            Log.w(TAG, "Google Map not initialized");
            return;
        }

        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // Check if the clicked marker is a waypoint marker (not home, drone, or POI)
                if (points != null && points.contains(marker)) {
                    int waypointIndex = points.indexOf(marker);
                    if (waypointIndex >= 0 && waypointIndex < waypointsList.size()) {
                        WaypointSetting waypointSetting = waypointsList.get(waypointIndex);
                        showWaypointSettingPopup(marker, waypointSetting, waypointIndex);
                        return true; // Consume the event
                    }
                }
                return false; // Allow default behavior for other markers
            }
        });

        Log.d(TAG, "Waypoint marker click listener setup successfully");
    }

    /**
     * Show waypoint setting popup dialog
     */
    private void showWaypointSettingPopup(Marker marker, WaypointSetting waypointSetting, int waypointIndex) {
        try {
            // Inflate the popup layout
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.popup_waypoint_setting, null);

            // Create popup window
            PopupWindow waypointSettingPopup = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );

            // Get UI elements from popup
            EditText waypointNameEdit = popupView.findViewById(R.id.waypointNameEdit);
            TextView latitudeText = popupView.findViewById(R.id.latitudeText);
            TextView longitudeText = popupView.findViewById(R.id.longitudeText);
            SeekBar altitudeSlider = popupView.findViewById(R.id.altitudeSlider);
            TextView altitudeLbl = popupView.findViewById(R.id.altitudeLbl);
            SeekBar waypointSpeedSlider = popupView.findViewById(R.id.waypointSpeedSlider);
            TextView waypointSpeedLbl = popupView.findViewById(R.id.waypointSpeedLbl);
            SeekBar gimbalPitchAngleSlider = popupView.findViewById(R.id.gimbalPitchAngleSlider);
            TextView gimbalPitchAngleLbl = popupView.findViewById(R.id.gimbalPitchAngleLbl);
            Button closeBtn = popupView.findViewById(R.id.closeBtn);
            Button saveBtn = popupView.findViewById(R.id.saveBtn);

            // Populate fields with current values
            waypointNameEdit.setText(waypointSetting.name != null ? waypointSetting.name : "Waypoint " + (waypointIndex + 1));
            latitudeText.setText(String.format(Locale.US, "%.6f", waypointSetting.latitude));
            longitudeText.setText(String.format(Locale.US, "%.6f", waypointSetting.longitude));

            // Altitude: 1-50 meters (seekbar 0-490 = 1.0-50.0 in steps of 0.1)
            double currentAltitude = waypointSetting.altitude != null ? waypointSetting.altitude : 10.0;
            altitudeSlider.setProgress((int) ((currentAltitude - 1.0) * 10));
            altitudeLbl.setText(String.format(Locale.US, "Altitude: %.1f m", currentAltitude));

            // Waypoint Speed: 1-15 m/s (seekbar 0-140 = 1.0-15.0 in steps of 0.1)
            double currentSpeed = waypointSetting.waypointSpeed != null ? waypointSetting.waypointSpeed : 5.0;
            waypointSpeedSlider.setProgress((int) ((currentSpeed - 1.0) * 10));
            waypointSpeedLbl.setText(String.format(Locale.US, "Waypoint Speed: %.1f m/s", currentSpeed));

            // Gimbal Pitch Angle: -90 to 0 degrees (seekbar 0-90, where 0 = -90°, 90 = 0°)
            double currentGimbalAngle = waypointSetting.gimbalPitchAngle != null ? waypointSetting.gimbalPitchAngle : 1.0;
            gimbalPitchAngleSlider.setProgress((int) (currentGimbalAngle + 90));
            gimbalPitchAngleLbl.setText(String.format(Locale.US, "Gimbal Pitch Angle: %.1f°", currentGimbalAngle));

            // Altitude slider listener
            altitudeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    double altitude = 1.0 + (progress / 10.0);
                    altitudeLbl.setText(String.format(Locale.US, "Altitude: %.1f m", altitude));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // Waypoint Speed slider listener
            waypointSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    double speed = 1.0 + (progress / 10.0);
                    waypointSpeedLbl.setText(String.format(Locale.US, "Waypoint Speed: %.1f m/s", speed));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // Gimbal Pitch Angle slider listener
            gimbalPitchAngleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    double angle = progress - 90.0;
                    gimbalPitchAngleLbl.setText(String.format(Locale.US, "Gimbal Pitch Angle: %.1f°", angle));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // Close button
            closeBtn.setOnClickListener(v -> {
                waypointSettingPopup.dismiss();
            });

            // Save button
            saveBtn.setOnClickListener(v -> {
                try {
                    // Update waypoint setting with new values
                    waypointSetting.name = waypointNameEdit.getText().toString();
                    waypointSetting.altitude = 1.0 + (altitudeSlider.getProgress() / 10.0);
                    waypointSetting.waypointSpeed = 1.0 + (waypointSpeedSlider.getProgress() / 10.0);
                    waypointSetting.gimbalPitchAngle = gimbalPitchAngleSlider.getProgress() - 90.0;
                    marker.setTitle(waypointSetting.name);
                    waypointSettingPopup.dismiss();
                } catch (Exception e) {
                    Log.e(TAG, "Error saving waypoint settings: " + e.getMessage());
                    Toast.makeText(WaypointActivity.this, "Error saving settings", Toast.LENGTH_SHORT).show();
                }
            });

            // Show popup at the bottom of the screen
            waypointSettingPopup.setAnimationStyle(android.R.style.Animation_Dialog);
            waypointSettingPopup.showAtLocation(findViewById(android.R.id.content), Gravity.BOTTOM, 0, 0);

            Log.d(TAG, "Waypoint setting popup displayed for waypoint " + waypointIndex);

        } catch (Exception e) {
            Log.e(TAG, "Error showing waypoint setting popup: " + e.getMessage());
            Toast.makeText(this, "Error showing waypoint settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================================================
    // DJI ACCOUNT MANAGEMENT FOR NFZ UNLOCKING
    // ============================================================================================

    private dji.v5.manager.account.LoginInfoUpdateListener loginInfoUpdateListener;

    /**
     * Check if user is logged into DJI account
     * Required for unlocking AUTHORIZATION zones
     */
    private boolean isDJIAccountLoggedIn() {
        try {
            IUserAccountManager accountManager = UserAccountManager.getInstance();

            if (accountManager == null) {
                Log.w(TAG, "UserAccountManager not available");
                return false;
            }

            dji.v5.manager.account.LoginInfo loginInfo = accountManager.getLoginInfo();

            if (loginInfo == null) {
                Log.d(TAG, "DJI account: Not logged in (LoginInfo is null)");
                return false;
            }

            dji.v5.manager.account.LoginState state = loginInfo.getLoginState();
            boolean isLoggedIn = (state == dji.v5.manager.account.LoginState.LOGGED_IN);

            Log.d(TAG, "DJI account state: " + (state != null ? state.name() : "null") +
                      ", Logged in: " + isLoggedIn);

            return isLoggedIn;

        } catch (Exception e) {
            Log.e(TAG, "Error checking DJI login status: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Trigger DJI account login UI
     * Shows DJI's built-in login fragment
     * - Landscape: Shows as sidebar (not fullscreen)
     * - Portrait: Shows as fullscreen
     */
    private void loginToDJIAccount() {
        try {
            IUserAccountManager accountManager = UserAccountManager.getInstance();

            if (accountManager == null) {
                Toast.makeText(this,
                    "DJI Account Manager not available. Please restart the app.",
                    Toast.LENGTH_LONG).show();
                return;
            }

            // Register listener for login state changes
            if (loginInfoUpdateListener == null) {
                loginInfoUpdateListener = new LoginInfoUpdateListener() {
                    @Override
                    public void onLoginInfoUpdate(dji.v5.manager.account.LoginInfo loginInfo) {
                        if (loginInfo != null) {
                            dji.v5.manager.account.LoginState state = loginInfo.getLoginState();
                            Log.d(TAG, "Login state changed: " + (state != null ? state.name() : "null"));
                            if (state == dji.v5.manager.account.LoginState.LOGGED_IN) {
                                runOnUiThread(() -> {
                                    Toast.makeText(WaypointActivity.this,
                                        "✅ Logged into DJI account successfully!",
                                        Toast.LENGTH_LONG).show();
                                    if (currentNFZ != null &&
                                        currentNFZ.category != null &&
                                        currentNFZ.category.equalsIgnoreCase("AUTHORIZATION")) {
                                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                            unlockNFZForMission();
                                        }, 1000);
                                    }
                                });
                            } else if (state == dji.v5.manager.account.LoginState.TOKEN_OUT_OF_DATE) {
                                runOnUiThread(() -> {
                                    Toast.makeText(WaypointActivity.this,
                                        "⚠️ DJI account token expired. Please login again.",
                                        Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    }
                };
                accountManager.addLoginInfoUpdateListener(loginInfoUpdateListener);
            }
            Log.d(TAG, "Initiating DJI account login UI...");
            int orientation = getResources().getConfiguration().orientation;
            boolean isFullScreen = (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
            accountManager.logInDJIUserAccount(this, true,
                new dji.v5.common.callback.CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "DJI login UI completed successfully");
                        runOnUiThread(() -> {
                            Toast.makeText(WaypointActivity.this,
                                "✅ Logged into DJI account!\n\nYou can now unlock authorization zones.",
                                Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onFailure(dji.v5.common.error.IDJIError error) {
                        String errorMsg = error != null && error.description() != null ?
                                         error.description() : "Login cancelled or failed";
                        Log.e(TAG, "DJI login UI failed: " + errorMsg);
                        runOnUiThread(() -> {
                            Toast.makeText(WaypointActivity.this,
                                "DJI login: " + errorMsg,
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                });

        } catch (Exception e) {
            Log.e(TAG, "Error triggering DJI login: " + e.getMessage(), e);
            Toast.makeText(this,
                "Error opening DJI login: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

}
