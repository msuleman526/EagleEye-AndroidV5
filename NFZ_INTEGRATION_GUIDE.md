# NFZ Integration Guide for WaypointActivity.java

## Step 1: Add Imports (Add these at the top of WaypointActivity.java)

```java
import com.empowerbits.dronifyit.util.NFZManager;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation;
import dji.v5.manager.aircraft.flysafe.info.FlyZoneCategory;
import android.graphics.PorterDuff;
```

## Step 2: Add Class Variables (Add after line 150)

```java
// NFZ Management
private NFZManager nfzManager;
private RelativeLayout nfzInfoPanel;
private TextView nfzNameText;
private TextView nfzLevelText;
private View nfzColorIndicator;
private TextView nfzAffectedWaypoints;
private Button btnUnlockNFZ;
private List<Integer> waypointsInNFZ = new ArrayList<>();
private FlyZoneInformation currentNFZ;
```

## Step 3: Initialize NFZ UI in onCreate() (Add after map initialization)

```java
private void initializeNFZPanel() {
    // Inflate NFZ panel
    View nfzPanel = getLayoutInflater().inflate(R.layout.nfz_info_panel, null);
    RelativeLayout rootLayout = findViewById(android.R.id.content);
    rootLayout.addView(nfzPanel);

    // Get references
    nfzInfoPanel = findViewById(R.id.nfzInfoPanel);
    nfzNameText = findViewById(R.id.nfzNameText);
    nfzLevelText = findViewById(R.id.nfzLevelText);
    nfzColorIndicator = findViewById(R.id.nfzColorIndicator);
    nfzAffectedWaypoints = findViewById(R.id.nfzAffectedWaypoints);
    btnUnlockNFZ = findViewById(R.id.btnUnlockNFZ);

    // Initialize NFZ manager
    nfzManager = new NFZManager();

    // Setup unlock button
    btnUnlockNFZ.setOnClickListener(v -> handleNFZUnlock());

    Log.d(TAG, "NFZ Panel initialized");
}
```

## Step 4: Check NFZ When Waypoints Change (Add this method)

```java
private void checkWaypointsForNFZ() {
    if (waypointsList == null || waypointsList.isEmpty()) {
        hideNFZPanel();
        return;
    }

    // Convert waypoints to LatLng list
    List<LatLng> waypointPositions = new ArrayList<>();
    for (WaypointSetting wp : waypointsList) {
        waypointPositions.add(new LatLng(wp.latitude, wp.longitude));
    }

    nfzManager.checkWaypoints(waypointPositions, new NFZManager.NFZCheckCallback() {
        @Override
        public void onNFZDetected(List<FlyZoneInformation> zones, List<Integer> affectedWaypoints) {
            runOnUiThread(() -> {
                waypointsInNFZ = affectedWaypoints;
                currentNFZ = NFZManager.getMostRestrictiveNFZ(zones);

                // Update waypoint markers to show NFZ status
                updateWaypointMarkersWithNFZ(affectedWaypoints);

                // Show NFZ info panel
                showNFZPanel(currentNFZ, affectedWaypoints.size());

                Log.d(TAG, "NFZ Detected: " + zones.size() + " zones, " +
                      affectedWaypoints.size() + " waypoints affected");
            });
        }

        @Override
        public void onNoNFZDetected() {
            runOnUiThread(() -> {
                waypointsInNFZ.clear();
                currentNFZ = null;
                hideNFZPanel();
                updateWaypointMarkersWithNFZ(new ArrayList<>());
                Log.d(TAG, "No NFZ detected");
            });
        }

        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                Log.e(TAG, "NFZ check error: " + error);
                Toast.makeText(WaypointActivity.this,
                    "Failed to check No-Fly Zones: " + error,
                    Toast.LENGTH_SHORT).show();
            });
        }
    });
}
```

## Step 5: Update Waypoint Markers with NFZ Indicator (Add this method)

```java
private void updateWaypointMarkersWithNFZ(List<Integer> affectedIndices) {
    if (points == null || points.isEmpty()) return;

    for (int i = 0; i < points.size(); i++) {
        Marker marker = points.get(i);

        if (affectedIndices.contains(i)) {
            // Waypoint in NFZ - show red marker
            marker.setIcon(createCustomMarkerIcon(i + 1, Color.RED));
        } else {
            // Normal waypoint - show blue marker
            marker.setIcon(createCustomMarkerIcon(i + 1, Color.BLUE));
        }
    }
}

private BitmapDescriptor createCustomMarkerIcon(int number, int color) {
    // Create a custom marker with number and color
    Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);

    // Draw circle
    Paint paint = new Paint();
    paint.setColor(color);
    paint.setStyle(Paint.Style.FILL);
    paint.setAntiAlias(true);
    canvas.drawCircle(40, 40, 35, paint);

    // Draw white border
    paint.setColor(Color.WHITE);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(4);
    canvas.drawCircle(40, 40, 35, paint);

    // Draw number
    paint.setColor(Color.WHITE);
    paint.setStyle(Paint.Style.FILL);
    paint.setTextSize(35);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(Typeface.DEFAULT_BOLD);

    Rect textBounds = new Rect();
    String text = String.valueOf(number);
    paint.getTextBounds(text, 0, text.length(), textBounds);
    canvas.drawText(text, 40, 40 + textBounds.height()/2, paint);

    return BitmapDescriptorFactory.fromBitmap(bitmap);
}
```

## Step 6: Show/Hide NFZ Panel (Add these methods)

```java
private void showNFZPanel(FlyZoneInformation zone, int affectedCount) {
    if (nfzInfoPanel == null || zone == null) return;

    nfzInfoPanel.setVisibility(View.VISIBLE);

    // Set zone name
    String zoneName = zone.getName() != null ? zone.getName() : "Restricted Area";
    nfzNameText.setText(zoneName);

    // Set level and color
    FlyZoneCategory category = zone.getCategory();
    nfzLevelText.setText(NFZManager.getNFZLevelText(category));

    int color = NFZManager.getNFZColor(category);
    nfzColorIndicator.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);

    // Show affected waypoints count
    if (affectedCount > 0) {
        nfzAffectedWaypoints.setVisibility(View.VISIBLE);
        nfzAffectedWaypoints.setText("Waypoints in zone: " + affectedCount);
    } else {
        nfzAffectedWaypoints.setVisibility(View.GONE);
    }

    // Show unlock button if zone is unlockable
    if (NFZManager.isNFZUnlockable(category)) {
        btnUnlockNFZ.setVisibility(View.VISIBLE);
    } else {
        btnUnlockNFZ.setVisibility(View.GONE);
    }
}

private void hideNFZPanel() {
    if (nfzInfoPanel != null) {
        nfzInfoPanel.setVisibility(View.GONE);
    }
}
```

## Step 7: Add Pre-Flight Validation on Start Button (Modify your start button click)

```java
private void onStartMissionClicked() {
    // Check NFZ before starting
    if (!waypointsInNFZ.isEmpty()) {
        if (currentNFZ != null && currentNFZ.getCategory() == FlyZoneCategory.RESTRICTED) {
            // Restricted zone - cannot fly
            Toast.makeText(this,
                "❌ Cannot fly: " + waypointsInNFZ.size() +
                " waypoints are in a RESTRICTED No-Fly Zone!\n\n" +
                "Zone: " + (currentNFZ.getName() != null ? currentNFZ.getName() : "Restricted Area"),
                Toast.LENGTH_LONG).show();
            return;
        } else if (currentNFZ != null && currentNFZ.getCategory() == FlyZoneCategory.AUTHORIZATION) {
            // Authorization required
            Toast.makeText(this,
                "⚠️ Warning: " + waypointsInNFZ.size() +
                " waypoints require authorization!\n\n" +
                "Please unlock the zone before starting.",
                Toast.LENGTH_LONG).show();

            // Highlight unlock button
            btnUnlockNFZ.setBackgroundColor(Color.YELLOW);
            btnUnlockNFZ.setTextColor(Color.BLACK);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                btnUnlockNFZ.setBackgroundResource(R.drawable.shape_button);
                btnUnlockNFZ.setTextColor(Color.WHITE);
            }, 2000);
            return;
        }
    }

    // Continue with normal mission start
    showMissionStartConfirmation();
}
```

## Step 8: Handle NFZ Unlock (Add this method)

```java
private void handleNFZUnlock() {
    if (currentNFZ == null) {
        Toast.makeText(this, "No NFZ to unlock", Toast.LENGTH_SHORT).show();
        return;
    }

    if (!NFZManager.isNFZUnlockable(currentNFZ.getCategory())) {
        Toast.makeText(this,
            "This zone cannot be unlocked. It is permanently restricted.",
            Toast.LENGTH_LONG).show();
        return;
    }

    // Show loading
    btnUnlockNFZ.setEnabled(false);
    btnUnlockNFZ.setText("Unlocking...");

    nfzManager.requestUnlock(currentNFZ, new dji.v5.common.callback.CommonCallbacks.CompletionCallback() {
        @Override
        public void onSuccess() {
            runOnUiThread(() -> {
                Toast.makeText(WaypointActivity.this,
                    "✅ NFZ Unlocked Successfully!\n\nYou can now fly in this area.",
                    Toast.LENGTH_LONG).show();

                btnUnlockNFZ.setText("Unlocked ✓");
                btnUnlockNFZ.setBackgroundColor(Color.GREEN);

                // Re-check waypoints
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    checkWaypointsForNFZ();
                }, 1500);
            });
        }

        @Override
        public void onFailure(dji.v5.common.error.IDJIError error) {
            runOnUiThread(() -> {
                Toast.makeText(WaypointActivity.this,
                    "❌ Failed to unlock NFZ:\n" + error.description(),
                    Toast.LENGTH_LONG).show();

                btnUnlockNFZ.setEnabled(true);
                btnUnlockNFZ.setText("Unlock Zone");
            });
        }
    });
}
```

## Step 9: Call NFZ Check When Adding/Removing Waypoints

In your existing methods where you add/remove waypoints, add this line:

```java
// After adding waypoint
checkWaypointsForNFZ();

// After removing waypoint
checkWaypointsForNFZ();

// After loading project waypoints
checkWaypointsForNFZ();
```

## Step 10: Initialize in onCreate() (Add this line)

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_waypoint);

    // ... existing initialization code ...

    // Initialize NFZ panel
    initializeNFZPanel();
}
```

## That's It!

This implementation will:
- ✅ Show red markers for waypoints in NFZ
- ✅ Display NFZ info panel with name, color, and restriction level
- ✅ Show affected waypoint count
- ✅ Prevent mission start if waypoints in restricted zones
- ✅ Show unlock button for unlockable zones
- ✅ Handle the unlock process

The NFZ panel will appear in the bottom-right corner above telemetry view, exactly as requested!
