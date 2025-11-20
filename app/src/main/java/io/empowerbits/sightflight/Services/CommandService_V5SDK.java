package io.empowerbits.sightflight.Services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dji.wpmzsdk.common.data.KMZInfo;
import com.dji.wpmzsdk.common.data.Template;
import com.dji.wpmzsdk.interfaces.IWPMZManager;
import com.dji.wpmzsdk.manager.WPMZManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.empowerbits.sightflight.models.MissionSetting;
import io.empowerbits.sightflight.models.WaypointSetting;
import io.empowerbits.sightflight.util.KMZTestUtil;
import io.empowerbits.sightflight.util.wpml.WaypointInfoModel;
import io.empowerbits.sightflight.util.KmzCleaner;

import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.wpmz.value.mission.WaylineActionInfo;
import dji.sdk.wpmz.value.mission.WaylineCheckError;
import dji.sdk.wpmz.value.mission.WaylineCheckErrorMsg;
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate2D;
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate3D;
import dji.sdk.wpmz.value.mission.WaylineMission;
import dji.sdk.wpmz.value.mission.WaylineMissionConfig;
import dji.sdk.wpmz.value.mission.WaylineMissionConfigParseInfo;
import dji.sdk.wpmz.value.mission.WaylineMissionParseInfo;
import dji.sdk.wpmz.value.mission.WaylineWaylinesParseInfo;
import dji.sdk.wpmz.value.mission.WaylineWaypoint;
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnMode;
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnParam;
import dji.sdk.wpmz.value.mission.WaylineWaypointYawMode;
import dji.sdk.wpmz.value.mission.WaylineWaypointYawParam;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.aircraft.simulator.InitializationSettings;
import dji.v5.manager.aircraft.simulator.SimulatorManager;
import dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
import dji.v5.manager.aircraft.waypoint3.model.WaylineExecutingInfo;
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState;
import dji.v5.utils.common.FileUtils;

import dji.v5.manager.aircraft.waypoint3.WaypointMissionExecuteStateListener;


public class CommandService_V5SDK extends Service {
    private static final String TAG = "CommandService_V5SDK";

    // Broadcast action constants
    public static final String ACTION_WAYPOINT_STATUS = "io.empowerbits.sightflight.WAYPOINT_STATUS";
    public static final String EXTRA_STATUS_MESSAGE = "status_message";
    public static final String EXTRA_STATUS_TYPE = "status_type";
    public static final String EXTRA_CURRENT_WAYPOINT = "current_waypoint";
    public static final String EXTRA_TOTAL_WAYPOINTS = "total_waypoints";

    // Status types
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_MISSION_STARTED = "MISSION_STARTED";
    public static final String STATUS_HEADING_TO_WAYPOINT = "HEADING_TO_WAYPOINT";
    public static final String STATUS_MISSION_COMPLETED = "MISSION_COMPLETED";
    public static final String STATUS_RETURNING_HOME = "RETURNING_HOME";
    public static final String STATUS_ERROR = "ERROR";

    // Mission parameters - 90 feet altitude
    private static final float DEFAULT_ALTITUDE = 27.43f; // 90 feet in meters
    private static final float DEFAULT_SPEED = 5.0f; // m/s
    private static final float GIMBAL_PITCH = -90.0f; // look down
    private static final String KMZ_FILE_NAME = "waypoint_mission";

    private LocalBroadcastManager localBroadcastManager;
    private Handler mainHandler;
    private WaypointMissionManager waypointMissionManager;
    private WaypointMissionExecuteStateListener missionStateListener;
    private WaylineExecutingInfoListener waylineExecutingInfoListener;

    private IWPMZManager wpmzManager;
    private Intent buttonListenerServiceIntent;
    private File currentKmzFile;
    private File externalKmzFile;
    private int currentWaypointIndex = 0;
    private boolean missionInProgress = false;
    private String currentMissionName = "";
    private final IBinder binder = new CommandServiceBinder();

    MissionSetting missionSetting;
    ArrayList<WaypointSetting> waypointSettings;
    public class CommandServiceBinder extends Binder {
        public CommandService_V5SDK getService() {
            return CommandService_V5SDK.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CommandService_V5SDK created");

        waypointSettings = new ArrayList<>();
        missionSetting = new MissionSetting();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        mainHandler = new Handler(Looper.getMainLooper());
        waypointMissionManager = WaypointMissionManager.getInstance();
        wpmzManager = WPMZManager.getInstance();
        buttonListenerServiceIntent = new Intent(this, ButtonsListenerService.class);

        setupMissionListeners();
        Log.d(TAG, "CommandService_V5SDK initialization complete with DJI SDK utilities");
    }

    /**
     * Setup waypoint mission listeners to track progress
     */
    private void setupMissionListeners() {
        missionStateListener = new WaypointMissionExecuteStateListener() {
            @Override
            public void onMissionStateUpdate(WaypointMissionExecuteState state) {
                mainHandler.post(() -> {
                    if (state == null) return;
                    String statusMessage = "";
                    String statusType = STATUS_HEADING_TO_WAYPOINT;

                    switch (state) {
                        case READY:
                            statusMessage = "Mission ready";
                            break;
                        case UPLOADING:
                            statusMessage = "Uploading mission...";
                            statusType = STATUS_UPLOADING;
                            break;
                        case PREPARING:
                            statusMessage = "Preparing for takeoff...";
                            break;
                        case ENTER_WAYLINE:
                            statusMessage = "Entering wayline mode...";
                            break;
                        case EXECUTING:
                            statusMessage = "Executing waypoint mission";
                            // Start RC button listener service when mission is executing
                            startButtonListenerService();
                            break;
                        case INTERRUPTED:
                            statusMessage = "Mission interrupted";
                            statusType = STATUS_ERROR;
                            // Stop button listener when mission is interrupted
                            stopButtonListenerService();
                            break;
                        case RECOVERING:
                            statusMessage = "Recovering mission...";
                            // Resume button listener when mission is recovering
                            startButtonListenerService();
                            break;
                        case FINISHED:
                            statusMessage = "Mission completed!";
                            statusType = STATUS_MISSION_COMPLETED;
                            missionInProgress = false;
                            // Stop button listener when mission finishes
                            stopButtonListenerService();
                            break;
                        case RETURN_TO_START_POINT:
                            statusMessage = "Returning to start point";
                            statusType = STATUS_RETURNING_HOME;
                            break;
                        case DISCONNECTED:
                            statusMessage = "Disconnected from aircraft";
                            statusType = STATUS_ERROR;
                            // Stop button listener on disconnection
                            stopButtonListenerService();
                            break;
                        case NOT_SUPPORTED:
                            statusMessage = "Mission not supported";
                            statusType = STATUS_ERROR;
                            // Stop button listener if mission not supported
                            stopButtonListenerService();
                            break;
                        default:
                            statusMessage = "Mission state: " + state.name();
                            break;
                    }
                    broadcastStatus(statusType, statusMessage, currentWaypointIndex, waypointSettings.size());
                });
            }
        };

        waylineExecutingInfoListener = new WaylineExecutingInfoListener() {
            @Override
            public void onWaylineExecutingInfoUpdate(WaylineExecutingInfo executingInfo) {
                mainHandler.post(() -> {
                    if (executingInfo == null) return;

                    int waypointIndex = executingInfo.getCurrentWaypointIndex();

                    // Only update if waypoint changed (to avoid duplicate messages)
                    if (waypointIndex != currentWaypointIndex) {
                        currentWaypointIndex = waypointIndex;

                        if (waypointIndex >= 0 && waypointIndex < waypointSettings.size()) {
                            // ✅ CORRECT: Say "Heading to" not "Reached at"
                            String message = "✓ Reached waypoint " + (waypointIndex + 1) + "/" + waypointSettings.size();
                            broadcastStatus(STATUS_HEADING_TO_WAYPOINT, message, waypointIndex, waypointSettings.size());
                            Log.d(TAG, "Progress: Heading to " + (waypointIndex + 1) + "/" +waypointSettings.size());

                        } else if (waypointIndex >= waypointSettings.size()) {
                            String message = "Completing mission...";
                            broadcastStatus(STATUS_MISSION_COMPLETED, message, waypointSettings.size(), waypointSettings.size());
                        }
                    }
                });
            }
        };

        // Register both listeners
        waypointMissionManager.addWaypointMissionExecuteStateListener(missionStateListener);
        waypointMissionManager.addWaylineExecutingInfoListener(waylineExecutingInfoListener);

        Log.d(TAG, "Mission listeners registered");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Start waypoint mission with predefined waypoints
     */
    public void startWaypointMission(MissionSetting missionSetting, ArrayList<WaypointSetting> waypointSettings) {
        Log.d(TAG, "Starting waypoint mission creation using DJI SDK KMZTestUtil");
        this.missionSetting = missionSetting;
        this.waypointSettings = waypointSettings;
        try {
            currentKmzFile = generateKmzFileUsingDJISDK();
            saveKmzToExternalStorage();
            verifyKMZFile();
            enableSimulator(waypointSettings.get(waypointSettings.size()-1).latitude, waypointSettings.get(waypointSettings.size()-1).longitude, 12);
            //uploadMissionToAircraft();

        } catch (Exception e) {
            Log.e(TAG, "Error starting waypoint mission: " + e.getMessage(), e);
            showToast("Error creating mission: " + e.getMessage());
            broadcastStatus(STATUS_ERROR, "Error creating mission: " + e.getMessage(), 0, waypointSettings.size());
        }
    }

    /**
     * Generate KMZ file using DJI SDK WPMZManager and KMZTestUtil patterns
     * This uses the official DJI SDK methods to create proper waylines.wpml and template.kml
     */
    /**
     * Generate KMZ file using DJI SDK WPMZManager and KMZTestUtil patterns
     * This uses the official DJI SDK methods to create proper waylines.wpml and template.kml
     */
    private File generateKmzFileUsingDJISDK() throws IOException {
        // 1. Create WaylineMission using KMZTestUtil pattern
        WaylineMission waylineMission = KMZTestUtil.createWaylineMission();
        WaylineMissionConfig missionConfig = KMZTestUtil.createMissionConfig(missionSetting);
        List<WaypointInfoModel> waypointInfoModels = createWaypointInfoModels();
        Template template = KMZTestUtil.createTemplate(waypointInfoModels);
        long timestamp = System.currentTimeMillis();
        currentMissionName = KMZ_FILE_NAME + "_" + timestamp;
        File outputDir = getFilesDir();
        String kmzPath = new File(outputDir, currentMissionName + ".kmz").getAbsolutePath();
        wpmzManager.generateKMZFile(
                kmzPath,            // Full path to output KMZ file
                waylineMission,     // WaylineMission object
                missionConfig,      // WaylineMissionConfig object
                template            // Template object
        );
        currentKmzFile = new File(kmzPath);
        if (!currentKmzFile.exists()) {
            throw new IOException("WPMZManager.generateKMZFile() did not create the KMZ file");
        }

        if (currentKmzFile.length() == 0) {
            throw new IOException("Generated KMZ file is empty");
        }

        Log.d(TAG, "KMZ file generated successfully - size: " + currentKmzFile.length() + " bytes");

        try {
            Log.d(TAG, "Post-processing KMZ to remove corrupt efficiencyFlightModeEnable fields");
            KmzCleaner.cleanKMZFile(currentKmzFile, outputDir);
            Log.d(TAG, "KMZ cleaned successfully - new size: " + currentKmzFile.length() + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean KMZ file: " + e.getMessage(), e);
            throw new IOException("KMZ cleaning failed: " + e.getMessage());
        }
        return currentKmzFile;
    }

    private void verifyKMZFile (){
        File outputDir = getFilesDir();
        Log.d(TAG, "Verifying KMZ File");
        String kmzPath = new File(outputDir, currentMissionName + ".kmz").getAbsolutePath();
        try {
            KMZInfo info = wpmzManager.getKMZInfo(kmzPath);
            Log.d(TAG, "KMZ Info");
            WaylineMissionConfigParseInfo info1 = info.getWaylineMissionConfigParseInfo();
            WaylineMissionParseInfo info2 = info.getWaylineMissionParseInfo();
            WaylineWaylinesParseInfo info4 = info.getWaylineWaylinesParseInfo();
            Log.d(TAG, "Info 1");
            Log.d(TAG, info1.toString());
            Log.d(TAG, "----------------------------");
            Log.d(TAG, "Info 2");
            Log.d(TAG, info2.toString());
            Log.d(TAG, "----------------------------");
            Log.d(TAG, "Info 3");
            Log.d(TAG, info.getWaylineTemplatesParseInfo().getError().toString());
            Log.d(TAG, "----------------------------");
            Log.d(TAG, "Info 4");
            Log.d(TAG, info4.toString());
            Log.d(TAG, "----------------------------");
            WaylineCheckErrorMsg check = wpmzManager.checkValidation(kmzPath);
            Log.d(TAG, "Wayline File Check");
            // Generic dump
            Log.d(TAG, "Full object: " + check.toString());

             List<WaylineCheckError> errors = check.getValue();
        } catch (Throwable t) {
            Log.e(TAG, "checkValidation threw", t);
        }
    }

    /**
     * Create waypoint info models with proper DJI SDK structure
     */
    /**
     * Create waypoint info models with Mini 4 Pro supported features
     */
    private List<WaypointInfoModel> createWaypointInfoModels() {
        List<WaypointInfoModel> waypointInfoModels = new ArrayList<>();

        // Create POI location (Point of Interest) - use first waypoint as POI
        WaylineLocationCoordinate3D poiLocation = new WaylineLocationCoordinate3D();
        poiLocation.setLatitude(missionSetting.poiLocation.latitude);
        poiLocation.setLongitude(missionSetting.poiLocation.longitude);
        poiLocation.setAltitude(missionSetting.poiHeight);

        for (int i = 0; i < waypointSettings.size(); i++) {
            WaypointSetting currencWaypoint = waypointSettings.get(i);
            // Create waypoint
            WaylineWaypoint waypoint = new WaylineWaypoint();
            WaylineLocationCoordinate2D location = new WaylineLocationCoordinate2D();
            location.setLatitude(currencWaypoint.latitude);
            location.setLongitude(currencWaypoint.longitude);
            waypoint.setLocation(location);

            waypoint.setHeight(currencWaypoint.altitude);
            waypoint.setEllipsoidHeight(currencWaypoint.altitude);
            waypoint.setGimbalPitchAngle(currencWaypoint.gimbalPitchAngle);
            waypoint.setSpeed(currencWaypoint.waypointSpeed);
            waypoint.setWaypointIndex(i);

            WaylineWaypointYawParam yawParam = new WaylineWaypointYawParam();
            yawParam.setYawMode(WaylineWaypointYawMode.TOWARD_POI);
            yawParam.setEnableYawAngle(true);
            yawParam.setYawAngle(currencWaypoint.gimbalPitchAngle);
            yawParam.setPoiLocation(poiLocation);
            waypoint.setYawParam(yawParam);

            WaylineWaypointTurnParam turnParam = new WaylineWaypointTurnParam();
            turnParam.setTurnMode(WaylineWaypointTurnMode.TO_POINT_AND_STOP_WITH_CONTINUITY_CURVATURE);
            turnParam.setTurnDampingDistance(0.0);
            waypoint.setTurnParam(turnParam);
            waypoint.setUseStraightLine(true);

            // Create info model and set the waypoint
            WaypointInfoModel infoModel = new WaypointInfoModel();
            infoModel.setWaylineWaypoint(waypoint);
            List<WaylineActionInfo> actionInfos = new ArrayList<>();
            WaylineActionInfo takePhotoAction = KMZTestUtil.createActionInfo(
                    com.dji.wpmzsdk.common.utils.kml.model.WaypointActionType.START_TAKE_PHOTO,
                    null
            );
            if (takePhotoAction != null) {
                actionInfos.add(takePhotoAction);
            }

            infoModel.setActionInfos(actionInfos);
            waypointInfoModels.add(infoModel);
        }
        return waypointInfoModels;
    }

    /**
     * Save KMZ file to external storage for file manager access
     */
    private void saveKmzToExternalStorage() {
        try {
            // Check if external storage is available
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Log.e(TAG, "External storage not available");
                showToast("External storage not available");
                return;
            }

            // Get Downloads directory
            File downloadsDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            } else {
                downloadsDir = new File(Environment.getExternalStorageDirectory(), "Download");
            }

            // Create EagleEye directory
            File eagleEyeDir = new File(downloadsDir, "EagleEye");
            if (!eagleEyeDir.exists()) {
                boolean created = eagleEyeDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create EagleEye directory");
                    showToast("Failed to create Downloads/EagleEye directory");
                    return;
                }
            }

            // Copy KMZ file to external storage
            externalKmzFile = new File(eagleEyeDir, currentMissionName + ".kmz");

            try (FileOutputStream fos = new FileOutputStream(externalKmzFile);
                 java.io.FileInputStream fis = new java.io.FileInputStream(currentKmzFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }
            broadcastStatus(STATUS_UPLOADING, "KMZ file saved to Downloads/EagleEye/" + currentMissionName + ".kmz", 0, waypointSettings.size());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save KMZ to external storage: " + e.getMessage(), e);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for external storage: " + e.getMessage(), e);
            showToast("Storage permission required. Please grant permission in Settings.");
        }
    }

    /**
     * Upload mission KMZ file to aircraft
     */
    private void uploadMissionToAircraft() {
        Log.d(TAG, "Uploading mission to aircraft: " + currentKmzFile.getAbsolutePath());

        // Validate KMZ file
        if (currentKmzFile == null || !currentKmzFile.exists() || currentKmzFile.length() == 0) {
            String error = "KMZ file is invalid or empty";
            Log.e(TAG, error);
            showToast(error);
            broadcastStatus(STATUS_ERROR, error, 0, waypointSettings.size());
            return;
        }

        Log.d(TAG, "KMZ file size: " + currentKmzFile.length() + " bytes");
        broadcastStatus(STATUS_UPLOADING, "Starting mission upload...", 0, waypointSettings.size());
        waypointMissionManager.pushKMZFileToAircraft(currentKmzFile.getAbsolutePath(), new CommonCallbacks.CompletionCallbackWithProgress<Double>() {
            @Override
            public void onProgressUpdate(Double progress) {
                int progressPercent = (int) (progress * 100);
                mainHandler.post(() -> {
                    broadcastStatus(STATUS_UPLOADING, "Uploading mission... " + progressPercent + "%", 0, waypointSettings.size());
                });
            }

            @Override
            public void onSuccess() {
                Log.d(TAG, "Mission uploaded successfully");
                mainHandler.post(() -> {
                    broadcastStatus(STATUS_UPLOADED, "Mission uploaded successfully", 0, waypointSettings.size());
                    startRealMission();
                });
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to upload mission: " + error.description());
                mainHandler.post(() -> {
                    broadcastStatus(STATUS_ERROR, "Upload failed: " + error.description(), 0, waypointSettings.size());
                });
            }
        });
    }

    /**
     * Start the real mission execution on the aircraft
     */
    private void startRealMission() {
        Log.d(TAG, "Starting REAL waypoint mission execution on aircraft");

        if (currentMissionName.isEmpty()) {
            broadcastStatus(STATUS_ERROR, "No mission uploaded", 0, waypointSettings.size());
            return;
        }

        File outputDir = getFilesDir();
        String kmzPath = new File(outputDir, currentMissionName + ".kmz").getAbsolutePath();
        String fileName =  FileUtils.getFileName(kmzPath, ".kmz");
        waypointMissionManager.startMission(fileName, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "REAL mission started successfully - Aircraft taking off!");
                mainHandler.post(() -> {
                    broadcastStatus(STATUS_MISSION_STARTED, "Mission execution started - Aircraft taking off to 90 feet!...", 0, waypointSettings.size());
                    missionInProgress = true;
                });
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to start REAL mission: " + error.description());
                mainHandler.post(() -> {
                    broadcastStatus(STATUS_ERROR, "Failed to start mission: " + error.description(), 0, waypointSettings.size());
                });
            }
        });
    }

    /**
     * Start the uploaded mission (called from UI)
     */
    public void startMission() {
        Log.d(TAG, "Manual mission start requested");

        if (currentMissionName.isEmpty()) {
            broadcastStatus(STATUS_ERROR, "No mission uploaded", 0, waypointSettings.size());
            return;
        }

        startRealMission();
    }

    /**
     * Stop the current mission
     */
    public void stopMission() {
        Log.d(TAG, "Stopping waypoint mission");

        if (currentMissionName.isEmpty()) {
            broadcastStatus(STATUS_ERROR, "No mission to stop", 0, waypointSettings.size());
            return;
        }

        waypointMissionManager.stopMission(currentMissionName + ".kmz", new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Mission stopped successfully");
                missionInProgress = false;
                mainHandler.post(() -> {
                    disableSimulator();
                    broadcastStatus(STATUS_ERROR, "Mission stopped by user", currentWaypointIndex, waypointSettings.size());
                });
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to stop mission: " + error.description());
                mainHandler.post(() -> {
                    showToast("Failed to stop mission: " + error.description());
                });
            }
        });
    }

    /**
     * Pause the current mission
     */
    public void pauseMission() {
        Log.d(TAG, "Pausing waypoint mission");

        waypointMissionManager.pauseMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Mission paused successfully");
                mainHandler.post(() -> {
                    showToast("Mission paused");
                });
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to pause mission: " + error.description());
                mainHandler.post(() -> {
                    showToast("Failed to pause mission: " + error.description());
                });
            }
        });
    }

    /**
     * Resume the paused mission
     */
    public void resumeMission() {
        Log.d(TAG, "Resuming waypoint mission");

        waypointMissionManager.resumeMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Mission resumed successfully");
                mainHandler.post(() -> {
                    showToast("Mission resumed");
                });
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to resume mission: " + error.description());
                mainHandler.post(() -> {
                    showToast("Failed to resume mission: " + error.description());
                });
            }
        });
    }

    /**
     * Show toast message on main thread
     */
    private void showToast(String message) {
        mainHandler.post(() -> {
            Toast.makeText(CommandService_V5SDK.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Broadcast status update to listening activities
     */
    private void broadcastStatus(String statusType, String message, int currentWaypoint, int totalWaypoints) {
        Intent intent = new Intent(ACTION_WAYPOINT_STATUS);
        intent.putExtra(EXTRA_STATUS_TYPE, statusType);
        intent.putExtra(EXTRA_STATUS_MESSAGE, message);
        intent.putExtra(EXTRA_CURRENT_WAYPOINT, currentWaypoint);
        intent.putExtra(EXTRA_TOTAL_WAYPOINTS, totalWaypoints);

        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG, "Status broadcast: " + statusType + " - " + message);
    }

    /**
     * Check if mission is currently in progress
     */
    public boolean isMissionInProgress() {
        return missionInProgress;
    }

    /**
     * Get current waypoint index
     */
    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    /**
     * Get external KMZ file path for sharing
     */
    public String getExternalKmzFilePath() {
        return externalKmzFile != null ? externalKmzFile.getAbsolutePath() : null;
    }

    /**
     * Start ButtonsListenerService to monitor RC button presses during mission
     */
    private void startButtonListenerService() {
        try {
            Log.d(TAG, "Starting ButtonsListenerService for RC button monitoring");
            startService(buttonListenerServiceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ButtonsListenerService: " + e.getMessage(), e);
        }
    }

    /**
     * Stop ButtonsListenerService when mission is paused, completed, or failed
     */
    private void stopButtonListenerService() {
        try {
            Log.d(TAG, "Stopping ButtonsListenerService - mission not executing");
            stopService(buttonListenerServiceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop ButtonsListenerService: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CommandService_V5SDK destroyed");

        // Stop button listener service if still running
        stopButtonListenerService();

        // Remove mission listeners
        if (missionStateListener != null) {
            waypointMissionManager.removeWaypointMissionExecuteStateListener(missionStateListener);
        }

        if (waylineExecutingInfoListener != null) {
            waypointMissionManager.removeWaylineExecutingInfoListener(waylineExecutingInfoListener);
        }

        // Clean up temporary files
        if (currentKmzFile != null && currentKmzFile.exists()) {
            currentKmzFile.delete();
        }
    }

    /**
     * Turn on simulator before mission upload
     * @param latitude Initial simulator latitude
     * @param longitude Initial simulator longitude
     * @param satelliteCount Number of GPS satellites (8-20 recommended)
     */
    private void enableSimulator(double latitude, double longitude, int satelliteCount) {
        Log.d(TAG, "Enabling simulator at location: " + latitude + ", " + longitude + " with " + satelliteCount + " satellites");

        // Check if simulator is already active
        if (SimulatorManager.getInstance().isSimulatorEnabled()) {
            Log.d(TAG, "Simulator already enabled");
            showToast("Simulator already active");
            // Proceed to upload mission
            uploadMissionToAircraft();
            return;
        }

        showToast("Starting simulator...");
        broadcastStatus(STATUS_UPLOADING, "Starting simulator...", 0, waypointSettings.size());

        // Enable simulator with location and satellite count
        InitializationSettings settings = new InitializationSettings(new LocationCoordinate2D(latitude, longitude), satelliteCount);
        SimulatorManager.getInstance().enableSimulator(settings,
                new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Simulator enabled successfully");
                        mainHandler.post(() -> {
                            showToast("Simulator started successfully");
                            broadcastStatus(STATUS_UPLOADING, "Simulator active - uploading mission...", 0, waypointSettings.size());
                            // Now upload the mission
                            uploadMissionToAircraft();
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        Log.e(TAG, "Failed to enable simulator: " + error.description());
                        mainHandler.post(() -> {
                            showToast("Simulator failed: " + error.description());
                            broadcastStatus(STATUS_ERROR, "Simulator failed: " + error.description(), 0, waypointSettings.size());
                        });
                    }
                }
        );
    }

    /**
     * Disable simulator after mission completion or on error
     */
    private void disableSimulator() {
        Log.d(TAG, "Disabling simulator");

        if (!SimulatorManager.getInstance().isSimulatorEnabled()) {
            Log.d(TAG, "Simulator not enabled");
            return;
        }

        SimulatorManager.getInstance().disableSimulator(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Simulator disabled successfully");
                mainHandler.post(() -> {
                    showToast("Simulator stopped");
                });
            }

            @Override
            public void onFailure(IDJIError error) {
                Log.e(TAG, "Failed to disable simulator: " + error.description());
                mainHandler.post(() -> {
                    showToast("Failed to stop simulator: " + error.description());
                });
            }
        });
    }
}