package io.empowerbits.sightflight.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.empowerbits.sightflight.models.Project;
import io.empowerbits.sightflight.models.WaypointSetting;

import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * SessionUtils - Utility class for saving/loading session data
 */
public class SessionUtils {
    private static final String TAG = "SessionUtils";
    private static final String PREF_NAME = "EagleEyeSession";
    private static final String KEY_WAYPOINTS = "waypoints";
    private static final String KEY_CURRENT_PROJECT = "current_project";
    private static final String KEY_LAST_WAYPOINT = "last_waypoint";
    private static final String KEY_FLIGHT_STATE = "flight_state";
    private static final String KEY_FLIGHT_MODE = "flight_mode";

    private static Context context;
    private static SharedPreferences getPreferences(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Initialize SessionUtils with application context
     */
    public static void initialize(Context appContext) {
        context = appContext.getApplicationContext();
    }

    /**
     * Save waypoints to SharedPreferences
     */
    public static void saveWaypoints(ArrayList<WaypointSetting> waypoints) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();

            Gson gson = new Gson();
            String waypointsJson = gson.toJson(waypoints);

            editor.putString(KEY_WAYPOINTS, waypointsJson);
            editor.apply();

            Log.d(TAG, "Saved " + waypoints.size() + " waypoints to preferences");
        } catch (Exception e) {
            Log.e(TAG, "Error saving waypoints: " + e.getMessage(), e);
        }
    }

    /**
     * Load waypoints from SharedPreferences
     */
    public static ArrayList<WaypointSetting> loadWaypoints() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return new ArrayList<>();
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            String waypointsJson = prefs.getString(KEY_WAYPOINTS, null);

            if (waypointsJson == null || waypointsJson.isEmpty()) {
                Log.d(TAG, "No saved waypoints found");
                return new ArrayList<>();
            }

            Gson gson = new Gson();
            Type waypointListType = new TypeToken<ArrayList<WaypointSetting>>(){}.getType();
            ArrayList<WaypointSetting> waypoints = gson.fromJson(waypointsJson, waypointListType);

            if (waypoints == null) {
                waypoints = new ArrayList<>();
            }

            Log.d(TAG, "Loaded " + waypoints.size() + " waypoints from preferences");
            return waypoints;

        } catch (Exception e) {
            Log.e(TAG, "Error loading waypoints: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Save current project to SharedPreferences
     */
    public static void saveCurrentProject(Project project) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();

            if (project == null) {
                editor.remove(KEY_CURRENT_PROJECT);
                Log.d(TAG, "Removed current project from preferences");
            } else {
                Gson gson = new Gson();
                String projectJson = gson.toJson(project);
                editor.putString(KEY_CURRENT_PROJECT, projectJson);
                Log.d(TAG, "Saved current project: " + project.name);
            }

            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Error saving current project: " + e.getMessage(), e);
        }
    }

    public static void clearCurrentProject() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();

            editor.remove(KEY_CURRENT_PROJECT);
            editor.apply();

            Log.d(TAG, "Cleared current project from preferences");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing current project: " + e.getMessage(), e);
        }
    }

    /**
     * Load current project from SharedPreferences
     */
    public static Project loadCurrentProject() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return null;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            String projectJson = prefs.getString(KEY_CURRENT_PROJECT, null);

            if (projectJson == null || projectJson.isEmpty()) {
                Log.d(TAG, "No saved current project found");
                return null;
            }

            Gson gson = new Gson();
            Project project = gson.fromJson(projectJson, Project.class);

            if (project != null) {
                Log.d(TAG, "Loaded current project: " + project.name);
            }

            return project;

        } catch (Exception e) {
            Log.e(TAG, "Error loading current project: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clear all session data
     */
    public static void clearSession() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            Log.d(TAG, "Session data cleared");

        } catch (Exception e) {
            Log.e(TAG, "Error clearing session: " + e.getMessage(), e);
        }
    }

    /**
     * Set last waypoint index
     */
    public static void setLastWaypoint(int waypointIndex) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_LAST_WAYPOINT, waypointIndex);
            editor.apply();

            Log.d(TAG, "Set last waypoint: " + waypointIndex);

        } catch (Exception e) {
            Log.e(TAG, "Error setting last waypoint: " + e.getMessage(), e);
        }
    }

    /**
     * Get last waypoint index
     */
    public static int getLastWaypoint() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return 0;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            return prefs.getInt(KEY_LAST_WAYPOINT, 0);

        } catch (Exception e) {
            Log.e(TAG, "Error getting last waypoint: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Set flight state
     */
    public static void setFlightState(boolean isFlying) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_FLIGHT_STATE, isFlying);
            editor.apply();

            Log.d(TAG, "Set flight state: " + isFlying);

        } catch (Exception e) {
            Log.e(TAG, "Error setting flight state: " + e.getMessage(), e);
        }
    }

    /**
     * Get flight state
     */
    public static boolean getFlightState() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return false;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            return prefs.getBoolean(KEY_FLIGHT_STATE, false);

        } catch (Exception e) {
            Log.e(TAG, "Error getting flight state: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Set flight mode
     */
    public static void setFlightMode(String flightMode) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_FLIGHT_MODE, flightMode);
            editor.apply();

            Log.d(TAG, "Set flight mode: " + flightMode);

        } catch (Exception e) {
            Log.e(TAG, "Error setting flight mode: " + e.getMessage(), e);
        }
    }

    /**
     * Get flight mode
     */
    public static String getFlightMode() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return "";
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            return prefs.getString(KEY_FLIGHT_MODE, "");

        } catch (Exception e) {
            Log.e(TAG, "Error getting flight mode: " + e.getMessage(), e);
            return "";
        }
    }

    /**
     * Get authentication token for API calls
     */
    public static String getAuthToken() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return "";
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            // Try to get token from SharedPreferences
            // Adjust the key name based on your existing implementation
            String token = prefs.getString("auth_token", null);
            if (token == null) {
                // Try alternative key names
                token = prefs.getString("user_token", null);
                if (token == null) {
                    token = prefs.getString("access_token", null);
                    if (token == null) {
                        token = prefs.getString("token", null);
                    }
                }
            }
            return token != null ? token : "";
        } catch (Exception e) {
            Log.e(TAG, "Error getting auth token: " + e.getMessage());
        }
        return "";
    }

    /**
     * Save authentication token
     */
    public static void saveAuthToken(String token) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            if (token != null) {
                SharedPreferences prefs = getPreferences(context);
                prefs.edit()
                        .putString("auth_token", token)
                        .apply();
                Log.d(TAG, "Auth token saved successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving auth token: " + e.getMessage());
        }
    }

    /**
     * Check if user is authenticated
     */
    public static boolean isUserAuthenticated() {
        String token = getAuthToken();
        return token != null && !token.isEmpty();
    }

    /**
     * Save current flight ID for flight ended log
     */
    public static void saveCurrentFlightId(Integer flightId) {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            if (flightId != null) {
                editor.putInt("current_flight_id", flightId);
                Log.d(TAG, "Saved current flight ID: " + flightId);
            } else {
                editor.remove("current_flight_id");
                Log.d(TAG, "Cleared current flight ID");
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving current flight ID: " + e.getMessage(), e);
        }
    }

    /**
     * Get current flight ID for flight ended log
     */
    public static Integer getCurrentFlightId() {
        if (context == null) {
            Log.e(TAG, "SessionUtils not initialized. Call initialize() first.");
            return null;
        }

        try {
            SharedPreferences prefs = getPreferences(context);
            if (prefs.contains("current_flight_id")) {
                int flightId = prefs.getInt("current_flight_id", -1);
                if (flightId != -1) {
                    Log.d(TAG, "Retrieved current flight ID: " + flightId);
                    return flightId;
                }
            }
            Log.d(TAG, "No current flight ID found");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current flight ID: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clear current flight ID after flight ended
     */
    public static void clearCurrentFlightId() {
        saveCurrentFlightId(null);
    }
}
