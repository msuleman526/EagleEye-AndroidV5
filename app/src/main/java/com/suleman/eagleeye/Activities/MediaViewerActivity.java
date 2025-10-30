package com.suleman.eagleeye.Activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.suleman.eagleeye.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.media.MediaFile;
import dji.v5.manager.datacenter.media.MediaFileListState;
import dji.sdk.keyvalue.value.camera.CameraStorageLocation;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.manager.datacenter.media.MediaFileListDataSource;
import dji.v5.manager.interfaces.IMediaDataCenter;

/**
 * MediaViewerActivity - View and download photos/videos from drone
 *
 * Displays high-quality media with download and info options
 *
 * @author Suleman
 * @version 1.0
 * @date 2025-10-30
 */
public class MediaViewerActivity extends AppCompatActivity {
    private static final String TAG = "MediaViewerActivity";

    // UI Components
    private FrameLayout mediaContainer;
    private RelativeLayout header;
    private RelativeLayout bottomBar;
    private ImageView backBtn;
    private ImageView infoBtn;
    private TextView headerTitle;
    private ImageView photoImageView;
    private SurfaceView videoSurfaceView;
    private ProgressBar loadingProgress;
    private TextView loadingText;
    private FrameLayout downloadBtn;
    private LinearLayout downloadButtonLayout;
    private LinearLayout downloadProgressLayout;
    private TextView downloadProgressText;
    private FrameLayout deleteBtn;
    private LinearLayout playPauseBtn;
    private ImageView playPauseIcon;
    private TextView playPauseText;
    private ScrollView infoPanel;
    private TextView infoFileName;
    private TextView infoDate;
    private TextView infoFileSize;
    private TextView infoResolution;
    private TextView infoFileType;
    private TextView infoDuration;
    private TextView infoEncoding;
    private TextView infoAperture;
    private TextView infoShutter;
    private TextView infoISO;
    private TextView infoStorageLocation;

    // ExoPlayer for video playback
    private ExoPlayer exoPlayer;
    private File cachedVideoFile;

    // Data
    private String fileName;
    private long fileSize;
    private boolean isVideo;
    private long videoDuration;
    private long createdDate;
    private String resolution;
    private CameraStorageLocation storageLocation;
    private Handler uiHandler;
    private MediaFile mediaFile;
    private boolean isPlaying = false;
    private boolean infoPanelVisible = false;
    private boolean isDownloading = false;
    private boolean isUIVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        uiHandler = new Handler(Looper.getMainLooper());

        // Get intent extras
        fileName = getIntent().getStringExtra("FILE_NAME");
        fileSize = getIntent().getLongExtra("FILE_SIZE", 0);
        isVideo = getIntent().getBooleanExtra("IS_VIDEO", false);
        videoDuration = getIntent().getLongExtra("VIDEO_DURATION", 0);
        createdDate = getIntent().getLongExtra("CREATED_DATE", 0);
        resolution = getIntent().getStringExtra("RESOLUTION");
        String storageLocationStr = getIntent().getStringExtra("STORAGE_LOCATION");
        storageLocation = CameraStorageLocation.valueOf(storageLocationStr);

        initializeUI();
        loadMediaFile();
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        mediaContainer = findViewById(R.id.mediaContainer);
        header = findViewById(R.id.header);
        bottomBar = findViewById(R.id.bottomBar);
        backBtn = findViewById(R.id.backBtn);
        infoBtn = findViewById(R.id.infoBtn);
        headerTitle = findViewById(R.id.headerTitle);
        photoImageView = findViewById(R.id.photoImageView);
        videoSurfaceView = findViewById(R.id.videoSurfaceView);
        loadingProgress = findViewById(R.id.loadingProgress);
        loadingText = findViewById(R.id.loadingText);
        downloadBtn = findViewById(R.id.downloadBtn);
        downloadButtonLayout = findViewById(R.id.downloadButtonLayout);
        downloadProgressLayout = findViewById(R.id.downloadProgressLayout);
        downloadProgressText = findViewById(R.id.downloadProgressText);
        deleteBtn = findViewById(R.id.deleteBtn);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        playPauseIcon = findViewById(R.id.playPauseIcon);
        playPauseText = findViewById(R.id.playPauseText);
        infoPanel = findViewById(R.id.infoPanel);
        infoFileName = findViewById(R.id.infoFileName);
        infoDate = findViewById(R.id.infoDate);
        infoFileSize = findViewById(R.id.infoFileSize);
        infoResolution = findViewById(R.id.infoResolution);
        infoFileType = findViewById(R.id.infoFileType);
        infoDuration = findViewById(R.id.infoDuration);
        infoEncoding = findViewById(R.id.infoEncoding);
        infoAperture = findViewById(R.id.infoAperture);
        infoShutter = findViewById(R.id.infoShutter);
        infoISO = findViewById(R.id.infoISO);
        infoStorageLocation = findViewById(R.id.infoStorageLocation);

        // Set header title
        headerTitle.setText(fileName);

        // Media container click listener - toggle UI visibility
        mediaContainer.setOnClickListener(v -> toggleUIVisibility());

        // Back button
        backBtn.setOnClickListener(v -> finish());

        // Info button
        infoBtn.setOnClickListener(v -> toggleInfoPanel());

        // Download button
        downloadBtn.setOnClickListener(v -> downloadMedia());

        // Delete button
        deleteBtn.setOnClickListener(v -> confirmDelete());

        // Play/Pause button (for videos)
        if (isVideo) {
            playPauseBtn.setVisibility(View.VISIBLE);
            playPauseBtn.setOnClickListener(v -> togglePlayPause());

            // Setup video surface
            videoSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    Log.d(TAG, "Video surface created");
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    Log.d(TAG, "Video surface changed: " + width + "x" + height);
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    Log.d(TAG, "Video surface destroyed");
                    if (isPlaying) {
                        stopVideoPlayback();
                    }
                }
            });
        }

        // Populate info panel
        updateInfoPanel();
    }

    /**
     * Load media file from drone
     */
    private void loadMediaFile() {
        Log.d(TAG, "Loading media file: " + fileName);

        try {
            IMediaDataCenter mediaDataCenter = MediaDataCenter.getInstance();
            if (mediaDataCenter == null || mediaDataCenter.getMediaManager() == null) {
                showError("Media manager not available");
                return;
            }

            // Set storage location
            MediaFileListDataSource dataSource = new MediaFileListDataSource.Builder()
                    .setLocation(storageLocation)
                    .setIndexType(ComponentIndexType.LEFT_OR_MAIN)
                    .build();

            mediaDataCenter.getMediaManager().setMediaFileDataSource(dataSource);

            // Get media file list to find our file
            List<MediaFile> mediaFileList = mediaDataCenter.getMediaManager()
                    .getMediaFileListData().getData();

            if (mediaFileList != null) {
                for (MediaFile file : mediaFileList) {
                    if (file.getFileName().equals(fileName)) {
                        mediaFile = file;
                        loadMediaContent();
                        return;
                    }
                }
            }

            showError("Media file not found: " + fileName);

        } catch (Exception e) {
            Log.e(TAG, "Error loading media file: " + e.getMessage(), e);
            showError("Error loading media: " + e.getMessage());
        }
    }

    /**
     * Load media content (photo or video preview)
     */
    private void loadMediaContent() {
        if (mediaFile == null) return;

        // Load EXIF metadata for photos
        if (!isVideo) {
            loadExifMetadata();
        }

        if (isVideo) {
            loadVideoPreview();
        } else {
            loadFullSizePhoto();
        }
    }

    /**
     * Load full-size photo
     */
    private void loadFullSizePhoto() {
        Log.d(TAG, "Loading full-size photo...");
        loadingText.setText("Loading high-quality image...");

        // First show thumbnail, then load full size
        mediaFile.pullThumbnailFromCamera(new CommonCallbacks.CompletionCallbackWithParam<Bitmap>() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                if (bitmap != null) {
                    uiHandler.post(() -> {
                        photoImageView.setImageBitmap(bitmap);
                        photoImageView.setVisibility(View.VISIBLE);
                    });

                    // Now load preview (higher quality)
                    loadPreviewImage();
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.w(TAG, "Failed to load thumbnail: " + error.description());
                // Try loading preview directly
                loadPreviewImage();
            }
        });
    }

    /**
     * Load preview image (higher quality than thumbnail)
     */
    private void loadPreviewImage() {
        mediaFile.pullPreviewFromCamera(new CommonCallbacks.CompletionCallbackWithParam<Bitmap>() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                if (bitmap != null) {
                    uiHandler.post(() -> {
                        photoImageView.setImageBitmap(bitmap);
                        photoImageView.setVisibility(View.VISIBLE);
                        loadingProgress.setVisibility(View.GONE);
                        loadingText.setVisibility(View.GONE);
                    });
                    Log.d(TAG, "Preview image loaded successfully");
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to load preview: " + error.description());
                uiHandler.post(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    loadingText.setText("Failed to load image");
                });
            }
        });
    }

    /**
     * Load video preview
     */
    private void loadVideoPreview() {
        Log.d(TAG, "Loading video preview...");
        loadingText.setText("Loading video preview...");

        // For videos, show thumbnail first
        mediaFile.pullThumbnailFromCamera(new CommonCallbacks.CompletionCallbackWithParam<Bitmap>() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                if (bitmap != null) {
                    uiHandler.post(() -> {
                        photoImageView.setImageBitmap(bitmap);
                        photoImageView.setVisibility(View.VISIBLE);
                        loadingProgress.setVisibility(View.GONE);
                        loadingText.setText("Tap play to stream video");
                    });
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to load video thumbnail: " + error.description());
                uiHandler.post(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    loadingText.setText("Failed to load video preview");
                });
            }
        });
    }

    /**
     * Toggle play/pause for video
     */
    private void togglePlayPause() {
        if (mediaFile == null) {
            showError("Media file not available");
            return;
        }

        if (!isPlaying) {
            startVideoPlayback();
        } else {
            pauseVideoPlayback();
        }
    }

    /**
     * Start video playback - downloads video to cache and plays with ExoPlayer
     */
    private void startVideoPlayback() {
        Log.d(TAG, "Starting video playback...");

        if (cachedVideoFile != null && cachedVideoFile.exists()) {
            // Video already downloaded, just play it
            playVideoWithExoPlayer(cachedVideoFile);
            return;
        }

        // Need to download video first
        Toast.makeText(this, "Preparing video for playback...", Toast.LENGTH_SHORT).show();
        loadingProgress.setVisibility(View.VISIBLE);
        loadingText.setText("Preparing video...");
        loadingText.setVisibility(View.VISIBLE);

        // Create cache directory
        File cacheDir = new File(getCacheDir(), "video_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        cachedVideoFile = new File(cacheDir, fileName);

        // Download video to cache
        mediaFile.pullOriginalMediaFileFromCamera(0L, new dji.v5.manager.datacenter.media.MediaFileDownloadListener() {
            private FileOutputStream fos;

            @Override
            public void onStart() {
                Log.d(TAG, "Downloading video for playback: " + fileName);
                try {
                    fos = new FileOutputStream(cachedVideoFile);
                    uiHandler.post(() -> {
                        loadingText.setText("Preparing video...");
                    });
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create cache file: " + e.getMessage(), e);
                    uiHandler.post(() -> {
                        showError("Failed to prepare video: " + e.getMessage());
                        loadingProgress.setVisibility(View.GONE);
                        loadingText.setVisibility(View.GONE);
                    });
                }
            }

            @Override
            public void onRealtimeDataUpdate(byte[] data, long position) {
                if (fos != null && data != null) {
                    try {
                        fos.write(data);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to write video data: " + e.getMessage(), e);
                    }
                }
            }

            @Override
            public void onProgress(long currentSize, long totalSize) {
                // Debug logging
                Log.d(TAG, "Video download progress - Current: " + currentSize + " bytes, Total: " + totalSize + " bytes");

                if (totalSize > 0) {
                    int progress = (int) ((currentSize * 100) / totalSize);
                    // Clamp progress between 0 and 100
                    progress = Math.max(0, Math.min(100, progress));

                    final int finalProgress = progress;
                    uiHandler.post(() -> {
                        loadingText.setText("Preparing video ...");
                    });
                } else {
                    // If totalSize is not available, show bytes downloaded
                    final String sizeStr = formatBytes(currentSize);
                    uiHandler.post(() -> {
                        loadingText.setText("Downloading: " + sizeStr);
                    });
                }
            }

            private String formatBytes(long bytes) {
                if (bytes < 1024) {
                    return bytes + " B";
                } else if (bytes < 1024 * 1024) {
                    return String.format("%.1f KB", bytes / 1024.0);
                } else {
                    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
                }
            }

            @Override
            public void onFinish() {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close cache file: " + e.getMessage(), e);
                    }
                }
                Log.d(TAG, "Video downloaded to cache, starting playback");
                uiHandler.post(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    loadingText.setVisibility(View.GONE);
                    playVideoWithExoPlayer(cachedVideoFile);
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close cache file: " + e.getMessage(), e);
                    }
                }
                if (cachedVideoFile != null && cachedVideoFile.exists()) {
                    cachedVideoFile.delete();
                    cachedVideoFile = null;
                }
                Log.e(TAG, "Failed to download video: " + error.description());
                uiHandler.post(() -> {
                    showError("Failed to prepare video: " + error.description());
                    loadingProgress.setVisibility(View.GONE);
                    loadingText.setVisibility(View.GONE);
                });
            }
        });
    }

    /**
     * Play video using ExoPlayer
     */
    private void playVideoWithExoPlayer(File videoFile) {
        Log.d(TAG, "Playing video with ExoPlayer: " + videoFile.getAbsolutePath());

        try {
            // Initialize ExoPlayer if not already created
            if (exoPlayer == null) {
                exoPlayer = new ExoPlayer.Builder(this).build();
                exoPlayer.setVideoSurfaceView(videoSurfaceView);

                // Add player listener
                exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_READY) {
                            Log.d(TAG, "ExoPlayer ready to play");
                            uiHandler.post(() -> {
                                isPlaying = true;
                                playPauseIcon.setImageResource(android.R.drawable.ic_media_pause);
                                playPauseText.setText("Pause");
                            });
                        } else if (playbackState == Player.STATE_ENDED) {
                            Log.d(TAG, "ExoPlayer playback ended");
                            uiHandler.post(() -> {
                                isPlaying = false;
                                playPauseIcon.setImageResource(android.R.drawable.ic_media_play);
                                playPauseText.setText("Play");
                            });
                        }
                    }

                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        Log.e(TAG, "ExoPlayer error: " + error.getMessage(), error);
                        uiHandler.post(() -> {
                            showError("Playback error: " + error.getMessage());
                            isPlaying = false;
                            playPauseIcon.setImageResource(android.R.drawable.ic_media_play);
                            playPauseText.setText("Play");
                        });
                    }
                });
            }

            // Hide photo view, show video surface
            photoImageView.setVisibility(View.GONE);
            videoSurfaceView.setVisibility(View.VISIBLE);

            // Prepare and play video
            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();

            isPlaying = true;
            playPauseIcon.setImageResource(android.R.drawable.ic_media_pause);
            playPauseText.setText("Pause");

        } catch (Exception e) {
            Log.e(TAG, "Error playing video with ExoPlayer: " + e.getMessage(), e);
            showError("Error playing video: " + e.getMessage());
        }
    }

    /**
     * Pause video playback
     */
    private void pauseVideoPlayback() {
        Log.d(TAG, "Pausing video playback...");

        try {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                exoPlayer.pause();
                isPlaying = false;
                playPauseIcon.setImageResource(android.R.drawable.ic_media_play);
                playPauseText.setText("Play");
            } else if (exoPlayer != null && !exoPlayer.isPlaying()) {
                // Resume playback
                exoPlayer.play();
                isPlaying = true;
                playPauseIcon.setImageResource(android.R.drawable.ic_media_pause);
                playPauseText.setText("Pause");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pausing video: " + e.getMessage(), e);
        }
    }

    /**
     * Stop video playback
     */
    private void stopVideoPlayback() {
        Log.d(TAG, "Stopping video playback...");

        try {
            if (exoPlayer != null) {
                exoPlayer.stop();
                exoPlayer.clearMediaItems();
                isPlaying = false;
                playPauseIcon.setImageResource(android.R.drawable.ic_media_play);
                playPauseText.setText("Play");
                videoSurfaceView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping video: " + e.getMessage(), e);
        }
    }

    /**
     * Download media file to device storage
     */
    private void downloadMedia() {
        if (mediaFile == null) {
            showError("Media file not available");
            return;
        }

        if (isDownloading) {
            Toast.makeText(this, "Download already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting download: " + fileName);
        Toast.makeText(this, "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show();

        // Show download progress, hide download button
        downloadButtonLayout.setVisibility(View.GONE);
        downloadProgressLayout.setVisibility(View.VISIBLE);
        isDownloading = true;

        // Create download directory
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "EagleEye");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        // Create file with timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String downloadFileName = fileName.replace(".", "_" + timestamp + ".");
        final File downloadFile = new File(downloadDir, downloadFileName);

        // Download the file using MediaFileDownloadListener
        mediaFile.pullOriginalMediaFileFromCamera(0L, new dji.v5.manager.datacenter.media.MediaFileDownloadListener() {
            private FileOutputStream fos;

            @Override
            public void onStart() {
                Log.d(TAG, "Download started for: " + fileName);
                try {
                    fos = new FileOutputStream(downloadFile);
                    uiHandler.post(() -> {
                        downloadProgressText.setText("0%");
                    });
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create file: " + e.getMessage(), e);
                    uiHandler.post(() -> {
                        showError("Failed to create file: " + e.getMessage());
                        resetDownloadButton();
                    });
                }
            }

            @Override
            public void onRealtimeDataUpdate(byte[] data, long position) {
                // Write data chunks as they arrive
                if (fos != null && data != null) {
                    try {
                        fos.write(data);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to write data: " + e.getMessage(), e);
                    }
                }
            }

            @Override
            public void onProgress(long currentSize, long totalSize) {
                int progress = 0;
                if (totalSize > 0) {
                    progress = (int) ((currentSize * 100) / totalSize);
                    // Clamp progress between 0 and 100
                    progress = Math.max(0, Math.min(100, progress));
                }
                Log.d(TAG, "Download progress: " + progress + "% (" + currentSize + "/" + totalSize + ")");

                final int finalProgress = progress;
                // Update UI with progress
                uiHandler.post(() -> {
                    downloadProgressText.setText(finalProgress + "%");
                });
            }

            @Override
            public void onFinish() {
                // Close file stream
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close file: " + e.getMessage(), e);
                    }
                }

                Log.d(TAG, "Download completed: " + downloadFile.getAbsolutePath());
                uiHandler.post(() -> {
                    Toast.makeText(MediaViewerActivity.this,
                            "Downloaded to: " + downloadFile.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                    resetDownloadButton();
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                // Close file stream on failure
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close file: " + e.getMessage(), e);
                    }
                }

                // Delete partial file
                if (downloadFile.exists()) {
                    downloadFile.delete();
                }

                Log.e(TAG, "Download failed: " + error.description());
                uiHandler.post(() -> {
                    showError("Download failed: " + error.description());
                    resetDownloadButton();
                });
            }
        });
    }

    /**
     * Reset download button after download completes or fails
     */
    private void resetDownloadButton() {
        downloadButtonLayout.setVisibility(View.VISIBLE);
        downloadProgressLayout.setVisibility(View.GONE);
        isDownloading = false;
    }

    /**
     * Confirm delete with dialog
     */
    private void confirmDelete() {
        if (mediaFile == null) {
            showError("Media file not available");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Media")
                .setMessage("Do you want to delete " + fileName + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteMedia())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Delete media file from drone
     */
    private void deleteMedia() {
        if (mediaFile == null) {
            showError("Media file not available");
            return;
        }

        Log.d(TAG, "Deleting media file: " + fileName);
        Toast.makeText(this, "Deleting " + fileName + "...", Toast.LENGTH_SHORT).show();

        try {
            IMediaDataCenter mediaDataCenter = MediaDataCenter.getInstance();
            if (mediaDataCenter == null || mediaDataCenter.getMediaManager() == null) {
                showError("Media manager not available");
                return;
            }

            // Delete using MediaManager
            java.util.List<MediaFile> filesToDelete = new java.util.ArrayList<>();
            filesToDelete.add(mediaFile);

            mediaDataCenter.getMediaManager().deleteMediaFiles(filesToDelete,
                    new CommonCallbacks.CompletionCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Media file deleted successfully");
                    uiHandler.post(() -> {
                        Toast.makeText(MediaViewerActivity.this,
                                "File deleted successfully",
                                Toast.LENGTH_SHORT).show();
                        // Set result to notify that media was deleted
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("MEDIA_DELETED", true);
                        setResult(RESULT_OK, resultIntent);
                        // Close activity after successful delete
                        finish();
                    });
                }

                @Override
                public void onFailure(@NonNull IDJIError error) {
                    Log.e(TAG, "Failed to delete media file: " + error.description());
                    uiHandler.post(() -> {
                        showError("Delete failed: " + error.description());
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error deleting media file: " + e.getMessage(), e);
            showError("Error deleting file: " + e.getMessage());
        }
    }

    /**
     * Toggle UI visibility (header, bottom bar, info panel)
     */
    private void toggleUIVisibility() {
        isUIVisible = !isUIVisible;

        int visibility = isUIVisible ? View.VISIBLE : View.GONE;

        // Toggle header and bottom bar
        header.setVisibility(visibility);
        bottomBar.setVisibility(visibility);

        // If hiding UI, also hide info panel
        if (!isUIVisible) {
            infoPanel.setVisibility(View.GONE);
            infoPanelVisible = false;
        }

        Log.d(TAG, "UI visibility toggled: " + (isUIVisible ? "visible" : "hidden"));
    }

    /**
     * Toggle info panel visibility
     */
    private void toggleInfoPanel() {
        infoPanelVisible = !infoPanelVisible;
        infoPanel.setVisibility(infoPanelVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Update info panel with media details
     */
    private void updateInfoPanel() {
        // File name
        infoFileName.setText("Name: " + fileName);

        // Creation date
        if (createdDate > 0) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(createdDate));
            infoDate.setText("Date: " + dateStr);
        } else {
            infoDate.setText("Date: Unknown");
        }

        // File size
        infoFileSize.setText("Size: " + formatFileSize(fileSize));

        // Resolution
        if (resolution != null && !resolution.isEmpty()) {
            infoResolution.setText("Resolution: " + resolution);
        } else {
            infoResolution.setText("Resolution: Unknown");
        }

        // File format
        String fileExt = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
        infoFileType.setText("File Format: " + fileExt);

        if (isVideo) {
            // Video-specific info
            if (videoDuration > 0) {
                long minutes = videoDuration / 60;
                long seconds = videoDuration % 60;
                infoDuration.setText("Duration: " + String.format("%d:%02d", minutes, seconds));
                infoDuration.setVisibility(View.VISIBLE);
            }

            // Video encoding (try to extract from MediaFile if available)
            infoEncoding.setText("Encoding: " + fileExt); // Using file extension as basic encoding info
            infoEncoding.setVisibility(View.VISIBLE);

            // Hide photo-specific fields
            infoAperture.setVisibility(View.GONE);
            infoShutter.setVisibility(View.GONE);
            infoISO.setVisibility(View.GONE);
        } else {
            // Photo-specific info - these will be extracted from MediaFile metadata if available
            // For now, showing placeholders - will be populated when MediaFile is loaded
            infoAperture.setVisibility(View.VISIBLE);
            infoShutter.setVisibility(View.VISIBLE);
            infoISO.setVisibility(View.VISIBLE);

            // Hide video-specific fields
            infoDuration.setVisibility(View.GONE);
            infoEncoding.setVisibility(View.GONE);
        }

        // Storage location
        infoStorageLocation.setText("Storage: " + storageLocation.name());
    }

    /**
     * Load EXIF metadata from MediaFile (for photos)
     */
    private void loadExifMetadata() {
        if (mediaFile == null || isVideo) return;

        // Try to extract EXIF data from DJI MediaFile
        // Note: DJI SDK V5 may not expose all EXIF data directly
        // This is a placeholder for future enhancement
        try {
            // TODO: Extract EXIF data when available from DJI SDK
            // For now, showing "N/A" for unavailable data
            infoAperture.setText("Aperture: N/A");
            infoShutter.setText("Shutter Speed: N/A");
            infoISO.setText("ISO: N/A");
        } catch (Exception e) {
            Log.e(TAG, "Error loading EXIF metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Format file size
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        uiHandler.post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            loadingProgress.setVisibility(View.GONE);
            loadingText.setText("Error: " + message);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Release ExoPlayer
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }

        // Clean up cached video file
        if (cachedVideoFile != null && cachedVideoFile.exists()) {
            cachedVideoFile.delete();
            cachedVideoFile = null;
        }

        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "MediaViewerActivity destroyed");
    }
}
