package io.empowerbits.sightflight.Activities;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.empowerbits.sightflight.Adapters.MediaGridAdapter;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.Retrofit.ApiClient;
import io.empowerbits.sightflight.Retrofit.ApiService;
import io.empowerbits.sightflight.models.FlightLog;
import io.empowerbits.sightflight.models.MediaItem;
import io.empowerbits.sightflight.models.Project;
import io.empowerbits.sightflight.util.UserSessionManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import dji.sdk.keyvalue.value.camera.DateTime;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.media.MediaFile;
import dji.v5.manager.datacenter.media.MediaFileListState;
import dji.v5.manager.datacenter.media.MediaFileListStateListener;
import dji.v5.manager.datacenter.media.PullMediaFileListParam;
import dji.v5.manager.interfaces.IMediaDataCenter;
import dji.sdk.keyvalue.key.CameraKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.camera.CameraSDCardState;
import dji.sdk.keyvalue.value.camera.CameraStorageLocation;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.manager.KeyManager;
import dji.v5.manager.datacenter.media.MediaFileListDataSource;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MediaManagerActivity - Display and manage drone media files
 *
 * Shows photos and videos from drone camera in a grid layout
 * Supports thumbnail loading and media file operations
 *
 * @author Suleman
 * @version 1.0
 * @date 2025-10-28
 */
public class MediaManagerActivity extends AppCompatActivity {
    private static final String TAG = "MediaManagerActivity";
    private static final int ITEM_WIDTH_DP = 170; // Item width in dp as defined in layout
    private static final int REQUEST_VIEW_MEDIA = 1001;

    // UI Components
    private RecyclerView mediaRecyclerView;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private ImageView backBtn;
    private TextView storageToggleBtn;
    private TextView headerTitle;
    private TextView uploadBtn;

    // Data
    private MediaGridAdapter mediaAdapter;
    private List<MediaItem> mediaItems;
    private List<MediaItem> allMediaItems; // Store all media before filtering
    private Handler uiHandler;
    private MediaFileListStateListener mediaFileListStateListener;
    private CameraStorageLocation currentStorageLocation = CameraStorageLocation.INTERNAL; // Default to internal storage
    private boolean needsRefresh = false;
    Project selectedProject;
    FlightLog flightLog;
    boolean isUploadPhotosMode = false;

    // Upload tracking
    private int currentUploadIndex = 0;
    private boolean isUploading = false;
    private boolean uploadCancelled = false;
    private Dialog uploadDialog;
    private Dialog uploadDoneDialog;
    private List<MediaItem> photosToUpload;
    private ApiService apiService;

    private UserSessionManager userSessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_manager);

        userSessionManager = new UserSessionManager(this);
        Intent intent = getIntent();
        if (intent != null) {
            selectedProject = (Project) intent.getSerializableExtra("project");
            flightLog = (FlightLog) intent.getSerializableExtra("flight");
            isUploadPhotosMode = intent.getBooleanExtra("upload_photos_mode", false);
        }

        uiHandler = new Handler(Looper.getMainLooper());
        mediaItems = new ArrayList<>();
        allMediaItems = new ArrayList<>();
        photosToUpload = new ArrayList<>();

        // Initialize API service
        apiService = ApiClient.getApiService();

        initializeUI();
        setupRecyclerView();
        loadMediaFiles();
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        mediaRecyclerView = findViewById(R.id.mediaRecyclerView);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyStateText = findViewById(R.id.emptyStateText);
        backBtn = findViewById(R.id.backBtn);
        storageToggleBtn = findViewById(R.id.storageToggleBtn);
        headerTitle = findViewById(R.id.headerTitle);
        uploadBtn = findViewById(R.id.uploadBtn);

        // Back button listener
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Add click listener to empty state text for retry
        emptyStateText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (emptyStateText.getText().toString().contains("Tap to retry") ||
                    emptyStateText.getText().toString().contains("tap here to refresh")) {
                    reloadMediaFiles();
                }
            }
        });

        // Storage toggle button listener
        storageToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStorageLocation();
            }
        });

        // Upload button listener
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buttonText = uploadBtn.getText().toString();
                if (buttonText.equals("Cancel")) {
                    // Cancel upload
                    uploadCancelled = true;
                } else {
                    // Start or resume upload
                    startUploadProcess();
                }
            }
        });

        // Show/hide upload button based on mode
        if (isUploadPhotosMode) {
            uploadBtn.setVisibility(View.VISIBLE);
            uploadBtn.setText("Upload");
            // Hide storage toggle in upload mode
            storageToggleBtn.setVisibility(View.GONE);
        } else {
            uploadBtn.setVisibility(View.GONE);
        }

        // Update toggle button text based on current storage
        updateStorageToggleButton();
    }

    /**
     * Toggle between internal storage and SD card storage
     */
    private void toggleStorageLocation() {
        // Switch storage location
        if (currentStorageLocation == CameraStorageLocation.INTERNAL) {
            currentStorageLocation = CameraStorageLocation.SDCARD;
            Log.d(TAG, "Switching to SD Card storage");
            Toast.makeText(this, "Switching to SD Card storage...", Toast.LENGTH_SHORT).show();
        } else {
            currentStorageLocation = CameraStorageLocation.INTERNAL;
            Log.d(TAG, "Switching to Internal storage");
            Toast.makeText(this, "Switching to Internal storage...", Toast.LENGTH_SHORT).show();
        }

        // Update button text
        updateStorageToggleButton();

        // Reload media files from new storage location
        reloadMediaFiles();
    }

    /**
     * Update storage toggle button text and header title
     */
    private void updateStorageToggleButton() {
        if (currentStorageLocation == CameraStorageLocation.INTERNAL) {
            storageToggleBtn.setText("Switch to SD Card");
            headerTitle.setText("Media Manager (Internal)");
        } else {
            storageToggleBtn.setText("Switch to Internal");
            headerTitle.setText("Media Manager (SD Card)");
        }
    }

    /**
     * Reload media files (clears current list and loads fresh)
     */
    private void reloadMediaFiles() {
        // Clear current media items
        mediaItems.clear();
        if (mediaAdapter != null) {
            mediaAdapter.notifyDataSetChanged();
        }

        // Load media files from current storage location
        loadMediaFiles();
    }

    /**
     * Setup RecyclerView with GridLayoutManager
     * Grid span count is calculated based on screen width and item width (150dp)
     */
    private void setupRecyclerView() {
        // Calculate grid span count based on screen width
        int spanCount = calculateGridSpanCount();
        Log.d(TAG, "Grid span count calculated: " + spanCount + " columns");

        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, spanCount);
        mediaRecyclerView.setLayoutManager(gridLayoutManager);

        mediaAdapter = new MediaGridAdapter(this, mediaItems);
        mediaRecyclerView.setAdapter(mediaAdapter);

        // Set item click listener
        mediaAdapter.setOnItemClickListener(new MediaGridAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MediaItem mediaItem, int position) {
                openMediaViewer(mediaItem);
            }
        });
    }

    /**
     * Open media viewer activity
     */
    private void openMediaViewer(MediaItem mediaItem) {
        Intent intent = new Intent(this, MediaViewerActivity.class);
        intent.putExtra("FILE_NAME", mediaItem.getFileName());
        intent.putExtra("FILE_SIZE", mediaItem.getFileSize());
        intent.putExtra("IS_VIDEO", mediaItem.isVideo());
        intent.putExtra("VIDEO_DURATION", mediaItem.getVideoDuration());
        intent.putExtra("CREATED_DATE", mediaItem.getCreatedDate());
        intent.putExtra("RESOLUTION", mediaItem.getResolution());
        intent.putExtra("STORAGE_LOCATION", currentStorageLocation.name());
        startActivityForResult(intent, REQUEST_VIEW_MEDIA);
    }

    /**
     * Calculate grid span count based on screen width and item width
     * @return Number of columns that can fit on screen
     */
    private int calculateGridSpanCount() {
        // Get screen width in pixels
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidthPx = displayMetrics.widthPixels;

        // Convert item width from dp to pixels
        float itemWidthPx = ITEM_WIDTH_DP * displayMetrics.density;

        // Calculate how many items can fit (minimum 1)
        int spanCount = (int) (screenWidthPx / itemWidthPx);

        // Ensure at least 1 column, and reasonable maximum
        return Math.max(1, Math.min(spanCount, 10));
    }

    /**
     * Load media files from drone camera
     * Step 1: Check camera availability
     * Step 2: Enable media manager mode
     * Step 3: Add state listener
     * Step 4: Pull media file list
     */
    private void loadMediaFiles() {
        showLoading(true);

        try {
            // Get IMediaDataCenter instance (returns interface type)
            IMediaDataCenter mediaDataCenter = MediaDataCenter.getInstance();

            if (mediaDataCenter == null) {
                showError("MediaDataCenter not available. Please ensure drone is connected.");
                showLoading(false);
                return;
            }

            if (mediaDataCenter.getMediaManager() == null) {
                showError("MediaManager not available. Please ensure camera is connected.");
                showLoading(false);
                return;
            }

            Log.d(TAG, "MediaManager ready. Preparing media manager mode...");

            // STEP 1: First disable media manager (in case it was left enabled)
            // Then enable it fresh to ensure clean state
            mediaDataCenter.getMediaManager().disable(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Media manager disabled (cleanup). Now enabling...");
                    enableMediaManagerAndPull(mediaDataCenter);
                }

                @Override
                public void onFailure(@NonNull IDJIError error) {
                    Log.d(TAG, "Media manager was not enabled (normal). Enabling now...");
                    // Not an error - it just wasn't enabled. Proceed to enable it.
                    enableMediaManagerAndPull(mediaDataCenter);
                }
            });

        } catch (Exception e) {
            showError("Error loading media: " + e.getMessage());
            showLoading(false);
            Log.e(TAG, "Exception loading media files: " + e.getMessage(), e);
        }
    }

    /**
     * Enable media manager and pull media file list
     */
    private void enableMediaManagerAndPull(IMediaDataCenter mediaDataCenter) {
        Log.d(TAG, "Enabling media manager mode...");

        // Enable media manager mode
        // This is CRITICAL - must be called before accessing media files
        // Camera will enter media management mode (no live view, no photo/video capture)
        mediaDataCenter.getMediaManager().enable(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Media manager enabled successfully");
                // Small delay to ensure camera is ready
                uiHandler.postDelayed(() -> {
                    pullMediaFileList(mediaDataCenter);
                }, 1500);
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to enable media manager: " + error.description());
                Log.e(TAG, "Error code: " + error.errorCode());
                uiHandler.post(() -> {
                    showLoading(false);
                    String errorMsg = "Failed to enable media mode: " + error.description();

                    if (error.description().contains("execution") || error.description().contains("could not be executed")) {
                        errorMsg += "\n\nPossible causes:\n" +
                                "• Camera is still initializing (wait 5-10 seconds)\n" +
                                "• Camera is recording (stop recording first)\n" +
                                "• Camera is busy with another operation\n" +
                                "• Try tapping to retry";
                    } else if (error.description().contains("timeout") || error.description().contains("TIMEOUT")) {
                        errorMsg += "\n\nCamera communication timeout\n" +
                                "• Check connection stability\n" +
                                "• Move closer to drone\n" +
                                "• Tap to retry";
                    } else {
                        errorMsg += "\n\nPlease ensure:\n• Camera is connected\n• SD card is inserted\n• Camera is not recording\n\nTap to retry";
                    }

                    showError(errorMsg);
                });
            }
        });
    }

    /**
     * Pull media file list from camera after enabling media manager
     */
    private void pullMediaFileList(IMediaDataCenter mediaDataCenter) {
        Log.d(TAG, "Pulling media file list from camera...");

        // Only check SD card state if using SD card storage
        if (currentStorageLocation == CameraStorageLocation.SDCARD) {
            checkSDCardState();
        }

        // Set media file data source based on current storage location
        try {
            String storageType = (currentStorageLocation == CameraStorageLocation.INTERNAL) ? "INTERNAL" : "SD CARD";
            Log.d(TAG, "Setting media file data source to " + storageType + " storage...");

            // Create MediaFileListDataSource for selected storage location
            MediaFileListDataSource storageSource = new MediaFileListDataSource.Builder()
                    .setLocation(currentStorageLocation)
                    .setIndexType(ComponentIndexType.LEFT_OR_MAIN)
                    .build();

            // Set the data source on MediaManager
            mediaDataCenter.getMediaManager().setMediaFileDataSource(storageSource);
            Log.d(TAG, "✅ Media file data source set to " + storageType + " storage successfully");

        } catch (Exception e) {
            String storageType = (currentStorageLocation == CameraStorageLocation.INTERNAL) ? "internal" : "SD card";
            Log.e(TAG, "❌ Error setting media data source: " + e.getMessage(), e);
            uiHandler.post(() -> {
                showError("Failed to access " + storageType + " storage: " + e.getMessage());
            });
            return;
        }

        // Add listener to get media file list updates
        mediaFileListStateListener = new MediaFileListStateListener() {
            @Override
            public void onUpdate(MediaFileListState state) {
                Log.d(TAG, "Media file list state changed: " + state);

                // When state is UP_TO_DATE, get the current media file list
                if (state == MediaFileListState.UP_TO_DATE) {
                    List<MediaFile> mediaFileList = mediaDataCenter.getMediaManager().getMediaFileListData().getData();
                    Log.d(TAG, "Media files count: " + (mediaFileList != null ? mediaFileList.size() : 0));

                    // Process the media files when list is updated
                    if (mediaFileList != null && !mediaFileList.isEmpty()) {
                        processMediaFiles(mediaFileList);
                    } else {
                        uiHandler.post(() -> {
                            showLoading(false);
                            emptyStateText.setText("No media files found\n\nTake photos/videos with the drone,\nthen tap here to refresh");
                            showEmptyState(true);
                            Log.d(TAG, "No media files found on camera storage");
                        });
                    }
                } else if (state == MediaFileListState.UPDATING) {
                    Log.d(TAG, "Media file list is updating (syncing from camera)...");
                } else if (state == MediaFileListState.IDLE) {
                    Log.d(TAG, "Media file list is idle (needs to be pulled)");
                } else {
                    Log.d(TAG, "Media file list state: " + state);
                }
            }
        };

        mediaDataCenter.getMediaManager().addMediaFileListStateListener(mediaFileListStateListener);

        // Check current state before pulling
        MediaFileListState currentState = mediaDataCenter.getMediaManager().getMediaFileListState();
        Log.d(TAG, "Current media file list state: " + currentState);

        // Pull media file list from drone camera storage
        // The data source has been set above to INTERNAL storage
        PullMediaFileListParam param = new PullMediaFileListParam.Builder().build();

        mediaDataCenter.getMediaManager().pullMediaFileListFromCamera(param, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Media file list pull request sent successfully");
                // Files will be delivered to the listener's onUpdate() method when state becomes UP_TO_DATE
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.e(TAG, "Failed to pull media files: " + idjiError.description());
                Log.e(TAG, "Error code: " + idjiError.errorCode());

                // Note: Even if pullMediaFileListFromCamera fails, the listener may still get UP_TO_DATE
                // state with 0 files. Don't show error immediately - let the listener handle it.
                // Only show error if the error is critical.

                String errorCode = idjiError.errorCode();
                if (errorCode.contains("FETCH_FILE_LIST_FAILED")) {
                    Log.w(TAG, "Fetch file list failed - this may be normal if SD card is empty or not inserted");
                    // Let the listener handle showing empty state
                } else if (errorCode.contains("TIMEOUT")) {
                    uiHandler.post(() -> {
                        showLoading(false);
                        showError("Camera communication timeout\n\nTroubleshooting:\n• Check connection stability\n• Ensure SD card is inserted\n• Try tapping to retry");
                    });
                } else if (errorCode.contains("STORAGE")) {
                    uiHandler.post(() -> {
                        showLoading(false);
                        showError("Storage access error\n\nPlease check:\n• SD card is inserted\n• SD card is readable\n• SD card format is supported");
                    });
                } else {
                    Log.w(TAG, "Pull media file list error (may be handled by listener): " + idjiError.errorCode());
                }
            }
        });
    }

    /**
     * Process media files from drone
     */
    private void processMediaFiles(List<MediaFile> djiMediaFiles) {
        if (djiMediaFiles == null || djiMediaFiles.isEmpty()) {
            uiHandler.post(() -> {
                showLoading(false);
                showEmptyState(true);
            });
            return;
        }

        // Clear existing items
        mediaItems.clear();
        allMediaItems.clear();

        // Convert DJI MediaFile to our MediaItem model
        for (MediaFile djiMediaFile : djiMediaFiles) {
            MediaItem mediaItem = new MediaItem();
            mediaItem.setFileName(djiMediaFile.getFileName());
            mediaItem.setFileSize(djiMediaFile.getFileSize());
            mediaItem.setMediaFile(djiMediaFile);

            // Determine if it's a video or photo
            String fileName = djiMediaFile.getFileName().toLowerCase();
            boolean isVideo = fileName.endsWith(".mp4") || fileName.endsWith(".mov");
            mediaItem.setVideo(isVideo);

            // Extract video duration if it's a video
            if (isVideo) {
                try {
                    long durationMillis = djiMediaFile.getDuration();
                    // Convert milliseconds to seconds
                    long durationSeconds = durationMillis / 1000;
                    mediaItem.setVideoDuration(durationSeconds);
                    Log.d(TAG, "Video duration for " + fileName + ": " + durationSeconds + " seconds (raw: " + durationMillis + " ms)");
                } catch (Exception e) {
                    Log.w(TAG, "Could not get video duration: " + e.getMessage());
                }
            }

            // Extract created date
            try {
                DateTime createdTime = djiMediaFile.getDate();
                // Convert DateTime to timestamp (milliseconds)
                // DateTime has year, month, day, hour, minute, second fields
                long timestamp = convertDateTimeToTimestamp(createdTime);
                mediaItem.setCreatedDate(timestamp);
            } catch (Exception e) {
                Log.w(TAG, "Could not get created date: " + e.getMessage());
            }

            // Extract resolution (try to get from custom information if available)
            try {
                // DJI SDK V5 may expose resolution through custom information
                // For now, we'll try to extract it or set a placeholder
                // TODO: Extract actual resolution when DJI SDK exposes it
                String resolution = "Unknown";
                mediaItem.setResolution(resolution);
            } catch (Exception e) {
                Log.w(TAG, "Could not get resolution: " + e.getMessage());
            }

            allMediaItems.add(mediaItem);
        }

        // Filter media if in upload mode
        if (isUploadPhotosMode && flightLog != null) {
            filterMediaByFlightTime();
        } else {
            mediaItems.addAll(allMediaItems);
        }

        // Load thumbnails for displayed items
        for (int i = 0; i < mediaItems.size(); i++) {
            loadThumbnail(mediaItems.get(i), i);
        }

        // Update UI
        uiHandler.post(() -> {
            showLoading(false);
            showEmptyState(mediaItems.isEmpty());
            mediaAdapter.notifyDataSetChanged();
            Log.d(TAG, "Displayed " + mediaItems.size() + " media items");
        });
    }

    /**
     * Load thumbnail for a media item
     */
    private void loadThumbnail(final MediaItem mediaItem, final int position) {
        try {
            MediaFile djiMediaFile = mediaItem.getMediaFile();
            if (djiMediaFile == null) return;

            // Pull thumbnail from drone camera
            djiMediaFile.pullThumbnailFromCamera(new CommonCallbacks.CompletionCallbackWithParam<Bitmap>() {
                @Override
                public void onSuccess(Bitmap bitmap) {
                    if (bitmap != null) {
                        mediaItem.setThumbnail(bitmap);
                        uiHandler.post(() -> {
                            mediaAdapter.notifyItemChanged(position);
                        });
                    }
                }

                @Override
                public void onFailure(@NonNull IDJIError error) {
                    Log.w(TAG, "Failed to load thumbnail for " +
                        mediaItem.getFileName() + ": " + error.description());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading thumbnail: " + e.getMessage(), e);
        }
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        uiHandler.post(() -> {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
            mediaRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        });
    }

    /**
     * Show/hide empty state
     */
    private void showEmptyState(boolean show) {
        uiHandler.post(() -> {
            emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
            mediaRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        });
    }

    /**
     * Check SD card state for diagnostics
     */
    private void checkSDCardState() {
        try {
            dji.sdk.keyvalue.key.DJIKey<CameraSDCardState> sdCardKey = KeyTools.createKey(
                    CameraKey.KeyCameraSDCardState,
                    ComponentIndexType.LEFT_OR_MAIN
            );

            KeyManager.getInstance().getValue(sdCardKey, new CommonCallbacks.CompletionCallbackWithParam<CameraSDCardState>() {
                @Override
                public void onSuccess(CameraSDCardState sdCardState) {
                    Log.i(TAG, "📀 SD Card State: " + sdCardState);
                    Log.i(TAG, "📀 SD Card Name: " + (sdCardState != null ? sdCardState.name() : "null"));

                    if (sdCardState == CameraSDCardState.NOT_INSERTED) {
                        uiHandler.post(() -> {
                            showError("SD card is not inserted in the camera.\n\nPlease insert an SD card.");
                        });
                    } else if (sdCardState == CameraSDCardState.FULL) {
                        Log.w(TAG, "SD card is full");
                    } else if (sdCardState == CameraSDCardState.INVALID) {
                        uiHandler.post(() -> {
                            showError("SD card is invalid or corrupted.\n\nPlease format the SD card.");
                        });
                    } else if (sdCardState == CameraSDCardState.UNKNOWN) {
                        uiHandler.post(() -> {
                            showError("SD card format error.\n\nPlease format the SD card in the camera.");
                        });
                    } else if (sdCardState == CameraSDCardState.NORMAL) {
                        Log.i(TAG, "✅ SD card is in NORMAL state - ready to access");
                    }
                }

                @Override
                public void onFailure(@NonNull IDJIError error) {
                    Log.w(TAG, "Could not get SD card state: " + error.description());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error checking SD card state: " + e.getMessage());
        }
    }

    /**
     * Show error message with retry option
     */
    private void showError(String message) {
        uiHandler.post(() -> {
            Toast.makeText(MediaManagerActivity.this, message, Toast.LENGTH_LONG).show();
            emptyStateText.setText("Failed to load media\n\nTap to retry");
            showEmptyState(true);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Disable media manager mode and clean up
        try {
            IMediaDataCenter mediaDataCenter = MediaDataCenter.getInstance();
            if (mediaDataCenter != null && mediaDataCenter.getMediaManager() != null) {

                // Remove listener first
                if (mediaFileListStateListener != null) {
                    mediaDataCenter.getMediaManager().removeMediaFileListStateListener(mediaFileListStateListener);
                    Log.d(TAG, "Media file list listener removed");
                }

                // Disable media manager mode to restore normal camera operation
                // This allows camera to take photos/videos and restore live view
                mediaDataCenter.getMediaManager().disable(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Media manager disabled successfully - camera restored to normal mode");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.w(TAG, "Failed to disable media manager: " + error.description());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up media manager: " + e.getMessage());
        }

        // Clear references
        if (mediaItems != null) {
            mediaItems.clear();
        }

        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "MediaManagerActivity destroyed");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Only reload if we've been flagged to refresh
        if (needsRefresh) {
            Log.d(TAG, "Refreshing media list after deletion");
            reloadMediaFiles();
            needsRefresh = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VIEW_MEDIA) {
            // Check if we need to refresh (e.g., after delete)
            if (data != null && data.getBooleanExtra("MEDIA_DELETED", false)) {
                needsRefresh = true;
                Log.d(TAG, "Media was deleted, will refresh on resume");
            }
            // For download or just viewing, no refresh needed
        }
    }

    /**
     * Filter media files by flight start and end time
     */
    private void filterMediaByFlightTime() {
        if (flightLog == null || flightLog.getStarted_at() == null || flightLog.getEnded_at() == null) {
            Log.w(TAG, "FlightLog or timestamps are null, showing all media");
            mediaItems.addAll(allMediaItems);
            return;
        }

        try {
            // Parse flight log timestamps
            long flightStartTime = parseFlightLogTimestamp(flightLog.getStarted_at());
            long flightEndTime = parseFlightLogTimestamp(flightLog.getEnded_at());
            for (MediaItem item : allMediaItems) {
                if (!item.isVideo()) {
                    long mediaTimestamp = item.getCreatedDate();
                    if (mediaTimestamp >= flightStartTime && mediaTimestamp <= flightEndTime) {
                        mediaItems.add(item);
                        Log.d(TAG, "Including photo: " + item.getFileName() + " at " + new Date(mediaTimestamp));
                    }
                }
            }

            Log.d(TAG, "Filtered " + mediaItems.size() + " photos from " + allMediaItems.size() + " total media files");

        } catch (Exception e) {
            Log.e(TAG, "Error filtering media by flight time: " + e.getMessage(), e);
            // On error, show all photos
            for (MediaItem item : allMediaItems) {
                if (!item.isVideo()) {
                    mediaItems.add(item);
                }
            }
        }
    }

    /**
     * Parse flight log timestamp string to milliseconds
     * Handles formats: "2025-01-31 14:30:00", "2025-01-31T14:30:00Z", ISO 8601, etc.
     */
    private long parseFlightLogTimestamp(String timestampStr) throws ParseException {
        if (timestampStr == null || timestampStr.isEmpty()) {
            throw new ParseException("Timestamp string is null or empty", 0);
        }

        // Try multiple date formats
        String[] formats = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                // Use default/local timezone for flight log timestamps (they are in local time)
                // Only use UTC for formats with 'Z' suffix
                if (format.contains("'Z'")) {
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = sdf.parse(timestampStr);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException e) {
                // Try next format
            }
        }

        throw new ParseException("Unable to parse timestamp: " + timestampStr, 0);
    }

    /**
     * Start upload process
     */
    private void startUploadProcess() {
        if (isUploading) {
            Log.w(TAG, "Upload already in progress");
            return;
        }

        // Validate project
        if (selectedProject == null) {
            Toast.makeText(this, "No project selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check authentication
        String token = userSessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare photos to upload (only photos from filtered list)
        photosToUpload.clear();
        for (MediaItem item : mediaItems) {
            if (!item.isVideo()) {
                photosToUpload.add(item);
            }
        }

        if (photosToUpload.isEmpty()) {
            Toast.makeText(this, "No photos to upload", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting upload of " + photosToUpload.size() + " photos");

        // Reset upload state
        uploadCancelled = false;
        isUploading = true;

        // Update button
        uploadBtn.setText("Cancel");

        // Start upload from current index (or 0 if starting fresh)
        uploadNextPhoto();
    }

    /**
     * Upload next photo in sequence
     */
    private void uploadNextPhoto() {
        if (uploadCancelled) {
            Log.d(TAG, "Upload cancelled by user");
            handleUploadCancelled();
            return;
        }

        if (currentUploadIndex >= photosToUpload.size()) {
            // All photos uploaded
            handleUploadComplete();
            return;
        }

        MediaItem currentPhoto = photosToUpload.get(currentUploadIndex);
        int photoNumber = currentUploadIndex + 1;
        int totalPhotos = photosToUpload.size();

        Log.d(TAG, "Uploading photo " + photoNumber + "/" + totalPhotos + ": " + currentPhoto.getFileName());

        // Show/update upload dialog
        showUploadDialog(photoNumber, totalPhotos);

        // Download full image from drone
        downloadAndUploadPhoto(currentPhoto, photoNumber, totalPhotos);
    }

    /**
     * Download photo from drone and upload to server
     */
    private void downloadAndUploadPhoto(MediaItem mediaItem, int photoNumber, int totalPhotos) {
        MediaFile djiMediaFile = mediaItem.getMediaFile();
        if (djiMediaFile == null) {
            Log.e(TAG, "MediaFile is null for " + mediaItem.getFileName());
            handleUploadError("Failed to access media file");
            return;
        }

        // ByteArrayOutputStream to collect downloaded data
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Pull original file from camera
        djiMediaFile.pullOriginalMediaFileFromCamera(0L, new dji.v5.manager.datacenter.media.MediaFileDownloadListener() {
            @Override
            public void onStart() {
                Log.d(TAG, "Download started for " + mediaItem.getFileName());
            }

            @Override
            public void onRealtimeDataUpdate(byte[] data, long position) {
                // Write incoming data to output stream
                try {
                    if (data != null && data.length > 0) {
                        outputStream.write(data);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error writing realtime data: " + e.getMessage());
                }
            }

            @Override
            public void onProgress(long currentSize, long totalSize) {
                // Log download progress
                if (totalSize > 0) {
                    int progress = (int) ((currentSize * 100) / totalSize);
                    Log.d(TAG, "Download progress: " + progress + "% (" + currentSize + "/" + totalSize + " bytes)");
                }
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "Download finished for " + mediaItem.getFileName());

                try {
                    // Get complete file data
                    byte[] fileData = outputStream.toByteArray();
                    outputStream.close();

                    if (fileData == null || fileData.length == 0) {
                        Log.e(TAG, "Downloaded file data is empty");
                        handleUploadError("Downloaded file is empty");
                        return;
                    }

                    Log.d(TAG, "Downloaded " + fileData.length + " bytes for " + mediaItem.getFileName());

                    // Upload to server
                    uploadPhotoToServer(mediaItem.getFileName(), fileData, photoNumber, totalPhotos);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing downloaded file: " + e.getMessage(), e);
                    handleUploadError("Error processing downloaded file: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to download photo from drone: " + error.description());
                try {
                    outputStream.close();
                } catch (Exception e) {
                    // Ignore
                }
                handleUploadError("Failed to download photo: " + error.description());
            }
        });
    }

    /**
     * Upload photo to server via API
     */
    private void uploadPhotoToServer(String fileName, byte[] fileData, int photoNumber, int totalPhotos) {
        try {
            // Create temporary file
            File tempFile = new File(getCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(fileData);
            fos.close();

            // Prepare multipart request
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), tempFile);
            MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", fileName, requestFile);

            // Determine if this is the last file
            boolean isLastFile = (photoNumber == totalPhotos);
            int completedFlag = isLastFile ? 1 : 0;
            RequestBody completedBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(completedFlag));

            // Get auth token
            String token = userSessionManager.getToken();
            String authHeader = "Bearer " + token;

            // Call API
            Call<retrofit2.Response<String>> call = apiService.uploadMediaImage(
                authHeader,
                String.valueOf(selectedProject.id),
                imagePart,
                completedBody
            );

            call.enqueue(new Callback<retrofit2.Response<String>>() {
                @Override
                public void onResponse(Call<retrofit2.Response<String>> call, Response<retrofit2.Response<String>> response) {
                    // Delete temp file
                    tempFile.delete();

                    if (response.isSuccessful()) {
                        Log.d(TAG, "Successfully uploaded photo " + photoNumber + "/" + totalPhotos);

                        // Move to next photo
                        currentUploadIndex++;
                        uploadNextPhoto();
                    } else {
                        Log.e(TAG, "Upload failed with code: " + response.code());
                        handleUploadError("Upload failed: " + response.message());
                    }
                }

                @Override
                public void onFailure(Call<retrofit2.Response<String>> call, Throwable t) {
                    // Delete temp file
                    tempFile.delete();

                    Log.e(TAG, "Upload network error: " + t.getMessage(), t);
                    handleUploadError("Network error: " + t.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error uploading photo: " + e.getMessage(), e);
            handleUploadError("Upload error: " + e.getMessage());
        }
    }

    /**
     * Show upload progress dialog
     */
    private void showUploadDialog(int current, int total) {
        if (uploadDialog == null) {
            uploadDialog = new Dialog(this);
            uploadDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            uploadDialog.setContentView(R.layout.dialog_uploading);
            uploadDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            uploadDialog.setCancelable(false);

            // Set up cancel icon click listener
            ImageView cancelIcon = uploadDialog.findViewById(R.id.cancelIcon);
            cancelIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Cancel upload
                    uploadCancelled = true;
                }
            });
        }

        TextView uploadingTxt = uploadDialog.findViewById(R.id.uploadingTxt);
        uploadingTxt.setText("Uploading " + current + "/" + total + "....");

        if (!uploadDialog.isShowing()) {
            uploadDialog.show();
        }
    }

    /**
     * Handle upload completion
     */
    private void handleUploadComplete() {
        Log.d(TAG, "All photos uploaded successfully!");

        // Hide upload dialog
        if (uploadDialog != null && uploadDialog.isShowing()) {
            uploadDialog.dismiss();
        }

        // Reset state
        isUploading = false;
        currentUploadIndex = 0;

        // Update button
        uploadBtn.setText("Upload");

        // Show completion dialog
        showUploadDoneDialog();
    }

    /**
     * Show upload done dialog
     */
    private void showUploadDoneDialog() {
        if (uploadDoneDialog == null) {
            uploadDoneDialog = new Dialog(this);
            uploadDoneDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            uploadDoneDialog.setContentView(R.layout.dialog_uploading_done);
            uploadDoneDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            uploadDoneDialog.setCancelable(false);

            ImageView closeIcon = uploadDoneDialog.findViewById(R.id.closeIcon);
            closeIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    uploadDoneDialog.dismiss();
                }
            });
        }

        uploadDoneDialog.show();
    }

    /**
     * Handle upload error
     */
    private void handleUploadError(String errorMessage) {
        Log.e(TAG, "Upload error: " + errorMessage);

        uiHandler.post(() -> {
            // Hide upload dialog
            if (uploadDialog != null && uploadDialog.isShowing()) {
                uploadDialog.dismiss();
            }

            // Reset uploading flag
            isUploading = false;

            // Update button to Resume
            uploadBtn.setText("Resume");

            // Show error message
            Toast.makeText(MediaManagerActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Handle upload cancellation
     */
    private void handleUploadCancelled() {
        Log.d(TAG, "Upload cancelled at photo " + (currentUploadIndex + 1));

        uiHandler.post(() -> {
            // Hide upload dialog
            if (uploadDialog != null && uploadDialog.isShowing()) {
                uploadDialog.dismiss();
            }

            // Reset uploading flag
            isUploading = false;

            // Update button to Resume
            uploadBtn.setText("Resume");

            Toast.makeText(MediaManagerActivity.this, "Upload cancelled. Press Resume to continue.", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Override back button to prevent navigation during upload
     */
    @Override
    public void onBackPressed() {
        if (isUploading) {
            Toast.makeText(this, "Cannot go back while uploading. Press Cancel to stop upload.", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Block hardware back button during upload
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isUploading) {
            Toast.makeText(this, "Cannot go back while uploading. Press Cancel to stop upload.", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Convert DJI DateTime to timestamp in milliseconds
     */
    private long convertDateTimeToTimestamp(DateTime dateTime) {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.set(dateTime.getYear(),
                        dateTime.getMonth() - 1,  // Calendar months are 0-indexed
                        dateTime.getDay(),
                        dateTime.getHour(),
                        dateTime.getMinute(),
                        dateTime.getSecond());
            return calendar.getTimeInMillis();
        } catch (Exception e) {
            Log.w(TAG, "Error converting DateTime to timestamp: " + e.getMessage());
            return 0;
        }
    }
}
