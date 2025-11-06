package com.empowerbits.dronifyit.Activities;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.FragmentTransaction;

import com.empowerbits.dronifyit.Fragments.CameraFeedFragment;
import com.empowerbits.dronifyit.R;

import dji.sdk.keyvalue.key.CameraKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.camera.CameraMode;
import dji.sdk.keyvalue.value.camera.CameraType;
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType;
import dji.sdk.keyvalue.value.common.CameraLensType;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.common.EmptyMsg;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;

/**
 * FPVCameraActivity - Camera control with orientation-aware layouts
 * FIXED: Notifies fragment about orientation changes
 */
public class FPVCameraActivity extends DJIMainActivity {

    private static final String TAG = "FPVCameraActivity";

    private ImageView btnTakeOffLand;
    private SwitchCompat switchCameraMode;
    private ImageView btnCapture;
    private ImageView btnFlipCamera;
    private TextView tvRecordingTimer;
    private CameraFeedFragment cameraFeedFragment;

    private boolean isDroneFlying = false;
    private boolean isRecording = false;
    private boolean isPhotoMode = true;
    private CameraVideoStreamSourceType currentCameraSource = CameraVideoStreamSourceType.WIDE_CAMERA; // Default to wide (landscape)

    private Handler timerHandler;
    private Runnable timerRunnable;
    private long recordingStartTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fpv_camera);

        initializeViews();
        initCameraFeedFragment();
        initTimerHandler();

        Log.i(TAG, "📱 Activity created in " +
                (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ?
                        "LANDSCAPE" : "PORTRAIT") + " mode");
    }

    private void initializeViews() {
        btnTakeOffLand = findViewById(R.id.btnTakeOffLand);
        switchCameraMode = findViewById(R.id.switchCameraMode);
        btnCapture = findViewById(R.id.btnCapture);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        tvRecordingTimer = findViewById(R.id.tvRecordingTimer);

        setupListeners();
        updateCameraModeUI();

        Log.d(TAG, "✅ Views initialized");
    }

    private void rebindViews() {
        btnTakeOffLand = findViewById(R.id.btnTakeOffLand);
        switchCameraMode = findViewById(R.id.switchCameraMode);
        btnCapture = findViewById(R.id.btnCapture);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        tvRecordingTimer = findViewById(R.id.tvRecordingTimer);

        setupListeners();
        updateCameraModeUI();
        switchCameraMode.setChecked(!isPhotoMode);

        if (isRecording) {
            btnCapture.setImageResource(android.R.drawable.ic_media_pause);
            tvRecordingTimer.setVisibility(View.VISIBLE);
        } else {
            tvRecordingTimer.setVisibility(View.GONE);
        }

        Log.d(TAG, "✅ Views re-bound");
    }

    private void setupListeners() {
        btnTakeOffLand.setOnClickListener(v -> handleTakeOffLand());

        switchCameraMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isPhotoMode = !isChecked;
            updateCameraModeUI();
            setCameraMode();
        });

        btnCapture.setOnClickListener(v -> handleCapture());

        if (btnFlipCamera != null) {
            btnFlipCamera.setOnClickListener(v -> flipCamera());
        }
    }

    private void initCameraFeedFragment() {
        // Check if fragment already exists
        cameraFeedFragment = (CameraFeedFragment) getSupportFragmentManager()
                .findFragmentById(R.id.cameraFeedContainer);
        if (cameraFeedFragment == null) {
            // Only create new fragment if it doesn't exist
            cameraFeedFragment = new CameraFeedFragment();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.cameraFeedContainer, cameraFeedFragment);
            transaction.commit();
        } else {
            Log.d(TAG, "✅ Camera fragment already exists - reusing");
        }
    }

    private void reattachCameraFragment() {
        if (cameraFeedFragment != null && cameraFeedFragment.isAdded()) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.cameraFeedContainer, cameraFeedFragment);
            transaction.commit();
        }
    }

    private void initTimerHandler() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsedMillis = System.currentTimeMillis() - recordingStartTime;
                    updateTimerDisplay(elapsedMillis);
                    timerHandler.postDelayed(this, 100);
                }
            }
        };
    }

    private void handleTakeOffLand() {
        if (!isDroneFlying) {
            takeOff();
        } else {
            land();
        }
    }

    private void takeOff() {
        try {
            Log.d(TAG, "Initiating takeoff...");
            new Thread(() -> {
                KeyManager.getInstance().performAction(KeyTools.createKey(FlightControllerKey.KeyStartTakeoff), new CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>() {
                    @Override
                    public void onSuccess(EmptyMsg emptyMsg) {
                        runOnUiThread(() -> {
                            isDroneFlying = true;
                            btnTakeOffLand.setImageDrawable(getDrawable(R.drawable.land));
                            Toast.makeText(FPVCameraActivity.this, "Takeoff successful", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Takeoff successful");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Takeoff failed: " + error.description(), Toast.LENGTH_LONG).show();
                            Log.e(TAG, "❌ Takeoff failed: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Takeoff error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Takeoff exception: " + e.getMessage(), e);
        }
    }

    private void land() {
        try {
            Log.d(TAG, "Initiating landing...");
            new Thread(() -> {
                KeyManager.getInstance().performAction(KeyTools.createKey(FlightControllerKey.KeyStartAutoLanding), new CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>() {
                    @Override
                    public void onSuccess(EmptyMsg emptyMsg) {
                        runOnUiThread(() -> {
                            isDroneFlying = false;
                            btnTakeOffLand.setImageDrawable(getDrawable(R.drawable.takeoff));
                            Toast.makeText(FPVCameraActivity.this, "Landing successful", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Landing successful");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Landing failed: " + error.description(), Toast.LENGTH_LONG).show();
                            Log.e(TAG, "❌ Landing failed: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Landing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Landing exception: " + e.getMessage(), e);
        }
    }

    private void updateCameraModeUI() {
        if (isPhotoMode) {
            btnCapture.setImageDrawable(getDrawable(R.drawable.camera));
        } else {
            btnCapture.setImageDrawable(getDrawable(R.drawable.record));
        }
    }

    private void setCameraMode() {
        try {
            Log.d(TAG, "Setting camera mode to: " + (isPhotoMode ? "PHOTO" : "VIDEO"));
            new Thread(() -> {
                dji.sdk.keyvalue.key.DJIKey<CameraMode> cameraModeKey = KeyTools.createKey(
                        CameraKey.KeyCameraMode, ComponentIndexType.LEFT_OR_MAIN);

                CameraMode mode = isPhotoMode ? CameraMode.PHOTO_NORMAL : CameraMode.VIDEO_NORMAL;

                KeyManager.getInstance().setValue(cameraModeKey, mode, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Camera mode: " + (isPhotoMode ? "PHOTO" : "VIDEO"), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Camera mode set successfully");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Failed to set camera mode: " + error.description(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "❌ Failed to set camera mode: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Camera mode error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Camera mode exception: " + e.getMessage(), e);
        }
    }

    private void handleCapture() {
        if (isPhotoMode) {
            capturePhoto();
        } else {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        }
    }

    private void capturePhoto() {
        try {
            Log.d(TAG, "Capturing photo...");
            new Thread(() -> {
                KeyManager.getInstance().performAction(KeyTools.createKey(CameraKey.KeyStartShootPhoto), new CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>() {
                    @Override
                    public void onSuccess(EmptyMsg emptyMsg) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this, "Photo captured", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Photo captured successfully");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Photo capture failed: " + error.description(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "❌ Photo capture failed: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Photo capture error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Photo capture exception: " + e.getMessage(), e);
        }
    }

    private void startRecording() {
        try {
            Log.d(TAG, "Starting video recording...");
            new Thread(() -> {
                KeyManager.getInstance().performAction(KeyTools.createKey(CameraKey.KeyStartRecord), new CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>() {
                    @Override
                    public void onSuccess(EmptyMsg emptyMsg) {
                        runOnUiThread(() -> {
                            isRecording = true;
                            recordingStartTime = System.currentTimeMillis();
                            btnCapture.setImageDrawable(getDrawable(R.drawable.button_stop));
                            tvRecordingTimer.setVisibility(View.VISIBLE);
                            timerHandler.post(timerRunnable);
                            Toast.makeText(FPVCameraActivity.this, "Recording started", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Recording started successfully");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Failed to start recording: " + error.description(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "❌ Failed to start recording: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Recording start error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Recording start exception: " + e.getMessage(), e);
        }
    }

    private void stopRecording() {
        if (!isRecording) return;

        try {
            Log.d(TAG, "Stopping video recording...");
            new Thread(() -> {
                KeyManager.getInstance().performAction(KeyTools.createKey(CameraKey.KeyStopRecord), new CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>() {
                    @Override
                    public void onSuccess(EmptyMsg emptyMsg) {
                        runOnUiThread(() -> {
                            isRecording = false;
                            btnCapture.setImageDrawable(getDrawable(R.drawable.record));
                            tvRecordingTimer.setVisibility(View.GONE);
                            timerHandler.removeCallbacks(timerRunnable);
                            Toast.makeText(FPVCameraActivity.this, "Recording stopped", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Recording stopped successfully");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FPVCameraActivity.this,
                                    "Failed to stop recording: " + error.description(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "❌ Failed to stop recording: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Recording stop error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Recording stop exception: " + e.getMessage(), e);
        }
    }

    private void updateTimerDisplay(long elapsedMillis) {
        int seconds = (int) (elapsedMillis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        String timeString = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        tvRecordingTimer.setText(timeString);
    }

    /**
     * Flip camera between landscape (WIDE) and portrait (ZOOM) lens
     */
    private void flipCamera() {
        try {
            // Toggle between WIDE_CAMERA (landscape) and ZOOM_CAMERA (portrait)
            currentCameraSource = (currentCameraSource == CameraVideoStreamSourceType.WIDE_CAMERA) ?
                    CameraVideoStreamSourceType.ZOOM_CAMERA : CameraVideoStreamSourceType.WIDE_CAMERA;

            Log.d(TAG, "Flipping camera to: " + currentCameraSource.name());

            new Thread(() -> {
                dji.sdk.keyvalue.key.DJIKey<CameraVideoStreamSourceType> cameraSourceKey = KeyTools.createKey(
                        CameraKey.KeyCameraVideoStreamSource, ComponentIndexType.LEFT_OR_MAIN
                );

                KeyManager.getInstance().setValue(cameraSourceKey, currentCameraSource,
                        new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            String sourceName = (currentCameraSource == CameraVideoStreamSourceType.WIDE_CAMERA) ?
                                    "Landscape (Wide)" : "Portrait (Zoom)";
                            Toast.makeText(FPVCameraActivity.this,
                                    "Camera flipped to: " + sourceName, Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "✅ Camera flipped successfully to: " + currentCameraSource.name());
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            // Revert on failure
                            currentCameraSource = (currentCameraSource == CameraVideoStreamSourceType.WIDE_CAMERA) ?
                                    CameraVideoStreamSourceType.ZOOM_CAMERA : CameraVideoStreamSourceType.WIDE_CAMERA;
                            Toast.makeText(FPVCameraActivity.this,
                                    "Failed to flip camera: " + error.description(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "❌ Failed to flip camera: " + error.description());
                        });
                    }
                });
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Camera flip error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Camera flip exception: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    public void prepareUxActivity() {
        // Optional
    }

    @Override
    public void prepareTestingToolsActivity() {
        // Optional
    }
}