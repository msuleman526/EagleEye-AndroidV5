package com.suleman.eagleeye.Activities;

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

import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.media.MediaFile;
import dji.v5.manager.datacenter.media.MediaFileListState;
import dji.v5.manager.datacenter.media.PullMediaFileListParam;
import dji.v5.manager.interfaces.IMediaDataCenter;

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
    private static final int GRID_SPAN_COUNT = 3; // 3 columns

    // UI Components
    private RecyclerView mediaRecyclerView;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private ImageView backBtn;
    private ImageView downloadAllBtn;

    // Data
    private MediaGridAdapter mediaAdapter;
    private List<MediaItem> mediaItems;
    private Handler uiHandler;

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
        downloadAllBtn = findViewById(R.id.downloadAllBtn);

        // Back button listener
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Download all button listener
        downloadAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Implement download all functionality
                Toast.makeText(MediaManagerActivity.this,
                    "Download all feature coming soon", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Setup RecyclerView with GridLayoutManager
     */
    private void setupRecyclerView() {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, GRID_SPAN_COUNT);
        mediaRecyclerView.setLayoutManager(gridLayoutManager);

        mediaAdapter = new MediaGridAdapter(this, mediaItems);
        mediaRecyclerView.setAdapter(mediaAdapter);

        // Set item click listener
        mediaAdapter.setOnItemClickListener(new MediaGridAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MediaItem mediaItem, int position) {
                // TODO: Open media viewer activity
                Toast.makeText(MediaManagerActivity.this,
                    "Clicked: " + mediaItem.getFileName(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Load media files from drone camera
     */
    private void loadMediaFiles() {
        showLoading(true);

        try {
            // Get IMediaDataCenter instance (returns interface type)
            IMediaDataCenter mediaDataCenter = MediaDataCenter.getInstance();

            if (mediaDataCenter == null) {
                showError("MediaDataCenter not available");
                return;
            }

            if (mediaDataCenter.getMediaManager() == null) {
                showError("MediaManager not available");
                return;
            }

            // Create pull media file list parameter - empty builder for default settings
            PullMediaFileListParam param = new PullMediaFileListParam.Builder().build();

            // Pull media file list from drone
            mediaDataCenter.getMediaManager().pullMediaFileListFromCamera(param, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onSuccess() {
                    uiHandler.post(() -> {
                        showLoading(false);
                        Log.d(TAG, "Media file list loaded successfully");
                    });
                }

                @Override
                public void onFailure(@NonNull IDJIError idjiError) {
                    uiHandler.post(() -> {
                        showLoading(false);
                        showError("Failed to load media: " + idjiError.description());
                        Log.e(TAG, "Failed to load media files: " + idjiError.description());
                    });
                }
            });
        } catch (Exception e) {
            showError("Error loading media: " + e.getMessage());
            Log.e(TAG, "Error loading media files: " + e.getMessage(), e);
        }
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
            mediaItem.setVideo(fileName.endsWith(".mp4") || fileName.endsWith(".mov"));

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
     * Show error message
     */
    private void showError(String message) {
        uiHandler.post(() -> {
            Toast.makeText(MediaManagerActivity.this, message, Toast.LENGTH_SHORT).show();
            showEmptyState(true);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clear references
        if (mediaItems != null) {
            mediaItems.clear();
        }

        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "MediaManagerActivity destroyed");
    }
}
