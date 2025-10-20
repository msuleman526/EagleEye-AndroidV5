package com.suleman.eagleeye.util;

import android.content.Context;
import android.util.Log;

import com.suleman.eagleeye.Services.TelemetryService;

/**
 * ServiceManager - Centralized service initialization and management
 * 
 * Provides easy initialization and management of all EagleEye services
 * Ensures proper service startup order and dependency management
 * 
 * Usage:
 * ServiceManager.getInstance(context).initializeAllServices();
 * 
 * @author Suleman
 * @version 1.0
 * @date 2025-06-27
 */
public class ServiceManager {
    private static final String TAG = "ServiceManager";
    
    private static ServiceManager instance;
    private static final Object LOCK = new Object();
    private Context context;
    private TelemetryService telemetryService;
    
    private boolean servicesInitialized = false;
    
    private ServiceManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static ServiceManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new ServiceManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize all EagleEye services
     * This should be called after DJI SDK registration
     */
    public void initializeAllServices() {
        if (servicesInitialized) {
            Log.d(TAG, "Services already initialized");
            return;
        }
        
        try {
            // Initialize TelemetryService
            telemetryService = TelemetryService.getInstance(context);
            telemetryService.initializeTelemetryService();
            
            // Start telemetry monitoring
            telemetryService.startTelemetryMonitoring();
            
            servicesInitialized = true;
            
            Log.i(TAG, "All EagleEye services initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize services: " + e.getMessage(), e);
            servicesInitialized = false;
        }
    }
    
    /**
     * Get TelemetryService instance
     */
    public TelemetryService getTelemetryService() {
        if (telemetryService == null) {
            telemetryService = TelemetryService.getInstance(context);
        }
        return telemetryService;
    }
    
    /**
     * Check if services are initialized
     */
    public boolean isServicesInitialized() {
        return servicesInitialized;
    }
    
    /**
     * Cleanup all services
     */
    public void cleanup() {
        try {
            Log.i(TAG, "Cleaning up all services...");
            
            if (telemetryService != null) {
                telemetryService.cleanup();
            }
            
            servicesInitialized = false;
            
            Log.i(TAG, "All services cleaned up successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up services: " + e.getMessage(), e);
        }
    }
    
    /**
     * Reset ServiceManager instance
     */
    public static void resetInstance() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.cleanup();
                instance = null;
            }
        }
    }
}
