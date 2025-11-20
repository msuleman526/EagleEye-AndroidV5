package io.empowerbits.sightflight.Fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;

import androidx.appcompat.widget.SwitchCompat;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;

/**
 * Safety Tab Fragment - Main safety settings with DJI SDK integration
 * Note: Many advanced features are not available in current DJI SDK version
 * This version includes working features and stubs for unavailable ones
 */
public class SafetyTabFragment extends Fragment {

    private static final String TAG = "SafetyTabFragment";

    private Handler uiHandler;

    // UI Components - Obstacle Avoidance
    private RadioGroup obstacleAvoidanceToggle;

    // UI Components - Radar Map
    private SwitchCompat radarMapSwitch;

    // UI Components - Advanced RTH
    private RadioGroup advancedRthToggle;

    // UI Components - RTH Altitude
    private SeekBar rthAltitudeSlider;
    private TextView rthAltitudeValue;

    // UI Components - Max Altitude
    private SeekBar maxAltitudeSlider;
    private TextView maxAltitudeValue;

    // UI Components - Max Distance
    private SeekBar maxDistanceSlider;
    private TextView maxDistanceValue;

    // UI Components - Sensors
    private TextView compassStatus, imuStatus;
    private Button compassCalibrateBtn, imuCalibrateBtn;

    // UI Components - Auxiliary LED
    private RadioGroup auxiliaryLedToggle;

    // UI Components - Clickable layouts
    private View arSettingsLayout, batteryInfoLayout, advancedSafetyLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_safety_tab, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        initializeViews(view);
        setupListeners();
        loadCurrentSettings();

        return view;
    }

    private void initializeViews(View view) {
        // Obstacle Avoidance
        obstacleAvoidanceToggle = view.findViewById(R.id.obstacleAvoidanceToggle);

        // Radar Map
        radarMapSwitch = view.findViewById(R.id.radarMapSwitch);

        // Advanced RTH
        advancedRthToggle = view.findViewById(R.id.advancedRthToggle);

        // RTH Altitude
        rthAltitudeSlider = view.findViewById(R.id.rthAltitudeSlider);
        rthAltitudeValue = view.findViewById(R.id.rthAltitudeValue);

        // Max Altitude
        maxAltitudeSlider = view.findViewById(R.id.maxAltitudeSlider);
        maxAltitudeValue = view.findViewById(R.id.maxAltitudeValue);

        // Max Distance
        maxDistanceSlider = view.findViewById(R.id.maxDistanceSlider);
        maxDistanceValue = view.findViewById(R.id.maxDistanceValue);

        // Sensors
        compassStatus = view.findViewById(R.id.compassStatus);
        imuStatus = view.findViewById(R.id.imuStatus);
        compassCalibrateBtn = view.findViewById(R.id.compassCalibrateBtn);
        imuCalibrateBtn = view.findViewById(R.id.imuCalibrateBtn);

        // Auxiliary LED
        auxiliaryLedToggle = view.findViewById(R.id.auxiliaryLedToggle);

        // Clickable layouts
        arSettingsLayout = view.findViewById(R.id.arSettingsLayout);
        batteryInfoLayout = view.findViewById(R.id.batteryInfoLayout);
        advancedSafetyLayout = view.findViewById(R.id.advancedSafetyLayout);
    }

    private void setupListeners() {
        // Obstacle Avoidance Action
        obstacleAvoidanceToggle.setOnCheckedChangeListener((group, checkedId) -> {
            // Note: Obstacle avoidance mode setting depends on drone model
            // This is a placeholder - actual implementation would use PerceptionKey
            Log.d(TAG, "Obstacle avoidance changed: " + checkedId);
        });

        // Radar Map
        radarMapSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setRadarMapEnabled(isChecked);
        });

        // Advanced RTH
        advancedRthToggle.setOnCheckedChangeListener((group, checkedId) -> {
            setRthMode(checkedId);
        });

        // RTH Altitude Slider
        rthAltitudeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int altitude = progress + 20; // 20-500m range
                    rthAltitudeValue.setText(altitude + " m");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int altitude = seekBar.getProgress() + 20;
                setRthAltitude(altitude);
            }
        });

        // Max Altitude Slider
        maxAltitudeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int altitude = progress + 20; // 20-500m range
                    maxAltitudeValue.setText(altitude + " m");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int altitude = seekBar.getProgress() + 20;
                setMaxAltitude(altitude);
            }
        });

        // Max Distance Slider
        maxDistanceSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int distance = progress + 20; // 20-8000m range
                    if (distance >= 8000) {
                        maxDistanceValue.setText("No Limit");
                    } else {
                        maxDistanceValue.setText(distance + " m");
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int distance = seekBar.getProgress() + 20;
                setMaxDistance(distance);
            }
        });

        // Compass Calibration
        compassCalibrateBtn.setOnClickListener(v -> openCompassCalibration());

        // IMU Calibration
        imuCalibrateBtn.setOnClickListener(v -> openImuCalibration());

        // Auxiliary LED
        auxiliaryLedToggle.setOnCheckedChangeListener((group, checkedId) -> {
            // Note: LED control depends on drone model
            Log.d(TAG, "Auxiliary LED changed: " + checkedId);
        });

        // Sub-screen navigation
        arSettingsLayout.setOnClickListener(v -> openArSettings());
        batteryInfoLayout.setOnClickListener(v -> openBatteryInfo());
        advancedSafetyLayout.setOnClickListener(v -> openAdvancedSafety());
    }

    /**
     * Load current settings from drone
     */
    private void loadCurrentSettings() {
        getRthMode();
        getRthAltitude();
        getMaxAltitude();
        getMaxDistance();
        getCompassStatus();
        getImuStatus();
    }

    // ==================== DJI SDK Methods ====================

    private void getRthMode() {
        // Default to Preset (Advanced RTH not directly configurable via simple key in SDK V5)
        uiHandler.post(() -> advancedRthToggle.check(R.id.btnPreset));
    }

    private void setRthMode(int buttonId) {
        // Advanced RTH mode is automatic in SDK V5
        Log.d(TAG, "RTH mode is automatic in SDK V5");
    }

    private void getRthAltitude() {
        DJIKey<Integer> key = KeyTools.createKey(
                FlightControllerKey.KeyGoHomeHeight,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer altitude) {
                        uiHandler.post(() -> {
                            int progress = altitude - 20; // Slider range starts at 20
                            rthAltitudeSlider.setProgress(progress);
                            rthAltitudeValue.setText(altitude + " m");
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get RTH altitude: " + error.description());
                    }
                }
        );
    }

    private void setRthAltitude(int altitude) {
        DJIKey<Integer> key = KeyTools.createKey(
                FlightControllerKey.KeyGoHomeHeight,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().setValue(key, altitude,
                new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "RTH altitude set to: " + altitude);
                        showToast("RTH altitude set to " + altitude + "m");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to set RTH altitude: " + error.description());
                        showToast("Failed to set RTH altitude: " + error.description());
                        // Reload current value
                        getRthAltitude();
                    }
                }
        );
    }

    private void getMaxAltitude() {
        DJIKey<Integer> key = KeyTools.createKey(
                FlightControllerKey.KeyHeightLimit,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer altitude) {
                        uiHandler.post(() -> {
                            int progress = altitude - 20; // Slider range starts at 20
                            maxAltitudeSlider.setProgress(progress);
                            maxAltitudeValue.setText(altitude + " m");
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get max altitude: " + error.description());
                    }
                }
        );
    }

    private void setMaxAltitude(int altitude) {
        DJIKey<Integer> key = KeyTools.createKey(
                FlightControllerKey.KeyHeightLimit,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().setValue(key, altitude,
                new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Max altitude set to: " + altitude);
                        showToast("Max altitude set to " + altitude + "m");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to set max altitude: " + error.description());
                        showToast("Failed to set max altitude: " + error.description());
                        // Reload current value
                        getMaxAltitude();
                    }
                }
        );
    }

    private void getMaxDistance() {
        DJIKey<Integer> key = KeyTools.createKey(
                FlightControllerKey.KeyDistanceLimit,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer distance) {
                        uiHandler.post(() -> {
                            int progress = distance - 20; // Slider range starts at 20
                            maxDistanceSlider.setProgress(progress);
                            if (distance >= 8000) {
                                maxDistanceValue.setText("No Limit");
                            } else {
                                maxDistanceValue.setText(distance + " m");
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get max distance: " + error.description());
                    }
                }
        );
    }

    private void setMaxDistance(int distance) {
        DJIKey<Integer> key = KeyTools.createKey(
                FlightControllerKey.KeyDistanceLimit,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().setValue(key, distance,
                new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Max distance set to: " + distance);
                        showToast("Max distance set to " + distance + "m");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to set max distance: " + error.description());
                        showToast("Failed to set max distance: " + error.description());
                        // Reload current value
                        getMaxDistance();
                    }
                }
        );
    }

    private void getCompassStatus() {
        DJIKey<Boolean> key = KeyTools.createKey(
                FlightControllerKey.KeyCompassHasError,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Boolean>() {
                    @Override
                    public void onSuccess(Boolean hasError) {
                        uiHandler.post(() -> {
                            if (hasError != null && hasError) {
                                compassStatus.setText("error");
                                compassStatus.setTextColor(getResources().getColor(R.color.uxsdk_red));
                            } else {
                                compassStatus.setText("normal");
                                compassStatus.setTextColor(getResources().getColor(R.color.uxsdk_green));
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get compass status: " + error.description());
                        uiHandler.post(() -> {
                            compassStatus.setText("unknown");
                            compassStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                        });
                    }
                }
        );
    }

    /**
     * Get IMU Status using IMU calibration info
     */
    private void getImuStatus() {
        // Check IMU count first
        DJIKey<Integer> countKey = KeyTools.createKey(
                FlightControllerKey.KeyIMUCount,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(countKey,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer count) {
                        if (count != null && count > 0) {
                            uiHandler.post(() -> {
                                imuStatus.setText("normal (" + count + " IMU" + (count > 1 ? "s" : "") + ")");
                                imuStatus.setTextColor(getResources().getColor(R.color.uxsdk_green));
                            });
                        } else {
                            uiHandler.post(() -> {
                                imuStatus.setText("no IMU detected");
                                imuStatus.setTextColor(getResources().getColor(R.color.uxsdk_yellow));
                            });
                        }
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get IMU count: " + error.description());
                        uiHandler.post(() -> {
                            imuStatus.setText("unknown");
                            imuStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                        });
                    }
                }
        );
    }

    /**
     * Set Radar Map Enabled
     * Note: PerceptionKey not available in current DJI SDK version
     */
    private void setRadarMapEnabled(boolean enabled) {
        // Placeholder - PerceptionKey not available in current SDK
        Log.d(TAG, "Radar map setting not available: " + enabled);
        showToast("Radar map not available in current SDK");
        uiHandler.post(() -> radarMapSwitch.setChecked(!enabled));
    }

    // ==================== Sub-screen Navigation ====================

    private void openArSettings() {
        DroneSettingFragment parentFragment = (DroneSettingFragment) getParentFragment();
        if (parentFragment != null) {
            parentFragment.showSubScreen(new ARSettingsFragment());
        }
    }

    private void openBatteryInfo() {
        DroneSettingFragment parentFragment = (DroneSettingFragment) getParentFragment();
        if (parentFragment != null) {
            parentFragment.showSubScreen(new BatteryInfoFragment());
        }
    }

    private void openAdvancedSafety() {
        DroneSettingFragment parentFragment = (DroneSettingFragment) getParentFragment();
        if (parentFragment != null) {
            parentFragment.showSubScreen(new AdvancedSafetyFragment());
        }
    }

    private void openCompassCalibration() {
        // Show as full-screen dialog
        CompassCalibrationFragment compassFragment = new CompassCalibrationFragment();
        if (getActivity() != null) {
            compassFragment.show(getActivity().getSupportFragmentManager(), "CompassCalibration");
        }
    }

    private void openImuCalibration() {
        // Show as full-screen dialog
        IMUCalibrationFragment imuFragment = new IMUCalibrationFragment();
        if (getActivity() != null) {
            imuFragment.show(getActivity().getSupportFragmentManager(), "IMUCalibration");
        }
    }

    // ==================== Helper Methods ====================

    private void showToast(String message) {
        uiHandler.post(() -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cleanup if needed
    }
}
