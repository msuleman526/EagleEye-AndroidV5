package io.empowerbits.sightflight.Fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

public class StickModeFragment extends Fragment {

    private static final String TAG = "StickModeFragment";
    private RadioGroup stickModeToggle;
    private ImageView leftStickImage, rightStickImage;
    private Handler uiHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stick_mode, container, false);

        uiHandler = new Handler(Looper.getMainLooper());
        stickModeToggle = view.findViewById(R.id.stickModeToggle);
        leftStickImage = view.findViewById(R.id.leftStickImage);
        rightStickImage = view.findViewById(R.id.rightStickImage);

        stickModeToggle.setOnCheckedChangeListener((group, checkedId) -> {
            // Note: Stick mode configuration not available in current SDK version
            if (checkedId == R.id.btnMode1) {
                updateStickImages(R.drawable.stick_mode1_1, R.drawable.stick_mode1_2);
            } else if (checkedId == R.id.btnMode2) {
                updateStickImages(R.drawable.stick_mode2_1, R.drawable.stick_mode2_2);
            } else if (checkedId == R.id.btnMode3) {
                updateStickImages(R.drawable.stick_mode3_1, R.drawable.stick_mode3_2);
            }
            showToast("Use DJI Pilot app to change stick mode");
        });

        loadCurrentMode();

        return view;
    }

    private void loadCurrentMode() {
        // Note: Cannot read RC mode from SDK V5 - default to Mode 2
        uiHandler.post(() -> {
            stickModeToggle.check(R.id.btnMode2);
            updateStickImages(R.drawable.stick_mode2_1, R.drawable.stick_mode2_2);
            Log.d(TAG, "Defaulting to Mode 2 - use DJI Pilot to change");
        });
    }


    private void updateStickImages(int leftImageRes, int rightImageRes) {
        leftStickImage.setImageResource(leftImageRes);
        rightStickImage.setImageResource(rightImageRes);
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
