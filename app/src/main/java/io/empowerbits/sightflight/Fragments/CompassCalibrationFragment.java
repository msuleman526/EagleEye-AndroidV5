package io.empowerbits.sightflight.Fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.empowerbits.sightflight.R;
import android.widget.Button;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.manager.KeyManager;

/**
 * Compass Calibration Fragment - Full-screen dialog for compass calibration
 */
public class CompassCalibrationFragment extends DialogFragment {

    private static final String TAG = "CompassCalibration";

    private Button startCalibrationBtn;
    private TextView calibrationInstructions, calibrationStatus;
    private Handler uiHandler;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make dialog full-screen
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_compass_calibration, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        startCalibrationBtn = view.findViewById(R.id.startCalibrationBtn);
        calibrationInstructions = view.findViewById(R.id.calibrationInstructions);
        calibrationStatus = view.findViewById(R.id.calibrationStatus);

        startCalibrationBtn.setOnClickListener(v -> startCompassCalibration());

        view.findViewById(R.id.closeBtn).setOnClickListener(v -> dismiss());

        return view;
    }

    private void startCompassCalibration() {
        Log.d(TAG, "Starting compass calibration");

        startCalibrationBtn.setEnabled(false);
        calibrationStatus.setVisibility(View.VISIBLE);
        calibrationStatus.setText("Compass calibration managed through DJI Pilot");
        calibrationStatus.setTextColor(getResources().getColor(R.color.uxsdk_yellow));

        // Note: Compass calibration is typically initiated through DJI Pilot app
        // SDK V5 may not expose calibration start action directly
        Log.d(TAG, "Compass calibration requested");
        showToast("Use DJI Pilot app for compass calibration");

        uiHandler.postDelayed(() -> {
            startCalibrationBtn.setEnabled(true);
        }, 2000);
    }

    private void monitorCompassCalibration() {
        // Monitor calibration status using KeyIsCompassCalibrating
        DJIKey<Boolean> isCalibKey = KeyTools.createKey(
                FlightControllerKey.KeyIsCompassCalibrating,
                ComponentIndexType.LEFT_OR_MAIN
        );

        // Also monitor KeyCompassCalibrationStatus for detailed status
        DJIKey<?> statusKey = KeyTools.createKey(
                FlightControllerKey.KeyCompassCalibrationStatus,
                ComponentIndexType.LEFT_OR_MAIN
        );

        // Listen for calibration status updates
        KeyManager.getInstance().listen(isCalibKey, this, (oldValue, newValue) -> {
            if (newValue != null) {
                uiHandler.post(() -> {
                    if (newValue) {
                        calibrationStatus.setText("Calibrating...");
                        calibrationStatus.setTextColor(getResources().getColor(R.color.uxsdk_green));
                    } else {
                        calibrationStatus.setText("Calibration completed");
                        calibrationStatus.setTextColor(getResources().getColor(R.color.uxsdk_green));
                        calibrationInstructions.setText("Compass calibration finished successfully!");
                        startCalibrationBtn.setEnabled(true);

                        // Stop listening
                        KeyManager.getInstance().cancelListen(isCalibKey, this);
                        KeyManager.getInstance().cancelListen(statusKey, this);
                    }
                });
            }
        });

        // Listen for detailed status updates
        KeyManager.getInstance().listen(statusKey, this, (oldValue, newValue) -> {
            if (newValue != null) {
                Log.d(TAG, "Compass calibration status: " + newValue.toString());
                uiHandler.post(() -> {
                    calibrationInstructions.setText("Status: " + newValue.toString());
                });
            }
        });

        // Timeout after 5 minutes
        uiHandler.postDelayed(() -> {
            KeyManager.getInstance().cancelListen(isCalibKey, this);
            KeyManager.getInstance().cancelListen(statusKey, this);
            if (startCalibrationBtn != null) {
                startCalibrationBtn.setEnabled(true);
            }
        }, 5 * 60 * 1000); // 5 minutes
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel all listeners
        DJIKey<Boolean> isCalibKey = KeyTools.createKey(
                FlightControllerKey.KeyIsCompassCalibrating,
                ComponentIndexType.LEFT_OR_MAIN
        );
        DJIKey<?> statusKey = KeyTools.createKey(
                FlightControllerKey.KeyCompassCalibrationStatus,
                ComponentIndexType.LEFT_OR_MAIN
        );
        KeyManager.getInstance().cancelListen(isCalibKey, this);
        KeyManager.getInstance().cancelListen(statusKey, this);
    }
}
