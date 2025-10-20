package com.suleman.eagleeye.models;

import androidx.lifecycle.MutableLiveData;

import com.suleman.eagleeye.data.MSDKInfo;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.key.ProductKey;
import dji.sdk.keyvalue.value.product.ProductType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;
import dji.v5.manager.SDKManager;
import dji.v5.manager.areacode.AreaCodeChangeListener;
import dji.v5.manager.areacode.AreaCodeManager;
import dji.v5.manager.ldm.LDMManager;
import dji.v5.network.DJINetworkManager;
import dji.v5.network.IDJINetworkStatusListener;

/**
 * MSDK Information ViewModel - Enhanced with comprehensive debugging
 * 
 * CRITICAL ENHANCEMENT: Added multiple detection methods and extensive logging
 * to diagnose why "No Drone Connected" issue persists with real hardware
 * 
 * Features:
 * - Real-time ProductType monitoring via KeyManager.listen()
 * - Fallback polling-based product type detection
 * - Comprehensive connection state logging
 * - Multiple detection approaches for reliability
 * - Extensive debugging for real hardware testing
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class MSDKInfoVm extends DJIViewModel {

    private final MutableLiveData<MSDKInfo> msdkInfo = new MutableLiveData<>();
    private final MutableLiveData<String> mainTitle = new MutableLiveData<>();
    
    // CRITICAL: Real-time ProductType LiveData
    private final MutableLiveData<ProductType> currentProductType = new MutableLiveData<>();
    
    private final AtomicBoolean isInited = new AtomicBoolean(false);
    private final MSDKInfoModel msdkInfoModel = new MSDKInfoModel();
    
    private AreaCodeChangeListener areaCodeChangeListener;
    private IDJINetworkStatusListener netWorkStatusListener;
    
    // DEBUGGING: Add fallback timer for polling detection
    private Timer productTypePollingTimer;

    /**
     * SAFE CONSTRUCTOR: No SDK-dependent calls to prevent initialization crashes
     */
    public MSDKInfoVm() {
        logi("🏗️ MSDKInfoVm constructor - creating safe instance");
        
        // Initialize basic info WITHOUT SDK dependencies
        initializeBasicMSDKInfo();
        
        // Setup listeners (but don't register them yet)
        setupListeners();
        
        // Initialize with UNKNOWN product type
        currentProductType.setValue(ProductType.UNKNOWN);
        
        logi("✅ MSDKInfoVm constructor completed safely");
    }

    /**
     * SAFE INITIALIZATION: Initialize basic info without SDK dependencies
     */
    private void initializeBasicMSDKInfo() {
        try {
            MSDKInfo info = msdkInfoModel.createMSDKInfo();
            info.setIsLDMEnabled("false");
            info.setIsLDMLicenseLoaded("false");
            msdkInfo.setValue(info);
            logi("✅ Basic MSDK info initialized safely");
        } catch (Exception e) {
            loge("❌ Failed to initialize basic MSDK info: " + e.getMessage());
            MSDKInfo fallbackInfo = new MSDKInfo("5.15.0");
            fallbackInfo.setIsLDMEnabled("false");
            fallbackInfo.setIsLDMLicenseLoaded("false");
            msdkInfo.setValue(fallbackInfo);
        }
    }

    private void setupListeners() {
        areaCodeChangeListener = (oldAreaCode, newAreaCode) -> {
            logi("🌍 Area code changed: " + oldAreaCode + " → " + newAreaCode);
            MSDKInfo info = msdkInfo.getValue();
            if (info != null) {
                info.setCountryCode(newAreaCode != null ? newAreaCode.getAreaCode() : MSDKInfo.DEFAULT_STR);
                refreshMSDKInfo();
            }
        };

        netWorkStatusListener = isAvailable -> {
            logi("🌐 Network status changed: " + (isAvailable ? "Available" : "Unavailable"));
            updateNetworkInfo(isAvailable);
            refreshMSDKInfo();
        };
    }

    /**
     * ENHANCED: Initialize listeners with comprehensive debugging
     */
    public void initListener() {
        if (!SDKManager.getInstance().isRegistered()) {
            logw("⚠️ SDK not registered yet, cannot initialize listeners");
            return;
        }
        
        if (isInited.getAndSet(true)) {
            logd("ℹ️ Listeners already initialized");
            return;
        }

        try {
            logi("🚀 Initializing SDK-dependent components with enhanced debugging...");
            
            // Log current SDK state for debugging
            debugCurrentSDKState();
            
            // Initialize LDM status safely
            updateLDMStatusSafely();
            
            // Add listeners
            AreaCodeManager.getInstance().addAreaCodeChangeListener(areaCodeChangeListener);
            DJINetworkManager.getInstance().addNetworkStatusListener(netWorkStatusListener);
            
            // CRITICAL: Multiple product type detection approaches
            initProductTypeListener();           // Real-time listener
            startProductTypePolling();           // Fallback polling
            performImmediateProductCheck();      // Immediate check
            
            // Update firmware version
            updateFirmwareVersion();

            logi("✅ All listeners initialized successfully with debugging enabled");
        } catch (Exception e) {
            loge("❌ Failed to initialize listeners: " + e.getMessage());
            logw("⚠️ Continuing with limited functionality");
        }
    }

    /**
     * DEBUGGING: Log current SDK state for analysis
     */
    private void debugCurrentSDKState() {
        try {
            logi("📊 === SDK STATE DEBUG ===");
            logi("🔹 SDK Registered: " + SDKManager.getInstance().isRegistered());
            logi("🔹 SDK Version: " + SDKManager.getInstance().getSDKVersion());
            
            // Check if product instance exists
            try {
                Object product = SDKManager.getInstance().getProductCategory();
                logi("🔹 Product Instance: " + (product != null ? product.getClass().getSimpleName() : "null"));
            } catch (Exception e) {
                logi("🔹 Product Instance: Error - " + e.getMessage());
            }
            
            // Check product category
            try {
                Object category = SDKManager.getInstance().getProductCategory();
                logi("🔹 Product Category: " + (category != null ? category.toString() : "null"));
            } catch (Exception e) {
                logi("🔹 Product Category: Error - " + e.getMessage());
            }
            
            logi("📊 === END SDK STATE DEBUG ===");
        } catch (Exception e) {
            loge("❌ Failed to debug SDK state: " + e.getMessage());
        }
    }

    /**
     * ENHANCED: Real-time product type listener with extensive logging
     */
    private void initProductTypeListener() {
        try {
            logi("🔍 Initializing real-time product type listener...");
            
            DJIKey<ProductType> productTypeKey = KeyTools.createKey(ProductKey.KeyProductType, 0, 0, 0, 0);
            logi("🔑 Created ProductType key: " + productTypeKey);
            
            KeyManager.getInstance().listen(productTypeKey, this, new CommonCallbacks.KeyListener<ProductType>() {
                @Override
                public void onValueChange(ProductType oldValue, ProductType newValue) {
                    logi("🔄 PRODUCT TYPE CHANGE DETECTED!");
                    logi("   Old Value: " + (oldValue != null ? oldValue.name() : "null"));
                    logi("   New Value: " + (newValue != null ? newValue.name() : "null"));
                    
                    if (newValue != null && newValue != ProductType.UNKNOWN) {
                        logi("🚁 VALID PRODUCT TYPE DETECTED: " + newValue.name());
                        currentProductType.postValue(newValue);
                        
                        MSDKInfo info = msdkInfo.getValue();
                        if (info != null) {
                            info.setProductType(newValue);
                            refreshMSDKInfo();
                        }
                        
                        logi("✅ Product type updated successfully: " + newValue.name());
                    } else {
                        logw("⚠️ Product type is null or unknown: " + newValue);
                        currentProductType.postValue(ProductType.UNKNOWN);
                    }
                }
            });
            
            logi("✅ Product type listener initialized successfully");
            
        } catch (Exception e) {
            loge("❌ Exception while initializing product type listener: " + e.getMessage());
            e.printStackTrace();
            currentProductType.setValue(ProductType.UNKNOWN);
        }
    }

    /**
     * FALLBACK: Polling-based product type detection
     * In case the real-time listener doesn't work with hardware
     */
    private void startProductTypePolling() {
        try {
            logi("🔄 Starting fallback product type polling...");
            
            if (productTypePollingTimer != null) {
                productTypePollingTimer.cancel();
            }
            
            productTypePollingTimer = new Timer();
            productTypePollingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        pollProductType();
                    } catch (Exception e) {
                        logw("⚠️ Error during product type polling: " + e.getMessage());
                    }
                }
            }, 2000, 5000); // Check every 5 seconds after 2 second delay
            
            logi("✅ Product type polling started successfully");
            
        } catch (Exception e) {
            loge("❌ Failed to start product type polling: " + e.getMessage());
        }
    }

    /**
     * POLLING METHOD: Direct product type check
     */
    private void pollProductType() {
        try {
            logi("🔍 Polling for product type...");
            
            DJIKey<ProductType> productTypeKey = KeyTools.createKey(ProductKey.KeyProductType, 0, 0, 0, 0);
            
            KeyManager.getInstance().getValue(productTypeKey, new CommonCallbacks.CompletionCallbackWithParam<ProductType>() {
                @Override
                public void onSuccess(ProductType productType) {
                    logi("📡 Polling result: " + (productType != null ? productType.name() : "null"));
                    
                    if (productType != null && productType != ProductType.UNKNOWN) {
                        ProductType currentType = currentProductType.getValue();
                        if (currentType != productType) {
                            logi("🔄 Product type changed via polling: " + currentType + " → " + productType);
                            currentProductType.postValue(productType);
                            
                            MSDKInfo info = msdkInfo.getValue();
                            if (info != null) {
                                info.setProductType(productType);
                                refreshMSDKInfo();
                            }
                        }
                    }
                }

                @Override
                public void onFailure(IDJIError error) {
                    logd("📡 Polling failed: " + error.description());
                }
            });
            
        } catch (Exception e) {
            logw("⚠️ Exception during product type polling: " + e.getMessage());
        }
    }

    /**
     * IMMEDIATE CHECK: Check product type right now
     */
    private void performImmediateProductCheck() {
        try {
            logi("⚡ Performing immediate product type check...");
            
            // Wait a bit for SDK to stabilize, then check
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    pollProductType();
                }
            }, 1000); // 1 second delay
            
        } catch (Exception e) {
            loge("❌ Failed immediate product check: " + e.getMessage());
        }
    }

    /**
     * SAFE LDM STATUS UPDATE
     */
    private void updateLDMStatusSafely() {
        try {
            if (SDKManager.getInstance().isRegistered()) {
                MSDKInfo info = msdkInfo.getValue();
                if (info != null) {
                    info.setIsLDMEnabled(String.valueOf(LDMManager.getInstance().isLDMEnabled()));
                    info.setIsLDMLicenseLoaded(String.valueOf(LDMManager.getInstance().isLDMLicenseLoaded()));
                    logi("✅ LDM status updated successfully");
                }
            } else {
                logw("⚠️ SDK not registered, using default LDM values");
            }
        } catch (Exception e) {
            logw("⚠️ Failed to get LDM status: " + e.getMessage() + " - using defaults");
            MSDKInfo info = msdkInfo.getValue();
            if (info != null) {
                info.setIsLDMEnabled("false");
                info.setIsLDMLicenseLoaded("false");
            }
        }
    }

    /**
     * Update firmware version
     */
    private void updateFirmwareVersion() {
        try {
            logi("🔄 Updating firmware version...");
            
            DJIKey<String> firmwareKey = KeyTools.createKey(ProductKey.KeyFirmwareVersion, 0, 0, 0, 0);
            
            KeyManager.getInstance().getValue(firmwareKey, new CommonCallbacks.CompletionCallbackWithParam<String>() {
                @Override
                public void onSuccess(String firmwareVersion) {
                    logi("📋 Firmware version: " + firmwareVersion);
                    MSDKInfo info = msdkInfo.getValue();
                    if (info != null) {
                        info.setFirmwareVer(firmwareVersion != null ? firmwareVersion : MSDKInfo.DEFAULT_STR);
                        refreshMSDKInfo();
                    }
                }

                @Override
                public void onFailure(IDJIError error) {
                    logw("❌ Failed to get firmware version: " + error.description());
                    MSDKInfo info = msdkInfo.getValue();
                    if (info != null) {
                        info.setFirmwareVer(MSDKInfo.DEFAULT_STR);
                        refreshMSDKInfo();
                    }
                }
            });
        } catch (Exception e) {
            loge("❌ Exception while updating firmware version: " + e.getMessage());
        }
    }

    /**
     * DEBUGGING: Manual refresh method to force detection
     */
    public void forceProductTypeRefresh() {
        logi("🔄 FORCE REFRESH: Manually checking product type...");
        performImmediateProductCheck();
        pollProductType();
    }

    /**
     * Remove all listeners
     */
    private void removeListener() {
        try {
            // Stop polling timer
            if (productTypePollingTimer != null) {
                productTypePollingTimer.cancel();
                productTypePollingTimer = null;
            }
            
            KeyManager.getInstance().cancelListen(this);
            
            if (areaCodeChangeListener != null) {
                AreaCodeManager.getInstance().removeAreaCodeChangeListener(areaCodeChangeListener);
            }
            if (netWorkStatusListener != null) {
                DJINetworkManager.getInstance().removeNetworkStatusListener(netWorkStatusListener);
            }
            
            logi("✅ All listeners removed successfully");
        } catch (Exception e) {
            loge("❌ Failed to remove listeners: " + e.getMessage());
        }
    }

    private void updateNetworkInfo(boolean isAvailable) {
        MSDKInfo info = msdkInfo.getValue();
        if (info != null) {
            info.setNetworkInfo(isAvailable ? MSDKInfo.ONLINE_STR : MSDKInfo.NO_NETWORK_STR);
        }
    }

    public void updateLDMStatus() {
        updateLDMStatusSafely();
        refreshMSDKInfo();
    }

    public void refreshMSDKInfo() {
        MSDKInfo info = msdkInfo.getValue();
        if (info != null) {
            msdkInfo.postValue(info);
        }
    }

    public String getSummaryString() {
        MSDKInfo info = msdkInfo.getValue();
        if (info == null) {
            return "No SDK information available";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("SDK Version: ").append(info.getSDKVersion()).append("\\n");
        summary.append("Build Version: ").append(info.getBuildVer()).append("\\n");
        summary.append("Product: ").append(info.getProductType().name()).append("\\n");
        summary.append("Firmware: ").append(info.getFirmwareVer()).append("\\n");
        summary.append("Network: ").append(info.getNetworkInfo()).append("\\n");
        summary.append("Country: ").append(info.getCountryCode());
        
        return summary.toString();
    }

    // Getters
    public MutableLiveData<MSDKInfo> getMsdkInfo() {
        return msdkInfo;
    }

    public MutableLiveData<String> getMainTitle() {
        return mainTitle;
    }
    
    public MutableLiveData<ProductType> getCurrentProductType() {
        return currentProductType;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        removeListener();
        logi("🧹 MSDKInfoVm cleared");
    }
}