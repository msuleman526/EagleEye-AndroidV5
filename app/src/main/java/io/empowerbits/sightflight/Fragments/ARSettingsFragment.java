package io.empowerbits.sightflight.Fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;
import androidx.appcompat.widget.SwitchCompat;

import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;

/**
 * AR Settings Fragment - AR display preferences connected to DJI SDK V5
 * Uses FlightControllerKey to verify home location and aircraft data availability
 * for AR overlay features
 */
public class ARSettingsFragment extends Fragment {

    private static final String TAG = "ARSettingsFragment";
    private static final String PREFS_NAME = "ARSettings";
    private static final String KEY_SHOW_HOME_POINT = "show_ar_home_point";
    private static final String KEY_SHOW_RTH_ROUTE = "show_ar_rth_route";
    private static final String KEY_SHOW_AIRCRAFT_SHADOW = "show_ar_aircraft_shadow";

    private SwitchCompat arHomePointSwitch, arRthRouteSwitch, arAircraftShadowSwitch;
    private SharedPreferences prefs;
    private Handler uiHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ar_settings, container, false);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        uiHandler = new Handler(Looper.getMainLooper());

        arHomePointSwitch = view.findViewById(R.id.arHomePointSwitch);
        arRthRouteSwitch = view.findViewById(R.id.arRthRouteSwitch);
        arAircraftShadowSwitch = view.findViewById(R.id.arAircraftShadowSwitch);

        // Load saved preferences
        arHomePointSwitch.setChecked(prefs.getBoolean(KEY_SHOW_HOME_POINT, true));
        arRthRouteSwitch.setChecked(prefs.getBoolean(KEY_SHOW_RTH_ROUTE, true));
        arAircraftShadowSwitch.setChecked(prefs.getBoolean(KEY_SHOW_AIRCRAFT_SHADOW, true));

        // Setup listeners with SDK validation
        arHomePointSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Verify home point is available before enabling
                verifyHomePointAvailable(isChecked);
            } else {
                prefs.edit().putBoolean(KEY_SHOW_HOME_POINT, isChecked).apply();
            }
        });

        arRthRouteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Verify RTH capability before enabling
                verifyRTHAvailable(isChecked);
            } else {
                prefs.edit().putBoolean(KEY_SHOW_RTH_ROUTE, isChecked).apply();
            }
        });

        arAircraftShadowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Verify aircraft location data available
                verifyAircraftLocationAvailable(isChecked);
            } else {
                prefs.edit().putBoolean(KEY_SHOW_AIRCRAFT_SHADOW, isChecked).apply();
            }
        });

        // Validate current settings against SDK data
        validateARSettings();

        return view;
    }

    /**
     * Validate AR settings against current SDK data availability
     */
    private void validateARSettings() {
        // Check if home point is set
        checkHomePointStatus();

        // Check aircraft location availability
        checkAircraftLocationStatus();
    }

    /**
     * Verify home point is available using FlightControllerKey
     */
    private void verifyHomePointAvailable(boolean enableSetting) {
        DJIKey<Boolean> key = KeyTools.createKey(
                FlightControllerKey.KeyIsHomeLocationSet,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Boolean>() {
            @Override
            public void onSuccess(Boolean isSet) {
                uiHandler.post(() -> {
                    if (isSet != null && isSet) {
                        prefs.edit().putBoolean(KEY_SHOW_HOME_POINT, enableSetting).apply();
                        showToast("AR Home Point enabled");
                        Log.d(TAG, "Home point is set, AR overlay enabled");
                    } else {
                        arHomePointSwitch.setChecked(false);
                        showToast("Home point not set. Take off to set home point.");
                        Log.w(TAG, "Home point not available");
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to check home point: " + error.description());
                uiHandler.post(() -> {
                    arHomePointSwitch.setChecked(false);
                    showToast("Unable to verify home point status");
                });
            }
        });
    }

    /**
     * Verify RTH is available
     */
    private void verifyRTHAvailable(boolean enableSetting) {
        DJIKey<Boolean> key = KeyTools.createKey(
                FlightControllerKey.KeyIsHomeLocationSet,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<Boolean>() {
            @Override
            public void onSuccess(Boolean isSet) {
                uiHandler.post(() -> {
                    if (isSet != null && isSet) {
                        prefs.edit().putBoolean(KEY_SHOW_RTH_ROUTE, enableSetting).apply();
                        showToast("AR RTH Route enabled");
                        Log.d(TAG, "RTH available, AR route overlay enabled");
                    } else {
                        arRthRouteSwitch.setChecked(false);
                        showToast("Home point required for RTH route display");
                        Log.w(TAG, "RTH not available - no home point");
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to check RTH availability: " + error.description());
                uiHandler.post(() -> {
                    arRthRouteSwitch.setChecked(false);
                    showToast("Unable to verify RTH status");
                });
            }
        });
    }

    /**
     * Verify aircraft location data is available
     */
    private void verifyAircraftLocationAvailable(boolean enableSetting) {
        DJIKey<LocationCoordinate2D> key = KeyTools.createKey(
                FlightControllerKey.KeyAircraftLocation,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key, new CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate2D>() {
            @Override
            public void onSuccess(LocationCoordinate2D location) {
                uiHandler.post(() -> {
                    if (location != null && location.getLatitude() != 0 && location.getLongitude() != 0) {
                        prefs.edit().putBoolean(KEY_SHOW_AIRCRAFT_SHADOW, enableSetting).apply();
                        showToast("AR Aircraft Shadow enabled");
                        Log.d(TAG, "Aircraft location available, AR shadow enabled");
                    } else {
                        arAircraftShadowSwitch.setChecked(false);
                        showToast("Aircraft location not available");
                        Log.w(TAG, "Aircraft location data not valid");
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get aircraft location: " + error.description());
                uiHandler.post(() -> {
                    arAircraftShadowSwitch.setChecked(false);
                    showToast("Unable to verify aircraft location");
                });
            }
        });
    }

    /**
     * Check home point status for validation
     */
    private void checkHomePointStatus() {
        DJIKey<LocationCoordinate2D> homeKey = KeyTools.createKey(
                FlightControllerKey.KeyHomeLocation,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(homeKey, new CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate2D>() {
            @Override
            public void onSuccess(LocationCoordinate2D location) {
                if (location != null) {
                    Log.d(TAG, "Home point: " + location.getLatitude() + ", " + location.getLongitude());
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get home location: " + error.description());
            }
        });
    }

    /**
     * Check aircraft location status for validation
     */
    private void checkAircraftLocationStatus() {
        DJIKey<LocationCoordinate2D> locationKey = KeyTools.createKey(
                FlightControllerKey.KeyAircraftLocation,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(locationKey, new CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate2D>() {
            @Override
            public void onSuccess(LocationCoordinate2D location) {
                if (location != null) {
                    Log.d(TAG, "Aircraft location: " + location.getLatitude() + ", " + location.getLongitude());
                }
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.e(TAG, "Failed to get aircraft location: " + error.description());
            }
        });
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
