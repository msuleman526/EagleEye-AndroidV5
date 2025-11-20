package io.empowerbits.sightflight.Activities.AddProject;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.empowerbits.sightflight.Activities.WaypointActivity;
import io.empowerbits.sightflight.ApiResponse.AddProjectResponse;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.Retrofit.ApiClient;
import io.empowerbits.sightflight.Retrofit.ApiService;
import io.empowerbits.sightflight.models.FlightAddress;
import io.empowerbits.sightflight.models.FlightSetting;
import io.empowerbits.sightflight.models.Project;
import io.empowerbits.sightflight.models.WaypointAddress;
import io.empowerbits.sightflight.util.ActivityCollector;
import io.empowerbits.sightflight.util.MapHelper;
import io.empowerbits.sightflight.util.UserSessionManager;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProjectWaypointActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ProjectWaypointActivity";

    // Validation Limits and Defaults (all in feet)
    private static final int HOUSE_HEIGHT_MIN = 20;
    private static final int HOUSE_HEIGHT_MAX = 60;
    private static final int HOUSE_HEIGHT_DEFAULT = 30;

    private static final int WAYPOINT_COUNT_MIN = 70;
    private static final int WAYPOINT_COUNT_MAX = 130;
    private static final int WAYPOINT_COUNT_DEFAULT = 80;

    private static final int WAYPOINT_RADIUS_MIN = 80;
    private static final int WAYPOINT_RADIUS_MAX = 120;
    private static final int WAYPOINT_RADIUS_DEFAULT = 80;
    private static final int MAX_OBSTACLE_HEIGHT_MIN = 80;
    private static final int MAX_OBSTACLE_HEIGHT_MAX = 120;
    private static final int MAX_OBSTACLE_HEIGHT_DEFAULT = 90;

    private static final int ALLOWED_AIRSPACE_MIN = 200;
    private static final int ALLOWED_AIRSPACE_MAX = 300;
    private static final int ALLOWED_AIRSPACE_DEFAULT = 200;

    private static final int SMALL_CIRCLE_RADIUS_MIN = 8;
    private static final int SMALL_CIRCLE_RADIUS_MAX = 35;
    private static final int SMALL_CIRCLE_RADIUS_DEFAULT = 15;
    private static final int SMALL_CIRCLE_WAYPOINT_COUNT = 10;

    // Bundle keys for state preservation
    private static final String KEY_HOUSE_HEIGHT = "house_height";
    private static final String KEY_WAYPOINT_COUNT = "waypoint_count";
    private static final String KEY_WAYPOINT_RADIUS = "waypoint_radius";
    private static final String KEY_MAX_OBSTACLE_HEIGHT = "max_obstacle_height";
    private static final String KEY_ALLOWED_AIRSPACE = "allowed_airspace";
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_CAMERA_ZOOM = "camera_zoom";
    private static final String KEY_WAYPOINTS_JSON = "waypoints_json";
    private static final String KEY_MAP_READY = "map_ready";

    // UI Components
    private ImageView backBtn;
    private EditText houseHeightEdit;
    private EditText noOfWaypointEdit;
    private ImageButton waypointMinusBtn;
    private ImageButton waypointPlusBtn;
    private EditText waypointRadiusEdt;
    private ImageButton radiusMinusBtn;
    private ImageButton radiusPlusBtn;
    private EditText maxObstacleHeight;
    private EditText allowdAirSpaceHeightEdt;
    private Button continueBtn;

    // Map Components
    private GoogleMap googleMap;
    private SupportMapFragment mapFragment;
    private MapHelper mapHelper; // For regular waypoints (numbered 9+)
    private MapHelper smallCircleMapHelper; // For small circle waypoints (numbered 1-8)
    private Marker projectLocationMarker;
    private List<Marker> inspectionWaypointMarkers = new ArrayList<>(); // Green dot markers for inspection waypoints
    private static final int MAX_INSPECTION_WAYPOINTS = 8;

    // Camera position state for orientation change
    private LatLng savedCameraPosition;
    private float savedCameraZoom = 19f;
    private boolean isMapReady = false;

    // Project Data
    private Project project;
    private List<FlightAddress> waypointList = new ArrayList<>();
    private FlightSetting waypointSetting;

    // API
    private ApiService apiService;
    private UserSessionManager sessionManager;
    private Dialog loadingDialog;

    // State restoration flag
    private boolean isRestoringState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_waypoints);
        ActivityCollector.addActivity(this);

        // Get project from intent
        if (getIntent() != null && getIntent().hasExtra("project")) {
            project = (Project) getIntent().getSerializableExtra("project");
        } else {
            Toast.makeText(this, "No project data found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Restore state if available
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }

        initializeComponents();
        initializeApiService();
        setupGoogleMaps();
        setupListeners();

        // Load project data only if not restoring from savedInstanceState
        if (savedInstanceState == null) {
            loadProjectData();
        } else {
            // Restore field values from saved state
            restoreFieldValues(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save current state
        if (houseHeightEdit != null && houseHeightEdit.getText() != null) {
            outState.putString(KEY_HOUSE_HEIGHT, houseHeightEdit.getText().toString());
        }
        if (noOfWaypointEdit != null && noOfWaypointEdit.getText() != null) {
            outState.putString(KEY_WAYPOINT_COUNT, noOfWaypointEdit.getText().toString());
        }
        if (waypointRadiusEdt != null && waypointRadiusEdt.getText() != null) {
            outState.putString(KEY_WAYPOINT_RADIUS, waypointRadiusEdt.getText().toString());
        }
        if (maxObstacleHeight != null && maxObstacleHeight.getText() != null) {
            outState.putString(KEY_MAX_OBSTACLE_HEIGHT, maxObstacleHeight.getText().toString());
        }
        if (allowdAirSpaceHeightEdt != null && allowdAirSpaceHeightEdt.getText() != null) {
            outState.putString(KEY_ALLOWED_AIRSPACE, allowdAirSpaceHeightEdt.getText().toString());
        }

        // Save waypoints as JSON
        if (!waypointList.isEmpty()) {
            Gson gson = new Gson();
            String waypointsJson = gson.toJson(waypointList);
            outState.putString(KEY_WAYPOINTS_JSON, waypointsJson);
        }

        // Save camera position if map is ready
        if (googleMap != null) {
            LatLng cameraPosition = googleMap.getCameraPosition().target;
            float cameraZoom = googleMap.getCameraPosition().zoom;
            outState.putParcelable(KEY_CAMERA_POSITION, cameraPosition);
            outState.putFloat(KEY_CAMERA_ZOOM, cameraZoom);
        }

        // Save map ready state
        outState.putBoolean(KEY_MAP_READY, isMapReady);
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        try {
            isRestoringState = true;

            // Restore camera position
            savedCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
            savedCameraZoom = savedInstanceState.getFloat(KEY_CAMERA_ZOOM, 16f);
            isMapReady = savedInstanceState.getBoolean(KEY_MAP_READY, false);

            // Restore waypoints
            String waypointsJson = savedInstanceState.getString(KEY_WAYPOINTS_JSON);
            if (waypointsJson != null && !waypointsJson.isEmpty()) {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<WaypointAddress>>(){}.getType();
                waypointList = gson.fromJson(waypointsJson, listType);
                if (waypointList == null) {
                    waypointList = new ArrayList<>();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring instance state: " + e.getMessage());
            waypointList = new ArrayList<>();
        }
    }

    private void restoreFieldValues(Bundle savedInstanceState) {
        try {
            String houseHeight = savedInstanceState.getString(KEY_HOUSE_HEIGHT);
            String waypointCount = savedInstanceState.getString(KEY_WAYPOINT_COUNT);
            String waypointRadius = savedInstanceState.getString(KEY_WAYPOINT_RADIUS);
            String maxObstacle = savedInstanceState.getString(KEY_MAX_OBSTACLE_HEIGHT);
            String allowedAirspace = savedInstanceState.getString(KEY_ALLOWED_AIRSPACE);

            if (houseHeight != null && !houseHeight.isEmpty()) {
                houseHeightEdit.setText(houseHeight);
            }
            if (waypointCount != null && !waypointCount.isEmpty()) {
                noOfWaypointEdit.setText(waypointCount);
            }
            if (waypointRadius != null && !waypointRadius.isEmpty()) {
                waypointRadiusEdt.setText(waypointRadius);
            }
            if (maxObstacle != null && !maxObstacle.isEmpty()) {
                maxObstacleHeight.setText(maxObstacle);
            }
            if (allowedAirspace != null && !allowedAirspace.isEmpty()) {
                allowdAirSpaceHeightEdt.setText(allowedAirspace);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring field values: " + e.getMessage());
        }
    }

    private void initializeComponents() {
        try {
            backBtn = findViewById(R.id.backBtn);
            houseHeightEdit = findViewById(R.id.houseHeightEdit);
            noOfWaypointEdit = findViewById(R.id.noOfWaypointEdit);
            waypointMinusBtn = findViewById(R.id.waypointMinusBtn);
            waypointPlusBtn = findViewById(R.id.waypointPlusBtn);
            waypointRadiusEdt = findViewById(R.id.waypointRadiusEdt);
            radiusMinusBtn = findViewById(R.id.radiusMinusBtn);
            radiusPlusBtn = findViewById(R.id.radiusPlusBtn);
            maxObstacleHeight = findViewById(R.id.maxObstacleHeight);
            allowdAirSpaceHeightEdt = findViewById(R.id.allowdAirSpaceHeightEdt);
            continueBtn = findViewById(R.id.continueBtn);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing components: " + e.getMessage());
        }
    }

    private void initializeApiService() {
        try {
            apiService = ApiClient.getApiService();
            sessionManager = new UserSessionManager(this);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing API service: " + e.getMessage());
        }
    }

    private void setupGoogleMaps() {
        try {
            // Remove existing fragment if present (handles orientation change)
            mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.mapFragment);

            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            } else {
                Log.e(TAG, "Google Maps setup - Fragment not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Google Maps: " + e.getMessage());
        }
    }

    private void setupListeners() {
        try {
            backBtn.setOnClickListener(v -> finish());
            waypointMinusBtn.setOnClickListener(v -> adjustWaypointCount(-1));
            waypointPlusBtn.setOnClickListener(v -> adjustWaypointCount(1));
            radiusMinusBtn.setOnClickListener(v -> adjustWaypointRadius(-1));
            radiusPlusBtn.setOnClickListener(v -> adjustWaypointRadius(1));
            continueBtn.setOnClickListener(v -> onContinueClicked());
            setupTextWatchers();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners: " + e.getMessage());
        }
    }

    private void setupTextWatchers() {
        // House Height validation (30-60 feet)
        houseHeightEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateAndConstrainValue(houseHeightEdit, HOUSE_HEIGHT_MIN, HOUSE_HEIGHT_MAX, "House Height");
            }
        });

        // Waypoint Count validation (70-85)
        noOfWaypointEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateAndConstrainValue(noOfWaypointEdit, WAYPOINT_COUNT_MIN, WAYPOINT_COUNT_MAX, "Waypoint Count");
                updateWaypointButtonStates(); // Update button states
                // Auto-regenerate waypoints when count changes
                if (!isRestoringState) {
                    regenerateWaypointsFromUI();
                }
            }
        });

        // Waypoint Radius validation (80-110 feet)
        waypointRadiusEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateAndConstrainValue(waypointRadiusEdt, WAYPOINT_RADIUS_MIN, WAYPOINT_RADIUS_MAX, "Waypoint Radius");
                updateRadiusButtonStates(); // Update button states
                // Auto-regenerate waypoints when radius changes
                if (!isRestoringState) {
                    regenerateWaypointsFromUI();
                }
            }
        });

        // Max Obstacle Height validation (80-110 feet)
        maxObstacleHeight.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateAndConstrainValue(maxObstacleHeight, MAX_OBSTACLE_HEIGHT_MIN, MAX_OBSTACLE_HEIGHT_MAX, "Max Obstacle Height");
            }
        });

        // Allowed Airspace Height validation (200-300 feet)
        allowdAirSpaceHeightEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateAndConstrainValue(allowdAirSpaceHeightEdt, ALLOWED_AIRSPACE_MIN, ALLOWED_AIRSPACE_MAX, "Allowed Airspace Height");
            }
        });
    }

    private void validateAndConstrainValue(EditText editText, int min, int max, String fieldName) {
        try {
            String text = editText.getText().toString().trim();
            if (text.isEmpty()) {
                return;
            }
            int value = Integer.parseInt(text);
            if (value < min) {
                editText.setError(fieldName + " must be at least " + min + " ft");
            } else if (value > max) {
                editText.setError(fieldName + " must be at most " + max + " ft");
            } else {
                editText.setError(null);
            }
        } catch (NumberFormatException e) {
            editText.setError("Invalid number");
        }
    }

    private void adjustWaypointCount(int delta) {
        try {
            String currentText = noOfWaypointEdit.getText().toString().trim();
            int currentValue = currentText.isEmpty() ? WAYPOINT_COUNT_DEFAULT : Integer.parseInt(currentText);

            int newValue = currentValue + delta;
            if (newValue >= WAYPOINT_COUNT_MIN && newValue <= WAYPOINT_COUNT_MAX) {
                noOfWaypointEdit.setText(String.valueOf(newValue));
                // TextWatcher will trigger regeneration
                updateWaypointButtonStates();
            }
        } catch (NumberFormatException e) {
            noOfWaypointEdit.setText(String.valueOf(WAYPOINT_COUNT_DEFAULT));
            updateWaypointButtonStates();
        }
    }

    private void adjustWaypointRadius(int delta) {
        try {
            String currentText = waypointRadiusEdt.getText().toString().trim();
            int currentValue = currentText.isEmpty() ? WAYPOINT_RADIUS_DEFAULT : Integer.parseInt(currentText);

            int newValue = currentValue + delta;
            if (newValue >= WAYPOINT_RADIUS_MIN && newValue <= WAYPOINT_RADIUS_MAX) {
                waypointRadiusEdt.setText(String.valueOf(newValue));
                // TextWatcher will trigger regeneration
                updateRadiusButtonStates();
            }
        } catch (NumberFormatException e) {
            waypointRadiusEdt.setText(String.valueOf(WAYPOINT_RADIUS_DEFAULT));
            updateRadiusButtonStates();
        }
    }

    /**
     * Update waypoint plus/minus button states based on current value
     */
    private void updateWaypointButtonStates() {
        try {
            String currentText = noOfWaypointEdit.getText().toString().trim();
            if (currentText.isEmpty()) {
                waypointMinusBtn.setEnabled(true);
                waypointPlusBtn.setEnabled(true);
                return;
            }

            int currentValue = Integer.parseInt(currentText);

            // Disable minus button at minimum
            waypointMinusBtn.setEnabled(currentValue > WAYPOINT_COUNT_MIN);
            waypointMinusBtn.setAlpha(currentValue > WAYPOINT_COUNT_MIN ? 1.0f : 0.3f);

            // Disable plus button at maximum
            waypointPlusBtn.setEnabled(currentValue < WAYPOINT_COUNT_MAX);
            waypointPlusBtn.setAlpha(currentValue < WAYPOINT_COUNT_MAX ? 1.0f : 0.3f);
        } catch (NumberFormatException e) {
            waypointMinusBtn.setEnabled(true);
            waypointPlusBtn.setEnabled(true);
        }
    }

    /**
     * Update radius plus/minus button states based on current value
     */
    private void updateRadiusButtonStates() {
        try {
            String currentText = waypointRadiusEdt.getText().toString().trim();
            if (currentText.isEmpty()) {
                radiusMinusBtn.setEnabled(true);
                radiusPlusBtn.setEnabled(true);
                return;
            }

            int currentValue = Integer.parseInt(currentText);

            // Disable minus button at minimum
            radiusMinusBtn.setEnabled(currentValue > WAYPOINT_RADIUS_MIN);
            radiusMinusBtn.setAlpha(currentValue > WAYPOINT_RADIUS_MIN ? 1.0f : 0.3f);

            // Disable plus button at maximum
            radiusPlusBtn.setEnabled(currentValue < WAYPOINT_RADIUS_MAX);
            radiusPlusBtn.setAlpha(currentValue < WAYPOINT_RADIUS_MAX ? 1.0f : 0.3f);
        } catch (NumberFormatException e) {
            radiusMinusBtn.setEnabled(true);
            radiusPlusBtn.setEnabled(true);
        }
    }

    private void regenerateWaypointsFromUI() {
        try {
            String countText = noOfWaypointEdit.getText().toString().trim();
            String radiusText = waypointRadiusEdt.getText().toString().trim();

            if (countText.isEmpty() || radiusText.isEmpty()) {
                return;
            }

            int count = Integer.parseInt(countText);
            int radiusFeet = Integer.parseInt(radiusText);

            if (count >= WAYPOINT_COUNT_MIN && count <= WAYPOINT_COUNT_MAX &&
                    radiusFeet >= WAYPOINT_RADIUS_MIN && radiusFeet <= WAYPOINT_RADIUS_MAX) {
                generateWaypoints(count, radiusFeet);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error regenerating waypoints: " + e.getMessage());
        }
    }

    private void loadProjectData() {
        try {
            if (project == null) {
                return;
            }

            // Load waypoints from project
            waypointList = project.getWaypointList();

            int inspectionWaypointCount = 0;
            int regularWaypointCount = 0;

            // Load flight settings (stored in feet)
            if (project.flight_setting != null) {
                String flightSettingStr = project.getFlightSettingAsString();
                if (flightSettingStr != null && !flightSettingStr.isEmpty() && !flightSettingStr.equals("null")) {
                    try {
                        JSONObject flightSettings = new JSONObject(flightSettingStr);
                        if (flightSettings.has("circleRadius")) {
                            waypointRadiusEdt.setText(String.valueOf(flightSettings.getInt("circleRadius")));
                        }

                        if (flightSettings.has("noOfWaypoints")) {
                            regularWaypointCount = flightSettings.getInt("noOfWaypoints");
                            noOfWaypointEdit.setText(String.valueOf(regularWaypointCount));
                        }

                        if (flightSettings.has("noOfInspectionWaypoints")) {
                            inspectionWaypointCount = flightSettings.getInt("noOfInspectionWaypoints");
                            Log.d(TAG, "Found " + inspectionWaypointCount + " inspection waypoints in flight_setting");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing flight settings: " + e.getMessage());
                    }
                }
            }else{
                noOfWaypointEdit.setText(String.valueOf(WAYPOINT_COUNT_DEFAULT));
                waypointRadiusEdt.setText(String.valueOf(WAYPOINT_RADIUS_DEFAULT));
            }

            // Load obstacle data (already in feet)
            if (project.height_of_house > 0) {
                houseHeightEdit.setText(String.valueOf(project.height_of_house));
            }else{
                houseHeightEdit.setText(String.valueOf(HOUSE_HEIGHT_DEFAULT));
            }
            if (project.must_height > 0) {
                maxObstacleHeight.setText(String.valueOf(project.must_height));
            }else{
                maxObstacleHeight.setText(String.valueOf(MAX_OBSTACLE_HEIGHT_DEFAULT));
            }
            if (project.highest_can > 0) {
                allowdAirSpaceHeightEdt.setText(String.valueOf(project.highest_can));
            }else{
                allowdAirSpaceHeightEdt.setText(String.valueOf(ALLOWED_AIRSPACE_DEFAULT));
            }

            // Update button states after loading data
            updateWaypointButtonStates();
            updateRadiusButtonStates();
        } catch (Exception e) {
            Log.e(TAG, "Error loading project data: " + e.getMessage());
        }
    }

    private void generateWaypoints(int count, int radiusFeet) {
        try {
            if (project == null || project.latitude == null || project.longitude == null) {
                Log.w(TAG, "Cannot generate waypoints: project location not set");
                return;
            }

            double centerLat = Double.parseDouble(project.latitude);
            double centerLng = Double.parseDouble(project.longitude);
            LatLng centerLocation = new LatLng(centerLat, centerLng);

            // Save existing inspection waypoints (last 5-8 in the list)
            List<FlightAddress> inspectionWaypoints = new ArrayList<>();
            int inspectionCount = 0;
            try {
                if (project != null && project.flight_setting != null) {
                    String flightSettingStr = project.getFlightSettingAsString();
                    if (flightSettingStr != null && !flightSettingStr.isEmpty()) {
                        JSONObject flightSettings = new JSONObject(flightSettingStr);
                        if (flightSettings.has("noOfInspectionWaypoints")) {
                            inspectionCount = flightSettings.getInt("noOfInspectionWaypoints");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing inspection waypoint count: " + e.getMessage());
            }

            // Extract inspection waypoints from existing list
            // Inspection waypoints are ALWAYS at the BEGINNING of the list (first 5-8 items)
            if (inspectionCount > 0 && waypointList.size() >= inspectionCount) {
                for (int i = 0; i < inspectionCount; i++) {
                    inspectionWaypoints.add(waypointList.get(i));
                }
                Log.d(TAG, "Preserved " + inspectionWaypoints.size() + " inspection waypoints from BEGINNING of list");
            }

            // Clear waypoint list to rebuild - INSPECTION FIRST, then REGULAR
            waypointList.clear();

            // 1. Add inspection waypoints FIRST
            if (!inspectionWaypoints.isEmpty()) {
                waypointList.addAll(inspectionWaypoints);
                Log.d(TAG, "Added " + inspectionWaypoints.size() + " inspection waypoints FIRST");
            }

            // 2. Generate and add regular waypoints AFTER inspection waypoints
            if (mapHelper != null) {
                mapHelper.setCenterLocation(centerLocation);
                // Start numbering from 1 (not SMALL_CIRCLE_WAYPOINT_COUNT + 1)
                mapHelper.updateWaypoints(count, radiusFeet, 1);

                // Get generated waypoints from MapHelper
                List<LatLng> regularWaypoints = mapHelper.getWaypoints();

                for (LatLng waypoint : regularWaypoints) {
                    FlightAddress waypointAddress = new FlightAddress(waypoint.latitude, waypoint.longitude);
                    waypointList.add(waypointAddress);
                }
                Log.d(TAG, "Added " + regularWaypoints.size() + " regular waypoints (numbered 1-" + count + ") AFTER inspection");
            }

            Log.d(TAG, "Total waypoints: " + waypointList.size() + " (Inspection: " + inspectionWaypoints.size() + ", Regular: " + count + ")");

            // Redisplay all waypoints with proper markers
            displayRestoredWaypoints();

        } catch (Exception e) {
            Log.e(TAG, "Error generating waypoints: " + e.getMessage());
            Toast.makeText(this, "Error generating waypoints", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        try {
            googleMap = map;
            isMapReady = true;

            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            // Get project location
            LatLng projectLocation = null;
            if (project != null && project.latitude != null && project.longitude != null) {
                try {
                    double lat = Double.parseDouble(project.latitude);
                    double lng = Double.parseDouble(project.longitude);
                    projectLocation = new LatLng(lat, lng);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing project coordinates: " + e.getMessage());
                }
            }

            // Initialize MapHelper
            if (projectLocation != null) {
                mapHelper = new MapHelper(this, googleMap, projectLocation);

                // Add project location marker
                MarkerOptions projectMarker = new MarkerOptions()
                        .position(projectLocation)
                        .title(project.name)
                        .snippet("Project Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                projectLocationMarker = googleMap.addMarker(projectMarker);
            }

            // Restore camera position or focus on project location
            if (savedCameraPosition != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedCameraPosition, savedCameraZoom));
            } else if (projectLocation != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(projectLocation, 19));
                savedCameraPosition = projectLocation;
                savedCameraZoom = 19f;
            }

            // Display existing waypoints or generate default
            // Use a slight delay to ensure TextFields are populated from loadProjectData()
            googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    try {
                        // Check if we need to generate waypoints
                        String waypointCountText = noOfWaypointEdit.getText().toString().trim();
                        String radiusText = waypointRadiusEdt.getText().toString().trim();

                        boolean hasRegularWaypoints = false;
                        int inspectionCount = 0;

                        // Check if waypoints contain regular waypoints or only inspection
                        try {
                            if (project != null && project.flight_setting != null) {
                                String flightSettingStr = project.getFlightSettingAsString();
                                if (flightSettingStr != null && !flightSettingStr.isEmpty()) {
                                    JSONObject flightSettings = new JSONObject(flightSettingStr);
                                    if (flightSettings.has("noOfInspectionWaypoints")) {
                                        inspectionCount = flightSettings.getInt("noOfInspectionWaypoints");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking waypoint types: " + e.getMessage());
                        }

                        // If waypointList size > inspectionCount, we have regular waypoints
                        hasRegularWaypoints = waypointList.size() > inspectionCount;

                        if (isRestoringState) {
                            // Restore after orientation change
                            if (hasRegularWaypoints) {
                                displayRestoredWaypoints();
                            } else {
                                // Need to regenerate regular waypoints
                                if (!waypointCountText.isEmpty() && !radiusText.isEmpty()) {
                                    int count = Integer.parseInt(waypointCountText);
                                    int radiusFeet = Integer.parseInt(radiusText);
                                    generateWaypoints(count, radiusFeet);
                                }
                            }
                        } else if (hasRegularWaypoints) {
                            // Has both regular and inspection waypoints - just display
                            displayRestoredWaypoints();
                        } else {
                            // Only has inspection waypoints or empty - generate regular waypoints
                            if (!waypointCountText.isEmpty() && !radiusText.isEmpty()) {
                                int count = Integer.parseInt(waypointCountText);
                                int radiusFeet = Integer.parseInt(radiusText);
                                generateWaypoints(count, radiusFeet);
                                Log.d(TAG, "Auto-generated waypoints on first load: count=" + count + ", radius=" + radiusFeet);
                            } else {
                                Log.w(TAG, "Cannot generate waypoints - TextField values not ready");
                            }
                        }

                        isRestoringState = false;
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onMapLoaded: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Map: " + e.getMessage());
        }
    }

    /**
     * Create a small green dot icon for inspection waypoints
     */
    private BitmapDescriptor createGreenDotIcon() {
        int size = 30; // Size of the dot in pixels
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.rgb(0, 200, 0)); // Green color
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // Draw filled circle
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // Optional: Add white border
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void displayRestoredWaypoints() {
        try {
            if (googleMap == null || waypointList.isEmpty()) {
                Log.w(TAG, "Cannot display waypoints - map not ready or no waypoints");
                return;
            }

            // Clear existing inspection markers
            for (Marker marker : inspectionWaypointMarkers) {
                if (marker != null) {
                    marker.remove();
                }
            }
            inspectionWaypointMarkers.clear();

            // Determine how many waypoints are inspection waypoints (FIRST 5-8 in list)
            // Structure: [inspection1, ..., inspection8, regular1, ..., regular130]
            int inspectionCount = 0;
            try {
                if (project != null && project.flight_setting != null) {
                    String flightSettingStr = project.getFlightSettingAsString();
                    if (flightSettingStr != null && !flightSettingStr.isEmpty()) {
                        JSONObject flightSettings = new JSONObject(flightSettingStr);
                        if (flightSettings.has("noOfInspectionWaypoints")) {
                            inspectionCount = flightSettings.getInt("noOfInspectionWaypoints");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing inspection waypoint count: " + e.getMessage());
            }

            // Calculate split point - inspection waypoints are FIRST
            int regularWaypointCount = waypointList.size() - inspectionCount;
            regularWaypointCount = Math.max(0, regularWaypointCount);

            Log.d(TAG, "Displaying " + inspectionCount + " inspection waypoints (first) and " + regularWaypointCount + " regular waypoints (after)");
            Log.d(TAG, "Total waypoints in list: " + waypointList.size());

            // Display inspection waypoints as green dots (FIRST N waypoints in the list)
            if (inspectionCount > 0 && waypointList.size() >= inspectionCount) {
                for (int i = 0; i < inspectionCount; i++) {
                    FlightAddress waypoint = waypointList.get(i);
                    LatLng position = new LatLng(waypoint.lat, waypoint.lng);

                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(position)
                            .title("Inspection " + (i + 1))
                            .icon(createGreenDotIcon())
                            .anchor(0.5f, 0.5f)
                            .draggable(false); // Not draggable in this activity

                    Marker marker = googleMap.addMarker(markerOptions);
                    if (marker != null) {
                        inspectionWaypointMarkers.add(marker);
                    }
                }
                Log.d(TAG, "Displayed " + inspectionWaypointMarkers.size() + " inspection waypoint markers (first " + inspectionCount + " items in list)");
            }

            // Display regular waypoints using MapHelper (AFTER inspection waypoints)
            // Extract ONLY the regular waypoints from the list (skip first inspectionCount items)
            if (mapHelper != null && regularWaypointCount > 0) {
                // Get current radius from UI
                String radiusText = waypointRadiusEdt.getText().toString().trim();
                int radiusFeet = radiusText.isEmpty() ? WAYPOINT_RADIUS_DEFAULT : Integer.parseInt(radiusText);

                // Extract regular waypoints from the list (starting from index inspectionCount)
                List<LatLng> regularWaypoints = new ArrayList<>();
                for (int i = inspectionCount; i < waypointList.size(); i++) {
                    FlightAddress waypoint = waypointList.get(i);
                    regularWaypoints.add(new LatLng(waypoint.lat, waypoint.lng));
                }

                // Use MapHelper to display regular waypoints with numbered markers starting from 1
                mapHelper.updateWaypoints(regularWaypointCount, radiusFeet, 1);
                Log.d(TAG, "Displayed " + regularWaypoints.size() + " regular waypoints using MapHelper");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error displaying restored waypoints: " + e.getMessage());
        }
    }

    private void onContinueClicked() {
        try {
            // Validate all fields
            if (!validateFields()) {
                return;
            }

            // Get values (all in feet)
            int houseHeightFeet = getHouseHeight();
            int waypointCount = getWaypointCount();
            int waypointRadiusFeet = getWaypointRadius();
            int maxObstacleFeet = getMaxObstacleHeight();
            int allowedAirspaceFeet = getAllowedAirspace();

            // Create or update waypoint settings (store in feet)
            if (waypointSetting == null) {
                waypointSetting = new FlightSetting();
            }

            waypointSetting.noOfWaypoints = waypointCount;
            waypointSetting.circleRadius = waypointRadiusFeet;

            // Calculate inspection waypoint count from existing flight_path
            int inspectionCount = 0;
            try {
                if (project != null && project.flight_setting != null) {
                    String flightSettingStr = project.getFlightSettingAsString();
                    if (flightSettingStr != null && !flightSettingStr.isEmpty()) {
                        JSONObject flightSettings = new JSONObject(flightSettingStr);
                        if (flightSettings.has("noOfInspectionWaypoints")) {
                            inspectionCount = flightSettings.getInt("noOfInspectionWaypoints");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing inspection waypoint count: " + e.getMessage());
            }

            waypointSetting.noOfInspectionWaypoints = inspectionCount;
            Log.d(TAG, "Setting noOfInspectionWaypoints to: " + inspectionCount);

            generateWaypoints(waypointCount, waypointRadiusFeet);

            // Show loading
            showLoading();

            // Call API to update project
            callUpdateWaypointsAPI(houseHeightFeet, maxObstacleFeet, allowedAirspaceFeet);

        } catch (Exception e) {
            Log.e(TAG, "Error handling continue button: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateFields() {
        boolean isValid = true;

        // Validate house height (30-60 feet)
        if (TextUtils.isEmpty(houseHeightEdit.getText().toString().trim())) {
            houseHeightEdit.setError("Required");
            isValid = false;
        } else {
            int value = Integer.parseInt(houseHeightEdit.getText().toString().trim());
            if (value < HOUSE_HEIGHT_MIN || value > HOUSE_HEIGHT_MAX) {
                houseHeightEdit.setError("Must be between " + HOUSE_HEIGHT_MIN + " and " + HOUSE_HEIGHT_MAX + " ft");
                isValid = false;
            }
        }

        // Validate waypoint count (70-85)
        if (TextUtils.isEmpty(noOfWaypointEdit.getText().toString().trim())) {
            noOfWaypointEdit.setError("Required");
            isValid = false;
        } else {
            int value = Integer.parseInt(noOfWaypointEdit.getText().toString().trim());
            if (value < WAYPOINT_COUNT_MIN || value > WAYPOINT_COUNT_MAX) {
                noOfWaypointEdit.setError("Must be between " + WAYPOINT_COUNT_MIN + " and " + WAYPOINT_COUNT_MAX);
                isValid = false;
            }
        }

        // Validate waypoint radius (80-110 feet)
        if (TextUtils.isEmpty(waypointRadiusEdt.getText().toString().trim())) {
            waypointRadiusEdt.setError("Required");
            isValid = false;
        } else {
            int value = Integer.parseInt(waypointRadiusEdt.getText().toString().trim());
            if (value < WAYPOINT_RADIUS_MIN || value > WAYPOINT_RADIUS_MAX) {
                waypointRadiusEdt.setError("Must be between " + WAYPOINT_RADIUS_MIN + " and " + WAYPOINT_RADIUS_MAX + " ft");
                isValid = false;
            }
        }

        // Validate max obstacle height (80-110 feet)
        if (TextUtils.isEmpty(maxObstacleHeight.getText().toString().trim())) {
            maxObstacleHeight.setError("Required");
            isValid = false;
        } else {
            int value = Integer.parseInt(maxObstacleHeight.getText().toString().trim());
            if (value < MAX_OBSTACLE_HEIGHT_MIN || value > MAX_OBSTACLE_HEIGHT_MAX) {
                maxObstacleHeight.setError("Must be between " + MAX_OBSTACLE_HEIGHT_MIN + " and " + MAX_OBSTACLE_HEIGHT_MAX + " ft");
                isValid = false;
            }
        }

        // Validate allowed airspace (200-300 feet)
        if (TextUtils.isEmpty(allowdAirSpaceHeightEdt.getText().toString().trim())) {
            allowdAirSpaceHeightEdt.setError("Required");
            isValid = false;
        } else {
            int value = Integer.parseInt(allowdAirSpaceHeightEdt.getText().toString().trim());
            if (value < ALLOWED_AIRSPACE_MIN || value > ALLOWED_AIRSPACE_MAX) {
                allowdAirSpaceHeightEdt.setError("Must be between " + ALLOWED_AIRSPACE_MIN + " and " + ALLOWED_AIRSPACE_MAX + " ft");
                isValid = false;
            }
        }

        if (!isValid) {
            Toast.makeText(this, "Please fix the errors", Toast.LENGTH_SHORT).show();
        }

        return isValid;
    }

    private int getHouseHeight() {
        try {
            String text = houseHeightEdit.getText().toString().trim();
            if (text.isEmpty()) return HOUSE_HEIGHT_DEFAULT;
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return HOUSE_HEIGHT_DEFAULT;
        }
    }

    private int getWaypointCount() {
        try {
            String text = noOfWaypointEdit.getText().toString().trim();
            if (text.isEmpty()) return WAYPOINT_COUNT_DEFAULT;
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return WAYPOINT_COUNT_DEFAULT;
        }
    }

    private int getWaypointRadius() {
        try {
            String text = waypointRadiusEdt.getText().toString().trim();
            if (text.isEmpty()) return WAYPOINT_RADIUS_DEFAULT;
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return WAYPOINT_RADIUS_DEFAULT;
        }
    }

    private int getMaxObstacleHeight() {
        try {
            String text = maxObstacleHeight.getText().toString().trim();
            if (text.isEmpty()) return MAX_OBSTACLE_HEIGHT_DEFAULT;
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return MAX_OBSTACLE_HEIGHT_DEFAULT;
        }
    }

    private int getAllowedAirspace() {
        try {
            String text = allowdAirSpaceHeightEdt.getText().toString().trim();
            if (text.isEmpty()) return ALLOWED_AIRSPACE_DEFAULT;
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return ALLOWED_AIRSPACE_DEFAULT;
        }
    }

    private void callUpdateWaypointsAPI(int houseHeightFeet, int maxObstacleFeet, int allowedAirspaceFeet) {
        Log.d(TAG, "DATA - House: " + houseHeightFeet + ", MaxObstacle: "+ maxObstacleFeet+ ", AllowedAirspace: " + allowedAirspaceFeet);
        try {
            // Build the final flight_path: [inspection waypoints] + [regular waypoints]
            // waypointList ALREADY contains: [inspection waypoints (first 5-8)] + [regular waypoints (next 70-130)]
            // We need to rebuild it with current inspection markers from the map

            // Get inspection waypoints from visible markers (green dots)
            List<FlightAddress> inspectionWaypointsList = new ArrayList<>();
            for (int i = 0; i < inspectionWaypointMarkers.size(); i++) {
                inspectionWaypointsList.add(new FlightAddress(
                    inspectionWaypointMarkers.get(i).getPosition().latitude,
                    inspectionWaypointMarkers.get(i).getPosition().longitude
                ));
            }

            // Get regular waypoints (SKIP the first inspection count items from waypointList)
            List<FlightAddress> regularWaypointsList = new ArrayList<>();
            int inspectionCount = waypointSetting.noOfInspectionWaypoints;
            if (waypointList.size() > inspectionCount) {
                // Extract only the regular waypoints (skip inspection waypoints at beginning)
                for (int i = inspectionCount; i < waypointList.size(); i++) {
                    regularWaypointsList.add(waypointList.get(i));
                }
            }

            // Build final merged list: Inspection FIRST, then Regular
            Gson gson = new Gson();
            List<FlightAddress> mergedList = new ArrayList<>();
            mergedList.addAll(inspectionWaypointsList);
            mergedList.addAll(regularWaypointsList);

            String waypointsJson = gson.toJson(mergedList);
            String settingsJson = gson.toJson(waypointSetting);
            String flightPathType = project.is_grid ? "grid" : "circular";

            Log.d(TAG, "Final waypoint structure - Inspection: " + inspectionWaypointsList.size() + ", Regular: " + regularWaypointsList.size() + ", Total: " + mergedList.size());

            Log.d(TAG, "FlightPath JSON length: " + waypointsJson.length());
            Log.d(TAG, "FlightSetting JSON: " + settingsJson);

            Call<AddProjectResponse> call = apiService.updateWaypoints(
                    "Bearer " + sessionManager.getToken(),
                    String.valueOf(project.id),
                    settingsJson,
                    waypointsJson,
                    flightPathType,
                    houseHeightFeet,
                    allowedAirspaceFeet,
                    maxObstacleFeet
            );

            call.enqueue(new Callback<AddProjectResponse>() {
                @Override
                public void onResponse(Call<AddProjectResponse> call, Response<AddProjectResponse> response) {
                    hideLoading();
                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(ProjectWaypointActivity.this, "Waypoints info saved successfully", Toast.LENGTH_SHORT).show();
                        project = response.body().project;
                        navigateToNextScreen();
                    } else {
                        Toast.makeText(ProjectWaypointActivity.this, "Error saving waypoints", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Waypoints API call failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<AddProjectResponse> call, Throwable t) {
                    hideLoading();
                    Toast.makeText(ProjectWaypointActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Waypoints API call error: " + t.getMessage());
                }
            });

        } catch (Exception e) {
            hideLoading();
            Log.e(TAG, "Error calling waypoints API: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToNextScreen() {
        try {
            Toast.makeText(this, "Project updated successfully.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, WaypointActivity.class);
            intent.putExtra("project", project);
            startActivity(intent);
            ActivityCollector.finishLastN(3); // closes last 4 activities
        } catch (Exception e) {
            Log.e(TAG, "Error navigating: " + e.getMessage());
        }
    }

    public void showLoading() {
        try {
            if (loadingDialog == null) {
                loadingDialog = new Dialog(this);
                loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                loadingDialog.setContentView(R.layout.dialog_loading);
                loadingDialog.setCancelable(false);
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            if (!loadingDialog.isShowing() && !isFinishing()) {
                loadingDialog.show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing loading dialog: " + e.getMessage());
        }
    }

    public void hideLoading() {
        try {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding loading dialog: " + e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Save current camera position before layout change
        if (googleMap != null) {
            savedCameraPosition = googleMap.getCameraPosition().target;
            savedCameraZoom = googleMap.getCameraPosition().zoom;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityCollector.removeActivity(this);
        try {
            hideLoading();
            if (loadingDialog != null) {
                loadingDialog = null;
            }

            // Cleanup inspection waypoint markers
            for (Marker marker : inspectionWaypointMarkers) {
                if (marker != null) {
                    marker.remove();
                }
            }
            inspectionWaypointMarkers.clear();

            // Cleanup MapHelper instances
            if (mapHelper != null) {
                mapHelper.clearMarkers();
                mapHelper = null;
            }
            if (smallCircleMapHelper != null) {
                smallCircleMapHelper.clearMarkers();
                smallCircleMapHelper = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error destroying activity: " + e.getMessage());
        }
    }
}