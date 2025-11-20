# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EagleEye is an Android application for controlling DJI drones, built on **DJI Mobile SDK V5 (MSDK V5)**. The app provides waypoint mission planning, real-time flight control, camera/FPV viewing, media management, and backend integration for project-based drone operations.

**Package**: `io.empowerbits.sightflight`

## Build Commands

### Essential Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Install debug APK to connected device
./gradlew installDebug

# Run all tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest
```

### Development Workflow
```bash
# Check for build issues without building
./gradlew tasks --all

# View project dependencies
./gradlew app:dependencies

# Lint check
./gradlew lint
```

## Critical DJI SDK V5 Architecture

### Native Library Initialization (CRITICAL)

**The `Helper.install()` call is ABSOLUTELY REQUIRED** for DJI SDK V5 to function. This happens in:

1. **EagleEyeApplication.attachBaseContext()** (app/src/main/java/io/empowerbits/sightflight/EagleEyeApplication.java)
   - Calls `Helper.install(this)` from `com.cySdkyc.clx.Helper`
   - This MUST succeed before any DJI SDK functionality works
   - Enhanced with device compatibility checks and native crash protection
   - ONLY works on **real arm64-v8a devices** (not x86/x86_64 emulators)

2. **DJIApplication.onCreate()** (app/src/main/java/io/empowerbits/sightflight/Activities/DJIApplication.java)
   - Initializes MSDKManagerVM with delayed execution (500ms)
   - Calls `msdkManagerVM.initMobileSDK()`

### DJI SDK V5 Dependency Pattern (CRITICAL)

In `app/build.gradle`, **ALL THREE dependencies are REQUIRED**:

```gradle
compileOnly deps.aircraftProvided  // Compile-time interface
implementation deps.aircraft        // Runtime implementation
implementation deps.networkImp      // Network implementation - REQUIRED!
```

**Never remove `networkImp`** - the SDK will fail silently without it.

### Architecture Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Architecture**: arm64-v8a ONLY (configured in ndk.abiFilters)
- **Will NOT work on**: x86/x86_64 emulators or tablets
- **Java Version**: 17 (toolchain configured)

### Native Library Packaging

The `app/build.gradle` includes extensive `packagingOptions` for DJI SDK V5 native libraries (`.so` files). **Do not modify** the `doNotStrip` directives - these prevent ProGuard from stripping debug symbols needed by DJI SDK.

## Application Architecture

### Application Lifecycle

```
EagleEyeApplication (extends DJIApplication)
  └─> attachBaseContext()
       └─> Helper.install() [CRITICAL - Native lib initialization]
  └─> onCreate()
       └─> MSDKManagerVM.initMobileSDK() [SDK initialization]
```

### Main Activities

1. **SplashActivity** - Entry point, checks permissions and SDK registration
2. **MainActivity** - Connection hub, shows drone connection status
3. **WaypointActivity** - Mission planning with Google Maps integration
4. **FPVCameraActivity** - Live camera feed and flight controls
5. **MediaManagerActivity** - Download/view/delete photos and videos from drone
6. **MediaViewerActivity** - Full-screen media viewer with ExoPlayer
7. **ProjectInfoActivity** - Create/edit project information
8. **ProjectWaypointActivity** - Configure waypoints for projects

### Core Services

#### CommandService_V5SDK
- Location: `app/src/main/java/io/empowerbits/sightflight/Services/CommandService_V5SDK.java`
- Manages waypoint missions using DJI Waypoint 3.0 SDK
- Generates KMZ files for waypoint missions using WPMZ SDK
- Broadcasts mission status via LocalBroadcastManager:
  - `ACTION_WAYPOINT_STATUS` with status types: UPLOADING, UPLOADED, MISSION_STARTED, HEADING_TO_WAYPOINT, MISSION_COMPLETED, RETURNING_HOME, ERROR
- Uses `WaypointMissionManager` and `WPMZManager` (from `com.dji.wpmzsdk`)

#### TelemetryService
- Location: `app/src/main/java/io/empowerbits/sightflight/Services/TelemetryService.java`
- Provides real-time telemetry data via LiveData/observers
- Flight data: altitude, speed, battery, GPS, etc.

#### ConnectionStateManager
- Location: `app/src/main/java/io/empowerbits/sightflight/Services/ConnectionStateManager.java`
- Singleton managing aircraft connection state
- Observable via LiveData

### ViewModels (Global Singleton Pattern)

The app uses **GlobalViewModelStore** (not standard Android ViewModelStore):

```java
GlobalViewModelStore.getGlobalViewModel(MSDKManagerVM.class)
GlobalViewModelStore.getGlobalViewModel(MSDKInfoVm.class)
```

- **MSDKManagerVM** - SDK lifecycle management
- **MSDKInfoVm** - Product type, connection state, registration status
- **DJIViewModel** - Generic DJI data holder

ViewModels persist across activity lifecycle and configuration changes.

### Models

Key data models in `app/src/main/java/io/empowerbits/sightflight/models/`:

- **Project** - Project metadata (name, location, flight settings)
- **FlightSetting** - Mission parameters (altitude, speed, gimbal pitch, etc.)
- **WaypointSetting** - Individual waypoint data (lat/lng, altitude, actions)
- **MissionSetting** - Global mission configuration
- **Obstacle** - Obstacle/boundary definitions for mission safety
- **MediaItem** - Drone media file metadata

### Backend Integration

**Retrofit API Service**: `app/src/main/java/io/empowerbits/sightflight/Retrofit/ApiService.java`

Key endpoints:
- `POST auth/login` - User authentication
- `POST projects` - Create new project
- `PUT projects/update/{projectId}` - Update project/waypoints/obstacles
- `GET projects/app` - Fetch all projects
- `POST flight/started/{projectId}` - Log flight start
- `POST flight/ended/{flight}` - Log flight end
- `POST projects/upload/{projectId}` - Upload media images

**Base URL configured in**: `ApiClient.java`

Authentication uses Bearer tokens stored in `UserSessionManager`.

## Waypoint Mission System

### Mission Creation Flow

1. User creates waypoints on Google Maps (WaypointActivity)
2. Waypoints stored as `WaypointSetting` objects with lat/lng/altitude
3. Flight settings configured (speed, altitude, gimbal pitch, etc.)
4. CommandService_V5SDK generates KMZ file using WPMZ SDK:
   - Creates `WaylineMission` with `WaylineWaypoint` list
   - Configures waypoint turn modes and yaw modes
   - Exports to KMZ format (DJI's waypoint file format)
5. KMZ uploaded to aircraft via `WaypointMissionManager.pushKMZFile()`
6. Mission started via `WaypointMissionManager.startMission()`

### Mission Monitoring

Listeners:
- `WaypointMissionExecuteStateListener` - Mission state (READY, EXECUTING, PAUSED, etc.)
- `WaylineExecutingInfoListener` - Current waypoint index, progress

Mission status broadcast to UI via LocalBroadcastManager.

### KMZ File Management

- KMZ files stored in: `{ExternalStorage}/DJI/io.empowerbits.sightflight/KMZCache/`
- Utility: `KmzCleaner.java` - Cleans up old KMZ files
- Utility: `KMZTestUtil.java` - KMZ generation helper

## Map Integration

**Google Maps SDK** used for waypoint planning:
- API Key in `AndroidManifest.xml`: `com.google.android.geo.API_KEY`
- Configured in `gradle.properties`: `GMAP_API_KEY`
- Map rendering in `WaypointActivity` and `ProjectWaypointActivity`

**DJI Map SDK** (MapLibre) support available but not currently primary.

## Configuration Files

### gradle.properties
Contains critical configuration:
- `AIRCRAFT_API_KEY` - DJI SDK registration key
- `GMAP_API_KEY` - Google Maps API key
- `ANDROID_MIN_SDK_VERSION`, `ANDROID_TARGET_SDK_VERSION`, `ANDROID_COMPILE_SDK_VERSION`
- Maven repository URLs

### dependencies.gradle
Centralized dependency versions:
- `deps.aircraft`, `deps.aircraftProvided`, `deps.networkImp` - DJI MSDK V5 (currently 5.16.0)
- `deps.wpmzSdk` - WPMZ SDK for KMZ generation (1.0.4.0)
- Google Play Services, RxJava, Retrofit, Glide, ExoPlayer

## UI/UX Patterns

### Orientation Change Handling
Activities use `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` in manifest.

MainActivity implements special handling in `onConfigurationChanged()`:
- Reloads layout (Android auto-picks portrait/landscape XML)
- Re-binds view references
- Does NOT re-register SDK observers (prevents duplicate listeners)

### Full-Screen/Edge-to-Edge Display
`EdgeToEdgeUtils.applyFullscreen()` called in activity lifecycle callbacks (onStart, onResume, onActivityPostResumed) to maintain immersive display.

### Fragment Usage
- **DroneSettingFragment** - Drone settings UI (battery, safety, calibration, AR settings)
- **CameraFeedFragment** - Embedded FPV camera feed
- **ProjectDialogFragment** - Project selection dialog

Fragments managed via `FragmentTransaction` and attached to activities dynamically.

## Permissions

Critical permissions (requested at runtime):
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - GPS for waypoint planning
- `MANAGE_EXTERNAL_STORAGE` (API 30+) - For KMZ file storage and media download
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (API 33+) - Media access
- `RECORD_AUDIO` - Required by DJI SDK for video recording

**PermissionHelper.java** utility handles permission requests.

## Testing & Debugging

### Device Requirements for Testing
- **Physical device with arm64-v8a architecture REQUIRED**
- Android 7.0+ (API 24+)
- USB debugging enabled
- DJI drone connected via USB or WiFi

### Logging
Extensive logging throughout codebase:
- SDK initialization: `DJIApplication`, `EagleEyeApplication`
- Connection state: `ConnectionStateManager`, `MainActivity`
- Mission execution: `CommandService_V5SDK`
- Use LogCat filter for: `DJIApplication`, `EagleEyeApp`, `CommandService_V5SDK`, `MainActivity`

### Common Issues

1. **Helper.install() crashes**: Device is not arm64-v8a (likely emulator)
2. **SDK not registering**: Check `AIRCRAFT_API_KEY` in gradle.properties
3. **Waypoint mission fails**: Ensure drone firmware supports Waypoint 3.0, check KMZ validation
4. **Native library conflicts**: Check `packagingOptions` in app/build.gradle

## Code Style Notes

- Java language (not Kotlin) for most of codebase
- ViewBinding enabled in activities
- Comprehensive null checks and try-catch for DJI SDK calls
- LocalBroadcastManager used for service-to-activity communication (deprecated but still in use)
- Gson for JSON serialization
- Retrofit for network calls

## Development Tips

- Always test on real arm64-v8a device (Xiaomi, Samsung, OnePlus, etc.)
- Check DJI developer docs for SDK changes: https://developer.dji.com/
- KMZ validation can be tested using DJI Pilot 2 app
- Media manager requires drone to be connected and on ground
- Waypoint missions require GPS lock and sufficient battery
