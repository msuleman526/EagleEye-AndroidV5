# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EagleEye Android V5 is a professional Java-based Android application for DJI SDK V5 integration. The app provides real-time drone connection management, telemetry data display, waypoint mission planning, and live camera feeds for DJI drones.

**Key Technologies:**
- Language: Java 8
- DJI SDK: V5.16.0 (Aircraft)
- Min SDK: 24 (Android 7.0)
- Target SDK: 34
- Architecture: arm64-v8a only (DJI SDK requirement)
- Build System: Gradle 8.5.2

## Build Commands

### Standard Build & Run
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug on connected device
./gradlew installDebug

# Clean build
./gradlew clean
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests on connected device
./gradlew connectedAndroidTest
```

## Critical Architecture Patterns

### 1. DJI SDK Initialization (CRITICAL)

**Two-stage initialization is required:**

1. **Application.attachBaseContext()** - `Helper.install(this)` MUST be called here
   - Located in: `EagleEyeApplication.java:30-75`
   - Uses `NativeCrashProtection` with multiple fallback strategies
   - This loads native DJI libraries before SDK initialization
   - Failure here breaks the entire SDK

2. **Application.onCreate()** - SDK registration via `DJIApplication` parent class
   - Located in: `Activities/DJIApplication.java`
   - Handles SDK registration with API key
   - Sets up product connection callbacks

**Key files:**
- `EagleEyeApplication.java` - Main application class extending `DJIApplication`
- `Activities/DJIApplication.java` - Base class with SDK initialization

### 2. Dependency Configuration (CRITICAL)

**Three DJI SDK dependencies are REQUIRED together:**
```gradle
compileOnly deps.aircraftProvided  // Compile-time interface
implementation deps.aircraft        // Runtime implementation
implementation deps.networkImp      // Network - CRITICAL, often forgotten!
```

Missing `networkImp` causes runtime crashes. See `app/build.gradle:102-105`.

### 3. ViewModel Architecture

**Global ViewModel Store Pattern:**
- `GlobalViewModelStore.java` - Singleton store for app-wide ViewModels
- Shared across activities to maintain state during lifecycle changes
- Usage: `getGlobalViewModelStore().get("key", ViewModelClass.class)`

**Key ViewModels:**
- `MSDKInfoVm` - SDK registration and connection state
- `MSDKManagerVM` - Product connection management
- `DJIViewModel` - General DJI data binding

### 4. Service Architecture

**TelemetryService (Singleton)**
- Real-time drone telemetry monitoring using DJI SDK V5 KeyManager
- Thread-safe with UI Handler for callbacks
- Monitors: Flight mode, GPS, battery, altitude, velocity, location
- Pattern: Listener interfaces + cached values for immediate access
- File: `Services/TelemetryService.java`

**CommandService_V5SDK (Bound Service)**
- Waypoint mission creation and execution
- Uses DJI's WPMZManager to generate KMZ mission files
- Implements mission state listeners and progress tracking
- Broadcasts status updates via LocalBroadcastManager
- File: `Services/CommandService_V5SDK.java`

**ConnectionStateManager**
- Manages SDK connection state across app
- Uses LiveData for reactive updates

### 5. KMZ Mission File Generation

**Critical flow for waypoint missions:**
1. Create `WaylineMission` using `KMZTestUtil.createWaylineMission()`
2. Create `WaylineMissionConfig` with global settings (finish action, lost action)
3. Create waypoint models with proper altitude via `setHeight()` and `setEllipsoidHeight()`
4. Generate Template with waypoint actions (photos, gimbal, etc.)
5. Call `WPMZManager.generateKMZFile()` to create KMZ
6. **Post-process with `KmzCleaner`** to remove corrupt XML fields from DJI SDK output
7. Upload via `WaypointMissionManager.pushKMZFileToAircraft()`

**Key utilities:**
- `KMZTestUtil.java` - Helper methods for mission creation (DJI patterns)
- `KmzCleaner.java` - Post-processes KMZ files to fix DJI SDK XML issues
- `util/wpml/WaypointInfoModel.java` - Waypoint data model

See `CommandService_V5SDK.java:271-332` for complete implementation.

### 6. Native Library Handling

**Packaging configuration in `app/build.gradle`:**
- `pickFirst` for duplicate shared libraries (libc++_shared.so, etc.)
- `doNotStrip` for all DJI native libraries to prevent optimization breaking them
- Only arm64-v8a ABI supported (line 20)

### 7. API Keys & Configuration

**Configured in `gradle.properties`:**
- `AIRCRAFT_API_KEY` - DJI SDK registration key
- `GMAP_API_KEY` - Google Maps (optional)
- `MAPLIBRE_TOKEN` - MapLibre (optional)

**Injected via manifest placeholders:**
```gradle
manifestPlaceholders["API_KEY"] = project.AIRCRAFT_API_KEY
```

## Common Workflows

### Adding New Telemetry Data

1. Add listener interface in `TelemetryService.java`
2. Create DJI Key using `KeyTools.createKey()`
3. Register listener with `KeyManager.getInstance().listen()`
4. Add cached value field and getter
5. Create `requestCurrent*()` method for initial value
6. Add setter for listener interface

### Creating Waypoint Missions

1. Define waypoints as `double[][]` (latitude, longitude pairs)
2. Create mission using `KMZTestUtil` helper methods
3. Add waypoint actions (photo, gimbal, hover, etc.)
4. Generate KMZ with `WPMZManager.generateKMZFile()`
5. Clean KMZ with `KmzCleaner.cleanKMZFile()`
6. Upload with `WaypointMissionManager.pushKMZFileToAircraft()`
7. Start with `WaypointMissionManager.startMission()`

### Handling Orientation Changes

Activities use `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` to handle config changes manually. Pattern in `MainActivity.java:64-70`:
- Override `onConfigurationChanged()`
- Call `setContentView()` to reload layout
- Re-bind view references
- Do NOT re-register observers/listeners

## Project Structure

```
app/src/main/java/com/suleman/eagleeye/
├── Activities/           # Activity classes
│   ├── DJIApplication.java          # Base app class with SDK init
│   ├── DJIMainActivity.java         # Base activity class
│   ├── MainActivity.java             # Main connection UI
│   ├── WaypointActivity.java        # Waypoint mission execution
│   ├── FPVCameraActivity.java       # Live camera feed
│   ├── AddProject/                  # Project creation flow
│   └── LoginActivity.java           # User authentication
├── Services/             # Background services
│   ├── TelemetryService.java        # Real-time telemetry
│   ├── CommandService_V5SDK.java    # Waypoint missions
│   └── ConnectionStateManager.java  # Connection state
├── models/              # Data models & ViewModels
│   ├── GlobalViewModelStore.java    # Global VM store
│   ├── MSDKInfoVm.java              # SDK state VM
│   ├── Project.java                 # Project data
│   ├── Flight.java                  # Flight records
│   └── MissionSetting.java          # Mission config
├── util/                # Utility classes
│   ├── KMZTestUtil.java             # Mission creation helpers
│   ├── KmzCleaner.java              # KMZ post-processing
│   ├── NativeCrashProtection.java   # SDK crash handling
│   ├── TelemetryDisplayManager.java # UI telemetry
│   └── wpml/WaypointInfoModel.java  # Waypoint data
├── Fragments/           # UI fragments
├── Adapters/            # RecyclerView adapters
├── Retrofit/            # Network layer
└── EagleEyeApplication.java  # Main application class
```

## Important Implementation Details

### DJI SDK V5 Key-Value System

DJI SDK V5 uses a Key-Value architecture instead of callbacks:
```java
// Create key
DJIKey<FlightMode> key = KeyTools.createKey(
    FlightControllerKey.KeyFlightMode,
    ComponentIndexType.LEFT_OR_MAIN
);

// Listen to changes
KeyManager.getInstance().listen(key, this, new CommonCallbacks.KeyListener<FlightMode>() {
    @Override
    public void onValueChange(FlightMode oldValue, FlightMode newValue) {
        // Handle change
    }
});

// Get current value
KeyManager.getInstance().getValue(key, callback);
```

### Thread Safety

- All DJI SDK callbacks run on background threads
- Use `Handler(Looper.getMainLooper())` to post UI updates
- TelemetryService caches values with `volatile` for thread-safe reads
- Services use `AtomicBoolean` for state flags

### Waypoint Mission Altitude

**Critical:** Use both `setHeight()` AND `setEllipsoidHeight()` when setting waypoint altitude:
```java
waypoint.setHeight((double) altitude);
waypoint.setEllipsoidHeight((double) altitude);
```
See `CommandService_V5SDK.java:393-395`.

### Simulator Mode

DJI SDK V5 includes simulator for testing without real drone:
```java
InitializationSettings settings = new InitializationSettings(
    new LocationCoordinate2D(latitude, longitude),
    satelliteCount
);
SimulatorManager.getInstance().enableSimulator(settings, callback);
```
See `CommandService_V5SDK.java:751-791`.

## Dependencies of Note

- **wpmzsdk** (1.0.4.0) - KMZ waypoint file generation
- **RxJava3** - Reactive programming (used by DJI SDK)
- **Glide** - Image loading
- **Retrofit2** + **Gson** - REST API communication
- **Google Maps** / **Play Services** - Map display
- **LocalBroadcastManager** - Service-Activity communication

## Testing Strategy

1. **Simulator Testing** - Use DJI simulator for initial testing
2. **Real Drone Testing** - Test with actual DJI drone via USB
3. **Architecture** - arm64-v8a devices only (emulators must support this)
4. **Permissions** - Test on Android 13+ for scoped storage and media permissions
