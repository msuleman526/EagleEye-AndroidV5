package com.empowerbits.dronifyit.Activities;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.empowerbits.dronifyit.models.GlobalViewModelStore;
import com.empowerbits.dronifyit.models.MSDKManagerVM;

/**
 * Base DJI Application class - Java equivalent of DJIApplication.kt
 * Enhanced with native crash protection for Helper.install() issues
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public abstract class DJIApplication extends Application {

    private static final String TAG = "DJIApplication";
    private MSDKManagerVM msdkManagerVM;

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "DJIApplication onCreate started");
        
        // Enhanced initialization with native crash protection
        try {
            // Get global ViewModel instance
            msdkManagerVM = GlobalViewModelStore.getGlobalViewModel(MSDKManagerVM.class);
            
            // Initialize DJI Mobile SDK with enhanced error handling
            if (msdkManagerVM != null) {
                // Use delayed initialization to avoid early native crashes
                initializeSDKSafely();
                Log.i(TAG, "✅ DJI SDK initialization scheduled successfully");
            } else {
                Log.e(TAG, "❌ Failed to get MSDKManagerVM instance");
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "❌ Critical error in onCreate: " + throwable.getMessage(), throwable);
            // Continue app execution even if SDK initialization fails
        }
        
        Log.d(TAG, "DJIApplication onCreate completed");
    }
    
    /**
     * Safe SDK initialization with enhanced error handling
     * Uses delayed initialization to avoid native library loading conflicts
     */
    private void initializeSDKSafely() {
        try {
            Log.d(TAG, "Starting safe SDK initialization with delayed execution...");
            
            // Post SDK initialization to main thread with small delay
            // This helps avoid early native library access that can cause crashes
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Executing delayed SDK initialization...");
                        
                        // Validate environment before SDK initialization
                        if (validateSDKEnvironment()) {
                            msdkManagerVM.initMobileSDK(DJIApplication.this);
                            Log.i(TAG, "✅ Safe SDK initialization completed successfully");
                        } else {
                            Log.w(TAG, "⚠️ SDK environment validation failed - SDK may have limited functionality");
                            // Still try to initialize but with reduced expectations
                            msdkManagerVM.initMobileSDK(DJIApplication.this);
                        }
                        
                    } catch (UnsatisfiedLinkError linkError) {
                        Log.e(TAG, "❌ Native library error during SDK init: " + linkError.getMessage(), linkError);
                    } catch (NoClassDefFoundError classError) {
                        Log.e(TAG, "❌ Class not found during SDK init: " + classError.getMessage(), classError);
                    } catch (RuntimeException runtimeError) {
                        Log.e(TAG, "❌ Runtime error during SDK init: " + runtimeError.getMessage(), runtimeError);
                    } catch (Throwable t) {
                        Log.e(TAG, "❌ Unexpected error during SDK init: " + t.getMessage(), t);
                    }
                }
            }, 500); // 500ms delay to allow native libraries to settle
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in safe SDK initialization setup: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate the environment before SDK initialization
     * @return true if environment appears compatible
     */
    private boolean validateSDKEnvironment() {
        try {
            // Check Android version
            int sdkVersion = android.os.Build.VERSION.SDK_INT;
            if (sdkVersion < 24) {
                Log.w(TAG, "Android SDK version may be too low: " + sdkVersion);
                return false;
            }
            
            // Check architecture support
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            boolean hasArm64 = false;
            for (String abi : abis) {
                if ("arm64-v8a".equals(abi)) {
                    hasArm64 = true;
                    break;
                }
            }
            
            if (!hasArm64) {
                Log.w(TAG, "Device may not support required arm64-v8a architecture");
                return false;
            }
            
            // Check available memory
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            
            Log.d(TAG, "Memory status - Max: " + (maxMemory / 1024 / 1024) + "MB, " +
                      "Total: " + (totalMemory / 1024 / 1024) + "MB, " +
                      "Free: " + (freeMemory / 1024 / 1024) + "MB");
            
            // Basic memory check
            if (maxMemory < 64 * 1024 * 1024) { // Less than 64MB
                Log.w(TAG, "Available memory may be insufficient for DJI SDK");
                return false;
            }
            
            Log.d(TAG, "SDK environment validation passed");
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Error during environment validation: " + e.getMessage());
            return true; // Assume valid if we can't check
        }
    }
    
    @Override
    public void onTerminate() {
        Log.d(TAG, "DJIApplication onTerminate started");
        
        super.onTerminate();
        
        try {
            if (msdkManagerVM != null) {
                msdkManagerVM.destroyMobileSDK();
                Log.d(TAG, "✅ SDK destroyed successfully");
            }
            GlobalViewModelStore.clear();
            Log.d(TAG, "✅ Global ViewModelStore cleared");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during termination: " + e.getMessage(), e);
        }
        
        Log.d(TAG, "DJIApplication onTerminate completed");
    }
    
    /**
     * Get the global MSDK Manager ViewModel
     */
    public MSDKManagerVM getMsdkManagerVM() {
        return msdkManagerVM;
    }
    
    /**
     * Check if SDK is properly initialized
     */
    public boolean isSDKInitialized() {
        return msdkManagerVM != null && msdkManagerVM.isInitialized();
    }
}
