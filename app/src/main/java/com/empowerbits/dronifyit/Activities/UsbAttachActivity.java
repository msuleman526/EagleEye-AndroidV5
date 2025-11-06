package com.empowerbits.dronifyit.Activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * USB Attach Activity for handling DJI drone USB connections
 * Java equivalent of UsbAttachActivity.java from sample code
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class UsbAttachActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // When USB device is attached, redirect to MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
