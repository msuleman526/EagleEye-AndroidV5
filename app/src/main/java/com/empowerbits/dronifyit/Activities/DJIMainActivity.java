package com.empowerbits.dronifyit.Activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.empowerbits.dronifyit.models.GlobalViewModelStore;
import com.empowerbits.dronifyit.models.MSDKManagerVM;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base DJI Main Activity - Java equivalent of DJIMainActivity.kt
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public abstract class DJIMainActivity extends AppCompatActivity {

    private static final String TAG = "DJIMainActivity";
    
    private final List<String> permissionArray = new ArrayList<>();
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private Handler handler;
    private MSDKManagerVM msdkManagerVM;
    
    // Abstract methods for subclasses to implement
    public abstract void prepareUxActivity();
    public abstract void prepareTestingToolsActivity();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if app was restarted from system launcher
        if (!isTaskRoot() && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            finish();
            return;
        }
        // Initialize components
        initializePermissions();
        initializeHandler();
        initializeViewModels();
        observeSDKManager();
        checkPermissionAndRequest();
    }
    
    private void initializePermissions() {
        permissionArray.add(Manifest.permission.RECORD_AUDIO);
        permissionArray.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionArray.add(Manifest.permission.ACCESS_FINE_LOCATION);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionArray.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissionArray.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissionArray.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            permissionArray.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissionArray.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        // Initialize permission launcher
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::handlePermissionResults
        );
    }
    
    private void initializeHandler() {
        handler = new Handler(Looper.getMainLooper());
    }
    
    private void initializeViewModels() {
        msdkManagerVM = GlobalViewModelStore.getGlobalViewModel(MSDKManagerVM.class);
    }
    
    private void observeSDKManager() {
        // Observe registration state
        msdkManagerVM.getLvRegisterState().observe(this, resultPair -> {
            if (resultPair.success) {
                showToast("Register Success");
                // Delay before preparing UX activity
                handler.postDelayed(this::prepareUxActivity, 5000);
            } else {
                showToast("Register Failure: " + (resultPair.error != null ? resultPair.error.description() : "Unknown error"));
            }
        });

        // Observe product connection state
        msdkManagerVM.getLvProductConnectionState().observe(this, resultPair -> {
            //showToast("Product: " + resultPair.productId + " ,ConnectionState: " + resultPair.connected);
        });
        
        // Observe product changes
        msdkManagerVM.getLvProductChanges().observe(this, productId -> {
            //showToast("Product: " + productId + " Changed");
        });
        
        // Observe initialization process
        msdkManagerVM.getLvInitProcess().observe(this, processPair -> {
            //showToast("Init Process event: " + processPair.event.name());
        });
        
        // Observe database download progress
        msdkManagerVM.getLvDBDownloadProgress().observe(this, resultPair -> {
            //showToast("Database Download Progress current: " + resultPair.current + ", total: " + resultPair.total);
        });
    }
    
    private void checkPermissionAndRequest() {
        if (!checkPermission()) {
            requestPermission();
        }
    }
    
    private boolean checkPermission() {
        for (String permission : permissionArray) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void requestPermission() {
        requestPermissionLauncher.launch(permissionArray.toArray(new String[0]));
    }
    
    private void handlePermissionResults(Map<String, Boolean> result) {
        boolean allGranted = true;
        for (Boolean granted : result.values()) {
            if (!granted) {
                allGranted = false;
                break;
            }
        }
        
        if (allGranted) {
            handleAfterPermissionPermitted();
        } else {
            showToast("Some permissions are required for drone operation");
        }
    }
    
    private void handleAfterPermissionPermitted() {
        prepareTestingToolsActivity();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission()) {
            handleAfterPermissionPermitted();
        }
    }
    
    protected void showToast(String content) {
        Toast.makeText(this, content, Toast.LENGTH_SHORT).show();
    }
    
    public MSDKManagerVM getMsdkManagerVM() {
        return msdkManagerVM;
    }
    
    public GlobalViewModelStore getGlobalViewModelStore() {
        return GlobalViewModelStore.getInstance();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
