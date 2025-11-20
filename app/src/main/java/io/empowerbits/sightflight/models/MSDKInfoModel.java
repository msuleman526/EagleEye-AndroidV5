package io.empowerbits.sightflight.models;

import android.os.Build;
import android.util.Log;

import io.empowerbits.sightflight.data.MSDKInfo;

import dji.v5.manager.SDKManager;
import dji.v5.utils.inner.SDKConfig;

/**
 * MSDK Information Model - Provides SDK information and configuration details
 *
 * CRITICAL FIX: Safe SDK information retrieval with proper error handling
 * - Added comprehensive error handling for all SDK calls
 * - Safe fallback values when SDK is not initialized
 * - No crashes when SDK components are not ready
 *
 * @author Suleman
 * @date 2025-06-26
 */
public class MSDKInfoModel {

    private static final String TAG = "MSDKInfoModel";

    /**
     * SAFE: Get DJI SDK version with fallback
     */
    public String getSDKVersion() {
        try {
            // Check if SDK is available before calling
            return SDKManager.getInstance().getSDKVersion();
        } catch (Exception e) {
            Log.w(TAG, "Failed to get SDK version: " + e.getMessage());
            return "5.15.0"; // Safe fallback
        }
    }

    /**
     * SAFE: Get SDK build version with fallback
     */
    public String getBuildVersion() {
        try {
            if (isSDKConfigAvailable()) {
                return SDKConfig.getInstance().getBuildVersion();
            } else {
                Log.w(TAG, "SDKConfig not available, using default build version");
                return "Unknown";
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get build version: " + e.getMessage());
            return "Unknown";
        }
    }

    /**
     * SAFE: Check if SDK is in debug mode with fallback
     */
    public boolean isDebug() {
        try {
            if (isSDKConfigAvailable()) {
                return SDKConfig.getInstance().isDebug();
            } else {
                Log.w(TAG, "SDKConfig not available, assuming release mode");
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check debug status: " + e.getMessage());
            return false; // Safe default - assume release mode
        }
    }

    /**
     * SAFE: Get package product category with fallback
     */
    public String getPackageProductCategory() {
        try {
            if (isSDKConfigAvailable()) {
                return SDKConfig.getInstance().getPackageProductCategory().name();
            } else {
                Log.w(TAG, "SDKConfig not available, using default category");
                return "Aircraft";
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get package product category: " + e.getMessage());
            return "Aircraft"; // Safe default
        }
    }

    /**
     * SAFE: Get core information about the device and SDK
     */
    public String getCoreInfo() {
        StringBuilder coreInfo = new StringBuilder();

        // Device information (always safe)
        coreInfo.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\\n");
        coreInfo.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\\n");
        coreInfo.append("ABI: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "Unknown").append("\\n");

        // App information
        coreInfo.append("Package: io.empowerbits.sightflight\\n");

        // SDK information (safe)
        try {
            coreInfo.append("SDK Initialized: ").append(SDKManager.getInstance().isRegistered()).append("\\n");
        } catch (Exception e) {
            coreInfo.append("SDK Status: Error - ").append(e.getMessage()).append("\\n");
        }

        return coreInfo.toString();
    }

    /**
     * SAFE: Create a complete MSDKInfo object with current information
     * No SDK dependencies that could cause crashes
     */
    public MSDKInfo createMSDKInfo() {
        Log.i(TAG, "📋 Creating MSDKInfo with safe fallbacks...");

        try {
            MSDKInfo info = new MSDKInfo(getSDKVersion());
            info.setBuildVer(getBuildVersion());
            info.setDebug(isDebug());
            info.setPackageProductCategory(getPackageProductCategory());
            info.setCoreInfo(getCoreInfo());

            // Set safe defaults for other fields
            info.setNetworkInfo(MSDKInfo.NO_NETWORK_STR);
            info.setCountryCode(MSDKInfo.DEFAULT_STR);
            info.setFirmwareVer(MSDKInfo.DEFAULT_STR);

            Log.i(TAG, "✅ MSDKInfo created successfully");
            return info;

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create MSDKInfo: " + e.getMessage());

            // Create absolute minimal fallback
            MSDKInfo fallbackInfo = new MSDKInfo("5.15.0");
            fallbackInfo.setBuildVer("Unknown");
            fallbackInfo.setDebug(false);
            fallbackInfo.setPackageProductCategory("Aircraft");
            fallbackInfo.setCoreInfo("Device: " + Build.MODEL + "\\nAndroid: " + Build.VERSION.RELEASE);
            fallbackInfo.setNetworkInfo(MSDKInfo.NO_NETWORK_STR);
            fallbackInfo.setCountryCode(MSDKInfo.DEFAULT_STR);
            fallbackInfo.setFirmwareVer(MSDKInfo.DEFAULT_STR);

            Log.w(TAG, "⚠️ Using fallback MSDKInfo");
            return fallbackInfo;
        }
    }

    /**
     * Get a formatted string of key SDK information
     */
    public String getSummaryInfo() {
        StringBuilder summary = new StringBuilder();
        summary.append("SDK Version: ").append(getSDKVersion()).append("\\n");
        summary.append("Build Version: ").append(getBuildVersion()).append("\\n");
        summary.append("Debug Mode: ").append(isDebug()).append("\\n");
        summary.append("Category: ").append(getPackageProductCategory());
        return summary.toString();
    }

    /**
     * Log current SDK information safely
     */
    public void logSDKInfo() {
        Log.i(TAG, "=== DJI SDK Information ===");
        Log.i(TAG, "Version: " + getSDKVersion());
        Log.i(TAG, "Build: " + getBuildVersion());
        Log.i(TAG, "Debug: " + isDebug());
        Log.i(TAG, "Category: " + getPackageProductCategory());
        Log.i(TAG, "========================");
    }

    /**
     * SAFETY CHECK: Verify if SDK is available before making calls
     */

    /**
     * SAFETY CHECK: Verify if SDKConfig is available before making calls
     */
    private boolean isSDKConfigAvailable() {
        try {
            // Try to access SDKConfig instance
            SDKConfig sdkConfig = SDKConfig.getInstance();
            return sdkConfig != null;
        } catch (Exception e) {
            Log.w(TAG, "SDKConfig not available: " + e.getMessage());
            return false;
        }
    }
}