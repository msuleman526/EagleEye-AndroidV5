package io.empowerbits.sightflight.Activities.AddProject;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.empowerbits.sightflight.models.FlightSetting;
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

import io.empowerbits.sightflight.ApiResponse.AddProjectResponse;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.Retrofit.ApiClient;
import io.empowerbits.sightflight.Retrofit.ApiService;
import io.empowerbits.sightflight.models.FlightAddress;
import io.empowerbits.sightflight.models.Project;
import io.empowerbits.sightflight.util.ActivityCollector;
import io.empowerbits.sightflight.util.UserSessionManager;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.madapps.placesautocomplete.PlaceAPI;
import in.madapps.placesautocomplete.adapter.PlacesAutoCompleteAdapter;
import in.madapps.placesautocomplete.listener.OnPlacesDetailsListener;
import in.madapps.placesautocomplete.model.Place;
import in.madapps.placesautocomplete.model.PlaceDetails;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProjectInfoActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ProjectInfoActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String GOOGLE_PLACES_API_KEY = "AIzaSyAo1viD-Ut0TzXTyihevwuf-9tv_J3dPa0";

    // Bundle keys for state preservation
    private static final String KEY_SELECTED_ADDRESS = "selected_address";
    private static final String KEY_SELECTED_LATITUDE = "selected_latitude";
    private static final String KEY_SELECTED_LONGITUDE = "selected_longitude";
    private static final String KEY_PROJECT_NAME = "project_name";
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_CAMERA_ZOOM = "camera_zoom";
    private static final String KEY_INSPECTION_WAYPOINTS = "inspection_waypoints";

    private GoogleMap googleMap;
    private SupportMapFragment mapFragment;
    private Marker addressMarker;
    private FusedLocationProviderClient fusedLocationClient;
    private AutoCompleteTextView addressSearchField;
    private ImageButton currentLocationButton;
    private Button nextButton;
    private ImageView backBtn;
    private Dialog loadingDialog;
    private String selectedAddress;
    private double selectedLatitude;
    private double selectedLongitude;
    EditText projectNameEdt;

    // Waypoint markers system
    private List<Marker> inspectionWaypointMarkers = new ArrayList<>();
    private List<LatLng> savedInspectionWaypoints = new ArrayList<>(); // For orientation change
    private static final int MIN_WAYPOINTS = 5;
    private static final int MAX_WAYPOINTS = 8;
    private Marker markerBeingDragged = null;
    private boolean isAddressMarkerDragging = false;
    private boolean isRestoringState = false;

    // Camera position state for orientation change
    private LatLng savedCameraPosition;
    private float savedCameraZoom = 10f;

    private Project project;
    private Boolean newProject = true;
    private ApiService apiService;
    private UserSessionManager sessionManager;

    private PlaceAPI placesApi;
    private PlacesAutoCompleteAdapter placesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_info);
        ActivityCollector.addActivity(this);

        project = null;
        if (getIntent() != null && getIntent().hasExtra("project")) {
            project = (Project) getIntent().getSerializableExtra("project");
            newProject = false;
        }

        // Restore state if available
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }

        findViewById(R.id.backBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        initializeComponents();
        initializeApiService();
        setupGoogleMaps();
        setupLocationServices();
        setupListeners();
        setupGooglePlacesAutoComplete();
        checkLocationPermissions();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save current state
        if (selectedAddress != null) {
            outState.putString(KEY_SELECTED_ADDRESS, selectedAddress);
        }
        outState.putDouble(KEY_SELECTED_LATITUDE, selectedLatitude);
        outState.putDouble(KEY_SELECTED_LONGITUDE, selectedLongitude);

        if (projectNameEdt != null && projectNameEdt.getText() != null) {
            outState.putString(KEY_PROJECT_NAME, projectNameEdt.getText().toString());
        }

        // Save camera position if map is ready
        if (googleMap != null) {
            LatLng cameraPosition = googleMap.getCameraPosition().target;
            float cameraZoom = googleMap.getCameraPosition().zoom;
            outState.putParcelable(KEY_CAMERA_POSITION, cameraPosition);
            outState.putFloat(KEY_CAMERA_ZOOM, cameraZoom);
        }

        // Save inspection waypoint positions
        if (!inspectionWaypointMarkers.isEmpty()) {
            ArrayList<LatLng> waypointPositions = new ArrayList<>();
            for (Marker marker : inspectionWaypointMarkers) {
                if (marker != null) {
                    waypointPositions.add(marker.getPosition());
                }
            }
            outState.putParcelableArrayList(KEY_INSPECTION_WAYPOINTS, waypointPositions);
            Log.d(TAG, "Saved " + waypointPositions.size() + " inspection waypoint positions");
        }

        Log.d(TAG, "Instance state saved");
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        try {
            isRestoringState = true;

            // Restore selected address and coordinates
            selectedAddress = savedInstanceState.getString(KEY_SELECTED_ADDRESS);
            selectedLatitude = savedInstanceState.getDouble(KEY_SELECTED_LATITUDE, 0);
            selectedLongitude = savedInstanceState.getDouble(KEY_SELECTED_LONGITUDE, 0);

            // Restore camera position
            savedCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
            savedCameraZoom = savedInstanceState.getFloat(KEY_CAMERA_ZOOM, 10f);

            // Restore inspection waypoint positions
            ArrayList<LatLng> waypointPositions = savedInstanceState.getParcelableArrayList(KEY_INSPECTION_WAYPOINTS);
            if (waypointPositions != null && !waypointPositions.isEmpty()) {
                savedInspectionWaypoints.clear();
                savedInspectionWaypoints.addAll(waypointPositions);
                Log.d(TAG, "Restored " + savedInspectionWaypoints.size() + " inspection waypoint positions");
            }

            Log.d(TAG, "Instance state restored - Address: " + selectedAddress);
        } catch (Exception e) {
            Log.e(TAG, "Error restoring instance state: " + e.getMessage());
        }
    }

    private void initializeComponents() {
        try {
            addressSearchField = findViewById(R.id.addressSearchField);
            projectNameEdt = findViewById(R.id.projectNameEdt);
            currentLocationButton = findViewById(R.id.currentLocationButton);
            nextButton = findViewById(R.id.continueBtn);
            backBtn = findViewById(R.id.backBtn);

            Log.d(TAG, "UI components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing components: " + e.getMessage());
        }
    }

    private void initializeApiService() {
        try {
            apiService = ApiClient.getApiService();
            sessionManager = new UserSessionManager(this);

            Log.d(TAG, "API service initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing API service: " + e.getMessage());
        }
    }

    private void setupLocationServices() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            Log.d(TAG, "Location services initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up location services: " + e.getMessage());
        }
    }

    private void setupGoogleMaps() {
        try {
            mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.mapFragment);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            }
            Log.d(TAG, "Google Maps setup initiated");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Google Maps: " + e.getMessage());
        }
    }


    private void setupListeners() {
        try {
            currentLocationButton.setOnClickListener(v -> getCurrentLocation());
            nextButton.setOnClickListener(v -> onNextButtonClicked());
            backBtn.setOnClickListener(v -> onPreviousButtonClicked());

            Log.d(TAG, "Listeners setup successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners: " + e.getMessage());
        }
    }

    private void setupGooglePlacesAutoComplete() {
        try {
            placesApi = new PlaceAPI.Builder()
                    .apiKey(GOOGLE_PLACES_API_KEY)
                    .build(this);

            placesAdapter = new PlacesAutoCompleteAdapter(this, placesApi);
            addressSearchField.setAdapter(placesAdapter);

            addressSearchField.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    try {
                        Place place = (Place) parent.getItemAtPosition(position);
                        addressSearchField.setText(place.getDescription());
                        selectedAddress = place.getDescription();

                        placesApi.fetchPlaceDetails(place.getId(), new OnPlacesDetailsListener() {
                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "Error fetching place details: " + errorMessage);
                                Toast.makeText(ProjectInfoActivity.this, "Error getting location details", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onPlaceDetailsFetched(PlaceDetails placeDetails) {
                                try {
                                    if (placeDetails != null) {
                                        selectedLatitude = placeDetails.getLat();
                                        selectedLongitude = placeDetails.getLng();

                                        LatLng latLng = new LatLng(selectedLatitude, selectedLongitude);

                                        runOnUiThread(() -> {
                                            try {
                                                if (googleMap != null) {
                                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
                                                    // Pass the selected address to marker
                                                    setAddressMarker(latLng, selectedAddress);
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error processing place details: " + e.getMessage());
                                            }
                                        });

                                        Toast.makeText(ProjectInfoActivity.this, "Location selected: " + selectedAddress, Toast.LENGTH_SHORT).show();
                                        Log.d(TAG, "Google Places location selected: " + selectedAddress + " at " + latLng.toString());
                                    } else {
                                        Log.w(TAG, "Place details missing coordinates");
                                        Toast.makeText(ProjectInfoActivity.this, "Unable to get location coordinates", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing place details: " + e.getMessage());
                                    Toast.makeText(ProjectInfoActivity.this, "Error processing location", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Error handling place selection: " + e.getMessage());
                        Toast.makeText(ProjectInfoActivity.this, "Error selecting place", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            addressSearchField.setHint("Search for an address...");
            addressSearchField.setThreshold(3);

            Log.d(TAG, "Google Places AutoComplete setup completed with adapter");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Google Places AutoComplete: " + e.getMessage());
        }
    }

    private void setAddressMarker(LatLng latLng, String title) {
        try {
            // Clear all inspection waypoint markers when address changes
            // EXCEPT during state restoration (orientation change)
            if (!isRestoringState) {
                clearInspectionWaypoints();
            }

            if (addressMarker != null) {
                addressMarker.remove();
            }

            // Use selectedAddress if title is generic or null
            String markerTitle = title;
            if (title == null || title.equals("Selected Location") || title.equals("Current Location")) {
                markerTitle = (selectedAddress != null && !selectedAddress.isEmpty())
                        ? selectedAddress
                        : "Location (" + String.format(Locale.getDefault(), "%.6f, %.6f", latLng.latitude, latLng.longitude) + ")";
            }

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(markerTitle)
                    .draggable(true); // Enable drag for address marker

            addressMarker = googleMap.addMarker(markerOptions);

            // Show the info window with the address
            if (addressMarker != null) {
                addressMarker.showInfoWindow();
            }

            float zoomLevel = 20.0f;
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel));

            selectedLatitude = latLng.latitude;
            selectedLongitude = latLng.longitude;

            Log.d(TAG, "Address marker set at: " + latLng.toString() + " with title: " + markerTitle);
        } catch (Exception e) {
            Log.e(TAG, "Error setting address marker: " + e.getMessage());
        }
    }

    /**
     * Clear all inspection waypoint markers
     */
    private void clearInspectionWaypoints() {
        try {
            for (Marker marker : inspectionWaypointMarkers) {
                if (marker != null) {
                    marker.remove();
                }
            }
            inspectionWaypointMarkers.clear();
            Log.d(TAG, "All inspection waypoint markers cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing inspection waypoints: " + e.getMessage());
        }
    }

    private void getCurrentLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                checkLocationPermissions();
                return;
            }

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                            getAddressFromLatLng(currentLatLng);
                            // Marker will be set after address is fetched

                            Log.d(TAG, "Current location obtained: " + currentLatLng.toString());
                        } else {
                            Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "Current location is null");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to get current location: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to get current location: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting current location: " + e.getMessage());
        }
    }

    private void getAddressFromLatLng(LatLng latLng) {
        try {
            // Run geocoding on background thread to avoid ANR
            new Thread(() -> {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);

                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String addressText = address.getAddressLine(0);

                        // Update UI on main thread
                        runOnUiThread(() -> {
                            try {
                                addressSearchField.setText(addressText);
                                selectedAddress = addressText;
                                // Update marker with the new address
                                setAddressMarker(latLng, selectedAddress);
                                Log.d(TAG, "Address obtained from coordinates: " + addressText);
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating UI with address: " + e.getMessage());
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            Log.w(TAG, "No address found for coordinates: " + latLng.toString());
                            // Keep coordinates but show generic address
                            selectedAddress = "Location (" +
                                    String.format(Locale.getDefault(), "%.6f, %.6f", latLng.latitude, latLng.longitude) + ")";
                            addressSearchField.setText(selectedAddress);
                            setAddressMarker(latLng, selectedAddress);
                        });
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Geocoder error: " + e.getMessage());
                    runOnUiThread(() -> {
                        // Fallback to coordinates display
                        selectedAddress = "Location (" +
                                String.format(Locale.getDefault(), "%.6f, %.6f", latLng.latitude, latLng.longitude) + ")";
                        addressSearchField.setText(selectedAddress);
                        setAddressMarker(latLng, selectedAddress);
                        Toast.makeText(ProjectInfoActivity.this, "Address lookup failed, using coordinates", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in geocoding thread: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting geocoding thread: " + e.getMessage());
        }
    }

    private void prePopulateProjectData() {
        try {
            if (project != null && !newProject) {
                if (!TextUtils.isEmpty(project.address)) {
                    addressSearchField.setText(project.address);
                    projectNameEdt.setText(project.name);
                    selectedAddress = project.address;
                }

                if (!TextUtils.isEmpty(project.latitude) && !TextUtils.isEmpty(project.longitude)) {
                    try {
                        selectedLatitude = Double.parseDouble(project.latitude);
                        selectedLongitude = Double.parseDouble(project.longitude);

                        LatLng projectLocation = new LatLng(selectedLatitude, selectedLongitude);
                        if (googleMap != null && !isRestoringState) {
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(projectLocation, 16));

                            // Temporarily clear waypoints list to avoid clearing during setAddressMarker
                            List<Marker> tempMarkers = new ArrayList<>(inspectionWaypointMarkers);
                            inspectionWaypointMarkers.clear();

                            setAddressMarker(projectLocation, selectedAddress);

                            // Restore waypoints list
                            inspectionWaypointMarkers.addAll(tempMarkers);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing project coordinates: " + e.getMessage());
                    }
                }

                // Load existing inspection waypoints only if not restoring state
                // (during restoration, waypoints are loaded separately)
                if (!isRestoringState) {
                    loadExistingWaypoints();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pre-populating project data: " + e.getMessage());
        }
    }

    /**
     * Load existing inspection waypoints from project data in edit mode
     */
    private void loadExistingWaypoints() {
        try {
            if (project != null && googleMap != null) {
                List<FlightAddress> waypoints = project.getWaypointList();

                if (waypoints != null && !waypoints.isEmpty()) {
                    // Clear existing markers first
                    for (Marker marker : inspectionWaypointMarkers) {
                        if (marker != null) {
                            marker.remove();
                        }
                    }
                    inspectionWaypointMarkers.clear();

                    // Inspection waypoints are ALWAYS at the BEGINNING of the list (first 5-8 items)
                    // Structure: [inspection1, ..., inspection8, regular1, ..., regular130]
                    // We need to load only the first 5-8 waypoints based on noOfInspectionWaypoints

                    // Get the count of inspection waypoints from flight_setting
                    int inspectionCount = 0;
                    try {
                        if (project.flight_setting != null) {
                            String flightSettingStr = project.getFlightSettingAsString();
                            if (flightSettingStr != null && !flightSettingStr.isEmpty() && !flightSettingStr.equals("null")) {
                                org.json.JSONObject flightSettings = new org.json.JSONObject(flightSettingStr);
                                if (flightSettings.has("noOfInspectionWaypoints")) {
                                    inspectionCount = flightSettings.getInt("noOfInspectionWaypoints");
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing inspection count: " + e.getMessage());
                    }

                    // Load only the FIRST 'inspectionCount' waypoints
                    int endIndex = Math.min(inspectionCount, waypoints.size());
                    for (int i = 0; i < endIndex; i++) {
                        FlightAddress waypoint = waypoints.get(i);
                        LatLng position = new LatLng(waypoint.lat, waypoint.lng);

                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(position)
                                .title("Inspection Waypoint " + (i + 1))
                                .icon(createGreenDotIcon()) // Use green dot
                                .anchor(0.5f, 0.5f)
                                .draggable(true); // Make directly draggable

                        Marker marker = googleMap.addMarker(markerOptions);
                        if (marker != null) {
                            inspectionWaypointMarkers.add(marker);
                        }
                    }

                    Log.d(TAG, "Loaded " + inspectionWaypointMarkers.size() + " existing inspection waypoints from BEGINNING of list");
                    Toast.makeText(this, "Loaded " + inspectionWaypointMarkers.size() + " existing waypoints", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading existing waypoints: " + e.getMessage());
        }
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted");
                enableLocationOnMap();
            } else {
                Log.w(TAG, "Location permission denied");
                Toast.makeText(this, "Location permission is required for current location feature",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        try {
            googleMap = map;
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);

            enableLocationOnMap();
            setupMapClickListener();
            setupMarkerDragListener();

            // Restore camera position or use default
            if (savedCameraPosition != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedCameraPosition, savedCameraZoom));

                // Restore marker if we have a selected location
                if (selectedLatitude != 0 && selectedLongitude != 0) {
                    LatLng restoredLocation = new LatLng(selectedLatitude, selectedLongitude);
                    setAddressMarker(restoredLocation, selectedAddress);

                    // Restore address field text
                    if (selectedAddress != null && addressSearchField != null) {
                        addressSearchField.setText(selectedAddress);
                    }

                    // Restore inspection waypoint markers after address marker is set
                    restoreInspectionWaypoints();
                }
                Log.d(TAG, "Camera position and marker restored after orientation change");
            } else {
                LatLng defaultLocation = new LatLng(40.7128, -74.0060);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));
            }

            prePopulateProjectData();

            // Reset state restoration flag
            isRestoringState = false;

            Log.d(TAG, "Google Map ready and configured with drag functionality");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Map: " + e.getMessage());
        }
    }

    /**
     * Restore inspection waypoint markers after orientation change
     */
    private void restoreInspectionWaypoints() {
        try {
            if (!savedInspectionWaypoints.isEmpty() && googleMap != null) {
                for (LatLng position : savedInspectionWaypoints) {
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(position)
                            .title("Inspection Waypoint " + (inspectionWaypointMarkers.size() + 1))
                            .icon(createGreenDotIcon())
                            .anchor(0.5f, 0.5f)
                            .draggable(true); // Make directly draggable

                    Marker marker = googleMap.addMarker(markerOptions);
                    if (marker != null) {
                        inspectionWaypointMarkers.add(marker);
                    }
                }
                Log.d(TAG, "Restored " + inspectionWaypointMarkers.size() + " inspection waypoint markers");
                savedInspectionWaypoints.clear(); // Clear after restoring
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring inspection waypoints: " + e.getMessage());
        }
    }

    /**
     * Setup map click listener to allow placing inspection waypoint markers
     */
    private void setupMapClickListener() {
        try {
            if (googleMap != null) {
                googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        try {
                            // Only allow waypoint placement if address is already selected
                            if (addressMarker == null || selectedAddress == null || selectedAddress.isEmpty()) {
                                Toast.makeText(ProjectInfoActivity.this, "Please select an address first", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Check if max waypoints reached
                            if (inspectionWaypointMarkers.size() >= MAX_WAYPOINTS) {
                                Toast.makeText(ProjectInfoActivity.this, "Maximum " + MAX_WAYPOINTS + " inspection markers allowed", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Validate if the tapped location is on the same property
                            validateAndAddInspectionWaypoint(latLng);

                            Log.d(TAG, "Map clicked for inspection waypoint at: " + latLng.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling map click: " + e.getMessage());
                        }
                    }
                });
                Log.d(TAG, "Map click listener setup successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up map click listener: " + e.getMessage());
        }
    }

    /**
     * Validate waypoint location and add if on same property
     */
    private void validateAndAddInspectionWaypoint(LatLng latLng) {
        // Run geocoding on background thread
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address waypointAddress = addresses.get(0);
                    String waypointAddressText = waypointAddress.getAddressLine(0);

                    // Compare with selected address (check if same street and number)
                    Log.d("Selected Address", selectedAddress);
                    Log.d("Waypoint Address", waypointAddressText);
                    runOnUiThread(() -> {
                        addInspectionWaypoint(latLng);
                    });
//                    if (isSameProperty(selectedAddress, waypointAddressText)) {
//                        runOnUiThread(() -> {
//                            addInspectionWaypoint(latLng);
//                        });
//                    } else {
//                        runOnUiThread(() -> {
//                            Toast.makeText(ProjectInfoActivity.this, "You cannot draw on another property. Draw on the address that you chosen.", Toast.LENGTH_LONG).show();
//                            Log.w(TAG, "Waypoint rejected - different property. Selected: " + selectedAddress + ", Waypoint: " + waypointAddressText);
//                        });
//                    }
                } else {
                    // If geocoding fails, allow placement with warning
                    runOnUiThread(() -> {
                        addInspectionWaypoint(latLng);
                        Log.w(TAG, "No address found for waypoint, allowing placement");
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Geocoder error validating waypoint: " + e.getMessage());
                runOnUiThread(() -> {
                    // Allow placement if geocoding fails
                    addInspectionWaypoint(latLng);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error validating waypoint location: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Check if two addresses belong to the same property
     */
    private boolean isSameProperty(String address1, String address2) {
        try {
            if (address1 == null || address2 == null) {
                return false;
            }

            // Normalize addresses for comparison
            String normalized1 = normalizeAddress(address1.toLowerCase().trim());
            String normalized2 = normalizeAddress(address2.toLowerCase().trim());

            // Extract street number and street name for comparison
            String[] parts1 = normalized1.split(",");
            String[] parts2 = normalized2.split(",");

            if (parts1.length > 0 && parts2.length > 0) {
                String street1 = parts1[0].trim();
                String street2 = parts2[0].trim();

                Log.d(TAG, "Comparing streets - Street1: '" + street1 + "' vs Street2: '" + street2 + "'");

                // Check if street addresses match after normalization
                return street1.equals(street2);
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error comparing addresses: " + e.getMessage());
            return false;
        }
    }

    /**
     * Normalize address by replacing common abbreviations
     */
    private String normalizeAddress(String address) {
        if (address == null) {
            return "";
        }

        // Replace common street type abbreviations with full words
        String normalized = address
                .replaceAll("\\bboulevard\\b", "blvd")
                .replaceAll("\\bstreet\\b", "st")
                .replaceAll("\\bavenue\\b", "ave")
                .replaceAll("\\bdrive\\b", "dr")
                .replaceAll("\\broad\\b", "rd")
                .replaceAll("\\blane\\b", "ln")
                .replaceAll("\\bcourt\\b", "ct")
                .replaceAll("\\bplace\\b", "pl")
                .replaceAll("\\bcircle\\b", "cir")
                .replaceAll("\\bparkway\\b", "pkwy")
                .replaceAll("\\bterrace\\b", "ter")
                .replaceAll("\\bway\\b", "wy")
                .replaceAll("\\bnorth\\b", "n")
                .replaceAll("\\bsouth\\b", "s")
                .replaceAll("\\beast\\b", "e")
                .replaceAll("\\bwest\\b", "w")
                .replaceAll("\\bapartment\\b", "apt")
                .replaceAll("\\bsuite\\b", "ste")
                .replaceAll("\\bbuilding\\b", "bldg");

        // Remove extra spaces
        normalized = normalized.replaceAll("\\s+", " ").trim();

        return normalized;
    }

    /**
     * Create a green dot icon for inspection waypoints
     */
    private BitmapDescriptor createGreenDotIcon() {
        int size = 50; // Increased size from 30 to 50 pixels
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.rgb(0, 200, 0)); // Green color
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // Draw filled circle
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // Add white border for better visibility
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3); // Increased border width
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1.5f, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Add inspection waypoint marker at the specified location
     */
    private void addInspectionWaypoint(LatLng latLng) {
        try {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title("Inspection Waypoint " + (inspectionWaypointMarkers.size() + 1))
                    .icon(createGreenDotIcon()) // Use green dot
                    .anchor(0.5f, 0.5f) // Center the icon
                    .draggable(true); // Make directly draggable

            Marker marker = googleMap.addMarker(markerOptions);
            if (marker != null) {
                inspectionWaypointMarkers.add(marker);
                Log.d(TAG, "Inspection waypoint added at: " + latLng.toString() + " Total: " + inspectionWaypointMarkers.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding inspection waypoint: " + e.getMessage());
        }
    }

    /**
     * Setup marker drag listener for press and hold drag functionality
     */
    private void setupMarkerDragListener() {
        try {
            if (googleMap != null) {
                // Long click listener to enable dragging
                googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        try {
                            marker.showInfoWindow();
                            Log.d(TAG, "Marker clicked: " + marker.getTitle());
                            return false; // Return false to allow default behavior
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling marker click: " + e.getMessage());
                            return false;
                        }
                    }
                });

                // Set up long press on marker to enable drag
                googleMap.setOnInfoWindowLongClickListener(new GoogleMap.OnInfoWindowLongClickListener() {
                    @Override
                    public void onInfoWindowLongClick(Marker marker) {
                        try {
                            marker.setDraggable(true);
                            markerBeingDragged = marker;
                            Toast.makeText(ProjectInfoActivity.this, "You can now drag the marker", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Marker drag enabled via long press: " + marker.getTitle());
                        } catch (Exception e) {
                            Log.e(TAG, "Error enabling marker drag: " + e.getMessage());
                        }
                    }
                });

                googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                    @Override
                    public void onMarkerDragStart(Marker marker) {
                        try {
                            Log.d(TAG, "Marker drag started at: " + marker.getPosition().toString());
                            if (marker.equals(addressMarker)) {
                                isAddressMarkerDragging = true;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling marker drag start: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onMarkerDrag(Marker marker) {
                        try {
                            // Update coordinates in real-time during drag
                            if (marker.equals(addressMarker)) {
                                LatLng position = marker.getPosition();
                                selectedLatitude = position.latitude;
                                selectedLongitude = position.longitude;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling marker drag: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onMarkerDragEnd(Marker marker) {
                        try {
                            LatLng finalPosition = marker.getPosition();
                            markerBeingDragged = null;

                            if (marker.equals(addressMarker)) {
                                // Address marker was dragged
                                isAddressMarkerDragging = false;

                                // Store old address to check if it changed
                                final String oldAddress = selectedAddress;

                                selectedLatitude = finalPosition.latitude;
                                selectedLongitude = finalPosition.longitude;

                                // Update address based on new position
                                new Thread(() -> {
                                    try {
                                        Geocoder geocoder = new Geocoder(ProjectInfoActivity.this, Locale.getDefault());
                                        List<Address> addresses = geocoder.getFromLocation(finalPosition.latitude, finalPosition.longitude, 1);

                                        if (addresses != null && !addresses.isEmpty()) {
                                            Address address = addresses.get(0);
                                            String newAddressText = address.getAddressLine(0);

                                            runOnUiThread(() -> {
                                                addressSearchField.setText(newAddressText);

                                                // Check if address actually changed (different property)
                                                boolean addressChanged = !isSameProperty(oldAddress, newAddressText);

                                                selectedAddress = newAddressText;
                                                marker.setTitle(selectedAddress);
                                                marker.showInfoWindow();

                                                // Clear inspection markers only if address changed to a different property
                                                if (addressChanged && !inspectionWaypointMarkers.isEmpty()) {
                                                    clearInspectionWaypoints();
                                                    Toast.makeText(ProjectInfoActivity.this,
                                                        "Address changed - inspection markers cleared",
                                                        Toast.LENGTH_LONG).show();
                                                    Log.d(TAG, "Address changed from '" + oldAddress + "' to '" + newAddressText + "' - inspection markers cleared");
                                                } else {
                                                    Toast.makeText(ProjectInfoActivity.this,
                                                        "Address marker updated",
                                                        Toast.LENGTH_SHORT).show();
                                                    Log.d(TAG, "Address marker moved but still same property");
                                                }
                                            });
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating address after drag: " + e.getMessage());
                                    }
                                }).start();

                                Log.d(TAG, "Address marker drag ended at: " + finalPosition.toString());
                            } else {
                                // Inspection waypoint was dragged - validate location
                                validateWaypointDrag(marker, finalPosition);
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error handling marker drag end: " + e.getMessage());
                        }
                    }
                });
                Log.d(TAG, "Marker drag listener setup successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up marker drag listener: " + e.getMessage());
        }
    }

    /**
     * Validate waypoint drag to ensure it's still on the same property
     */
    private void validateWaypointDrag(Marker marker, LatLng newPosition) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(newPosition.latitude, newPosition.longitude, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address waypointAddress = addresses.get(0);
                    String waypointAddressText = waypointAddress.getAddressLine(0);

                    if (isSameProperty(selectedAddress, waypointAddressText)) {
                        runOnUiThread(() -> {
                            Toast.makeText(ProjectInfoActivity.this, "Waypoint position updated", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Waypoint drag validated and accepted");
                        });
                    } else {
                        // Move marker back to original position
                        runOnUiThread(() -> {
                            // Remove and re-add at old position - or keep at new position with warning
                            Toast.makeText(ProjectInfoActivity.this, "Warning: Waypoint moved outside property boundary", Toast.LENGTH_LONG).show();
                            Log.w(TAG, "Waypoint drag moved to different property");
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ProjectInfoActivity.this, "Waypoint position updated", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error validating waypoint drag: " + e.getMessage());
            }
        }).start();
    }

    private void enableLocationOnMap() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                if (googleMap != null) {
                    googleMap.setMyLocationEnabled(true);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception enabling location on map: " + e.getMessage());
        }
    }

    private void onNextButtonClicked() {
        try {
            String name = projectNameEdt.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter project name", Toast.LENGTH_SHORT).show();
                projectNameEdt.requestFocus();
                return;
            }

            if (selectedAddress == null || selectedAddress.isEmpty()) {
                Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate minimum inspection waypoints
            if (inspectionWaypointMarkers.size() < MIN_WAYPOINTS) {
                Toast.makeText(this, "Please add Inspection markers atleast " + MIN_WAYPOINTS, Toast.LENGTH_LONG).show();
                return;
            }

            showLoading();
            callAddressAPI();
        } catch (Exception e) {
            Log.e(TAG, "Error handling next button click: " + e.getMessage());
            hideLoading();
        }
    }

    private void callAddressAPI() {
        String name = projectNameEdt.getText().toString().trim();

        try {
            String latStr = String.valueOf(selectedLatitude);
            String lngStr = String.valueOf(selectedLongitude);

            // Prepare FlightAddress list - ONLY inspection waypoints
            // Regular waypoints will be added later in ProjectWaypointActivity
            List<FlightAddress> flightAddresses = new ArrayList<>();

            // Add ONLY inspection waypoints (5-8 markers)
            for (Marker marker : inspectionWaypointMarkers) {
                LatLng position = marker.getPosition();
                flightAddresses.add(new FlightAddress(position.latitude, position.longitude));
            }

            String flightPathJson = new Gson().toJson(flightAddresses);
            FlightSetting flightSetting = new FlightSetting();
            flightSetting.noOfWaypoints = 130;
            flightSetting.circleRadius = 90;
            flightSetting.noOfInspectionWaypoints = inspectionWaypointMarkers.size();
            String flightSettingJson = new Gson().toJson(flightSetting);

            Log.d(TAG, "Inspection waypoints in FlightPath: " + flightAddresses.size());
            Log.d(TAG, "FlightPath JSON (inspection only): " + flightPathJson);
            Log.d(TAG, "FlightSetting JSON (initial): " + flightSettingJson);

            Call<AddProjectResponse> call;
            if (newProject) {
                call = apiService.createProjectInfo("Bearer " + sessionManager.getToken(),
                        selectedAddress, latStr, lngStr, sessionManager.getUserId(), name,
                        flightSettingJson, flightPathJson);
            } else {
                call = apiService.updateProjectInfo("Bearer " + sessionManager.getToken(),
                        String.valueOf(project.id), selectedAddress, latStr, lngStr, name,
                        flightSettingJson, flightPathJson);
            }

            call.enqueue(new Callback<AddProjectResponse>() {
                @Override
                public void onResponse(Call<AddProjectResponse> call, Response<AddProjectResponse> response) {
                    hideLoading();
                    if (response.isSuccessful() && response.body() != null) {
                        if (newProject && response.body().project != null) {
                            project = response.body().project;
                        }
                        Toast.makeText(ProjectInfoActivity.this, "Project info saved successfully", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Address API call successful with flight data");
                        // Navigate to next screen if needed
                        navigateToHouseBoundary();
                    } else {
                        Toast.makeText(ProjectInfoActivity.this, "Error updating address", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Address API call failed: " + response.toString());
                    }
                }

                @Override
                public void onFailure(Call<AddProjectResponse> call, Throwable t) {
                    hideLoading();
                    Toast.makeText(ProjectInfoActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Address API call error: " + t.getMessage());
                }
            });

        } catch (Exception e) {
            hideLoading();
            Log.e(TAG, "Error calling address API: " + e.getMessage());
        }
    }

    private void navigateToHouseBoundary() {
        try {
            Intent intent = new Intent(this, ProjectWaypointActivity.class);
            if (project != null) {
                intent.putExtra("project", project);
            }
            startActivity(intent);
            Log.d(TAG, "Navigating to HouseBoundaryActivity");
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to HouseBoundary: " + e.getMessage());
        }
    }

    private void onPreviousButtonClicked() {
        try {
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error handling previous button click: " + e.getMessage());
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
                Log.d(TAG, "Loading dialog shown");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing loading dialog: " + e.getMessage());
        }
    }

    public void hideLoading() {
        try {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
                Log.d(TAG, "Loading dialog hidden");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding loading dialog: " + e.getMessage());
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
        ActivityCollector.removeActivity(this);
        try {
            hideLoading();
            if (loadingDialog != null) {
                loadingDialog = null;
            }

            Log.d(TAG, "Activity destroyed and services cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error destroying activity: " + e.getMessage());
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

        Log.d(TAG, "Configuration changed - Orientation: " +
                (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "Landscape" : "Portrait"));
    }
}