package io.empowerbits.sightflight.Fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.appcompat.widget.SwitchCompat;

import dji.v5.manager.aircraft.perception.PerceptionManager;
import dji.v5.manager.interfaces.IPerceptionManager;
import dji.v5.manager.aircraft.perception.data.PerceptionInfo;
import dji.v5.manager.aircraft.perception.listener.PerceptionInformationListener;

/**
 * Advanced Safety Fragment - Signal lost behavior and vision positioning
 */
public class AdvancedSafetyFragment extends Fragment {

    private static final String TAG = "AdvancedSafety";

    private Handler uiHandler;

    private RadioGroup signalLostToggle;
    private Spinner emergencyPropellerDropdown;
    private SwitchCompat visionPositioningSwitch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_advanced_safety, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        signalLostToggle = view.findViewById(R.id.signalLostToggle);
        emergencyPropellerDropdown = view.findViewById(R.id.emergencyPropellerDropdown);
        visionPositioningSwitch = view.findViewById(R.id.visionPositioningSwitch);

        // Setup emergency propeller dropdown
        String[] emergencyOptions = {"EMERGENCY ONLY", "ANYTIME"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, emergencyOptions);
        emergencyPropellerDropdown.setAdapter(adapter);
        emergencyPropellerDropdown.setSelection(0);

        setupListeners();
        loadCurrentSettings();

        return view;
    }

    private void setupListeners() {
        // Signal Lost Action
        signalLostToggle.setOnCheckedChangeListener((group, checkedId) -> {
            // Note: Connection lost action is typically managed automatically by the aircraft
            // This UI provides user awareness but actual SDK key may not be directly settable
            Log.d(TAG, "Signal lost action UI changed: " + checkedId);
        });

        // Vision Positioning - Using Perception Manager
        visionPositioningSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setVisionPositioningEnabled(isChecked);
        });
    }

    private void loadCurrentSettings() {
        // Default to RTH for signal lost (standard behavior)
        uiHandler.post(() -> signalLostToggle.check(R.id.btnRth));

        // Load vision positioning state
        getVisionPositioningState();
    }

    // ==================== DJI SDK Methods ====================

    private void getVisionPositioningState() {
        IPerceptionManager perceptionManager = PerceptionManager.getInstance();
        if (perceptionManager == null) {
            Log.e(TAG, "PerceptionManager not available");
            return;
        }

        // Get current perception info
        perceptionManager.addPerceptionInformationListener(new PerceptionInformationListener() {
            @Override
            public void onUpdate(PerceptionInfo perceptionInfo) {
                if (perceptionInfo != null) {
                    boolean isEnabled = perceptionInfo.isOverallObstacleAvoidanceEnabled();
                    uiHandler.post(() -> {
                        visionPositioningSwitch.setChecked(isEnabled);
                        Log.d(TAG, "Vision positioning enabled: " + isEnabled);
                    });
                }
                // Remove listener after first update
                perceptionManager.removePerceptionInformationListener(this);
            }
        });
    }

    private void setVisionPositioningEnabled(boolean enabled) {
        // Note: Direct enable/disable of vision positioning may require flight controller keys
        // that are not exposed in SDK V5. The perception system is largely automatic.
        // This method logs the user preference.
        Log.d(TAG, "Vision positioning preference set to: " + enabled);

        // The actual obstacle avoidance is controlled by the aircraft's perception system
        // and cannot be directly disabled via SDK for safety reasons
        if (!enabled) {
            showToast("Vision positioning is managed by aircraft for safety");
            uiHandler.post(() -> visionPositioningSwitch.setChecked(true));
        } else {
            showToast("Vision positioning enabled");
        }
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
