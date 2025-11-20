package io.empowerbits.sightflight.Services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.key.RemoteControllerKey;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.manager.KeyManager;
import io.empowerbits.sightflight.Activities.WaypointActivity;

/**
 * ButtonsListenerService monitors DJI Remote Controller button presses and joystick movements during waypoint missions.
 *
 * Features:
 * - Listens to all RC buttons (C1, C2, C3, Pause, GoHome, Shutter, Record)
 * - Monitors all joystick movements (Left/Right Stick Vertical/Horizontal)
 * - Automatically pauses waypoint mission when any button is pressed or joystick moved
 * - Uses dead zone threshold (50 out of 660) to prevent false triggers
 * - Broadcasts button press and joystick movement events to UI for user notification
 * - Auto-starts when mission begins, auto-stops when mission ends
 * - Resumes monitoring when mission is manually resumed
 *
 * Joystick Detection:
 * - Left Stick Vertical: Throttle (up/down) - Mode 2
 * - Left Stick Horizontal: Yaw (rotation) - Mode 2
 * - Right Stick Vertical: Pitch (forward/backward) - Mode 2
 * - Right Stick Horizontal: Roll (left/right) - Mode 2
 * - Pause rate limiting: 5 second cooldown after joystick movement detected
 *
 * Integration:
 * - Started by CommandService_V5SDK when mission begins
 * - Stopped by CommandService_V5SDK when mission completes/fails
 * - Communicates with WaypointActivity via LocalBroadcastManager
 *
 * @see CommandService_V5SDK
 * @see WaypointActivity
 */
public class ButtonsListenerService extends Service {
    private static final String TAG = "ButtonsListenerService";

    // Broadcast action constants
    public static final String ACTION_RC_BUTTON_PRESSED = "io.empowerbits.sightflight.RC_BUTTON_PRESSED";
    public static final String EXTRA_BUTTON_NAME = "button_name";

    // Button name constants
    private static final String BUTTON_C1 = "Custom Button C1";
    private static final String BUTTON_C2 = "Custom Button C2";
    private static final String BUTTON_C3 = "Custom Button C3";
    private static final String BUTTON_PAUSE = "Emergency Stop/Pause Button";
    private static final String BUTTON_GO_HOME = "Return-to-Home Button";
    private static final String BUTTON_SHUTTER = "Camera Shutter Button";
    private static final String BUTTON_RECORD = "Video Record Button";
    private static final String JOYSTICK_MOVED = "Joystick Movement Detected";

    private KeyManager keyManager;
    private Handler mainHandler;
    private LocalBroadcastManager localBroadcastManager;

    // Listener holder for managing all RC button listeners
    private final Object listenerHolder = new Object();

    // Flag to track if listeners are active
    private boolean listenersActive = false;

    // Joystick movement detection
    private static final int JOYSTICK_THRESHOLD = 50; // Dead zone threshold (out of 660 range)
    private boolean missionPausedByJoystick = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ButtonsListenerService created");

        keyManager = KeyManager.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ButtonsListenerService started - beginning RC button monitoring");

        if (!listenersActive) {
            registerAllButtonListeners();
            listenersActive = true;
        }

        return START_STICKY; // Service will be restarted if killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not a bound service
    }

    /**
     * Register listeners for all DJI Remote Controller buttons
     */
    private void registerAllButtonListeners() {
        Log.d(TAG, "Registering all RC button listeners");

        // Listen to Custom Button C1
        DJIKey<Boolean> c1Key = KeyTools.createKey(RemoteControllerKey.KeyCustomButton1Down, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            c1Key,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Custom Button C1 PRESSED");
                            handleButtonPress(BUTTON_C1);
                        }
                    });
                }
            }
        );

        // Listen to Custom Button C2
        DJIKey<Boolean> c2Key = KeyTools.createKey(RemoteControllerKey.KeyCustomButton2Down, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            c2Key,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Custom Button C2 PRESSED");
                            handleButtonPress(BUTTON_C2);
                        }
                    });
                }
            }
        );

        // Listen to Custom Button C3
        DJIKey<Boolean> c3Key = KeyTools.createKey(RemoteControllerKey.KeyCustomButton3Down, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            c3Key,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Custom Button C3 PRESSED");
                            handleButtonPress(BUTTON_C3);
                        }
                    });
                }
            }
        );

        // Listen to Emergency Stop (Pause) Button
        DJIKey<Boolean> pauseKey = KeyTools.createKey(RemoteControllerKey.KeyPauseButtonDown, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            pauseKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Emergency Stop/Pause button PRESSED");
                            handleButtonPress(BUTTON_PAUSE);
                        }
                    });
                }
            }
        );

        // Listen to Return Home (Go Home) Button
        DJIKey<Boolean> goHomeKey = KeyTools.createKey(RemoteControllerKey.KeyGoHomeButtonDown, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            goHomeKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Return-to-Home button PRESSED");
                            handleButtonPress(BUTTON_GO_HOME);
                        }
                    });
                }
            }
        );

        // Listen to Shutter (Photo/Camera) Button
        DJIKey<Boolean> shutterKey = KeyTools.createKey(RemoteControllerKey.KeyShutterButtonDown, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            shutterKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Camera Shutter button PRESSED");
                            handleButtonPress(BUTTON_SHUTTER);
                        }
                    });
                }
            }
        );

        // Listen to Record (Video Recording) Button
        DJIKey<Boolean> recordKey = KeyTools.createKey(RemoteControllerKey.KeyRecordButtonDown, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            recordKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Boolean>() {
                @Override
                public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                    mainHandler.post(() -> {
                        if (newValue != null && newValue) {
                            Log.d(TAG, "Video Record button PRESSED");
                            handleButtonPress(BUTTON_RECORD);
                        }
                    });
                }
            }
        );

        // Listen to Left Stick Vertical Movement (Throttle in Mode 2)
        DJIKey<Integer> leftStickVerticalKey = KeyTools.createKey(RemoteControllerKey.KeyStickLeftVertical, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            leftStickVerticalKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Integer>() {
                @Override
                public void onValueChange(@Nullable Integer oldValue, @Nullable Integer newValue) {
                    if (newValue != null && Math.abs(newValue) > JOYSTICK_THRESHOLD) {
                        mainHandler.post(() -> {
                            Log.d(TAG, "Left Stick Vertical movement detected: " + newValue);
                            handleJoystickMovement("Left Stick Vertical (Throttle)");
                        });
                    }
                }
            }
        );

        // Listen to Left Stick Horizontal Movement (Yaw in Mode 2)
        DJIKey<Integer> leftStickHorizontalKey = KeyTools.createKey(RemoteControllerKey.KeyStickLeftHorizontal, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            leftStickHorizontalKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Integer>() {
                @Override
                public void onValueChange(@Nullable Integer oldValue, @Nullable Integer newValue) {
                    if (newValue != null && Math.abs(newValue) > JOYSTICK_THRESHOLD) {
                        mainHandler.post(() -> {
                            Log.d(TAG, "Left Stick Horizontal movement detected: " + newValue);
                            handleJoystickMovement("Left Stick Horizontal (Yaw)");
                        });
                    }
                }
            }
        );

        // Listen to Right Stick Vertical Movement (Pitch in Mode 2)
        DJIKey<Integer> rightStickVerticalKey = KeyTools.createKey(RemoteControllerKey.KeyStickRightVertical, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            rightStickVerticalKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Integer>() {
                @Override
                public void onValueChange(@Nullable Integer oldValue, @Nullable Integer newValue) {
                    if (newValue != null && Math.abs(newValue) > JOYSTICK_THRESHOLD) {
                        mainHandler.post(() -> {
                            Log.d(TAG, "Right Stick Vertical movement detected: " + newValue);
                            handleJoystickMovement("Right Stick Vertical (Pitch)");
                        });
                    }
                }
            }
        );

        // Listen to Right Stick Horizontal Movement (Roll in Mode 2)
        DJIKey<Integer> rightStickHorizontalKey = KeyTools.createKey(RemoteControllerKey.KeyStickRightHorizontal, ComponentIndexType.LEFT_OR_MAIN);
        keyManager.listen(
            rightStickHorizontalKey,
            listenerHolder,
            new CommonCallbacks.KeyListener<Integer>() {
                @Override
                public void onValueChange(@Nullable Integer oldValue, @Nullable Integer newValue) {
                    if (newValue != null && Math.abs(newValue) > JOYSTICK_THRESHOLD) {
                        mainHandler.post(() -> {
                            Log.d(TAG, "Right Stick Horizontal movement detected: " + newValue);
                            handleJoystickMovement("Right Stick Horizontal (Roll)");
                        });
                    }
                }
            }
        );

        Log.d(TAG, "All RC button and joystick listeners registered successfully");
    }

    /**
     * Handle button press event:
     * 1. Broadcast button name to UI for user notification
     * 2. Pause the waypoint mission for safety
     *
     * @param buttonName The name of the button that was pressed
     */
    private void handleButtonPress(String buttonName) {
        Log.i(TAG, "RC Button Pressed: " + buttonName + " - Pausing mission for safety");

        // Broadcast button press event to UI
        Intent broadcastIntent = new Intent(ACTION_RC_BUTTON_PRESSED);
        broadcastIntent.putExtra(EXTRA_BUTTON_NAME, buttonName);
        localBroadcastManager.sendBroadcast(broadcastIntent);

        // Pause the waypoint mission via CommandService_V5SDK
        pauseWaypointMission();
    }

    /**
     * Handle joystick movement event:
     * 1. Pause the waypoint mission once (don't spam pause calls)
     * 2. Broadcast joystick movement to UI for user notification
     *
     * @param stickName The name of the stick that moved
     */
    private void handleJoystickMovement(String stickName) {
        // Only pause once when joystick is first moved
        if (!missionPausedByJoystick) {
            Log.i(TAG, "Joystick Movement Detected: " + stickName + " - Pausing mission for pilot control");

            // Broadcast joystick movement event to UI
            Intent broadcastIntent = new Intent(ACTION_RC_BUTTON_PRESSED);
            broadcastIntent.putExtra(EXTRA_BUTTON_NAME, JOYSTICK_MOVED + " (" + stickName + ")");
            localBroadcastManager.sendBroadcast(broadcastIntent);

            // Pause the waypoint mission
            pauseWaypointMission();

            // Set flag to prevent repeated pause calls
            missionPausedByJoystick = true;

            // Reset flag after 5 seconds to allow re-detection if mission is resumed
            mainHandler.postDelayed(() -> {
                missionPausedByJoystick = false;
                Log.d(TAG, "Joystick pause flag reset - ready to detect movement again");
            }, 5000);
        }
    }

    /**
     * Pause the waypoint mission by communicating with WaypointMissionManager
     * This gives the pilot control when any RC button is pressed or joystick moved
     */
    private void pauseWaypointMission() {
        try {
            // Access WaypointMissionManager and pause the mission
            dji.v5.manager.aircraft.waypoint3.WaypointMissionManager waypointMissionManager =
                dji.v5.manager.aircraft.waypoint3.WaypointMissionManager.getInstance();

            waypointMissionManager.pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "✅ Mission paused successfully after RC button press");
                }

                @Override
                public void onFailure(dji.v5.common.error.IDJIError error) {
                    Log.e(TAG, "❌ Failed to pause mission: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception while pausing mission: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel all RC button listeners to prevent memory leaks
     */
    private void cancelAllListeners() {
        if (listenersActive) {
            Log.d(TAG, "Cancelling all RC button listeners");
            KeyManager.getInstance().cancelListen(listenerHolder);
            listenersActive = false;
            Log.d(TAG, "All RC button listeners cancelled");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ButtonsListenerService destroyed - stopping RC button monitoring");

        // Clean up all listeners
        cancelAllListeners();
    }
}
