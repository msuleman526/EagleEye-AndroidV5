//package com.empowerbits.dronifyit.Services;
//
//import android.app.Service;
//import android.content.Intent;
//import android.os.Binder;
//import android.os.Build;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.util.Log;
//import android.widget.Toast;
//import androidx.localbroadcastmanager.content.LocalBroadcastManager;
//
//import dji.v5.common.callback.CommonCallbacks;
//import dji.v5.common.error.IDJIError;
//import dji.v5.manager.interfaces.IWaypointMissionManager;
//import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.zip.ZipEntry;
//import java.util.zip.ZipOutputStream;
//
///**
// * CommandService handles waypoint mission operations with real-time status updates
// * Creates KMZ files using DJI WPML format and manages mission execution
// * DJI SDK V5.16.0 Compatible - REAL MISSION EXECUTION
// * COMPLETELY REWRITTEN: Now matches DJI documentation structure exactly
// */
//public class CommandService extends Service {
//    private static final String TAG = "CommandService";
//
//    // Broadcast action constants
//    public static final String ACTION_WAYPOINT_STATUS = "com.empowerbits.dronifyit.WAYPOINT_STATUS";
//    public static final String EXTRA_STATUS_MESSAGE = "status_message";
//    public static final String EXTRA_STATUS_TYPE = "status_type";
//    public static final String EXTRA_CURRENT_WAYPOINT = "current_waypoint";
//    public static final String EXTRA_TOTAL_WAYPOINTS = "total_waypoints";
//
//    // Status types
//    public static final String STATUS_UPLOADING = "UPLOADING";
//    public static final String STATUS_UPLOADED = "UPLOADED";
//    public static final String STATUS_MISSION_STARTED = "MISSION_STARTED";
//    public static final String STATUS_HEADING_TO_WAYPOINT = "HEADING_TO_WAYPOINT";
//    public static final String STATUS_MISSION_COMPLETED = "MISSION_COMPLETED";
//    public static final String STATUS_RETURNING_HOME = "RETURNING_HOME";
//    public static final String STATUS_ERROR = "ERROR";
//
//    // Static waypoints as provided - [latitude, longitude] format
//    private static final double[][] WAYPOINTS = {
//        {33.695843675830965, 73.05188642388882},
//        {33.695748383286784, 73.05199694013237},
//        {33.69557785952293, 73.0520411466298},
//        {33.69544411515762, 73.05189245204755},
//        {33.695460833214675, 73.05175782316904},
//        {33.69553773623518, 73.051635250608},
//        {33.69563804441939, 73.05154683761317},
//        {33.6957433678868, 73.05157697840686},
//        {33.69587711178632, 73.05167945710544}
//    };
//
//    // Mission parameters - 90 feet altitude
//    private static final float DEFAULT_ALTITUDE = 27.43f; // 90 feet in meters
//    private static final float DEFAULT_SPEED = 5.0f; // m/s
//    private static final float GIMBAL_PITCH = -90.0f; // look down
//    private static final String KMZ_FILE_NAME = "waypoint_mission";
//
//    private LocalBroadcastManager localBroadcastManager;
//    private Handler mainHandler;
//    private IWaypointMissionManager waypointMissionManager;
//    private File currentKmzFile;
//    private File externalKmzFile;
//    private int currentWaypointIndex = 0;
//    private boolean missionInProgress = false;
//    private String currentMissionName = "";
//
//    // Binder for activity communication
//    private final IBinder binder = new CommandServiceBinder();
//
//    public class CommandServiceBinder extends Binder {
//        public CommandService getService() {
//            return CommandService.this;
//        }
//    }
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Log.d(TAG, "CommandService created");
//
//        localBroadcastManager = LocalBroadcastManager.getInstance(this);
//        mainHandler = new Handler(Looper.getMainLooper());
//        waypointMissionManager = WaypointMissionManager.getInstance();
//
//        Log.d(TAG, "CommandService initialization complete");
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return binder;
//    }
//
//    /**
//     * Start waypoint mission with predefined waypoints
//     */
//    public void startWaypointMission() {
//        Log.d(TAG, "Starting waypoint mission creation");
//
//        try {
//            // Generate KMZ file
//            currentKmzFile = generateKmzFile();
//
//            // Save to external storage for file manager access
//            saveKmzToExternalStorage();
//
//            // Upload mission to aircraft
//            uploadMissionToAircraft();
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error starting waypoint mission: " + e.getMessage());
//            showToast("Error creating mission: " + e.getMessage());
//            broadcastStatus(STATUS_ERROR, "Error creating mission: " + e.getMessage(), 0, WAYPOINTS.length);
//        }
//    }
//
//    /**
//     * Generate KMZ file with waypoints using DJI WPML format
//     * COMPLETELY REWRITTEN: Match DJI documentation exactly
//     */
//    private File generateKmzFile() throws IOException {
//        Log.d(TAG, "Generating KMZ file with " + WAYPOINTS.length + " waypoints at " + DEFAULT_ALTITUDE + "m altitude");
//
//        // Create waypoint mission XML content
//        String waypointXml = generateWaypointXml();
//        String templateXml = generateTemplateXml();
//
//        // Create KMZ file in internal storage first
//        long timestamp = System.currentTimeMillis();
//        currentMissionName = KMZ_FILE_NAME + "_" + timestamp;
//        File kmzFile = new File(getFilesDir(), currentMissionName + ".kmz");
//
//        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(kmzFile))) {
//            // Add waypoint mission file (directly in root, not in wpmz folder)
//            ZipEntry waypointEntry = new ZipEntry("waylines.wpml");
//            zipOut.putNextEntry(waypointEntry);
//            zipOut.write(waypointXml.getBytes("UTF-8"));
//            zipOut.closeEntry();
//
//            // Add template file (directly in root, not in wpmz folder)
//            ZipEntry templateEntry = new ZipEntry("template.kml");
//            zipOut.putNextEntry(templateEntry);
//            zipOut.write(templateXml.getBytes("UTF-8"));
//            zipOut.closeEntry();
//        }
//
//        Log.d(TAG, "KMZ file generated: " + kmzFile.getAbsolutePath());
//        showToast("KMZ file created - 90 feet altitude - DJI compliant format");
//        return kmzFile;
//    }
//
//    /**
//     * Save KMZ file to external storage for file manager access
//     */
//    private void saveKmzToExternalStorage() {
//        try {
//            // Check if external storage is available
//            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
//                Log.e(TAG, "External storage not available");
//                showToast("External storage not available");
//                return;
//            }
//
//            // For Android 11+ use MediaStore or direct Downloads access
//            File downloadsDir;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                // Use public Downloads directory
//                downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//            } else {
//                // Legacy external storage access
//                downloadsDir = new File(Environment.getExternalStorageDirectory(), "Download");
//            }
//
//            // Create EagleEye directory
//            File eagleEyeDir = new File(downloadsDir, "EagleEye");
//            if (!eagleEyeDir.exists()) {
//                boolean created = eagleEyeDir.mkdirs();
//                if (!created) {
//                    Log.e(TAG, "Failed to create EagleEye directory");
//                    showToast("Failed to create Downloads/EagleEye directory");
//                    return;
//                }
//            }
//
//            // Copy KMZ file to external storage
//            externalKmzFile = new File(eagleEyeDir, currentMissionName + ".kmz");
//
//            try (FileOutputStream fos = new FileOutputStream(externalKmzFile)) {
//                // Read from internal file and write to external
//                java.io.FileInputStream fis = new java.io.FileInputStream(currentKmzFile);
//                byte[] buffer = new byte[1024];
//                int length;
//                while ((length = fis.read(buffer)) > 0) {
//                    fos.write(buffer, 0, length);
//                }
//                fis.close();
//            }
//
//            Log.d(TAG, "KMZ file saved to external storage: " + externalKmzFile.getAbsolutePath());
//            showToast("KMZ saved to Downloads/EagleEye/" + currentMissionName + ".kmz");
//
//            // Broadcast file location to UI
//            broadcastStatus(STATUS_UPLOADING, "KMZ file saved to Downloads/EagleEye/" + currentMissionName + ".kmz", 0, WAYPOINTS.length);
//
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to save KMZ to external storage: " + e.getMessage());
//            showToast("Failed to save KMZ to external storage: " + e.getMessage());
//        } catch (SecurityException e) {
//            Log.e(TAG, "Permission denied for external storage: " + e.getMessage());
//            showToast("Storage permission required. Please grant permission in Settings.");
//        }
//    }
//
//    /**
//     * Generate waypoint XML content using DJI WPML format
//     * COMPLETELY REWRITTEN: Match DJI documentation structure exactly
//     */
//    private String generateWaypointXml() {
//        StringBuilder xml = new StringBuilder();
//        long timestamp = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
//
//        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
//        xml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:wpml=\"http://www.dji.com/wpmz/1.0.2\">\n");
//        xml.append("  <Document>\n");
//
//        // FIXED: Add required document metadata (like DJI templates)
//        xml.append("    <wpml:author>EagleEye</wpml:author>\n");
//        xml.append("    <wpml:createTime>").append(timestamp).append("</wpml:createTime>\n");
//        xml.append("    <wpml:updateTime>").append(timestamp).append("</wpml:updateTime>\n");
//
//        // Mission configuration - CORRECTED to match DJI format
//        xml.append("    <wpml:missionConfig>\n");
//        xml.append("      <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>\n");
//        xml.append("      <wpml:finishAction>goHome</wpml:finishAction>\n");
//        xml.append("      <wpml:exitOnRCLost>goContinue</wpml:exitOnRCLost>\n");
//        xml.append("      <wpml:executeRCLostAction>hover</wpml:executeRCLostAction>\n");
//        xml.append("      <wpml:takeOffSecurityHeight>20</wpml:takeOffSecurityHeight>\n");
//        xml.append("      <wpml:globalTransitionalSpeed>").append(DEFAULT_SPEED).append("</wpml:globalTransitionalSpeed>\n");
//
//        // Drone information (required)
//        xml.append("      <wpml:droneInfo>\n");
//        xml.append("        <wpml:droneEnumValue>67</wpml:droneEnumValue>\n"); // M30
//        xml.append("        <wpml:droneSubEnumValue>0</wpml:droneSubEnumValue>\n");
//        xml.append("      </wpml:droneInfo>\n");
//
//        // Payload information (required)
//        xml.append("      <wpml:payloadInfo>\n");
//        xml.append("        <wpml:payloadEnumValue>52</wpml:payloadEnumValue>\n"); // M30 Camera
//        xml.append("        <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n");
//        xml.append("      </wpml:payloadInfo>\n");
//
//        xml.append("    </wpml:missionConfig>\n");
//
//        // FIXED: Wayline folder with CORRECT DJI structure
//        xml.append("    <Folder>\n");
//        xml.append("      <wpml:templateId>0</wpml:templateId>\n");
//
//        // FIXED: Use WGS84 height mode (like DJI templates)
//        xml.append("      <wpml:executeHeightMode>WGS84</wpml:executeHeightMode>\n");
//        xml.append("      <wpml:waylineId>0</wpml:waylineId>\n");
//
//        // Calculate total distance
//        double totalDistance = calculateTotalDistance();
//        xml.append("      <wpml:distance>").append(String.format("%.1f", totalDistance)).append("</wpml:distance>\n");
//
//        // Calculate duration
//        int duration = (int) Math.ceil(totalDistance / DEFAULT_SPEED);
//        xml.append("      <wpml:duration>").append(duration).append("</wpml:duration>\n");
//        xml.append("      <wpml:autoFlightSpeed>").append(DEFAULT_SPEED).append("</wpml:autoFlightSpeed>\n");
//
//        // Add waypoints with CORRECTED DJI format
//        for (int i = 0; i < WAYPOINTS.length; i++) {
//            xml.append("      <Placemark>\n");
//            xml.append("        <Point>\n");
//            xml.append("          <coordinates>").append(WAYPOINTS[i][1]).append(",").append(WAYPOINTS[i][0]).append("</coordinates>\n");
//            xml.append("        </Point>\n");
//            xml.append("        <wpml:index>").append(i).append("</wpml:index>\n");
//
//            // FIXED: Use executeHeight only for WGS84 mode (ellipsoid height not needed)
//            xml.append("        <wpml:executeHeight>").append(DEFAULT_ALTITUDE).append("</wpml:executeHeight>\n");
//            xml.append("        <wpml:waypointSpeed>").append(DEFAULT_SPEED).append("</wpml:waypointSpeed>\n");
//
//            // Waypoint heading parameters
//            xml.append("        <wpml:waypointHeadingParam>\n");
//            xml.append("          <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>\n");
//            xml.append("        </wpml:waypointHeadingParam>\n");
//
//            // Waypoint turn parameters - CORRECTED to match DJI format
//            xml.append("        <wpml:waypointTurnParam>\n");
//            xml.append("          <wpml:waypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:waypointTurnMode>\n");
//            xml.append("          <wpml:waypointTurnDampingDist>0</wpml:waypointTurnDampingDist>\n");
//            xml.append("        </wpml:waypointTurnParam>\n");
//
//            // Add action groups (required for KMZ compatibility)
//            if (i > 0) { // Skip first waypoint for actions (like DJI template)
//                xml.append("        <wpml:actionGroup>\n");
//                xml.append("          <wpml:actionGroupId>").append(i - 1).append("</wpml:actionGroupId>\n");
//                xml.append("          <wpml:actionGroupStartIndex>").append(i).append("</wpml:actionGroupStartIndex>\n");
//                xml.append("          <wpml:actionGroupEndIndex>").append(i).append("</wpml:actionGroupEndIndex>\n");
//                xml.append("          <wpml:actionGroupMode>sequence</wpml:actionGroupMode>\n");
//                xml.append("          <wpml:actionTrigger>\n");
//                xml.append("            <wpml:actionTriggerType>reachPoint</wpml:actionTriggerType>\n");
//                xml.append("          </wpml:actionTrigger>\n");
//
//                // FIXED: Gimbal action like DJI template
//                xml.append("          <wpml:action>\n");
//                xml.append("            <wpml:actionId>").append((i - 1) * 2).append("</wpml:actionId>\n");
//                xml.append("            <wpml:actionActuatorFunc>gimbalRotate</wpml:actionActuatorFunc>\n");
//                xml.append("            <wpml:actionActuatorFuncParam>\n");
//                xml.append("              <wpml:gimbalRotateMode>absoluteAngle</wpml:gimbalRotateMode>\n");
//                xml.append("              <wpml:gimbalPitchRotateEnable>1</wpml:gimbalPitchRotateEnable>\n");
//                xml.append("              <wpml:gimbalPitchRotateAngle>").append(GIMBAL_PITCH).append("</wpml:gimbalPitchRotateAngle>\n");
//                xml.append("              <wpml:gimbalRollRotateEnable>0</wpml:gimbalRollRotateEnable>\n");
//                xml.append("              <wpml:gimbalRollRotateAngle>0</wpml:gimbalRollRotateAngle>\n");
//                xml.append("              <wpml:gimbalYawRotateEnable>0</wpml:gimbalYawRotateEnable>\n");
//                xml.append("              <wpml:gimbalYawRotateAngle>0</wpml:gimbalYawRotateAngle>\n");
//                xml.append("              <wpml:gimbalRotateTimeEnable>0</wpml:gimbalRotateTimeEnable>\n");
//                xml.append("              <wpml:gimbalRotateTime>0</wpml:gimbalRotateTime>\n");
//                xml.append("              <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n");
//                xml.append("            </wpml:actionActuatorFuncParam>\n");
//                xml.append("          </wpml:action>\n");
//
//                // FIXED: Take photo action like DJI template
//                xml.append("          <wpml:action>\n");
//                xml.append("            <wpml:actionId>").append((i - 1) * 2 + 1).append("</wpml:actionId>\n");
//                xml.append("            <wpml:actionActuatorFunc>takePhoto</wpml:actionActuatorFunc>\n");
//                xml.append("            <wpml:actionActuatorFuncParam>\n");
//                xml.append("              <wpml:fileSuffix>point").append(i).append("</wpml:fileSuffix>\n");
//                xml.append("              <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n");
//                xml.append("            </wpml:actionActuatorFuncParam>\n");
//                xml.append("          </wpml:action>\n");
//
//                xml.append("        </wpml:actionGroup>\n");
//            }
//
//            xml.append("      </Placemark>\n");
//        }
//
//        xml.append("    </Folder>\n");
//        xml.append("  </Document>\n");
//        xml.append("</kml>\n");
//
//        return xml.toString();
//    }
//
//    /**
//     * Calculate total distance between waypoints (approximate)
//     */
//    private double calculateTotalDistance() {
//        double totalDistance = 0.0;
//        for (int i = 0; i < WAYPOINTS.length - 1; i++) {
//            double lat1 = WAYPOINTS[i][0];
//            double lon1 = WAYPOINTS[i][1];
//            double lat2 = WAYPOINTS[i + 1][0];
//            double lon2 = WAYPOINTS[i + 1][1];
//
//            // Haversine formula for distance calculation
//            double earthRadius = 6371000; // meters
//            double dLat = Math.toRadians(lat2 - lat1);
//            double dLon = Math.toRadians(lon2 - lon1);
//            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
//                    Math.sin(dLon / 2) * Math.sin(dLon / 2);
//            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//            totalDistance += earthRadius * c;
//        }
//        return totalDistance;
//    }
//
//    /**
//     * Generate template XML for KMZ file
//     * CORRECT: Empty template for Mobile SDK waypoint missions (according to DJI specification)
//     */
//    private String generateTemplateXml() {
//        long timestamp = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
//        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
//               "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:wpml=\"http://www.dji.com/wpmz/1.0.2\">\n" +
//                "<Document>\n" +
//                "  <wpml:author>EagleEye-Drone-Imaging</wpml:author>\n" +
//                "  <wpml:createTime>"+timestamp+"</wpml:createTime>\n" +
//                "  <wpml:updateTime>"+timestamp+"</wpml:updateTime>\n" +
//                "  <wpml:missionConfig>\n" +
//                "    <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>\n" +
//                "    <wpml:finishAction>goHome</wpml:finishAction>\n" +
//                "    <wpml:exitOnRCLost>executeLostAction</wpml:exitOnRCLost>\n" +
//                "    <wpml:executeRCLostAction>goBack</wpml:executeRCLostAction>\n" +
//                "    <wpml:takeOffSecurityHeight>46</wpml:takeOffSecurityHeight>\n" +
//                "    <wpml:globalTransitionalSpeed>8</wpml:globalTransitionalSpeed>\n" +
//                "    <wpml:droneInfo>\n" +
//                "      <wpml:droneEnumValue>67</wpml:droneEnumValue>\n" +
//                "      <wpml:droneSubEnumValue>0</wpml:droneSubEnumValue>\n" +
//                "    </wpml:droneInfo>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//                "  <Document>\n" +
//               "</kml>\n";
//    }
//
//    /**
//     * Upload mission KMZ file to aircraft
//     */
//    private void uploadMissionToAircraft() {
//        Log.d(TAG, "Uploading mission to aircraft: " + currentKmzFile.getAbsolutePath());
//
//        // Validate KMZ file exists and is not empty
//        if (currentKmzFile == null || !currentKmzFile.exists() || currentKmzFile.length() == 0) {
//            String error = "KMZ file is invalid or empty";
//            Log.e(TAG, error);
//            showToast(error);
//            broadcastStatus(STATUS_ERROR, error, 0, WAYPOINTS.length);
//            return;
//        }
//
//        Log.d(TAG, "KMZ file size: " + currentKmzFile.length() + " bytes");
//        broadcastStatus(STATUS_UPLOADING, "Starting mission upload...", 0, WAYPOINTS.length);
//        showToast("Uploading mission to aircraft...");
//
//        waypointMissionManager.pushKMZFileToAircraft(currentKmzFile.getAbsolutePath(), new CommonCallbacks.CompletionCallbackWithProgress<Double>() {
//            @Override
//            public void onProgressUpdate(Double progress) {
//                // Update upload progress
//                int progressPercent = (int) (progress * 100);
//                mainHandler.post(() -> {
//                    broadcastStatus(STATUS_UPLOADING, "Uploading mission... " + progressPercent + "%", 0, WAYPOINTS.length);
//                });
//            }
//
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Mission uploaded successfully");
//                mainHandler.post(() -> {
//                    showToast("Mission uploaded successfully");
//                    broadcastStatus(STATUS_UPLOADED, "Mission uploaded successfully", 0, WAYPOINTS.length);
//
//                    // **REAL MISSION EXECUTION** - Start the mission immediately after upload
//                    startRealMission();
//                });
//            }
//
//            @Override
//            public void onFailure(IDJIError error) {
//                Log.e(TAG, "Failed to upload mission: " + error.description());
//                mainHandler.post(() -> {
//                    showToast("Upload failed: " + error.description());
//                    broadcastStatus(STATUS_ERROR, "Upload failed: " + error.description(), 0, WAYPOINTS.length);
//                });
//            }
//        });
//    }
//
//    /**
//     * Start the real mission execution on the aircraft
//     */
//    private void startRealMission() {
//        Log.d(TAG, "Starting REAL waypoint mission execution on aircraft");
//
//        if (currentMissionName.isEmpty()) {
//            showToast("No mission uploaded");
//            broadcastStatus(STATUS_ERROR, "No mission uploaded", 0, WAYPOINTS.length);
//            return;
//        }
//
//        waypointMissionManager.startMission(currentMissionName + ".kmz", new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "REAL mission started successfully - Aircraft should take off now!");
//                mainHandler.post(() -> {
//                    showToast("Mission started! Aircraft taking off to 90 feet...");
//                    broadcastStatus(STATUS_MISSION_STARTED, "Mission execution started - Aircraft taking off to 90 feet!", 0, WAYPOINTS.length);
//                    missionInProgress = true;
//
//                    // Note: Real mission progress will be handled by DJI SDK listeners
//                    // The aircraft will actually take off and follow the waypoints at 90 feet altitude
//                });
//            }
//
//            @Override
//            public void onFailure(IDJIError error) {
//                Log.e(TAG, "Failed to start REAL mission: " + error.description());
//                mainHandler.post(() -> {
//                    showToast("Failed to start mission: " + error.description());
//                    broadcastStatus(STATUS_ERROR, "Failed to start mission: " + error.description(), 0, WAYPOINTS.length);
//                });
//            }
//        });
//    }
//
//    /**
//     * Start the uploaded mission (called from UI)
//     */
//    public void startMission() {
//        Log.d(TAG, "Manual mission start requested");
//
//        if (currentMissionName.isEmpty()) {
//            showToast("No mission uploaded. Please start waypoint mission first.");
//            broadcastStatus(STATUS_ERROR, "No mission uploaded", 0, WAYPOINTS.length);
//            return;
//        }
//
//        // Start the real mission
//        startRealMission();
//    }
//
//    /**
//     * Stop the current mission
//     */
//    public void stopMission() {
//        Log.d(TAG, "Stopping waypoint mission");
//
//        if (currentMissionName.isEmpty()) {
//            showToast("No mission to stop");
//            broadcastStatus(STATUS_ERROR, "No mission to stop", 0, WAYPOINTS.length);
//            return;
//        }
//
//        waypointMissionManager.stopMission(currentMissionName + ".kmz", new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Mission stopped successfully");
//                missionInProgress = false;
//                mainHandler.post(() -> {
//                    showToast("Mission stopped successfully");
//                    broadcastStatus(STATUS_ERROR, "Mission stopped by user", currentWaypointIndex, WAYPOINTS.length);
//                });
//            }
//
//            @Override
//            public void onFailure(IDJIError error) {
//                Log.e(TAG, "Failed to stop mission: " + error.description());
//                mainHandler.post(() -> {
//                    showToast("Failed to stop mission: " + error.description());
//                });
//            }
//        });
//    }
//
//    /**
//     * Pause the current mission
//     */
//    public void pauseMission() {
//        Log.d(TAG, "Pausing waypoint mission");
//
//        waypointMissionManager.pauseMission(new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Mission paused successfully");
//                mainHandler.post(() -> {
//                    showToast("Mission paused");
//                });
//            }
//
//            @Override
//            public void onFailure(IDJIError error) {
//                Log.e(TAG, "Failed to pause mission: " + error.description());
//                mainHandler.post(() -> {
//                    showToast("Failed to pause mission: " + error.description());
//                });
//            }
//        });
//    }
//
//    /**
//     * Resume the paused mission
//     */
//    public void resumeMission() {
//        Log.d(TAG, "Resuming waypoint mission");
//
//        waypointMissionManager.resumeMission(new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "Mission resumed successfully");
//                mainHandler.post(() -> {
//                    showToast("Mission resumed");
//                });
//            }
//
//            @Override
//            public void onFailure(IDJIError error) {
//                Log.e(TAG, "Failed to resume mission: " + error.description());
//                mainHandler.post(() -> {
//                    showToast("Failed to resume mission: " + error.description());
//                });
//            }
//        });
//    }
//
//    /**
//     * Show toast message on main thread
//     */
//    private void showToast(String message) {
//        mainHandler.post(() -> {
//            Toast.makeText(CommandService.this, message, Toast.LENGTH_SHORT).show();
//        });
//    }
//
//    /**
//     * Broadcast status update to listening activities
//     */
//    private void broadcastStatus(String statusType, String message, int currentWaypoint, int totalWaypoints) {
//        Intent intent = new Intent(ACTION_WAYPOINT_STATUS);
//        intent.putExtra(EXTRA_STATUS_TYPE, statusType);
//        intent.putExtra(EXTRA_STATUS_MESSAGE, message);
//        intent.putExtra(EXTRA_CURRENT_WAYPOINT, currentWaypoint);
//        intent.putExtra(EXTRA_TOTAL_WAYPOINTS, totalWaypoints);
//
//        localBroadcastManager.sendBroadcast(intent);
//        Log.d(TAG, "Status broadcast: " + statusType + " - " + message);
//    }
//
//    /**
//     * Get the static waypoints array
//     */
//    public double[][] getWaypoints() {
//        return WAYPOINTS;
//    }
//
//    /**
//     * Check if mission is currently in progress
//     */
//    public boolean isMissionInProgress() {
//        return missionInProgress;
//    }
//
//    /**
//     * Get current waypoint index
//     */
//    public int getCurrentWaypointIndex() {
//        return currentWaypointIndex;
//    }
//
//    /**
//     * Get external KMZ file path for sharing
//     */
//    public String getExternalKmzFilePath() {
//        return externalKmzFile != null ? externalKmzFile.getAbsolutePath() : null;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        Log.d(TAG, "CommandService destroyed");
//
//        // Clean up temporary files
//        if (currentKmzFile != null && currentKmzFile.exists()) {
//            currentKmzFile.delete();
//        }
//    }
//}
