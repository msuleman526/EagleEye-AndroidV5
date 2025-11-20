package io.empowerbits.sightflight.Fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

import java.util.List;

import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.KeyManager;

/**
 * Battery Info Fragment - Display detailed battery information
 */
public class BatteryInfoFragment extends Fragment {

    private static final String TAG = "BatteryInfo";

    private ImageView batteryCell1Icon, batteryCell2Icon;
    private TextView batteryCell1Voltage, batteryCell2Voltage;
    private TextView batteryVoltage, batteryTemperature;
    private TextView batterySerialNumber, batteryCycleCount;

    private Handler uiHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_info, container, false);

        uiHandler = new Handler(Looper.getMainLooper());

        batteryCell1Icon = view.findViewById(R.id.batteryCell1Icon);
        batteryCell2Icon = view.findViewById(R.id.batteryCell2Icon);
        batteryCell1Voltage = view.findViewById(R.id.batteryCell1Voltage);
        batteryCell2Voltage = view.findViewById(R.id.batteryCell2Voltage);
        batteryVoltage = view.findViewById(R.id.batteryVoltage);
        batteryTemperature = view.findViewById(R.id.batteryTemperature);
        batterySerialNumber = view.findViewById(R.id.batterySerialNumber);
        batteryCycleCount = view.findViewById(R.id.batteryCycleCount);

        // Load battery information
        loadBatteryInfo();

        return view;
    }

    private void loadBatteryInfo() {
        getBatteryChargePercentage();
        getBatteryCellVoltages();
        getBatteryVoltage();
        getBatteryTemperature();
        getBatterySerialNumber();
        getBatteryCycleCount();
    }

    private void getBatteryChargePercentage() {
        DJIKey<Integer> key = KeyTools.createKey(
                BatteryKey.KeyChargeRemainingInPercent,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer percentage) {
                        uiHandler.post(() -> {
                            updateBatteryIcons(percentage);
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get battery percentage: " + error.description());
                    }
                }
        );
    }

    private void updateBatteryIcons(int percentage) {
        int batteryIcon;

        if (percentage < 20) {
            batteryIcon = R.drawable.battery_big_1;
        } else if (percentage < 40) {
            batteryIcon = R.drawable.battery_big_2;
        } else if (percentage < 55) {
            batteryIcon = R.drawable.battery_big_3;
        } else if (percentage < 70) {
            batteryIcon = R.drawable.battery_big_4;
        } else if (percentage < 85) {
            batteryIcon = R.drawable.battery_big_5;
        } else {
            batteryIcon = R.drawable.battery_big_6;
        }

        batteryCell1Icon.setImageResource(batteryIcon);
        batteryCell2Icon.setImageResource(batteryIcon);
    }

    private void getBatteryCellVoltages() {
        DJIKey<List<Integer>> key = KeyTools.createKey(
                BatteryKey.KeyCellVoltages,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<List<Integer>>() {
                    @Override
                    public void onSuccess(List<Integer> voltages) {
                        uiHandler.post(() -> {
                            if (voltages != null && voltages.size() >= 2) {
                                batteryCell1Voltage.setText(String.format("%.2f V", voltages.get(0) / 1000.0));
                                batteryCell2Voltage.setText(String.format("%.2f V", voltages.get(1) / 1000.0));
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get cell voltages: " + error.description());
                    }
                }
        );
    }

    private void getBatteryVoltage() {
        DJIKey<Integer> key = KeyTools.createKey(
                BatteryKey.KeyVoltage,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer voltage) {
                        uiHandler.post(() -> {
                            batteryVoltage.setText(String.format("%.2f V", voltage / 1000.0));
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get battery voltage: " + error.description());
                    }
                }
        );
    }

    private void getBatteryTemperature() {
        DJIKey<Double> key = KeyTools.createKey(
                BatteryKey.KeyBatteryTemperature,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Double>() {
                    @Override
                    public void onSuccess(Double temperature) {
                        uiHandler.post(() -> {
                            batteryTemperature.setText(String.format("%.1f °C", temperature));
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get battery temperature: " + error.description());
                        uiHandler.post(() -> batteryTemperature.setText("N/A"));
                    }
                }
        );
    }

    private void getBatterySerialNumber() {
        DJIKey<String> key = KeyTools.createKey(
                BatteryKey.KeySerialNumber,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<String>() {
                    @Override
                    public void onSuccess(String serialNumber) {
                        uiHandler.post(() -> {
                            batterySerialNumber.setText(serialNumber != null ? serialNumber : "N/A");
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get battery serial number: " + error.description());
                        uiHandler.post(() -> batterySerialNumber.setText("Not Available"));
                    }
                }
        );
    }

    private void getBatteryCycleCount() {
        DJIKey<Integer> key = KeyTools.createKey(
                BatteryKey.KeyNumberOfDischarges,
                ComponentIndexType.LEFT_OR_MAIN
        );

        KeyManager.getInstance().getValue(key,
                new CommonCallbacks.CompletionCallbackWithParam<Integer>() {
                    @Override
                    public void onSuccess(Integer cycleCount) {
                        uiHandler.post(() -> {
                            batteryCycleCount.setText(String.valueOf(cycleCount));
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        Log.e(TAG, "Failed to get battery cycle count: " + error.description());
                        uiHandler.post(() -> batteryCycleCount.setText("N/A"));
                    }
                }
        );
    }
}
