package io.empowerbits.sightflight;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.cySdkyc.clx.Helper;
import io.empowerbits.sightflight.Activities.DJIApplication;
import io.empowerbits.sightflight.util.EdgeToEdgeUtils;
import io.empowerbits.sightflight.util.NativeCrashProtection;

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

            // Strategy 1: STRICT device compatibility check
            if (!isDeviceCompatible()) {
                Log.e(TAG, "❌ DEVICE NOT COMPATIBLE - ABORTING SDK INITIALIZATION");
                Log.e(TAG, "DJI SDK will NOT be initialized on this device");
                Log.e(TAG, "Please use a real ARM64 device (not an emulator or x86 tablet)");
                // DO NOT attempt Helper.install() on incompatible devices - it will crash
                return;
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
     * CRITICAL: DJI SDK native libraries ONLY work on real arm64-v8a devices
     */
    private boolean isDeviceCompatible() {
        try {
            // Check Android version
            int sdkVersion = android.os.Build.VERSION.SDK_INT;
            if (sdkVersion < 24) {
                Log.w(TAG, "Android SDK version too low: " + sdkVersion);
                return false;
            }

            // Check architecture - MUST be arm64-v8a as PRIMARY ABI
            String[] abis = android.os.Build.SUPPORTED_ABIS;

            if (abis.length == 0) {
                Log.e(TAG, "No supported ABIs found on device");
                return false;
            }

            // Log all supported ABIs
            for (String abi : abis) {
                Log.d(TAG, "Supported ABI: " + abi);
            }

            // CRITICAL: arm64-v8a MUST be the PRIMARY (first) ABI
            // DJI SDK native libraries crash under x86/x86_64 emulation
            String primaryAbi = abis[0];

            if (!"arm64-v8a".equals(primaryAbi)) {
                Log.e(TAG, "❌ INCOMPATIBLE DEVICE ARCHITECTURE");
                Log.e(TAG, "Primary ABI is: " + primaryAbi);
                Log.e(TAG, "DJI SDK requires arm64-v8a as PRIMARY architecture");
                Log.e(TAG, "This device (likely an emulator or x86 tablet) is NOT compatible");
                return false;
            }

            Log.d(TAG, "✅ Device compatible: Android " + sdkVersion + ", arm64-v8a primary ABI");
            return true;

        } catch (Exception e) {
            Log.w(TAG, "Error checking device compatibility: " + e.getMessage());
            return false; // Fail-safe: assume incompatible if we can't check
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

        // Force Dark Mode for the entire application
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        Log.d(TAG, "Dark mode enforced for application");

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
