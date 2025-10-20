package com.suleman.eagleeye.models;

import android.content.Context;
import androidx.lifecycle.MutableLiveData;
import dji.v5.common.error.IDJIError;
import dji.v5.common.register.DJISDKInitEvent;
import dji.v5.manager.SDKManager;
import dji.v5.manager.interfaces.SDKManagerCallback;
import dji.v5.network.DJINetworkManager;

/**
 * MSDK Manager ViewModel - Java equivalent of MSDKManagerVM.kt
 * Manages DJI SDK initialization, registration, and connection states
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class MSDKManagerVM extends DJIViewModel {
    
    // LiveData for registration state - Pair<Boolean, IDJIError?>
    private final MutableLiveData<RegisterResult> lvRegisterState = new MutableLiveData<>();
    
    // LiveData for product connection state - Pair<Boolean, Integer>
    private final MutableLiveData<ConnectionResult> lvProductConnectionState = new MutableLiveData<>();
    
    // LiveData for product changes
    private final MutableLiveData<Integer> lvProductChanges = new MutableLiveData<>();
    
    // LiveData for initialization process - Pair<DJISDKInitEvent, Integer>
    private final MutableLiveData<InitResult> lvInitProcess = new MutableLiveData<>();
    
    // LiveData for database download progress - Pair<Long, Long>
    private final MutableLiveData<DownloadResult> lvDBDownloadProgress = new MutableLiveData<>();
    
    private boolean isInit = false;
    
    /**
     * Initialize DJI Mobile SDK
     */
    public void initMobileSDK(Context appContext) {
        logd("Initializing DJI Mobile SDK...");
        
        // Initialize and set the SDK callback
        SDKManager.getInstance().init(appContext, new SDKManagerCallback() {
            @Override
            public void onRegisterSuccess() {
                logi("DJI SDK registration successful");
                lvRegisterState.postValue(new RegisterResult(true, null));
            }

            @Override
            public void onRegisterFailure(IDJIError error) {
                loge("DJI SDK registration failed: " + error.description());
                lvRegisterState.postValue(new RegisterResult(false, error));
            }

            @Override
            public void onProductDisconnect(int productId) {
                logi("🔌 PRODUCT DISCONNECTED - ID: " + productId);
                // Validate product ID
                if (productId < 0 || productId > 100) {
                    logw("⚠️ Invalid product ID on disconnect: " + productId);
                }
                lvProductConnectionState.postValue(new ConnectionResult(false, productId));
            }

            @Override
            public void onProductConnect(int productId) {
                logi("🔗 PRODUCT CONNECTED - ID: " + productId);
                // Validate product ID
                if (productId <= 0) {
                    logw("⚠️ Invalid product ID on connect: " + productId + " (should be > 0)");
                } else {
                    logi("✅ Valid product ID: " + productId);
                }
                lvProductConnectionState.postValue(new ConnectionResult(true, productId));
            }

            @Override
            public void onProductChanged(int productId) {
                logi("🔄 PRODUCT CHANGED - ID: " + productId);
                // Validate product ID
                if (productId <= 0) {
                    logw("⚠️ Invalid product ID on change: " + productId);
                } else {
                    logi("✅ Product changed to valid ID: " + productId);
                }
                lvProductChanges.postValue(productId);
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
                logd("⚙️ SDK init process: " + event.name() + " (" + totalProcess + "%)");
                lvInitProcess.postValue(new InitResult(event, totalProcess));
                
                // Register app when initialization is complete
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    isInit = true;
                    logi("✅ SDK initialization complete. Registering app...");
                    SDKManager.getInstance().registerApp();
                }
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
                int percentage = total > 0 ? (int) ((current * 100) / total) : 0;
                logd("📥 Database download progress: " + percentage + "% (" + current + "/" + total + ")");
                lvDBDownloadProgress.postValue(new DownloadResult(current, total));
            }
        });
        
        // Add network status listener for automatic re-registration
        DJINetworkManager.getInstance().addNetworkStatusListener(isAvailable -> {
            logd("🌐 Network status changed: " + (isAvailable ? "Available" : "Unavailable"));
            if (isInit && isAvailable && !SDKManager.getInstance().isRegistered()) {
                logi("🔄 Network available, attempting to register app...");
                SDKManager.getInstance().registerApp();
            }
        });
    }
    
    /**
     * Destroy DJI Mobile SDK
     */
    public void destroyMobileSDK() {
        logd("💥 Destroying DJI Mobile SDK...");
        SDKManager.getInstance().destroy();
        isInit = false;
    }
    
    /**
     * Get current SDK and product information for debugging
     */
    public void debugCurrentStatus() {
        logd("📊 DEBUGGING CURRENT STATUS:");
        logd("🔹 SDK Initialized: " + isInit);
        logd("🔹 SDK Registered: " + SDKManager.getInstance().isRegistered());
        
        try {
            // Try to get current product
            if (SDKManager.getInstance().getProductCategory() != null) {
                logi("✅ Product instance exists: " + SDKManager.getInstance().getProductCategory().getClass().getSimpleName());
            } else {
                logw("⚠️ No product instance available");
            }
        } catch (Exception e) {
            loge("❌ Error getting product instance: " + e.getMessage());
        }
    }
    
    // Getters for LiveData
    public MutableLiveData<RegisterResult> getLvRegisterState() {
        return lvRegisterState;
    }
    
    public MutableLiveData<ConnectionResult> getLvProductConnectionState() {
        return lvProductConnectionState;
    }
    
    public MutableLiveData<Integer> getLvProductChanges() {
        return lvProductChanges;
    }
    
    public MutableLiveData<InitResult> getLvInitProcess() {
        return lvInitProcess;
    }
    
    public MutableLiveData<DownloadResult> getLvDBDownloadProgress() {
        return lvDBDownloadProgress;
    }
    
    public boolean isInitialized() {
        return isInit;
    }
    
    /**
     * Check if SDK is properly registered
     */
    public boolean isRegistered() {
        return SDKManager.getInstance().isRegistered();
    }
    
    /**
     * Get current product instance (for debugging)
     */
    public Object getCurrentProduct() {
        try {
            return SDKManager.getInstance().getProductCategory();
        } catch (Exception e) {
            loge("❌ Error getting current product: " + e.getMessage());
            return null;
        }
    }
    
    // Result classes to replace Kotlin Pair
    public static class RegisterResult {
        public final boolean success;
        public final IDJIError error;
        
        public RegisterResult(boolean success, IDJIError error) {
            this.success = success;
            this.error = error;
        }
    }
    
    public static class ConnectionResult {
        public final boolean connected;
        public final int productId;
        
        public ConnectionResult(boolean connected, int productId) {
            this.connected = connected;
            this.productId = productId;
        }
    }
    
    public static class InitResult {
        public final DJISDKInitEvent event;
        public final int totalProcess;
        
        public InitResult(DJISDKInitEvent event, int totalProcess) {
            this.event = event;
            this.totalProcess = totalProcess;
        }
    }
    
    public static class DownloadResult {
        public final long current;
        public final long total;
        
        public DownloadResult(long current, long total) {
            this.current = current;
            this.total = total;
        }
    }
}
