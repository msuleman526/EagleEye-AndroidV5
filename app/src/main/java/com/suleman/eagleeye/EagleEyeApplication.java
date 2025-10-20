package com.suleman.eagleeye;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cySdkyc.clx.Helper;
import com.suleman.eagleeye.Activities.DJIApplication;
import com.suleman.eagleeye.util.EdgeToEdgeUtils;
import com.suleman.eagleeye.util.NativeCrashProtection;

/**
 * EagleEye Application class - Java equivalent of DJIAircraftApplication.kt
 * Extends DJIApplication to inherit DJI SDK initialization
 * Enhanced with comprehensive native crash protection
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class EagleEyeApplication extends DJIApplication implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "EagleEyeApp";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        Log.d(TAG, "Starting attachBaseContext - preparing for Helper.install()");
        
        // CRITICAL: Helper.install() is ABSOLUTELY REQUIRED for DJI SDK V5
        // Enhanced approach with multiple protection strategies

        boolean helperInstallSuccess = false;
        
        try {
            // Pre-load system libraries to prevent conflicts
            NativeCrashProtection.preloadSystemLibraries();
            
            // Strategy 1: Enhanced device compatibility check
            if (!isDeviceCompatible()) {
                Log.w(TAG, "Device may have compatibility issues with DJI SDK native libraries");
                // Continue anyway - compatibility check is just a warning
            }
            
            Log.d(TAG, "Attempting Helper.install() with enhanced protection...");
            
            // Strategy 2: Use multiple fallback approaches for Helper.install()
            helperInstallSuccess = NativeCrashProtection.safeHelperInstall(this);
            
            if (!helperInstallSuccess) {
                // Strategy 3: Last resort - try direct call with comprehensive protection
                helperInstallSuccess = attemptDirectHelperInstall();
            }
            
            if (helperInstallSuccess) {
                Log.i(TAG, "✅ Helper.install() completed successfully - DJI SDK ready");
            } else {
                Log.e(TAG, "❌ All Helper.install() strategies failed - DJI SDK may have limited functionality");
                Log.e(TAG, "This may be a device-specific issue. App will continue with reduced functionality.");
            }

        } catch (Throwable throwable) {
            Log.e(TAG, "❌ Critical error in attachBaseContext: " + throwable.getMessage());
            Log.e(TAG, "This is likely a device-specific native library issue");
            Log.e(TAG, "Stack trace:", throwable);
            helperInstallSuccess = false;
        }

        Log.d(TAG, "EagleEyeApplication attachBaseContext completed - HelperInstall: " + helperInstallSuccess);
    }

    /**
     * Check basic device compatibility for DJI SDK
     */
    private boolean isDeviceCompatible() {
        try {
            // Check Android version
            int sdkVersion = android.os.Build.VERSION.SDK_INT;
            if (sdkVersion < 24) {
                Log.w(TAG, "Android SDK version too low: " + sdkVersion);
                return false;
            }

            // Check architecture
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            boolean hasArm64 = false;
            for (String abi : abis) {
                Log.d(TAG, "Supported ABI: " + abi);
                if ("arm64-v8a".equals(abi)) {
                    hasArm64 = true;
                    break;
                }
            }

            if (!hasArm64) {
                Log.w(TAG, "Device does not support arm64-v8a architecture required by DJI SDK");
                return false;
            }

            Log.d(TAG, "Device appears compatible: Android " + sdkVersion + ", arm64-v8a supported");
            return true;

        } catch (Exception e) {
            Log.w(TAG, "Error checking device compatibility: " + e.getMessage());
            return true; // Assume compatible if we can't check
        }
    }

    /**
     * Attempt Helper.install() with maximum protection - DIRECT APPROACH
     * This is the last resort method with maximum crash protection
     */
    private boolean attemptDirectHelperInstall() {
        try {
            Log.d(TAG, "Last resort: Attempting direct Helper.install() call...");

            // The critical call that must succeed for DJI SDK to work
            Helper.install(this);

            Log.i(TAG, "Direct Helper.install() completed without exceptions");
            return true;

        } catch (UnsatisfiedLinkError linkError) {
            Log.e(TAG, "❌ UnsatisfiedLinkError in direct Helper.install(): " + linkError.getMessage());
            Log.e(TAG, "Native library loading failed. This is device-specific.");
            return false;

        } catch (ExceptionInInitializerError initError) {
            Log.e(TAG, "❌ ExceptionInInitializerError in direct Helper.install(): " + initError.getMessage());
            Log.e(TAG, "Static initialization failed.");
            return false;

        } catch (NoClassDefFoundError classError) {
            Log.e(TAG, "❌ NoClassDefFoundError in direct Helper.install(): " + classError.getMessage());
            Log.e(TAG, "Class loading failed.");
            return false;

        } catch (RuntimeException runtimeError) {
            Log.e(TAG, "❌ RuntimeException in direct Helper.install(): " + runtimeError.getMessage());
            Log.e(TAG, "Runtime error occurred.");
            return false;

        } catch (Error error) {
            Log.e(TAG, "❌ Error in direct Helper.install(): " + error.getMessage());
            Log.e(TAG, "System error occurred: " + error.getClass().getSimpleName());
            return false;

        } catch (Exception exception) {
            Log.e(TAG, "❌ Exception in direct Helper.install(): " + exception.getMessage());
            Log.e(TAG, "Unexpected exception: " + exception.getClass().getSimpleName());
            return false;

        } catch (Throwable throwable) {
            Log.e(TAG, "❌ Throwable in direct Helper.install(): " + throwable.getMessage());
            Log.e(TAG, "Critical error: " + throwable.getClass().getSimpleName());
            Log.e(TAG, "This is likely a native crash that couldn't be caught at Java level");
            return false;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        // SDK initialization is handled by parent DJIApplication class
        Log.d(TAG, "EagleEyeApplication onCreate completed");
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        EdgeToEdgeUtils.applyFullscreen(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        EdgeToEdgeUtils.applyFullscreen(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPostResumed(@NonNull Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPostResumed(activity);
        EdgeToEdgeUtils.applyFullscreen(activity);
    }
}
