package com.suleman.eagleeye.Services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.AirLinkKey;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.flightcontroller.FlightMode;
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.sdk.keyvalue.value.common.Velocity3D;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;
import dji.v5.manager.SDKManager;

/**
 * TelemetryService - Real-Time Drone Telemetry Data Service
 * 
 * Comprehensive telemetry service for DJI SDK V5 Android integration
 * Provides real-time monitoring of all essential drone telemetry data
 * 
 * Supported Telemetry Data:
 * - Flight Mode (GPS, ATTI, Sport, etc.)
 * - GPS Signal Status (0-5 signal level)
 * - Satellite Count (number of GPS satellites)
 * - Link Signal Quality (0-100% remote signal strength)
 * - Battery Charge (0-100% remaining battery)
 * - Altitude in meters
 * - Horizontal velocity (m/s)
 * - Vertical velocity (m/s)
 * - Location coordinates (latitude, longitude, altitude)
 * 
 * Architecture:
 * - Singleton pattern for app-wide usage
 * - Thread-safe implementation with UI handler
 * - Automatic listener registration/cleanup
 * - Real-time callbacks to UI components
 * - Proper lifecycle management
 * 
 * Usage:
 * TelemetryService service = TelemetryService.getInstance(context);
 * service.setFlightModeChangedListener(flightMode -> updateUI(flightMode));
 * service.startTelemetryMonitoring();
 * 
 * @author Suleman
 * @version 1.0
 * @date 2025-06-27
 */
public class TelemetryService {
    private static final String TAG = "TelemetryService";
    
    // Singleton instance
    private static TelemetryService instance;
    private static final Object LOCK = new Object();
    
    // Context and handlers
    private Context context;
    private Handler uiHandler;
    
    // Service state
    private AtomicBoolean isInitialized = new AtomicBoolean(false);
    private AtomicBoolean isMonitoring = new AtomicBoolean(false);
    
    // Current telemetry values (cached for immediate access)
    private volatile String currentFlightMode = "N/A";
    private volatile Integer currentGpsSignalLevel = 0;
    private volatile Integer currentSatelliteCount = 0;
    private volatile Integer currentLinkSignalQuality = 0;
    private volatile Integer currentBatteryPercentage = 0;
    private volatile Double currentAltitude = 0.0;
    private volatile Double currentHorizontalVelocity = 0.0;
    private volatile Double currentVerticalVelocity = 0.0;
    private volatile LocationCoordinate3D currentLocation = null;
    
    // Static telemetry interfaces (equivalent to iOS callbacks)
    public interface FlightModeChangedListener {
        void onFlightModeChanged(String flightMode);
    }
    
    public interface GpsSignalStatusChangedListener {
        void onGpsSignalStatusChanged(Integer signalLevel);
    }
    
    public interface GpsSatCountChangedListener {
        void onGpsSatCountChanged(Integer satCount);
    }
    
    public interface LinkSignalQualityChangedListener {
        void onLinkSignalQualityChanged(Integer signalQuality);
    }
    
    public interface BatteryChargeChangedListener {
        void onBatteryChargeChanged(Integer batteryPercentage);
    }
    
    public interface AltitudeChangedListener {
        void onAltitudeChanged(Double altitude);
    }
    
    public interface HorizontalVelocityChangedListener {
        void onHorizontalVelocityChanged(Double velocity);
    }
    
    public interface VerticalVelocityChangedListener {
        void onVerticalVelocityChanged(Double velocity);
    }
    
    public interface LocationChangedListener {
        void onLocationChanged(LocationCoordinate3D location);
    }
    
    // Listener instances
    private FlightModeChangedListener flightModeChangedListener;
    private GpsSignalStatusChangedListener gpsSignalStatusChangedListener;
    private GpsSatCountChangedListener gpsSatCountChangedListener;
    private LinkSignalQualityChangedListener linkSignalQualityChangedListener;
    private BatteryChargeChangedListener batteryChargeChangedListener;
    private AltitudeChangedListener altitudeChangedListener;
    private HorizontalVelocityChangedListener horizontalVelocityChangedListener;
    private VerticalVelocityChangedListener verticalVelocityChangedListener;
    private LocationChangedListener locationChangedListener;
    
    /**
     * Private constructor for singleton pattern
     */
    private TelemetryService(Context context) {
        this.context = context.getApplicationContext();
        this.uiHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "TelemetryService created");
    }
    
    /**
     * Get singleton instance of TelemetryService
     * @param context Application context
     * @return TelemetryService instance
     */
    public static TelemetryService getInstance(Context context) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new TelemetryService(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize telemetry service and setup DJI SDK listeners
     * This method should be called after SDK registration
     */
    public void initializeTelemetryService() {
        if (!SDKManager.getInstance().isRegistered()) {
            Log.w(TAG, "SDK not registered yet, telemetry initialization postponed");
            return;
        }
        
        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "TelemetryService already initialized");
            return;
        }
        
        try {
            Log.i(TAG, "Initializing telemetry service with DJI SDK V5...");
            
            setupTelemetryListeners();
            
            Log.i(TAG, "TelemetryService initialization completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize telemetry service: " + e.getMessage(), e);
            isInitialized.set(false);
        }
    }
    
    /**
     * Start telemetry monitoring
     * This will begin real-time telemetry data collection
     */
    public void startTelemetryMonitoring() {
        if (!isInitialized.get()) {
            Log.w(TAG, "Service not initialized, attempting to initialize...");
            initializeTelemetryService();
            return;
        }
        
        if (isMonitoring.getAndSet(true)) {
            Log.d(TAG, "Telemetry monitoring already started");
            return;
        }
        
        Log.i(TAG, "Starting telemetry monitoring...");
        
        // Force initial telemetry update
        forceInitialTelemetryUpdate();
        
        Log.i(TAG, "Telemetry monitoring started successfully");
    }
    
    /**
     * Stop telemetry monitoring
     */
    public void stopTelemetryMonitoring() {
        if (isMonitoring.getAndSet(false)) {
            Log.i(TAG, "Telemetry monitoring stopped");
        }
    }
    
    /**
     * Setup all DJI SDK telemetry listeners
     */
    private void setupTelemetryListeners() {
        try {
            Log.d(TAG, "Setting up telemetry listeners...");
            
            // Flight Mode Listener
            setupFlightModeListener();
            
            // GPS Signal Status Listener
            setupGpsSignalStatusListener();
            
            // Satellite Count Listener
            setupSatelliteCountListener();
            
            // Link Signal Quality Listener (AirLink)
            setupLinkSignalQualityListener();
            
            // Battery Charge Listener
            setupBatteryChargeListener();
            
            // Altitude Listener
            setupAltitudeListener();
            
            // Velocity Listeners (Horizontal & Vertical)
            setupVelocityListeners();
            
            // Location Listener
            setupLocationListener();
            
            Log.d(TAG, "All telemetry listeners setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up telemetry listeners: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Flight Mode listener
     */
    private void setupFlightModeListener() {
        try {
            DJIKey<FlightMode> flightModeKey = KeyTools.createKey(
                FlightControllerKey.KeyFlightMode, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(flightModeKey, this, 
                new CommonCallbacks.KeyListener<FlightMode>() {
                    @Override
                    public void onValueChange(FlightMode oldValue, FlightMode newValue) {
                        String flightModeString = newValue != null ? newValue.name() : "N/A";
                        currentFlightMode = flightModeString;
                        
                        Log.v(TAG, "Flight mode changed: " + flightModeString);
                        
                        if (flightModeChangedListener != null) {
                            uiHandler.post(() -> flightModeChangedListener.onFlightModeChanged(flightModeString));
                        }
                    }
                });
                
            Log.d(TAG, "Flight mode listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up flight mode listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup GPS Signal Status listener
     */
    private void setupGpsSignalStatusListener() {
        try {
            DJIKey<GPSSignalLevel> gpsSignalKey = KeyTools.createKey(
                FlightControllerKey.KeyGPSSignalLevel, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(gpsSignalKey, this, 
                new CommonCallbacks.KeyListener<GPSSignalLevel>() {
                    @Override
                    public void onValueChange(GPSSignalLevel oldValue, GPSSignalLevel newValue) {
                        Integer signalLevel = newValue != null ? newValue.value() : 0;
                        currentGpsSignalLevel = signalLevel;
                        
                        Log.v(TAG, "GPS signal level changed: " + signalLevel);
                        
                        if (gpsSignalStatusChangedListener != null) {
                            uiHandler.post(() -> gpsSignalStatusChangedListener.onGpsSignalStatusChanged(signalLevel));
                        }
                    }
                });
                
            Log.d(TAG, "GPS signal status listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up GPS signal status listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Satellite Count listener
     */
    private void setupSatelliteCountListener() {
        try {
            DJIKey<Integer> satCountKey = KeyTools.createKey(
                FlightControllerKey.KeyGPSSatelliteCount, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(satCountKey, this, 
                new CommonCallbacks.KeyListener<Integer>() {
                    @Override
                    public void onValueChange(Integer oldValue, Integer newValue) {
                        Integer satCount = newValue != null ? newValue : 0;
                        currentSatelliteCount = satCount;
                        
                        Log.v(TAG, "Satellite count changed: " + satCount);
                        
                        if (gpsSatCountChangedListener != null) {
                            uiHandler.post(() -> gpsSatCountChangedListener.onGpsSatCountChanged(satCount));
                        }
                    }
                });
                
            Log.d(TAG, "Satellite count listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up satellite count listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Link Signal Quality listener (AirLink)
     */
    private void setupLinkSignalQualityListener() {
        try {
            DJIKey<Integer> linkSignalKey = KeyTools.createKey(
                AirLinkKey.KeySignalQuality, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(linkSignalKey, this, 
                new CommonCallbacks.KeyListener<Integer>() {
                    @Override
                    public void onValueChange(Integer oldValue, Integer newValue) {
                        Integer signalQuality = newValue != null ? newValue : 0;
                        currentLinkSignalQuality = signalQuality;
                        
                        Log.v(TAG, "Link signal quality changed: " + signalQuality + "%");
                        
                        if (linkSignalQualityChangedListener != null) {
                            uiHandler.post(() -> linkSignalQualityChangedListener.onLinkSignalQualityChanged(signalQuality));
                        }
                    }
                });
                
            Log.d(TAG, "Link signal quality listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up link signal quality listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Battery Charge listener
     */
    private void setupBatteryChargeListener() {
        try {
            DJIKey<Integer> batteryKey = KeyTools.createKey(
                BatteryKey.KeyChargeRemainingInPercent, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(batteryKey, this, 
                new CommonCallbacks.KeyListener<Integer>() {
                    @Override
                    public void onValueChange(Integer oldValue, Integer newValue) {
                        Integer batteryPercentage = newValue != null ? newValue : 0;
                        currentBatteryPercentage = batteryPercentage;
                        
                        Log.v(TAG, "Battery charge changed: " + batteryPercentage + "%");
                        
                        if (batteryChargeChangedListener != null) {
                            uiHandler.post(() -> batteryChargeChangedListener.onBatteryChargeChanged(batteryPercentage));
                        }
                    }
                });
                
            Log.d(TAG, "Battery charge listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up battery charge listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Altitude listener
     */
    private void setupAltitudeListener() {
        try {
            DJIKey<Double> altitudeKey = KeyTools.createKey(
                FlightControllerKey.KeyAltitude, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(altitudeKey, this, 
                new CommonCallbacks.KeyListener<Double>() {
                    @Override
                    public void onValueChange(Double oldValue, Double newValue) {
                        Double altitude = newValue != null ? newValue : 0.0;
                        currentAltitude = altitude;
                        
                        Log.v(TAG, "Altitude changed: " + String.format("%.1f", altitude) + "m");
                        
                        if (altitudeChangedListener != null) {
                            uiHandler.post(() -> altitudeChangedListener.onAltitudeChanged(altitude));
                        }
                    }
                });
                
            Log.d(TAG, "Altitude listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up altitude listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Velocity listeners (Horizontal & Vertical)
     */
    private void setupVelocityListeners() {
        try {
            DJIKey<Velocity3D> velocityKey = KeyTools.createKey(
                FlightControllerKey.KeyAircraftVelocity, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(velocityKey, this, 
                new CommonCallbacks.KeyListener<Velocity3D>() {
                    @Override
                    public void onValueChange(Velocity3D oldValue, Velocity3D newValue) {
                        if (newValue != null) {
                            // Calculate horizontal velocity (magnitude of x and y components)
                            double horizontalVel = Math.sqrt(Math.pow(newValue.getX(), 2) + Math.pow(newValue.getY(), 2));
                            double verticalVel = newValue.getZ();
                            
                            currentHorizontalVelocity = horizontalVel;
                            currentVerticalVelocity = verticalVel;
                            
                            Log.v(TAG, "Velocity changed - H: " + String.format("%.1f", horizontalVel) + 
                                      "m/s, V: " + String.format("%.1f", verticalVel) + "m/s");
                            
                            if (horizontalVelocityChangedListener != null) {
                                uiHandler.post(() -> horizontalVelocityChangedListener.onHorizontalVelocityChanged(horizontalVel));
                            }
                            
                            if (verticalVelocityChangedListener != null) {
                                uiHandler.post(() -> verticalVelocityChangedListener.onVerticalVelocityChanged(verticalVel));
                            }
                        }
                    }
                });
                
            Log.d(TAG, "Velocity listeners setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up velocity listeners: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup Location listener
     */
    private void setupLocationListener() {
        try {
            DJIKey<LocationCoordinate3D> locationKey = KeyTools.createKey(
                FlightControllerKey.KeyAircraftLocation3D, ComponentIndexType.LEFT_OR_MAIN);
                
            KeyManager.getInstance().listen(locationKey, this, 
                new CommonCallbacks.KeyListener<LocationCoordinate3D>() {
                    @Override
                    public void onValueChange(LocationCoordinate3D oldValue, LocationCoordinate3D newValue) {
                        currentLocation = newValue;
                        
                        if (newValue != null) {
                            Log.v(TAG, "Location changed: Lat=" + String.format("%.6f", newValue.getLatitude()) + 
                                      ", Lon=" + String.format("%.6f", newValue.getLongitude()) + 
                                      ", Alt=" + String.format("%.1f", newValue.getAltitude()) + "m");
                        }
                        
                        if (locationChangedListener != null) {
                            uiHandler.post(() -> locationChangedListener.onLocationChanged(newValue));
                        }
                    }
                });
                
            Log.d(TAG, "Location listener setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up location listener: " + e.getMessage(), e);
        }
    }
    
    /**
     * Force initial telemetry update by requesting current values
     */
    public void forceInitialTelemetryUpdate() {
        if (!isInitialized.get()) {
            Log.w(TAG, "Service not initialized, cannot force telemetry update");
            return;
        }
        
        Log.d(TAG, "Forcing initial telemetry update...");
        
        // Force update for each telemetry parameter
        uiHandler.postDelayed(() -> {
            try {
                requestCurrentFlightMode();
                requestCurrentGpsSignalStatus();
                requestCurrentSatelliteCount();
                requestCurrentLinkSignalQuality();
                requestCurrentBatteryCharge();
                requestCurrentAltitude();
                requestCurrentVelocity();
                requestCurrentLocation();
                
                Log.d(TAG, "Initial telemetry update requests sent");
            } catch (Exception e) {
                Log.e(TAG, "Error during initial telemetry update: " + e.getMessage(), e);
            }
        }, 500); // Small delay to ensure SDK is ready
    }
    
    // Request current value methods
    private void requestCurrentFlightMode() {
        try {
            DJIKey<FlightMode> key = KeyTools.createKey(FlightControllerKey.KeyFlightMode, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<FlightMode>() {
                @Override
                public void onSuccess(FlightMode flightMode) {
                    String flightModeString = flightMode != null ? flightMode.name() : "N/A";
                    currentFlightMode = flightModeString;
                    if (flightModeChangedListener != null) {
                        uiHandler.post(() -> flightModeChangedListener.onFlightModeChanged(flightModeString));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current flight mode: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current flight mode: " + e.getMessage());
        }
    }
    
    private void requestCurrentGpsSignalStatus() {
        try {
            DJIKey<GPSSignalLevel> key = KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<GPSSignalLevel>() {
                @Override
                public void onSuccess(GPSSignalLevel signalLevel) {
                    Integer level = signalLevel != null ? signalLevel.value() : 0;
                    currentGpsSignalLevel = level;
                    if (gpsSignalStatusChangedListener != null) {
                        uiHandler.post(() -> gpsSignalStatusChangedListener.onGpsSignalStatusChanged(level));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current GPS signal status: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current GPS signal status: " + e.getMessage());
        }
    }
    
    private void requestCurrentSatelliteCount() {
        try {
            DJIKey<Integer> key = KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                @Override
                public void onSuccess(Integer satCount) {
                    Integer count = satCount != null ? satCount : 0;
                    currentSatelliteCount = count;
                    if (gpsSatCountChangedListener != null) {
                        uiHandler.post(() -> gpsSatCountChangedListener.onGpsSatCountChanged(count));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current satellite count: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current satellite count: " + e.getMessage());
        }
    }
    
    private void requestCurrentLinkSignalQuality() {
        try {
            DJIKey<Integer> key = KeyTools.createKey(AirLinkKey.KeySignalQuality, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                @Override
                public void onSuccess(Integer signalQuality) {
                    Integer quality = signalQuality != null ? signalQuality : 0;
                    currentLinkSignalQuality = quality;
                    if (linkSignalQualityChangedListener != null) {
                        uiHandler.post(() -> linkSignalQualityChangedListener.onLinkSignalQualityChanged(quality));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current link signal quality: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current link signal quality: " + e.getMessage());
        }
    }
    
    private void requestCurrentBatteryCharge() {
        try {
            DJIKey<Integer> key = KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                @Override
                public void onSuccess(Integer batteryPercentage) {
                    Integer percentage = batteryPercentage != null ? batteryPercentage : 0;
                    currentBatteryPercentage = percentage;
                    if (batteryChargeChangedListener != null) {
                        uiHandler.post(() -> batteryChargeChangedListener.onBatteryChargeChanged(percentage));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current battery charge: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current battery charge: " + e.getMessage());
        }
    }
    
    private void requestCurrentAltitude() {
        try {
            DJIKey<Double> key = KeyTools.createKey(FlightControllerKey.KeyAltitude, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Double>() {
                @Override
                public void onSuccess(Double altitude) {
                    Double alt = altitude != null ? altitude : 0.0;
                    currentAltitude = alt;
                    if (altitudeChangedListener != null) {
                        uiHandler.post(() -> altitudeChangedListener.onAltitudeChanged(alt));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current altitude: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current altitude: " + e.getMessage());
        }
    }
    
    private void requestCurrentVelocity() {
        try {
            DJIKey<Velocity3D> key = KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Velocity3D>() {
                @Override
                public void onSuccess(Velocity3D velocity) {
                    if (velocity != null) {
                        double horizontalVel = Math.sqrt(Math.pow(velocity.getX(), 2) + Math.pow(velocity.getY(), 2));
                        double verticalVel = velocity.getZ();
                        
                        currentHorizontalVelocity = horizontalVel;
                        currentVerticalVelocity = verticalVel;
                        
                        if (horizontalVelocityChangedListener != null) {
                            uiHandler.post(() -> horizontalVelocityChangedListener.onHorizontalVelocityChanged(horizontalVel));
                        }
                        if (verticalVelocityChangedListener != null) {
                            uiHandler.post(() -> verticalVelocityChangedListener.onVerticalVelocityChanged(verticalVel));
                        }
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current velocity: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current velocity: " + e.getMessage());
        }
    }
    
    private void requestCurrentLocation() {
        try {
            DJIKey<LocationCoordinate3D> key = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D, ComponentIndexType.LEFT_OR_MAIN);
            KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate3D>() {
                @Override
                public void onSuccess(LocationCoordinate3D location) {
                    currentLocation = location;
                    if (locationChangedListener != null) {
                        uiHandler.post(() -> locationChangedListener.onLocationChanged(location));
                    }
                }
                
                @Override
                public void onFailure(IDJIError error) {
                    Log.d(TAG, "Failed to get current location: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting current location: " + e.getMessage());
        }
    }
    
    // Setter methods for listeners
    public void setFlightModeChangedListener(FlightModeChangedListener listener) {
        this.flightModeChangedListener = listener;
    }
    
    public void setGpsSignalStatusChangedListener(GpsSignalStatusChangedListener listener) {
        this.gpsSignalStatusChangedListener = listener;
    }
    
    public void setGpsSatCountChangedListener(GpsSatCountChangedListener listener) {
        this.gpsSatCountChangedListener = listener;
    }
    
    public void setLinkSignalQualityChangedListener(LinkSignalQualityChangedListener listener) {
        this.linkSignalQualityChangedListener = listener;
    }
    
    public void setBatteryChargeChangedListener(BatteryChargeChangedListener listener) {
        this.batteryChargeChangedListener = listener;
    }
    
    public void setAltitudeChangedListener(AltitudeChangedListener listener) {
        this.altitudeChangedListener = listener;
    }
    
    public void setHorizontalVelocityChangedListener(HorizontalVelocityChangedListener listener) {
        this.horizontalVelocityChangedListener = listener;
    }
    
    public void setVerticalVelocityChangedListener(VerticalVelocityChangedListener listener) {
        this.verticalVelocityChangedListener = listener;
    }
    
    public void setLocationChangedListener(LocationChangedListener listener) {
        this.locationChangedListener = listener;
    }
    
    // Getter methods for current values (for immediate access)
    public String getCurrentFlightMode() {
        return currentFlightMode;
    }
    
    public Integer getCurrentGpsSignalLevel() {
        return currentGpsSignalLevel;
    }
    
    public Integer getCurrentSatelliteCount() {
        return currentSatelliteCount;
    }
    
    public Integer getCurrentLinkSignalQuality() {
        return currentLinkSignalQuality;
    }
    
    public Integer getCurrentBatteryPercentage() {
        return currentBatteryPercentage;
    }
    
    public Double getCurrentAltitude() {
        return currentAltitude;
    }
    
    public Double getCurrentHorizontalVelocity() {
        return currentHorizontalVelocity;
    }
    
    public Double getCurrentVerticalVelocity() {
        return currentVerticalVelocity;
    }
    
    public LocationCoordinate3D getCurrentLocation() {
        return currentLocation;
    }
    
    /**
     * Check if service is initialized
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }
    
    /**
     * Check if monitoring is active
     */
    public boolean isMonitoring() {
        return isMonitoring.get();
    }
    
    /**
     * Cleanup and release resources
     */
    public void cleanup() {
        try {
            Log.i(TAG, "Cleaning up TelemetryService...");
            
            stopTelemetryMonitoring();
            
            // Remove all listeners from DJI SDK
            if (isInitialized.get()) {
                KeyManager.getInstance().cancelListen(this);
            }
            
            // Clear listener references
            flightModeChangedListener = null;
            gpsSignalStatusChangedListener = null;
            gpsSatCountChangedListener = null;
            linkSignalQualityChangedListener = null;
            batteryChargeChangedListener = null;
            altitudeChangedListener = null;
            horizontalVelocityChangedListener = null;
            verticalVelocityChangedListener = null;
            locationChangedListener = null;
            
            // Reset state
            isInitialized.set(false);
            isMonitoring.set(false);
            
            // Clear UI handler
            if (uiHandler != null) {
                uiHandler.removeCallbacksAndMessages(null);
            }
            
            Log.i(TAG, "TelemetryService cleanup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during TelemetryService cleanup: " + e.getMessage(), e);
        }
    }
    
    /**
     * Reset singleton instance (for testing or complete reinitialization)
     */
    public static void resetInstance() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.cleanup();
                instance = null;
            }
        }
        Log.i(TAG, "TelemetryService instance reset");
    }
}
