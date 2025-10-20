# EagleEye Android - DJI SDK V5 Integration

A professional Android application built in **Java** for DJI SDK V5 integration, featuring real-time drone connection management and telemetry data display.

## 🚁 Features

- **Real-time Connection Monitoring**: Live status updates for drone connection and SDK registration
- **USB Connection Support**: Automatic detection and handling of USB-connected DJI drones
- **Telemetry Data Display**: Real-time display of drone telemetry (expandable framework)
- **Professional UI**: Clean, card-based interface with color-coded status indicators
- **Thread-safe Architecture**: Singleton service pattern with LiveData for UI updates
- **Comprehensive Permissions**: Automatic handling of all required DJI SDK permissions

## 📋 Requirements

- **Android Studio**: Latest version
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **DJI SDK**: V5.14.0
- **Architecture**: arm64-v8a (required by DJI SDK)
- **DJI Drone**: Any DJI drone compatible with SDK V5

## 🛠️ Project Structure

```
app/src/main/java/com/suleman/eagleeye/
├── EagleEyeApplication.java          # Application class with SDK initialization
├── MainActivity.java                 # Main UI with real-time status display
├── UsbAttachActivity.java            # USB connection handler
└── services/
    └── DroneConnectionService.java   # Singleton service for drone management
```

## 🔧 Setup Instructions

### 1. Clone and Open Project
Open the project in Android Studio from:
```
D:\Sulemans-WorkSpace\Android-Apps\EagleEye-Android
```

### 2. API Key Configuration
Your DJI API key is already configured in `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.dji.sdk.API_KEY"
    android:value="576dfaef2c28f1ff3923f73b" />
```

### 3. Build and Install
1. Connect your Android device
2. Enable Developer Options and USB Debugging
3. Build and install the app
4. Grant all requested permissions

### 4. Connect DJI Drone
1. Connect your DJI drone via USB cable
2. The app will automatically detect the connection
3. Monitor real-time status in the app interface

## 📱 User Interface

The main interface displays five key sections:

### Connection Status Card
- **Connection Status**: Connected/Disconnected with color indicators
- **Registration Status**: SDK registration state
- **Message**: Current status message

### Product Information Card
- **Product Name**: Detected drone model and ID

### Initialization Progress Card
- **Progress**: Real-time SDK initialization status

### Real-Time Telemetry Card
- **Telemetry Data**: Live drone data (altitude, speed, battery, GPS)

## 🔧 Key Classes and Methods

### DroneConnectionService
The core service managing all DJI SDK operations:

```java
// Get singleton instance
DroneConnectionService service = DroneConnectionService.getInstance();

// Initialize SDK
service.initializeSDK(context);

// Add connection listener
service.addConnectionStatusListener(listener);

// Access LiveData for UI binding
service.getConnectionState().observe(this, observer);
```

### MainActivity
Main UI controller with LiveData observers:

```java
// Observe connection state
droneService.getConnectionState().observe(this, isConnected -> {
    // Update UI based on connection status
});

// Observe telemetry data
droneService.getTelemetryData().observe(this, telemetryData -> {
    // Update telemetry display
});
```

## 📡 Expanding Telemetry Data

Currently, the app simulates telemetry data. To add real DJI telemetry:

### 1. Add Key Imports
```java
import dji.v5.manager.aircraft.FlightControllerManager;
import dji.v5.manager.aircraft.battery.BatteryManager;
// Add other required managers
```

### 2. Subscribe to Real Data
```java
// In DroneConnectionService.startTelemetryUpdates()
private void startTelemetryUpdates() {
    // Subscribe to altitude
    FlightControllerManager.getInstance().getAltitudeKey().listen(altitude -> {
        // Update altitude data
    });
    
    // Subscribe to battery percentage
    BatteryManager.getInstance().getBatteryPercentageKey().listen(battery -> {
        // Update battery data
    });
    
    // Add more subscriptions as needed
}
```

## 🔐 Permissions

The app automatically requests these permissions:
- **RECORD_AUDIO**: For drone audio features
- **ACCESS_FINE_LOCATION**: For GPS functionality
- **ACCESS_COARSE_LOCATION**: For location services
- **READ_MEDIA_***: For media access (Android 13+)
- **READ/WRITE_EXTERNAL_STORAGE**: For legacy storage access

## 🐛 Troubleshooting

### SDK Registration Failed
- Check internet connection
- Verify API key is correct
- Ensure device time/date is accurate

### USB Connection Not Detected
- Check USB cable and connection
- Enable USB debugging
- Verify drone is powered on
- Check USB accessory permissions

### App Crashes on Startup
- Ensure all permissions are granted
- Check if DJI SDK dependencies are properly included
- Verify minimum SDK version (24)

## 🚀 Testing Workflow

### 1. Simulator Testing
- Use DJI SDK simulator for initial testing
- Test connection callbacks without physical drone

### 2. Real Drone Testing
- Connect actual DJI drone via USB
- Test all connection states (connect/disconnect)
- Verify telemetry data accuracy
- Test permission flows

### 3. Production Deployment
- Test on multiple Android versions
- Verify performance with extended usage
- Test network connectivity scenarios

## 📝 Architecture Notes

### Thread Safety
- All DJI SDK callbacks are handled on background threads
- UI updates are posted to main thread via `Handler`
- LiveData ensures thread-safe UI communication

### Memory Management
- Singleton pattern prevents memory leaks
- Proper cleanup in service lifecycle
- Listeners are properly removed in `onDestroy()`

### Error Handling
- Comprehensive error logging
- User-friendly error messages
- Graceful fallback for connection failures

## 🔄 Future Enhancements

1. **Flight Control**: Add basic flight control capabilities
2. **Camera Control**: Integrate camera operation features
3. **Mission Planning**: Add waypoint mission functionality
4. **Data Logging**: Implement flight data recording
5. **Real-time Video**: Add live video stream display

## 📞 Support

For issues related to:
- **DJI SDK**: Check [DJI Developer Documentation](https://developer.dji.com/cn/api-reference-v5/android-api/)
- **Android Development**: Refer to Android documentation
- **This Implementation**: Check the AI log file for development decisions

## 📄 License

Copyright (c) 2025, EagleEye All Rights Reserved.

---

**Ready for real drone testing!** 🚁

Connect your DJI drone and start monitoring real-time connection status and telemetry data.
