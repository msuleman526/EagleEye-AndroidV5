package com.suleman.eagleeye.models;

import com.dji.wpmzsdk.common.data.HeightMode;
import com.dji.wpmzsdk.common.utils.kml.model.Location2D;

import java.io.Serializable;

import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction;
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostBehavior;
import dji.sdk.wpmz.value.mission.WaylineFinishedAction;
import dji.sdk.wpmz.value.mission.WaylineFlyToWaylineMode;

public class MissionSetting implements Serializable {
    public String missionName = "DronifyIt Mission";
    public WaylineFlyToWaylineMode flyToWaylineMode = WaylineFlyToWaylineMode.SAFELY;
    public WaylineFinishedAction finishAction = WaylineFinishedAction.GO_HOME;
    public WaylineExitOnRCLostBehavior exitOnRCLost = WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION;
    public WaylineExitOnRCLostAction executeRCLostAction = WaylineExitOnRCLostAction.GO_BACK;
    public double takeOffSecurityHeight = 50d; //Feet Security Drone Height
    public double globalTransitionalSpeed = 10d;
    public double autoFlighSpeed = 8d;
    public double poiHeight = 4;
    public double globalHeight = 40;
    public Location2D poiLocation = null;
    public HeightMode heightMode = HeightMode.RELATIVE;

    // ==================== Fly To Wayline Mode Methods ====================

    /**
     * Get all available fly to wayline modes as display strings
     * @return Array of mode display names for UI dropdowns
     */
    public static String[] getHeightModes() {
        return new String[]{
                "EGM96",
                "Relative To Start Point",
                "aboveGroundLevel"
        };
    }

    public String getHeightModeDisplay() {
        switch (heightMode) {
            case EGM96:
                return "EGM96";
            case RELATIVE:
                return "Relative To Start Point";
            case WGS84:
                return "WGS84";
            default:
                return "Relative To Start Point";
        }
    }

    public int getHeightModeIndex() {
        switch (heightMode) {
            case EGM96:
                return 2;
            case WGS84:
                return 1;
            case RELATIVE:
                return 0;
            default:
                return 0;
        }
    }

    public static String[] getFlyToWaylineModes() {
        return new String[]{
            "Safely",
            "Point to Point"
        };
    }

    /**
     * Get current fly to wayline mode as display string
     * @return Current mode display name
     */
    public String getFlyToWaylineModeDisplay() {
        switch (flyToWaylineMode) {
            case SAFELY:
                return "Safely";
            case POINT_TO_POINT:
                return "Point to Point";
            default:
                return "Unknown";
        }
    }

    /**
     * Get current fly to wayline mode index for spinner selection
     * @return Index in the modes array
     */
    public int getFlyToWaylineModeIndex() {
        switch (flyToWaylineMode) {
            case SAFELY:
                return 0;
            case POINT_TO_POINT:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Set fly to wayline mode from spinner selection index
     * @param index Selected index from dropdown
     */
    public void setFlyToWaylineModeFromIndex(int index) {
        switch (index) {
            case 0:
                flyToWaylineMode = WaylineFlyToWaylineMode.SAFELY;
                break;
            case 1:
                flyToWaylineMode = WaylineFlyToWaylineMode.POINT_TO_POINT;
                break;
            default:
                flyToWaylineMode = WaylineFlyToWaylineMode.SAFELY;
                break;
        }
    }

    // ==================== Finish Action Methods ====================

    /**
     * Get all available finish actions as display strings
     * @return Array of finish action display names for UI dropdowns
     */
    public static String[] getFinishActions() {
        return new String[]{
            "No Action",
            "Go Home",
            "Auto Land",
            "Go to First Waypoint"
        };
    }

    /**
     * Get current finish action as display string
     * @return Current finish action display name
     */
    public String getFinishActionDisplay() {
        switch (finishAction) {
            case NO_ACTION:
                return "No Action";
            case GO_HOME:
                return "Go Home";
            case AUTO_LAND:
                return "Auto Land";
            case GOTO_FIRST_WAYPOINT:
                return "Go to First Waypoint";
            default:
                return "Unknown";
        }
    }

    /**
     * Get current finish action index for spinner selection
     * @return Index in the finish actions array
     */
    public int getFinishActionIndex() {
        switch (finishAction) {
            case NO_ACTION:
                return 0;
            case GO_HOME:
                return 1;
            case AUTO_LAND:
                return 2;
            case GOTO_FIRST_WAYPOINT:
                return 3;
            default:
                return 1;
        }
    }

    /**
     * Set finish action from spinner selection index
     * @param index Selected index from dropdown
     */
    public void setFinishActionFromIndex(int index) {
        switch (index) {
            case 0:
                finishAction = WaylineFinishedAction.NO_ACTION;
                break;
            case 1:
                finishAction = WaylineFinishedAction.GO_HOME;
                break;
            case 2:
                finishAction = WaylineFinishedAction.AUTO_LAND;
                break;
            case 3:
                finishAction = WaylineFinishedAction.GOTO_FIRST_WAYPOINT;
                break;
            default:
                finishAction = WaylineFinishedAction.GO_HOME;
                break;
        }
    }

    // ==================== RC Lost Behavior Methods ====================

    /**
     * Get all available RC lost behaviors as display strings
     * @return Array of behavior display names for UI dropdowns
     */
    public static String[] getExitOnRCLostBehaviors() {
        return new String[]{
            "Execute RC Lost Action",
            "Go ON",
        };
    }

    /**
     * Get current RC lost behavior as display string
     * @return Current behavior display name
     */
    public String getExitOnRCLostBehaviorDisplay() {
        switch (exitOnRCLost) {
            case EXCUTE_RC_LOST_ACTION:
                return "Execute RC Lost Action";
            case GO_ON:
                return "Go ON";
            default:
                return "Unknown";
        }
    }

    /**
     * Get current RC lost behavior index for spinner selection
     * @return Index in the behaviors array
     */
    public int getExitOnRCLostBehaviorIndex() {
        switch (exitOnRCLost) {
            case EXCUTE_RC_LOST_ACTION:
                return 0;
            case GO_ON:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Set RC lost behavior from spinner selection index
     * @param index Selected index from dropdown
     */
    public void setExitOnRCLostBehaviorFromIndex(int index) {
        switch (index) {
            case 0:
                exitOnRCLost = WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION;
                break;
            case 1:
                exitOnRCLost = WaylineExitOnRCLostBehavior.GO_ON;
                break;
            default:
                exitOnRCLost = WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION;
                break;
        }
    }

    // ==================== RC Lost Action Methods ====================

    /**
     * Get all available RC lost actions as display strings
     * @return Array of action display names for UI dropdowns
     */
    public static String[] getExecuteRCLostActions() {
        return new String[]{
            "Go Back",
            "Land",
            "Hover",
            "Go Alternate Point"
        };
    }

    /**
     * Get current RC lost action as display string
     * @return Current action display name
     */
    public String getExecuteRCLostActionDisplay() {
        switch (executeRCLostAction) {
            case GO_BACK:
                return "Go Back";
            case LANDING:
                return "Land";
            case HOVER:
                return "Hover";
            case GO_ALTERNATE_POINT:
                return "Go Alternate Point";
            default:
                return "Unknown";
        }
    }

    /**
     * Get current RC lost action index for spinner selection
     * @return Index in the actions array
     */
    public int getExecuteRCLostActionIndex() {
        switch (executeRCLostAction) {
            case GO_BACK:
                return 0;
            case LANDING:
                return 1;
            case HOVER:
                return 2;
            case GO_ALTERNATE_POINT:
                return 3;
            default:
                return 0;
        }
    }

    /**
     * Set RC lost action from spinner selection index
     * @param index Selected index from dropdown
     */
    public void setExecuteRCLostActionFromIndex(int index) {
        switch (index) {
            case 0:
                executeRCLostAction = WaylineExitOnRCLostAction.GO_BACK;
                break;
            case 1:
                executeRCLostAction = WaylineExitOnRCLostAction.LANDING;
                break;
            case 2:
                executeRCLostAction = WaylineExitOnRCLostAction.HOVER;
                break;
            case 3:
                executeRCLostAction = WaylineExitOnRCLostAction.GO_ALTERNATE_POINT;
                break;
            default:
                executeRCLostAction = WaylineExitOnRCLostAction.GO_BACK;
                break;
        }
    }

    // ==================== Numeric Value Helper Methods ====================

    /**
     * Get takeoff security height display string
     * @return Formatted height string in feet
     */
    public String getTakeOffSecurityHeightDisplay() {
        return String.format("%.0f ft", takeOffSecurityHeight);
    }

    /**
     * Get global transitional speed display string
     * @return Formatted speed string
     */
    public String getGlobalTransitionalSpeedDisplay() {
        return String.format("%.1f m/s", globalTransitionalSpeed);
    }

    /**
     * Get takeoff security height in meters
     * @return Height in meters
     */
    public double getTakeOffSecurityHeightInMeters() {
        return takeOffSecurityHeight * 0.3048; // Convert feet to meters
    }

    /**
     * Set takeoff security height from meters
     * @param meters Height in meters
     */
    public void setTakeOffSecurityHeightFromMeters(double meters) {
        this.takeOffSecurityHeight = meters / 0.3048; // Convert meters to feet
    }

    // ==================== Summary Methods ====================

    /**
     * Get a complete summary of all mission settings for display
     * @return Multi-line string with all settings
     */
    public String getMissionSettingsSummary() {
        return "Mission Name: " + missionName + "\n" +
               "Fly Mode: " + getFlyToWaylineModeDisplay() + "\n" +
               "Finish Action: " + getFinishActionDisplay() + "\n" +
               "RC Lost Behavior: " + getExitOnRCLostBehaviorDisplay() + "\n" +
               "RC Lost Action: " + getExecuteRCLostActionDisplay() + "\n" +
               "Takeoff Height: " + getTakeOffSecurityHeightDisplay() + "\n" +
               "Speed: " + getGlobalTransitionalSpeedDisplay();
    }

    /**
     * Validate mission settings
     * @return true if all settings are valid
     */
    public boolean isValid() {
        if (missionName == null || missionName.trim().isEmpty()) {
            return false;
        }
        if (takeOffSecurityHeight <= 0 || takeOffSecurityHeight > 500) {
            return false; // Height must be between 0-500 feet
        }
        if (globalTransitionalSpeed <= 0 || globalTransitionalSpeed > 15) {
            return false; // Speed must be between 0-15 m/s
        }
        return true;
    }

    /**
     * Get validation error message if settings are invalid
     * @return Error message or null if valid
     */
    public String getValidationError() {
        if (missionName == null || missionName.trim().isEmpty()) {
            return "Mission name cannot be empty";
        }
        if (takeOffSecurityHeight <= 0) {
            return "Takeoff height must be greater than 0";
        }
        if (takeOffSecurityHeight > 500) {
            return "Takeoff height cannot exceed 500 feet";
        }
        if (globalTransitionalSpeed <= 0) {
            return "Speed must be greater than 0";
        }
        if (globalTransitionalSpeed > 15) {
            return "Speed cannot exceed 15 m/s";
        }
        return null;
    }
}
