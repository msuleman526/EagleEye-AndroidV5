package io.empowerbits.sightflight.models;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Global ViewModel Store for sharing ViewModels across the entire application
 * Java equivalent of GlobalVM.kt
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class GlobalViewModelStore {
    
    private static GlobalViewModelStore instance;
    
    private GlobalViewModelStore() {
        // Private constructor for singleton
    }
    
    public static GlobalViewModelStore getInstance() {
        if (instance == null) {
            instance = new GlobalViewModelStore();
        }
        return instance;
    }
    
    private static final ViewModelStore globalViewModelStore = new ViewModelStore();
    private static final Map<Class<?>, ViewModel> globalViewModels = new HashMap<>();
    
    /**
     * Get a global ViewModel instance
     */
    @SuppressWarnings("unchecked")
    public static <T extends ViewModel> T getGlobalViewModel(Class<T> modelClass) {
        ViewModel viewModel = globalViewModels.get(modelClass);
        if (viewModel == null) {
            try {
                viewModel = modelClass.newInstance();
                globalViewModels.put(modelClass, viewModel);
            } catch (Exception e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
        return (T) viewModel;
    }
    
    /**
     * Instance method to get a global ViewModel with key
     */
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T get(String key, Class<T> modelClass) {
        // For simplicity, we'll use the class as the key and ignore the string key
        // This matches the usage pattern from MainActivity
        return getGlobalViewModel(modelClass);
    }
    
    /**
     * Clear all global ViewModels
     */
    public static void clear() {
        globalViewModelStore.clear();
        globalViewModels.clear();
    }
    
    /**
     * Get the global ViewModelStore
     */
    public static ViewModelStore getViewModelStore() {
        return globalViewModelStore;
    }
}
