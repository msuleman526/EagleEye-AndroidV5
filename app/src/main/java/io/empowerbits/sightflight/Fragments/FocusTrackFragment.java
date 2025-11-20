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
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

/**
 * FocusTrack Settings Fragment - Configure SmartTrack/POI parameters
 * Note: SmartTrack parameters in DJI SDK V5 are minimal. Most settings stored locally.
 */
public class FocusTrackFragment extends Fragment {

    private static final String TAG = "FocusTrackFragment";
    private static final String PREFS_NAME = "FocusTrackSettings";

    private RadioGroup subjectTypeToggle, cameraMotionToggle;
    private SeekBar radiusRangeSlider, innerHeightSlider, outerHeightSlider;
    private TextView innerRadiusValue, outerRadiusValue, innerHeightValue, outerHeightValue;
    private SwitchCompat nearGroundFlightSwitch;

    private SharedPreferences prefs;
    private Handler uiHandler;

    private double innerRadius = 5.0; // meters
    private double outerRadius = 10.0; // meters

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_focus_track, container, false);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        uiHandler = new Handler(Looper.getMainLooper());

        // Initialize views
        subjectTypeToggle = view.findViewById(R.id.subjectTypeToggle);
        cameraMotionToggle = view.findViewById(R.id.cameraMotionToggle);
        radiusRangeSlider = view.findViewById(R.id.radiusRangeSlider);
        innerHeightSlider = view.findViewById(R.id.innerHeightSlider);
        outerHeightSlider = view.findViewById(R.id.outerHeightSlider);

        innerRadiusValue = view.findViewById(R.id.innerRadiusValue);
        outerRadiusValue = view.findViewById(R.id.outerRadiusValue);
        innerHeightValue = view.findViewById(R.id.innerHeightValue);
        outerHeightValue = view.findViewById(R.id.outerHeightValue);

        nearGroundFlightSwitch = view.findViewById(R.id.nearGroundFlightSwitch);

        setupListeners();
        loadSettings();

        view.findViewById(R.id.resetFocusTrackBtn).setOnClickListener(v -> resetSettings());

        return view;
    }

    private void setupListeners() {
        // Subject type toggle
        subjectTypeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            String subject = (checkedId == R.id.btnPerson) ? "person" : "vehicle";
            prefs.edit().putString("subject_type", subject).apply();
            Log.d(TAG, "Subject type changed to: " + subject);
        });

        // Radius range slider (dual-thumb simulation with single SeekBar)
        radiusRangeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Map progress to inner and outer radius
                // Range: inner 2-15m, outer 5-30m
                innerRadius = 2.0 + (progress / 100.0 * 13.0);
                outerRadius = innerRadius + 5.0 + (progress / 100.0 * 20.0);

                innerRadiusValue.setText(String.format("%.0f m", innerRadius));
                outerRadiusValue.setText(String.format("%.0f m", outerRadius));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit()
                        .putFloat("inner_radius", (float) innerRadius)
                        .putFloat("outer_radius", (float) outerRadius)
                        .apply();
                Log.d(TAG, "Radius updated: inner=" + innerRadius + ", outer=" + outerRadius);
            }
        });

        // Inner height slider
        innerHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double height = 0.5 + (progress / 100.0 * 9.5); // Range: 0.5-10m
                innerHeightValue.setText(String.format("%.1f m", height));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double height = 0.5 + (seekBar.getProgress() / 100.0 * 9.5);
                prefs.edit().putFloat("inner_height", (float) height).apply();
                Log.d(TAG, "Inner height set to: " + height);
            }
        });

        // Outer height slider
        outerHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double height = 1.0 + (progress / 100.0 * 14.0); // Range: 1-15m
                outerHeightValue.setText(String.format("%.1f m", height));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double height = 1.0 + (seekBar.getProgress() / 100.0 * 14.0);
                prefs.edit().putFloat("outer_height", (float) height).apply();
                Log.d(TAG, "Outer height set to: " + height);
            }
        });

        // Camera motion toggle
        cameraMotionToggle.setOnCheckedChangeListener((group, checkedId) -> {
            String motion = (checkedId == R.id.btnNormal) ? "normal" : "fast";
            prefs.edit().putString("camera_motion", motion).apply();
            Log.d(TAG, "Camera motion changed to: " + motion);
        });

        // Near-ground flight switch
        nearGroundFlightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("near_ground_flight", isChecked).apply();
            Log.d(TAG, "Near-ground flight: " + isChecked);

            if (isChecked) {
                showToast("Near-ground flight enabled - Use with caution");
            }
        });
    }

    private void loadSettings() {
        // Load subject type
        String subjectType = prefs.getString("subject_type", "person");
        subjectTypeToggle.check(subjectType.equals("person") ? R.id.btnPerson : R.id.btnVehicle);

        // Load radius
        innerRadius = prefs.getFloat("inner_radius", 5.0f);
        outerRadius = prefs.getFloat("outer_radius", 10.0f);

        // Calculate progress from radius values
        int radiusProgress = (int) ((innerRadius - 2.0) / 13.0 * 100);
        radiusRangeSlider.setProgress(radiusProgress);

        // Load heights
        float innerHeight = prefs.getFloat("inner_height", 2.0f);
        float outerHeight = prefs.getFloat("outer_height", 5.0f);

        int innerHeightProgress = (int) ((innerHeight - 0.5) / 9.5 * 100);
        int outerHeightProgress = (int) ((outerHeight - 1.0) / 14.0 * 100);

        innerHeightSlider.setProgress(innerHeightProgress);
        outerHeightSlider.setProgress(outerHeightProgress);

        // Load camera motion
        String cameraMotion = prefs.getString("camera_motion", "normal");
        cameraMotionToggle.check(cameraMotion.equals("normal") ? R.id.btnNormal : R.id.btnFast);

        // Load near-ground flight
        boolean nearGroundFlight = prefs.getBoolean("near_ground_flight", false);
        nearGroundFlightSwitch.setChecked(nearGroundFlight);
    }

    private void resetSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("subject_type", "person");
        editor.putFloat("inner_radius", 5.0f);
        editor.putFloat("outer_radius", 10.0f);
        editor.putFloat("inner_height", 2.0f);
        editor.putFloat("outer_height", 5.0f);
        editor.putString("camera_motion", "normal");
        editor.putBoolean("near_ground_flight", false);
        editor.apply();

        loadSettings();
        showToast("FocusTrack settings reset to defaults");
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
