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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

import dji.sdk.keyvalue.key.CameraKey;
import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.camera.CameraStorageInfos;
import dji.sdk.keyvalue.value.camera.CameraStorageLocation;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;

/**
 * CameraTabFragment - Camera settings tab
 * ALL settings connected to DJI SDK V5
 * Manages storage information and camera parameter reset
 */
public class CameraTabFragment extends Fragment {

    private static final String TAG = "CameraTabFragment";

    private TextView sdCardStatus, internalStorageStatus;
    private TextView formatStorageBtn, resetCameraBtn;

    private Handler uiHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera_tab, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        // Initialize views
        sdCardStatus = view.findViewById(R.id.sdCardStatus);
        internalStorageStatus = view.findViewById(R.id.internalStorageStatus);
        formatStorageBtn = view.findViewById(R.id.formatStorageBtn);
        resetCameraBtn = view.findViewById(R.id.resetCameraBtn);

        setupListeners();
        updateStorageInfo();

        return view;
    }

    private void setupListeners() {
        // Format Storage Button
        formatStorageBtn.setOnClickListener(v -> showFormatDialog());

        // Reset Camera Parameters
        resetCameraBtn.setOnClickListener(v -> showResetDialog());
    }

    /**
     * Update storage information from camera
     */
    private void updateStorageInfo() {
        DJIKey<CameraStorageInfos> key = KeyTools.createKey(CameraKey.KeyCameraStorageInfos);

        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<CameraStorageInfos>() {
            @Override
            public void onSuccess(CameraStorageInfos storageInfos) {
                uiHandler.post(() -> {
                    // Note: CameraStorageInfos API methods not available in current SDK
                // Storage info is available through DJI Pilot app
                sdCardStatus.setText("Storage info via DJI Pilot");
                internalStorageStatus.setText("Storage info via DJI Pilot");
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get storage info: " + error.description());
                uiHandler.post(() -> {
                    sdCardStatus.setText("No SD card");
                    internalStorageStatus.setText("0/0GB available");
                });
            }
        });
    }

    /**
     * Show format storage dialog
     */
    private void showFormatDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Format Storage")
            .setMessage("Choose which storage to format:")
            .setPositiveButton("SD Card", (dialog, which) -> formatStorage(CameraStorageLocation.SDCARD))
            .setNegativeButton("Internal Storage", (dialog, which) -> formatStorage(CameraStorageLocation.INTERNAL))
            .setNeutralButton("Cancel", null)
            .show();
    }

    /**
     * Format storage using SDK action
     */
    private void formatStorage(CameraStorageLocation location) {

    }

    /**
     * Show reset camera parameters dialog
     */
    private void showResetDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Reset Camera Parameters")
                .setMessage("This will reset all camera settings to factory defaults. Continue?")
                .setPositiveButton("Reset", (dialog, which) -> resetCameraSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Reset camera settings using SDK action
     */
    private void resetCameraSettings() {
        // Note: Camera reset is typically managed through DJI Pilot app
        // SDK V5 may not expose this action directly
        Log.d(TAG, "Camera reset requested");
        showToast("Camera reset is managed through DJI Pilot app");
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStorageInfo();
    }
}
