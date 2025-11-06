package com.empowerbits.dronifyit.util;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermissionHelper - Handle runtime permissions for Android 11+ storage access
 * Required for external storage access in Downloads/EagleEye/ folder
 */
public class PermissionHelper {
    private static final String TAG = "PermissionHelper";
    
    // Permission request codes
    public static final int STORAGE_PERMISSION_REQUEST_CODE = 2001;
    public static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 2002;
    
    /**
     * Check if storage permissions are granted
     */
    public static boolean hasStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Check MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 and below - Check WRITE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Request storage permissions based on Android version
     */
    public static void requestStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Request MANAGE_EXTERNAL_STORAGE
            requestManageExternalStoragePermission(activity);
        } else {
            // Android 10 and below - Request WRITE_EXTERNAL_STORAGE
            requestWriteExternalStoragePermission(activity);
        }
    }
    
    /**
     * Request MANAGE_EXTERNAL_STORAGE permission for Android 11+
     */
    private static void requestManageExternalStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    // Fallback to general storage settings
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
                }
            }
        }
    }
    
    /**
     * Request WRITE_EXTERNAL_STORAGE permission for Android 10 and below
     */
    private static void requestWriteExternalStoragePermission(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            }
            
            ActivityCompat.requestPermissions(activity,
                new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
                STORAGE_PERMISSION_REQUEST_CODE);
        }
    }
    
    /**
     * Handle permission result
     */
    public static boolean handlePermissionResult(Activity activity, int requestCode, 
            String[] permissions, int[] grantResults) {
        
        switch (requestCode) {
            case STORAGE_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return true;
                } else {
                    return false;
                }
                
            case MANAGE_EXTERNAL_STORAGE_REQUEST_CODE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Toast.makeText(activity, "Storage permission granted", Toast.LENGTH_SHORT).show();
                        return true;
                    } else {
                        Toast.makeText(activity, 
                            "Storage permission denied. KMZ files cannot be saved to Downloads folder.", 
                            Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
                break;
        }
        
        return false;
    }
    
    /**
     * Show storage permission explanation dialog
     */
    public static void showStoragePermissionExplanation(Activity activity) {

    }
}
