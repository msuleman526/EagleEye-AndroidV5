package com.suleman.eagleeye.Fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.suleman.eagleeye.R;
import com.suleman.eagleeye.Services.ConnectionStateManager;

import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.product.ProductType;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.interfaces.ICameraStreamManager;

/**
 * CameraFeedFragment - Camera video stream display
 * COMPLETE FIX: Properly manages surface lifecycle and stream restarts
 */
public class CameraFeedFragment extends Fragment {

    private static final String TAG = "CameraFeedFragment";

    private TextureView cameraTextureView;
    private ICameraStreamManager cameraStreamManager;
    private Surface currentSurface;
    private boolean isDroneConnected = false;
    private boolean isStreamStarted = false;
    private ProductType productType = ProductType.UNKNOWN;

    private static final ComponentIndexType CAMERA_INDEX = ComponentIndexType.LEFT_OR_MAIN;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "📄 onCreateView called");
        setRetainInstance(true); // Fragment survives orientation change
        return inflater.inflate(R.layout.fragment_camera_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "👁️ onViewCreated called");

        initUI(view);
        initCameraStreamManager();

        ConnectionStateManager.getInstance().getConnectionState().observe(getViewLifecycleOwner(), isConnected -> {
            Log.d(TAG, "🔗 Connection state changed: " + isConnected);
            isDroneConnected = isConnected;
            updateConnectionLogic();
        });

        ConnectionStateManager.getInstance().getProductType().observe(getViewLifecycleOwner(), productTypee -> {
            Log.d(TAG, "🚁 Product type changed: " + (productTypee != null ? productTypee.name() : "null"));
            productType = productTypee;
            updateConnectionLogic();
        });

        isDroneConnected = ConnectionStateManager.getInstance().isCurrentlyConnected();
        productType = ConnectionStateManager.getInstance().getCurrentProductType();

        Log.d(TAG, "✅ Fragment view created - Connected: " + isDroneConnected + ", Product: " + productType);
    }

    private void initUI(View view) {
        cameraTextureView = view.findViewById(R.id.cameraTextureView);

        cameraTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull android.graphics.SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "🎨 Surface AVAILABLE: " + width + "x" + height);

                // Small delay to ensure everything is ready
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isDroneConnected && productType != ProductType.UNKNOWN) {
                        startCameraStream();
                    } else {
                        Log.w(TAG, "⚠️ Not starting stream - Connected: " + isDroneConnected + ", Product: " + productType);
                    }
                }, 200);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "📐 Surface SIZE CHANGED: " + width + "x" + height);
                if (isStreamStarted) {
                    updateStreamSize(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture surfaceTexture) {
                Log.d(TAG, "💥 Surface DESTROYED");
                stopCameraStream();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture surfaceTexture) {
                // Called very frequently - don't log
            }
        });
    }

    private void initCameraStreamManager() {
        cameraStreamManager = MediaDataCenter.getInstance().getCameraStreamManager();
        if (cameraStreamManager != null) {
            Log.d(TAG, "✅ CameraStreamManager initialized");
        } else {
            Log.e(TAG, "❌ Failed to get CameraStreamManager");
        }
    }

    private void updateConnectionLogic() {
        Log.d(TAG, "🔄 updateConnectionLogic - Connected: " + isDroneConnected +
                ", Product: " + productType +
                ", TextureAvailable: " + (cameraTextureView != null && cameraTextureView.isAvailable()) +
                ", StreamStarted: " + isStreamStarted);

        if (isDroneConnected && productType != ProductType.UNKNOWN) {
            if (cameraTextureView != null && cameraTextureView.isAvailable() && !isStreamStarted) {
                Log.i(TAG, "▶️ Conditions met - starting stream");
                startCameraStream();
            }
        } else {
            if (isStreamStarted) {
                Log.i(TAG, "⏹️ Stopping stream - drone disconnected or product unknown");
                stopCameraStream();
            }
        }
    }

    private void startCameraStream() {
        if (cameraStreamManager == null) {
            Log.e(TAG, "❌ Cannot start stream - cameraStreamManager is null");
            return;
        }

        if (isStreamStarted) {
            Log.w(TAG, "⚠️ Stream already started");
            return;
        }

        if (getActivity() == null || cameraTextureView == null || !cameraTextureView.isAvailable()) {
            Log.e(TAG, "❌ Cannot start stream - view not ready");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                // Clean up old surface first
                if (currentSurface != null) {
                    Log.d(TAG, "🧹 Cleaning up old surface");
                    try {
                        cameraStreamManager.removeCameraStreamSurface(currentSurface);
                        currentSurface.release();
                    } catch (Exception e) {
                        Log.w(TAG, "⚠️ Error cleaning old surface: " + e.getMessage());
                    }
                    currentSurface = null;
                }

                int width = cameraTextureView.getWidth();
                int height = cameraTextureView.getHeight();

                Log.i(TAG, "▶️ Starting camera stream: " + width + "x" + height);

                currentSurface = new Surface(cameraTextureView.getSurfaceTexture());

                cameraStreamManager.putCameraStreamSurface(
                        CAMERA_INDEX,
                        currentSurface,
                        width,
                        height,
                        ICameraStreamManager.ScaleType.CENTER_INSIDE
                );

                isStreamStarted = true;
                Log.i(TAG, "✅✅✅ Camera stream STARTED successfully");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting camera stream: " + e.getMessage(), e);
                isStreamStarted = false;
            }
        });
    }

    private void updateStreamSize(int width, int height) {
        if (cameraStreamManager == null || !isStreamStarted || currentSurface == null) {
            Log.w(TAG, "⚠️ Cannot update size - stream not active");
            return;
        }

        if (getActivity() == null || cameraTextureView == null || !cameraTextureView.isAvailable()) {
            Log.w(TAG, "⚠️ Cannot update size - view not available");
            return;
        }

        getActivity().runOnUiThread(() -> {
            try {
                Log.i(TAG, "📐 Updating stream size: " + width + "x" + height);

                cameraStreamManager.putCameraStreamSurface(
                        CAMERA_INDEX,
                        currentSurface,
                        width,
                        height,
                        ICameraStreamManager.ScaleType.CENTER_INSIDE
                );

                Log.i(TAG, "✅ Stream size updated");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error updating stream size: " + e.getMessage(), e);
            }
        });
    }

    private void stopCameraStream() {
        if (cameraStreamManager == null || !isStreamStarted) {
            Log.d(TAG, "⚠️ Stream not active, nothing to stop");
            return;
        }

        try {
            if (currentSurface != null) {
                Log.i(TAG, "⏹️ Stopping camera stream");
                cameraStreamManager.removeCameraStreamSurface(currentSurface);
                currentSurface.release();
                currentSurface = null;
            }

            isStreamStarted = false;
            Log.i(TAG, "✅ Video stream stopped");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping camera stream: " + e.getMessage(), e);
        }
    }

    /**
     * CRITICAL: Called when orientation changes to restart stream
     */
    public void onOrientationChanged() {
        if (getView() == null || cameraTextureView == null) {
            Log.e(TAG, "❌ View is null, cannot restart stream");
            return;
        }

        // Force view to re-layout first
        getView().post(() -> {
            if (cameraTextureView != null) {
                cameraTextureView.requestLayout();

                // Wait for layout to complete
                cameraTextureView.post(() -> {
                    Log.d(TAG, "🔄 Layout complete, checking stream state");

                    // Stop existing stream
                    if (isStreamStarted) {
                        Log.d(TAG, "⏹️ Stopping existing stream before restart");
                        stopCameraStream();
                    }

                    // Wait a bit then restart
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                            int width = cameraTextureView.getWidth();
                            int height = cameraTextureView.getHeight();
                            Log.i(TAG, "📐 New size: " + width + "x" + height + " - Restarting stream");

                            if (isDroneConnected && productType != ProductType.UNKNOWN) {
                                startCameraStream();
                            } else {
                                Log.w(TAG, "⚠️ Not restarting - Connected: " + isDroneConnected + ", Product: " + productType);
                            }
                        } else {
                            Log.e(TAG, "❌ TextureView not available for restart");
                        }
                    }, 300);
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "▶️ onResume");
        if (isDroneConnected && !isStreamStarted && cameraTextureView != null && cameraTextureView.isAvailable()) {
            startCameraStream();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ onPause");
        if (isStreamStarted) {
            stopCameraStream();
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "💀 onDestroyView");
        if (isStreamStarted) {
            stopCameraStream();
        }
        super.onDestroyView();
    }

    public boolean isStreamActive() {
        return isStreamStarted;
    }
}