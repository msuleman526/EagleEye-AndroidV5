package io.empowerbits.sightflight.Activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.empowerbits.sightflight.Activities.AddProject.ProjectInfoActivity;
import io.empowerbits.sightflight.Fragments.DroneSettingFragment;
import io.empowerbits.sightflight.Fragments.ProjectDialogFragment;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.Services.ConnectionStateManager;
import io.empowerbits.sightflight.Services.TelemetryService;
import io.empowerbits.sightflight.models.MSDKInfoVm;
import io.empowerbits.sightflight.models.MSDKManagerVM;
import io.empowerbits.sightflight.util.TelemetryDisplayManager;

import dji.sdk.keyvalue.value.product.ProductType;

/**
 * Main Activity for EagleEye - Optimized for orientation changes
 *
 * OPTIMIZED: Only reloads UI on orientation change, keeps all logic intact
 * - SDK observers NOT re-registered
 * - Connection logic continues running
 * - Only view references updated
 *
 * @author Suleman
 * @date 2025-10-16
 */
public class MainActivity extends DJIMainActivity {

    private static final String TAG = "MainActivity";

    private TextView connectStatusTxt;
    private TextView productNameTxt;
    private ImageView bigDroneImg;
    private ImageView dotImg;

    private MSDKInfoVm msdkInfoVm;
    private boolean isDroneConnected = false;
    private ProductType currentProductType = ProductType.UNKNOWN;
    private String currentDroneName = "No Drone Connected";

    private PopupWindow menuPopupWindow;
    private TelemetryDisplayManager telemetryDisplayManager;
    private TelemetryService telemetryService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        // Check device compatibility before initializing DJI SDK features
        if (!isDeviceCompatibleForDJI()) {
            showIncompatibleDeviceDialog();
            return; // Don't initialize SDK features on incompatible devices
        }

        msdkInfoVm = getGlobalViewModelStore().get("MSDKInfoVm", MSDKInfoVm.class);

        initializeUI();
        initializeTelemetryService();
        initializeDisplayManagers();
        observeSDKStates();
        observeProductTypeChanges();
    }

    /**
     * CRITICAL: Handle orientation change
     * Only reloads layout and re-binds views - no observer re-registration
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Reload layout (Android automatically picks portrait/landscape XML)
        setContentView(R.layout.activity_connection);
        rebindViews();
        updateConnectionLogic();
    }

    /**
     * Initial UI setup - called only once in onCreate
     */
    private void initializeUI() {
        rebindViews();
        updateConnectionStatus(false);
        updateProductInfo("Unknown", "UNKNOWN");
        updateConnectionMessage("Initializing...");
    }

    /**
     * Re-bind view references after layout reload
     * Called only on orientation change
     */
    private void rebindViews() {
        // Get new view references from reloaded layout
        connectStatusTxt = findViewById(R.id.connectStatusTxt);
        dotImg = findViewById(R.id.dotImg);
        bigDroneImg = findViewById(R.id.bigDroneImg);
        productNameTxt = findViewById(R.id.droneNameTxt);

        // Re-attach click listeners
        setupClickListeners();

        Log.d(TAG, "✅ Views re-bound");
    }

    /**
     * Setup click listeners - called in initializeUI and rebindViews
     */
    private void setupClickListeners() {
        findViewById(R.id.profileButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, FPVCameraActivity.class));
            }
        });

        findViewById(R.id.mediaButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, MediaManagerActivity.class));
            }
        });

        findViewById(R.id.settingButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (!isDroneConnected) {
//                    Toast.makeText(MainActivity.this,
//                            "Please connect a drone to access settings",
//                            Toast.LENGTH_SHORT).show();
//                    Log.d(TAG, "Drone settings - Drone not connected");
//                    return;
//                }

                // Open Drone Settings Fragment
                Log.d(TAG, "Opening Drone Settings");
                DroneSettingFragment settingFragment = new DroneSettingFragment();
                settingFragment.show(getSupportFragmentManager(), "DroneSettings");
            }
        });

        findViewById(R.id.startSurveyButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //startActivity(new Intent(MainActivity.this, WaypointActivity.class));
                ProjectDialogFragment dialogFragment = new ProjectDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), "ProjectDialog");
            }
        });

        findViewById(R.id.drawerMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPopupMenu(view);
            }
        });
    }

    /**
     * Observe product type changes - called only once in onCreate
     */
    private void observeProductTypeChanges() {
        msdkInfoVm.getCurrentProductType().observe(this, productType -> {
            if (productType != null) {
                currentProductType = productType;
                currentDroneName = getProductTypeName(productType, isDroneConnected);
                updateConnectionLogic();
            } else {
                currentProductType = ProductType.UNKNOWN;
                currentDroneName = "Unknown Drone";
                updateConnectionLogic();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            msdkInfoVm.forceProductTypeRefresh();
        } catch (Exception ignored) {}
    }

    private void updateConnectionLogic() {
        Log.d(TAG, String.format("📡 Connection Update - Drone: %s, ProductType: %s, DroneName: %s",
                isDroneConnected, currentProductType.name(), currentDroneName));

        if (isDroneConnected && currentProductType != ProductType.UNKNOWN) {
            Log.i(TAG, "✅ DRONE CONNECTED - Product: " + currentDroneName + " (" + currentProductType.name() + ")");
            updateConnectionStatus(true);
            updateConnectionMessage("Connected");
            updateProductInfo(currentDroneName, currentProductType.name());

            // Update ConnectionStateManager
            ConnectionStateManager.getInstance().updateConnectionState(true, currentProductType, currentDroneName);
        } else {
            Log.i(TAG, "❌ DRONE DISCONNECTED - ProductType: " + currentProductType.name());
            updateConnectionStatus(false);
            updateConnectionMessage("Disconnected");
            updateProductInfo("No Drone Connected", "UNKNOWN");

            // Update ConnectionStateManager
            ConnectionStateManager.getInstance().updateConnectionState(false, ProductType.UNKNOWN, "No Drone Connected");
        }
    }

    public String getProductTypeName(ProductType productType, Boolean isDroneConnected) {
        Log.d("Eagle Eye", "🏷️ Getting drone name for ProductType: " + productType.name());
        String droneName;
        switch (productType) {
            case DJI_MINI_3:
                droneName = "DJI Mini 3";
                break;
            case DJI_MINI_3_PRO:
                droneName = "DJI Mini 3 Pro";
                break;
            case DJI_MINI_4_PRO:
                droneName = "DJI Mini 4 Pro";
                break;
            case DJI_AIR_2S:
                droneName = "DJI AIR 2S";
                break;
            case DJI_MAVIC_3:
                droneName = "DJI Mavic 3";
                break;
            case DJI_MATRICE_4_SERIES:
                droneName = "DJI MATRICE 4 SERIES";
                break;
            case DJI_MATRICE_4D_SERIES:
                droneName = "DJI MATRICE 4D SERIES";
                break;
            case MAVIC_AIR:
                droneName = "MAVIC AIR";
                break;
            case DJI_MATRICE_400:
                droneName = "DJI MATRICE 400";
                break;
            case UNKNOWN:
                droneName = "UNKNOWN";
                break;
            default:
                droneName = isDroneConnected ? "Unknown Drone" : "No Drone Connected";
                break;
        }
        return droneName;
    }

    /**
     * Observe SDK states - called only once in onCreate
     */
    private void observeSDKStates() {
        MSDKManagerVM msdkManagerVM = getMsdkManagerVM();

        // Observe registration state
        msdkManagerVM.getLvRegisterState().observe(this, result -> {
            Log.d(TAG, "📋 Registration state changed: " + result.success);
            if (result.success) {
                updateConnectionMessage("SDK registered successfully");
                Log.i(TAG, "✅ DJI SDK registered successfully!");
                msdkInfoVm.initListener();
            } else {
                String errorMsg = result.error != null ? result.error.description() : "Unknown error";
                updateConnectionMessage("Registration failed: " + errorMsg);
                Log.e(TAG, "❌ Registration failed: " + errorMsg);
            }
        });

        msdkManagerVM.getLvProductConnectionState().observe(this, result -> {
            Log.i(TAG, String.format("🔄 PRODUCT CONNECTION CHANGED - Connected: %s, ProductID: %d",
                    result.connected, result.productId));
            isDroneConnected = result.connected;
            if (isDroneConnected && msdkInfoVm != null) {
                new android.os.Handler().postDelayed(() -> msdkInfoVm.forceProductTypeRefresh(), 2000);
            }
            updateConnectionLogic();
        });

        // Observe product changes
        msdkManagerVM.getLvProductChanges().observe(this, productId -> {
            Log.i(TAG, "🔄 PRODUCT CHANGED to ID: " + productId);
            Log.d(TAG, "📝 Product change logged, ProductType detection handled by MSDKInfoVm");
        });

        // Observe initialization process
        msdkManagerVM.getLvInitProcess().observe(this, result -> {
            String progressMsg = result.event.name() + " (" + result.totalProcess + "%)";
            Log.d(TAG, "⚙️ SDK Init Progress: " + progressMsg);
        });
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void updateConnectionStatus(boolean isConnected) {
        String status = isConnected ? "Connected" : "Disconnected";
        connectStatusTxt.setText(status);
        connectStatusTxt.setTextColor(ContextCompat.getColor(this, isConnected ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
        bigDroneImg.setImageDrawable(isConnected ? getDrawable(R.drawable.big_drone_green) : getDrawable(R.drawable.big_drone_red));
        dotImg.setImageDrawable(isConnected ? getDrawable(R.drawable.dot_green) : getDrawable(R.drawable.dot_red));
    }

    private void updateProductInfo(String productName, String productType) {
        String productInfo = productName;
        productNameTxt.setText(productInfo);
    }

    private void updateConnectionMessage(String message) {
        connectStatusTxt.setText(message);
    }

    @Override
    public void prepareUxActivity() {
        if (msdkInfoVm != null) {
            Log.i(TAG, "🔄 Forcing product type refresh after UX preparation...");
            msdkInfoVm.forceProductTypeRefresh();
        }
    }

    public void prepareTestingToolsActivity() {
        if (productNameTxt != null) {
            productNameTxt.setOnClickListener(v -> {
                if (msdkInfoVm != null) {
                    msdkInfoVm.forceProductTypeRefresh();
                }
            });
        }
    }

    /**
     * Show popup menu below drawerMenu button
     */
    private void showPopupMenu(View anchorView) {
        // Dismiss existing popup if showing
        if (menuPopupWindow != null && menuPopupWindow.isShowing()) {
            menuPopupWindow.dismiss();
            return;
        }

        // Inflate popup menu layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_menu, null);

        // Create popup window
        menuPopupWindow = new PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true // focusable
        );

        // Set transparent background to enable dismiss on outside touch
        menuPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent));
        menuPopupWindow.setOutsideTouchable(true);

        // Setup menu item click listeners
        LinearLayout uploadPhotosBtn = popupView.findViewById(R.id.uploadPhotosBtn);
        LinearLayout droneWaypointMode = popupView.findViewById(R.id.waypointModeButton);
        LinearLayout addProjectItem = popupView.findViewById(R.id.addProjectItem);
        LinearLayout fpvModeItem = popupView.findViewById(R.id.fpvModeItem);

        droneWaypointMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, WaypointActivity.class);
                intent.putExtra("mode", true);
                startActivity(intent);
            }
        });

        uploadPhotosBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menuPopupWindow.dismiss();
                // Open ProjectDialogFragment in upload photos mode
                ProjectDialogFragment dialogFragment = ProjectDialogFragment.newInstanceForUploadPhotos();
                dialogFragment.show(getSupportFragmentManager(), "UploadPhotosDialog");
            }
        });

        addProjectItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menuPopupWindow.dismiss();
                startActivity(new Intent(MainActivity.this, ProjectInfoActivity.class));
            }
        });

        fpvModeItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menuPopupWindow.dismiss();
                startActivity(new Intent(MainActivity.this, FPVCameraActivity.class));
            }
        });

        // Calculate position to show popup below drawerMenu
        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);

        // Show popup below the anchor view with right margin
        // X offset: -16dp (negative to move left from right edge)
        int rightMarginPx = (int) (16 * getResources().getDisplayMetrics().density);
        menuPopupWindow.showAsDropDown(anchorView, -rightMarginPx, 0, Gravity.END);

        Log.d(TAG, "Popup menu shown");
    }

    /**
     * Initialize TelemetryService
     */
    private void initializeTelemetryService() {
        try {
            Log.d(TAG, "Initializing TelemetryService...");

            // Initialize TelemetryService (singleton pattern, not Android Service)
            telemetryService = TelemetryService.getInstance(this);
            telemetryService.initializeTelemetryService();

            Log.d(TAG, "TelemetryService initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TelemetryService: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize Display Managers
     */
    private void initializeDisplayManagers() {
        try {
            Log.d(TAG, "Initializing display managers...");

            // Create display manager
            telemetryDisplayManager = new TelemetryDisplayManager(this, "MainActivity");

            // Initialize TelemetryDisplayManager UI components from telemetry_view.xml
            TextView flightModeText = findViewById(R.id.flightModeText);
            TextView satelliteCountText = findViewById(R.id.satelliteCountText);
            TextView batteryText = findViewById(R.id.batteryText);
            TextView remoteSignalText = findViewById(R.id.remoteSignalText);
            ImageView droneIcon = findViewById(R.id.droneIcon);
            ImageView satelliteIcon = findViewById(R.id.satelliteIcon);
            ImageView gpsSignalIcon = findViewById(R.id.gpsSignalIcon);
            ImageView batteryIcon = findViewById(R.id.batteryIcon);
            ImageView remoteIcon = findViewById(R.id.remoteIcon);
            ImageView remoteSignalIcon = findViewById(R.id.remoteSignalIcon);

            telemetryDisplayManager.initializeComponents(
                flightModeText, satelliteCountText, batteryText, remoteSignalText,
                droneIcon, satelliteIcon, gpsSignalIcon, batteryIcon, remoteIcon, remoteSignalIcon
            );

            // Setup telemetry services
            if (telemetryService != null) {
                telemetryDisplayManager.setupTelemetryServices(telemetryService);
            }

            Log.d(TAG, "Display managers initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing display managers: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup telemetry display manager
        if (telemetryDisplayManager != null) {
            telemetryDisplayManager.cleanup();
        }

        // Dismiss popup if showing
        if (menuPopupWindow != null && menuPopupWindow.isShowing()) {
            menuPopupWindow.dismiss();
        }
    }

    /**
     * Check if device is compatible with DJI SDK
     * DJI SDK requires arm64-v8a as PRIMARY architecture
     */
    private boolean isDeviceCompatibleForDJI() {
        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            if (abis.length > 0) {
                String primaryAbi = abis[0];
                Log.d(TAG, "Primary device ABI: " + primaryAbi);
                return "arm64-v8a".equals(primaryAbi);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking device compatibility: " + e.getMessage());
            return false;
        }
    }

    /**
     * Show dialog explaining device incompatibility
     */
    private void showIncompatibleDeviceDialog() {
        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            String primaryAbi = abis.length > 0 ? abis[0] : "unknown";

            new android.app.AlertDialog.Builder(this)
                    .setTitle("Incompatible Device")
                    .setMessage("This device is not compatible with EagleEye.\n\n" +
                            "Device Architecture: " + primaryAbi + "\n" +
                            "Required Architecture: arm64-v8a\n\n" +
                            "DJI SDK requires a real ARM64 device (not an emulator or x86 tablet).\n\n" +
                            "Please use a compatible Android device.")
                    .setPositiveButton("Exit", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();

            Log.e(TAG, "App running on incompatible device: " + primaryAbi);
        } catch (Exception e) {
            Log.e(TAG, "Error showing incompatibility dialog: " + e.getMessage());
            finish();
        }
    }
}