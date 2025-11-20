package io.empowerbits.sightflight.util;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSessionManager {

    private static final String PREF_NAME = "UserSession";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_TOKEN = "user_token";

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public UserSessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveFieldEmail(String email) {
        editor.putString("field_email", email);
        editor.apply();
    }

    public void saveFieldPassword(String password) {
        editor.putString("field_password", password);
        editor.apply();
    }

    // Save user data
    public void saveUser(int id, String name, String email, String token) {
        editor.putInt(KEY_USER_ID, id);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_TOKEN, token);
        editor.apply();
    }

    // Get user ID
    public int getUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, 0);
    }

    // Get user name
    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }

    public String getFieldEmail() {
        return sharedPreferences.getString("field_email", "");
    }

    public String getFieldPassword() {
        return sharedPreferences.getString("field_password", "");
    }

    // Get user email
    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    // Get user token
    public String getToken() {
        return sharedPreferences.getString(KEY_USER_TOKEN, "");
    }

    // Clear all saved data (logout)
    public void clearSession() {
        editor.clear();
        editor.apply();
    }
}
