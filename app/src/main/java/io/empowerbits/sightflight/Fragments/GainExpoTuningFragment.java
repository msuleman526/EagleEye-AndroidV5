package io.empowerbits.sightflight.Fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.GimbalKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;

/**
 * Gain & Expo Tuning Fragment - Fine-tune control sensitivity and gimbal settings
 */
public class GainExpoTuningFragment extends Fragment {

    private static final String TAG = "GainExpoTuning";
    private static final String PREFS_NAME = "GainExpoSettings";

    private RadioGroup flightModeToggle;
    private SeekBar upDownExpoSlider, yawExpoSlider, pitchRollExpoSlider;
    private SeekBar gimbalMaxSpeedSlider, gimbalSmoothnessSlider;
    private SeekBar maxHorizontalSpeedSlider, maxAscentSpeedSlider, maxDescentSpeedSlider;
    private SeekBar maxAngularVelocitySlider, yawSmoothnessSlider, brakeSensitivitySlider;

    private TextView upDownExpoValue, yawExpoValue, pitchRollExpoValue;
    private TextView gimbalMaxSpeedValue, gimbalSmoothnessValue;
    private TextView maxHorizontalSpeedValue, maxAscentSpeedValue, maxDescentSpeedValue;
    private TextView maxAngularVelocityValue, yawSmoothnessValue, brakeSensitivityValue;

    private SharedPreferences prefs;
    private Handler uiHandler;
    private String currentMode = "Normal";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gain_expo_tuning, container, false);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        uiHandler = new Handler(Looper.getMainLooper());

        // Initialize views
        flightModeToggle = view.findViewById(R.id.flightModeToggle);

        upDownExpoSlider = view.findViewById(R.id.upDownExpoSlider);
        yawExpoSlider = view.findViewById(R.id.yawExpoSlider);
        pitchRollExpoSlider = view.findViewById(R.id.pitchRollExpoSlider);

        gimbalMaxSpeedSlider = view.findViewById(R.id.gimbalMaxSpeedSlider);
        gimbalSmoothnessSlider = view.findViewById(R.id.gimbalSmoothnessSlider);

        maxHorizontalSpeedSlider = view.findViewById(R.id.maxHorizontalSpeedSlider);
        maxAscentSpeedSlider = view.findViewById(R.id.maxAscentSpeedSlider);
        maxDescentSpeedSlider = view.findViewById(R.id.maxDescentSpeedSlider);
        maxAngularVelocitySlider = view.findViewById(R.id.maxAngularVelocitySlider);
        yawSmoothnessSlider = view.findViewById(R.id.yawSmoothnessSlider);
        brakeSensitivitySlider = view.findViewById(R.id.brakeSensitivitySlider);

        upDownExpoValue = view.findViewById(R.id.upDownExpoValue);
        yawExpoValue = view.findViewById(R.id.yawExpoValue);
        pitchRollExpoValue = view.findViewById(R.id.pitchRollExpoValue);

        gimbalMaxSpeedValue = view.findViewById(R.id.gimbalMaxSpeedValue);
        gimbalSmoothnessValue = view.findViewById(R.id.gimbalSmoothnessValue);

        maxHorizontalSpeedValue = view.findViewById(R.id.maxHorizontalSpeedValue);
        maxAscentSpeedValue = view.findViewById(R.id.maxAscentSpeedValue);
        maxDescentSpeedValue = view.findViewById(R.id.maxDescentSpeedValue);
        maxAngularVelocityValue = view.findViewById(R.id.maxAngularVelocityValue);
        yawSmoothnessValue = view.findViewById(R.id.yawSmoothnessValue);
        brakeSensitivityValue = view.findViewById(R.id.brakeSensitivityValue);

        setupListeners();
        loadSettings();

        view.findViewById(R.id.resetSettingsBtn).setOnClickListener(v -> resetSettings());

        return view;
    }

    private void setupListeners() {
        // Flight mode toggle
        flightModeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.btnCine) currentMode = "Cine";
            else if (checkedId == R.id.btnNormal) currentMode = "Normal";
            else if (checkedId == R.id.btnSport) currentMode = "Sport";
            loadSettings();
        });

        // Expo sliders (stored locally - not in SDK)
        upDownExpoSlider.setOnSeekBarChangeListener(createExpoListener(upDownExpoValue, "up_down_expo"));
        yawExpoSlider.setOnSeekBarChangeListener(createExpoListener(yawExpoValue, "yaw_expo"));
        pitchRollExpoSlider.setOnSeekBarChangeListener(createExpoListener(pitchRollExpoValue, "pitch_roll_expo"));

        // Gimbal sliders (SDK-connected)
        gimbalMaxSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 1; // Range: 1-100
                gimbalMaxSpeedValue.setText(value + "°/s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setGimbalMaxSpeed(seekBar.getProgress() + 1);
            }
        });

        gimbalSmoothnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gimbalSmoothnessValue.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setGimbalSmoothness(seekBar.getProgress());
            }
        });

        // Aircraft speed sliders (local storage - represent virtual stick ranges)
        maxHorizontalSpeedSlider.setOnSeekBarChangeListener(createSpeedListener(maxHorizontalSpeedValue, "max_horizontal_speed", 1.0, 23.0, "m/s"));
        maxAscentSpeedSlider.setOnSeekBarChangeListener(createSpeedListener(maxAscentSpeedValue, "max_ascent_speed", 1.0, 6.0, "m/s"));
        maxDescentSpeedSlider.setOnSeekBarChangeListener(createSpeedListener(maxDescentSpeedValue, "max_descent_speed", 1.0, 6.0, "m/s"));
        maxAngularVelocitySlider.setOnSeekBarChangeListener(createSpeedListener(maxAngularVelocityValue, "max_angular_velocity", 20, 100, "°/s"));
        yawSmoothnessSlider.setOnSeekBarChangeListener(createSpeedListener(yawSmoothnessValue, "yaw_smoothness", 1, 100, ""));
        brakeSensitivitySlider.setOnSeekBarChangeListener(createSpeedListener(brakeSensitivityValue, "brake_sensitivity", 10, 150, ""));
    }

    private SeekBar.OnSeekBarChangeListener createExpoListener(TextView valueView, String prefKey) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double value = 0.1 + (progress / 100.0 * 0.8); // Range: 0.1 to 0.9
                valueView.setText(String.format("%.2f", value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double value = 0.1 + (seekBar.getProgress() / 100.0 * 0.8);
                saveLocalSetting(prefKey + "_" + currentMode, (float) value);
            }
        };
    }

    private SeekBar.OnSeekBarChangeListener createSpeedListener(TextView valueView, String prefKey, double min, double max, String unit) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double value = min + (progress / (double) seekBar.getMax() * (max - min));
                if (unit.isEmpty()) {
                    valueView.setText(String.valueOf((int) value));
                } else {
                    valueView.setText(String.format("%.1f%s", value, unit));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double value = min + (seekBar.getProgress() / (double) seekBar.getMax() * (max - min));
                saveLocalSetting(prefKey + "_" + currentMode, (float) value);
            }
        };
    }

    private void loadSettings() {
        // Load expo settings (local)
        float upDownExpo = prefs.getFloat("up_down_expo_" + currentMode, 0.3f);
        float yawExpo = prefs.getFloat("yaw_expo_" + currentMode, 0.2f);
        float pitchRollExpo = prefs.getFloat("pitch_roll_expo_" + currentMode, 0.25f);

        upDownExpoSlider.setProgress((int) ((upDownExpo - 0.1) / 0.8 * 100));
        yawExpoSlider.setProgress((int) ((yawExpo - 0.1) / 0.8 * 100));
        pitchRollExpoSlider.setProgress((int) ((pitchRollExpo - 0.1) / 0.8 * 100));

        // Load gimbal settings from SDK
        getGimbalMaxSpeed();
        getGimbalSmoothness();

        // Load aircraft speed settings (local)
        float maxHSpeed = prefs.getFloat("max_horizontal_speed_" + currentMode, 12.0f);
        float maxASpeed = prefs.getFloat("max_ascent_speed_" + currentMode, 5.0f);
        float maxDSpeed = prefs.getFloat("max_descent_speed_" + currentMode, 5.0f);
        float maxAngVel = prefs.getFloat("max_angular_velocity_" + currentMode, 80.0f);
        float yawSmooth = prefs.getFloat("yaw_smoothness_" + currentMode, 12.0f);
        float brakeSens = prefs.getFloat("brake_sensitivity_" + currentMode, 100.0f);

        maxHorizontalSpeedSlider.setProgress((int) ((maxHSpeed - 1.0) / 22.0 * 220));
        maxAscentSpeedSlider.setProgress((int) ((maxASpeed - 1.0) / 5.0 * 50));
        maxDescentSpeedSlider.setProgress((int) ((maxDSpeed - 1.0) / 5.0 * 50));
        maxAngularVelocitySlider.setProgress((int) ((maxAngVel - 20) / 80.0 * 80));
        yawSmoothnessSlider.setProgress((int) (yawSmooth - 1));
        brakeSensitivitySlider.setProgress((int) (brakeSens - 10));
    }

    // ==================== DJI SDK Methods ====================

    private void getGimbalMaxSpeed() {
        DJIKey<Integer> key = KeyTools.createKey(
                GimbalKey.KeyPitchControlMaxSpeed,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
            @Override
            public void onSuccess(Integer speed) {
                uiHandler.post(() -> {
                    if (speed != null) {
                        gimbalMaxSpeedSlider.setProgress(speed - 1);
                        gimbalMaxSpeedValue.setText(speed + "°/s");
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get gimbal max speed: " + error.description());
            }
        });
    }

    private void setGimbalMaxSpeed(int speed) {
        DJIKey<Integer> key = KeyTools.createKey(
                GimbalKey.KeyPitchControlMaxSpeed,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().setValue(key, speed, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Gimbal max speed set to: " + speed);
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to set gimbal max speed: " + error.description());
                getGimbalMaxSpeed(); // Reload current value
            }
        });
    }

    private void getGimbalSmoothness() {
        DJIKey<Integer> key = KeyTools.createKey(
                GimbalKey.KeyPitchSmoothingFactor,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
            @Override
            public void onSuccess(Integer smoothness) {
                uiHandler.post(() -> {
                    if (smoothness != null) {
                        gimbalSmoothnessSlider.setProgress(smoothness);
                        gimbalSmoothnessValue.setText(String.valueOf(smoothness));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get gimbal smoothness: " + error.description());
            }
        });
    }

    private void setGimbalSmoothness(int smoothness) {
        DJIKey<Integer> key = KeyTools.createKey(
                GimbalKey.KeyPitchSmoothingFactor,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().setValue(key, smoothness, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Gimbal smoothness set to: " + smoothness);
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to set gimbal smoothness: " + error.description());
                getGimbalSmoothness(); // Reload current value
            }
        });
    }

    private void saveLocalSetting(String key, float value) {
        prefs.edit().putFloat(key, value).apply();
        Log.d(TAG, "Saved " + key + " = " + value);
    }

    private void resetSettings() {
        // Reset to defaults
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("up_down_expo_" + currentMode, 0.3f);
        editor.putFloat("yaw_expo_" + currentMode, 0.2f);
        editor.putFloat("pitch_roll_expo_" + currentMode, 0.25f);
        editor.putFloat("max_horizontal_speed_" + currentMode, 12.0f);
        editor.putFloat("max_ascent_speed_" + currentMode, 5.0f);
        editor.putFloat("max_descent_speed_" + currentMode, 5.0f);
        editor.putFloat("max_angular_velocity_" + currentMode, 80.0f);
        editor.putFloat("yaw_smoothness_" + currentMode, 12.0f);
        editor.putFloat("brake_sensitivity_" + currentMode, 100.0f);
        editor.apply();

        // Reset gimbal to defaults
        setGimbalMaxSpeed(15);
        setGimbalSmoothness(8);

        loadSettings();
        showToast("Settings reset to defaults");
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
