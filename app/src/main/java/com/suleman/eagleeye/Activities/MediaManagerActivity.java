package com.suleman.eagleeye.Activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.suleman.eagleeye.Adapters.MediaGridAdapter;
import com.suleman.eagleeye.R;
import com.suleman.eagleeye.models.MediaItem;

import java.util.ArrayList;
import java.util.List;

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

    // Data
    private MediaGridAdapter mediaAdapter;
    private List<MediaItem> mediaItems;
    private Handler uiHandler;
    private MediaFileListStateListener mediaFileListStateListener;
    private CameraStorageLocation currentStorageLocation = CameraStorageLocation.INTERNAL; // Default to internal storage
    private boolean needsRefresh = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_manager);

        uiHandler = new Handler(Looper.getMainLooper());
        mediaItems = new ArrayList<>();

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

            mediaItems.add(mediaItem);

            // Load thumbnail for this media item
            loadThumbnail(mediaItem, mediaItems.size() - 1);
        }

        // Update UI
        uiHandler.post(() -> {
            showLoading(false);
            showEmptyState(false);
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
