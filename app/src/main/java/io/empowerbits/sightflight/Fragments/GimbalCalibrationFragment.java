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
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.empowerbits.sightflight.R;

/**
 * Gimbal Calibration Fragment - Full-screen dialog for gimbal calibration
 */
public class GimbalCalibrationFragment extends DialogFragment {

    private static final String TAG = "GimbalCalibration";

    private RadioGroup calibrationModeToggle;
    private Button startCalibrationBtn;
    private TextView calibrationStatus;
    private Handler uiHandler;
    private boolean isAutoMode = true;

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
        View view = inflater.inflate(R.layout.fragment_gimbal_calibration, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        calibrationModeToggle = view.findViewById(R.id.calibrationModeToggle);
        startCalibrationBtn = view.findViewById(R.id.startCalibrationBtn);
        calibrationStatus = view.findViewById(R.id.calibrationStatus);

        calibrationModeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            isAutoMode = (checkedId == R.id.btnAuto);
            Log.d(TAG, "Calibration mode: " + (isAutoMode ? "Auto" : "Manual"));
        });

        startCalibrationBtn.setOnClickListener(v -> startGimbalCalibration());

        view.findViewById(R.id.closeBtn).setOnClickListener(v -> dismiss());

        return view;
    }

    private void startGimbalCalibration() {
        Log.d(TAG, "Starting gimbal calibration (Auto mode: " + isAutoMode + ")");

        startCalibrationBtn.setEnabled(false);
        calibrationStatus.setVisibility(View.VISIBLE);
        calibrationStatus.setText("Gimbal calibration managed through DJI Pilot");
        calibrationStatus.setTextColor(getResources().getColor(R.color.uxsdk_yellow));

        // Note: Gimbal calibration is typically managed through DJI Pilot app
        // SDK V5 may not expose calibration action directly
        Log.d(TAG, "Gimbal calibration requested");
        showToast("Use DJI Pilot app for gimbal calibration");

        uiHandler.postDelayed(() -> {
            startCalibrationBtn.setEnabled(true);
        }, 2000);
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
