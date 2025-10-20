package com.suleman.eagleeye.Activities;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.FragmentTransaction;

import com.suleman.eagleeye.Fragments.CameraFeedFragment;
import com.suleman.eagleeye.R;

/**
 * FPVCameraActivity - Camera control with orientation-aware layouts
 * FIXED: Notifies fragment about orientation changes
 */
public class FPVCameraActivity extends DJIMainActivity {

    private static final String TAG = "FPVCameraActivity";

    private Button btnTakeOffLand;
    private SwitchCompat switchCameraMode;
    private ImageButton btnCapture;
    private ImageButton btnStopRecording;
    private TextView tvRecordingTimer;
    private TextView tvCameraMode;
    private CameraFeedFragment cameraFeedFragment;

    private boolean isDroneFlying = false;
    private boolean isRecording = false;
    private boolean isPhotoMode = true;

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
        btnStopRecording = findViewById(R.id.btnStopRecording);
        tvRecordingTimer = findViewById(R.id.tvRecordingTimer);
        tvCameraMode = findViewById(R.id.tvCameraMode);

        setupListeners();
        updateCameraModeUI();

        Log.d(TAG, "✅ Views initialized");
    }

    private void rebindViews() {
        btnTakeOffLand = findViewById(R.id.btnTakeOffLand);
        switchCameraMode = findViewById(R.id.switchCameraMode);
        btnCapture = findViewById(R.id.btnCapture);
        btnStopRecording = findViewById(R.id.btnStopRecording);
        tvRecordingTimer = findViewById(R.id.tvRecordingTimer);
        tvCameraMode = findViewById(R.id.tvCameraMode);

        setupListeners();
        updateCameraModeUI();
        switchCameraMode.setChecked(!isPhotoMode);

        if (isRecording) {
            btnCapture.setImageResource(android.R.drawable.ic_media_pause);
            btnStopRecording.setVisibility(View.VISIBLE);
            tvRecordingTimer.setVisibility(View.VISIBLE);
        } else {
            btnStopRecording.setVisibility(View.GONE);
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
        btnStopRecording.setOnClickListener(v -> stopRecording());
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
        Toast.makeText(this, "Take off functionality coming soon", Toast.LENGTH_SHORT).show();
    }

    private void land() {
        Toast.makeText(this, "Land functionality coming soon", Toast.LENGTH_SHORT).show();
    }

    private void updateCameraModeUI() {
        if (isPhotoMode) {
            tvCameraMode.setText("PHOTO");
            btnCapture.setImageResource(android.R.drawable.ic_menu_camera);
        } else {
            tvCameraMode.setText("VIDEO");
            btnCapture.setImageResource(android.R.drawable.presence_video_online);
        }
    }

    private void setCameraMode() {
        Toast.makeText(this, "Camera mode: " + (isPhotoMode ? "PHOTO" : "VIDEO"), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Capturing photo...", Toast.LENGTH_SHORT).show();
    }

    private void startRecording() {
        isRecording = true;
        recordingStartTime = System.currentTimeMillis();
        btnCapture.setImageResource(android.R.drawable.ic_media_pause);
        btnStopRecording.setVisibility(View.VISIBLE);
        tvRecordingTimer.setVisibility(View.VISIBLE);
        timerHandler.post(timerRunnable);
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        btnCapture.setImageResource(android.R.drawable.presence_video_online);
        btnStopRecording.setVisibility(View.GONE);
        tvRecordingTimer.setVisibility(View.GONE);
        timerHandler.removeCallbacks(timerRunnable);
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
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