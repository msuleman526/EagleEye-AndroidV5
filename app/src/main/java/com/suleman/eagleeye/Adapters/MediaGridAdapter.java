package com.suleman.eagleeye.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.suleman.eagleeye.R;
import com.suleman.eagleeye.models.MediaItem;

import java.util.List;

/**
 * MediaGridAdapter - RecyclerView adapter for displaying media in grid
 *
 * Displays drone photos and videos in a grid layout with thumbnails
 *
 * @author Suleman
 * @version 1.0
 * @date 2025-10-28
 */
public class MediaGridAdapter extends RecyclerView.Adapter<MediaGridAdapter.MediaViewHolder> {
    private Context context;
    private List<MediaItem> mediaItems;
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(MediaItem mediaItem, int position);
    }

    public MediaGridAdapter(Context context, List<MediaItem> mediaItems) {
        this.context = context;
        this.mediaItems = mediaItems;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media_grid, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem mediaItem = mediaItems.get(position);

        // Set file name
        holder.mediaFileName.setText(mediaItem.getFileName());

        // Set file size
        holder.mediaFileSize.setText(mediaItem.getFormattedFileSize());

        // Set thumbnail if available
        if (mediaItem.getThumbnail() != null) {
            holder.mediaThumbnail.setImageBitmap(mediaItem.getThumbnail());
        } else {
            // Show placeholder
            holder.mediaThumbnail.setImageResource(R.drawable.media);
        }

        // Show video play icon for videos
        if (mediaItem.isVideo()) {
            holder.videoPlayIcon.setVisibility(View.VISIBLE);
        } else {
            holder.videoPlayIcon.setVisibility(View.GONE);
        }

        // Set selection state
        holder.mediaCheckbox.setChecked(mediaItem.isSelected());

        // Click listener
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(mediaItem, holder.getAdapterPosition());
                }
            }
        });

        // Checkbox listener
        holder.mediaCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mediaItem.setSelected(isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return mediaItems != null ? mediaItems.size() : 0;
    }

    /**
     * ViewHolder for media grid items
     */
    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView mediaThumbnail;
        ImageView videoPlayIcon;
        TextView mediaFileName;
        TextView mediaFileSize;
        CheckBox mediaCheckbox;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaThumbnail = itemView.findViewById(R.id.mediaThumbnail);
            videoPlayIcon = itemView.findViewById(R.id.videoPlayIcon);
            mediaFileName = itemView.findViewById(R.id.mediaFileName);
            mediaFileSize = itemView.findViewById(R.id.mediaFileSize);
            mediaCheckbox = itemView.findViewById(R.id.mediaCheckbox);
        }
    }

    /**
     * Update media items list
     */
    public void updateMediaItems(List<MediaItem> newMediaItems) {
        this.mediaItems = newMediaItems;
        notifyDataSetChanged();
    }

    /**
     * Get selected media items
     */
    public List<MediaItem> getSelectedItems() {
        List<MediaItem> selectedItems = new java.util.ArrayList<>();
        for (MediaItem item : mediaItems) {
            if (item.isSelected()) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    /**
     * Clear all selections
     */
    public void clearSelections() {
        for (MediaItem item : mediaItems) {
            item.setSelected(false);
        }
        notifyDataSetChanged();
    }
}
