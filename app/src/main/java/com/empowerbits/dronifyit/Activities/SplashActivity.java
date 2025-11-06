package com.empowerbits.dronifyit.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.empowerbits.dronifyit.R;
import com.empowerbits.dronifyit.util.UserSessionManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000; // 5 seconds
    UserSessionManager sessionManager;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        sessionManager = new UserSessionManager(getApplicationContext());
        if(sessionManager.getToken().equals("") || sessionManager.getUserId() == 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish(); // optional: close SplashActivity
            }, SPLASH_DELAY);
        }else{
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish(); // optional: close SplashActivity
            }, SPLASH_DELAY);
        }
    }
}