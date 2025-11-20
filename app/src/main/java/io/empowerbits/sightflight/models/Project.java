package io.empowerbits.sightflight.models;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Project implements Serializable {
    private static final String TAG = "Project";

    public int id;
    public String first_name;
    public String last_name;
    public String name;
    public String email;
    public String type;
    public String address_image;
    public String address;
    public String flight_path; // This is a STRING - needs to be parsed to array
    public String house_boundary;
    public String obstacle;
    public String obstacle_boundary; // This is a STRING - needs to be parsed to array

    // Change from String to Object so Gson/Jackson can handle both string and object
    public Object flight_setting;

    public int height_of_house;
    public String longitude;
    public String latitude;
    public String survey_date;
    public Status status;
    public String uuid;
    public int must_height;
    public int highest_can;
    public List<FlightLog> flights;
    public Object progress;
    public int images_count;
    public String created_at;
    public String updated_at;
    public Object model_generated_at;
    public boolean is_grid;
    public String flight_path_type;
    public Object zipcode;
    public Object state;
    public Object city;

    // Helper method to always get flight_setting as String
    public String getFlightSettingAsString() {
        if (flight_setting == null) {
            return null;
        }
        return flight_setting.toString();
    }


    /**
     * Parse obstacle_boundary string into List<Obstacle>
     * The obstacle_boundary field is a STRING that contains JSON array data
     */
    public List<Obstacle> getObstacles() {
        List<Obstacle> obstacles = new ArrayList<>();
        
        try {
            if (obstacle_boundary == null || obstacle_boundary.trim().isEmpty() 
                || obstacle_boundary.equals("null")) {
                Log.d(TAG, "obstacle_boundary is null or empty");
                return obstacles;
            }
            
            String obstacleStr = obstacle_boundary.trim();
            
            // Remove any surrounding quotes and clean the string
            if (obstacleStr.startsWith("\"") && obstacleStr.endsWith("\"")) {
                obstacleStr = obstacleStr.substring(1, obstacleStr.length() - 1);
            }
            
            // Replace escaped quotes
            obstacleStr = obstacleStr.replace("\\\"", "\"");
            
            Log.d(TAG, "Parsing obstacle_boundary: " + obstacleStr);
            
            Gson gson = new Gson();
            
            // Try to parse as JSON array
            if (obstacleStr.trim().startsWith("[") && obstacleStr.trim().endsWith("]")) {
                Type obstacleListType = new TypeToken<List<Obstacle>>(){}.getType();
                obstacles = gson.fromJson(obstacleStr, obstacleListType);
                if (obstacles == null) {
                    obstacles = new ArrayList<>();
                }
                Log.d(TAG, "Successfully parsed " + obstacles.size() + " obstacles from obstacle_boundary");
            } else {
                Log.w(TAG, "obstacle_boundary is not in expected JSON array format: " + obstacleStr);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing obstacle_boundary: " + e.getMessage(), e);
            Log.e(TAG, "obstacle_boundary content: " + obstacle_boundary);
        }
        
        return obstacles;
    }

    public List<FlightAddress> getWaypointList() {
        List<FlightAddress> waypoints = new ArrayList<>();

        try {
            if (flight_path == null || flight_path.trim().isEmpty() || flight_path.equals("null")) {
                Log.d(TAG, "flight_path is null or empty");
                return waypoints;
            }

            String pathStr = flight_path.trim();

            // Remove surrounding quotes if present
            if (pathStr.startsWith("\"") && pathStr.endsWith("\"")) {
                pathStr = pathStr.substring(1, pathStr.length() - 1);
            }

            // Replace escaped quotes
            pathStr = pathStr.replace("\\\"", "\"");

            Log.d(TAG, "Parsing flight_path: " + pathStr);

            Gson gson = new Gson();
            if (pathStr.startsWith("[") && pathStr.endsWith("]")) {
                Type listType = new TypeToken<List<FlightAddress>>(){}.getType();
                waypoints = gson.fromJson(pathStr, listType);
                if (waypoints == null) {
                    waypoints = new ArrayList<>();
                }
                Log.d(TAG, "Successfully parsed " + waypoints.size() + " waypoints from flight_path");
            } else {
                Log.w(TAG, "flight_path is not in expected JSON array format: " + pathStr);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing flight_path: " + e.getMessage(), e);
            Log.e(TAG, "flight_path content: " + flight_path);
        }

        return waypoints;
    }

    public static class Status implements Serializable {
        public int id;
        public String name;
        public String slug;
        public String description;
        public String color;
        public String created_at;
        public String updated_at;
    }


}
