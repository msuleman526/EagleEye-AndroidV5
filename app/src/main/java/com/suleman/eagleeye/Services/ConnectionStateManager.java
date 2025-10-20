package com.suleman.eagleeye.Services;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import dji.sdk.keyvalue.value.product.ProductType;

/**
 * Singleton class to manage drone connection state across the entire app
 * Allows any Activity/Fragment to observe connection status in real-time
 */
public class ConnectionStateManager {
    private static final String TAG = "ConnectionStateManager";
    private static ConnectionStateManager instance;

    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<ProductType> productType = new MutableLiveData<>(ProductType.UNKNOWN);
    private final MutableLiveData<String> droneName = new MutableLiveData<>("No Drone Connected");

    private ConnectionStateManager() {
        // Private constructor for singleton
    }

    public static synchronized ConnectionStateManager getInstance() {
        if (instance == null) {
            instance = new ConnectionStateManager();
        }
        return instance;
    }

    // Called from MainActivity to update connection state
    public void updateConnectionState(boolean connected, ProductType type, String name) {
        Log.d(TAG, String.format("📡 Connection state updated - Connected: %s, Type: %s, Name: %s",
                connected, type != null ? type.name() : "null", name));

        isConnected.postValue(connected);
        productType.postValue(type);
        droneName.postValue(name);
    }

    // Getters for LiveData (observe from any Activity/Fragment)
    public LiveData<Boolean> getConnectionState() {
        return isConnected;
    }

    public LiveData<ProductType> getProductType() {
        return productType;
    }

    public LiveData<String> getDroneName() {
        return droneName;
    }

    // Synchronous getters for immediate values
    public boolean isCurrentlyConnected() {
        Boolean value = isConnected.getValue();
        return value != null && value;
    }

    public ProductType getCurrentProductType() {
        ProductType value = productType.getValue();
        return value != null ? value : ProductType.UNKNOWN;
    }

    public String getCurrentDroneName() {
        String value = droneName.getValue();
        return value != null ? value : "No Drone Connected";
    }
}