package com.empowerbits.dronifyit.models;

import androidx.lifecycle.ViewModel;
import android.util.Log;

/**
 * Base ViewModel class for DJI operations
 * Java equivalent of DJIViewModel.kt
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public abstract class DJIViewModel extends ViewModel {
    
    protected final String logTag;
    
    public DJIViewModel() {
        this.logTag = getClass().getSimpleName();
    }
    
    protected void logd(String message) {
        Log.d(logTag, message);
    }
    
    protected void logi(String message) {
        Log.i(logTag, message);
    }
    
    protected void logw(String message) {
        Log.w(logTag, message);
    }
    
    protected void loge(String message) {
        Log.e(logTag, message);
    }
    
    protected void loge(String message, Throwable throwable) {
        Log.e(logTag, message, throwable);
    }
}
