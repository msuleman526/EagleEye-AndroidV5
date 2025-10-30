package com.suleman.eagleeye.models;

import android.graphics.Bitmap;

import dji.v5.manager.datacenter.media.MediaFile;

/**
 * MediaItem - Model class for drone media files
 *
 * Represents a photo or video file from the drone camera
 *
 * @author Suleman
 * @version 1.0
 * @date 2025-10-28
 */
public class MediaItem {
    private String fileName;
    private long fileSize;
    private Bitmap thumbnail;
    private boolean isVideo;
    private MediaFile mediaFile; // DJI MediaFile reference
    private boolean isSelected;
    private long videoDuration; // Duration in seconds for videos
    private long createdDate; // Timestamp
    private String resolution; // e.g., "3840x2160"

    public MediaItem() {
        this.isSelected = false;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideo(boolean video) {
        isVideo = video;
    }

    public MediaFile getMediaFile() {
        return mediaFile;
    }

    public void setMediaFile(MediaFile mediaFile) {
        this.mediaFile = mediaFile;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public long getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(long videoDuration) {
        this.videoDuration = videoDuration;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    /**
     * Get formatted file size string
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Get formatted video duration (MM:SS)
     */
    public String getFormattedDuration() {
        if (!isVideo || videoDuration <= 0) {
            return "";
        }
        long minutes = videoDuration / 60;
        long seconds = videoDuration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
