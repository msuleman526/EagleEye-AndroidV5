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
 * IMU Calibration Fragment - Full-screen dialog for IMU calibration
 * Note: IMU calibration takes 5-10 minutes
 */
public class IMUCalibrationFragment extends DialogFragment {

    private static final String TAG = "IMUCalibration";

    private Button startCalibrationBtn;
    private TextView calibrationStatus, calibrationProgress;
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
        View view = inflater.inflate(R.layout.fragment_imu_calibration, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        startCalibrationBtn = view.findViewById(R.id.startCalibrationBtn);
        calibrationStatus = view.findViewById(R.id.calibrationStatus);
        calibrationProgress = view.findViewById(R.id.calibrationProgress);

        startCalibrationBtn.setOnClickListener(v -> startImuCalibration());

        view.findViewById(R.id.closeBtn).setOnClickListener(v -> dismiss());

        return view;
    }

    private void startImuCalibration() {
        Log.d(TAG, "Starting IMU calibration");

        startCalibrationBtn.setEnabled(false);
        calibrationStatus.setVisibility(View.VISIBLE);
        calibrationStatus.setText("IMU calibration managed through DJI Pilot");
        calibrationStatus.setTextColor(getResources().getColor(R.color.uxsdk_yellow));
        calibrationProgress.setVisibility(View.VISIBLE);
        calibrationProgress.setText("Use DJI Pilot app for IMU calibration");

        // Note: IMU calibration is typically managed through DJI Pilot app
        // SDK V5 may not expose calibration action directly
        Log.d(TAG, "IMU calibration requested");
        showToast("Use DJI Pilot app for IMU calibration");

        uiHandler.postDelayed(() -> {
            startCalibrationBtn.setEnabled(true);
        }, 2000);
    }

    private void monitorIMUCalibration() {
        // Monitor calibration status using KeyIMUCalibrationInfo
        DJIKey<?> calibInfoKey = KeyTools.createKey(
                FlightControllerKey.KeyIMUCalibrationInfo,
                ComponentIndexType.LEFT_OR_MAIN
        );

        // Listen for calibration status updates
        KeyManager.getInstance().listen(calibInfoKey, this, (oldValue, newValue) -> {
            if (newValue != null) {
                Log.d(TAG, "IMU calibration info updated: " + newValue.toString());
                uiHandler.post(() -> {
                    calibrationProgress.setText("Calibration in progress...\n" + newValue.toString());
                });
            }
        });

        // Auto-stop monitoring after 15 minutes (timeout)
        uiHandler.postDelayed(() -> {
            KeyManager.getInstance().cancelListen(calibInfoKey, this);
            if (startCalibrationBtn != null) {
                startCalibrationBtn.setEnabled(true);
            }
            calibrationStatus.setText("Calibration process timeout");
            calibrationStatus.setTextColor(getResources().getColor(R.color.uxsdk_yellow));
        }, 15 * 60 * 1000); // 15 minutes
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
        DJIKey<?> calibInfoKey = KeyTools.createKey(
                FlightControllerKey.KeyIMUCalibrationInfo,
                ComponentIndexType.LEFT_OR_MAIN
        );
        KeyManager.getInstance().cancelListen(calibInfoKey, this);
    }
}
