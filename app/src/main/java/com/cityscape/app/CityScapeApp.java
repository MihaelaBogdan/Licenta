package com.cityscape.app;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.cityscape.app.database.AppDatabase;

/**
 * Main Application class for CityScape
 * Initializes database and global settings
 */
public class CityScapeApp extends Application {

    private static CityScapeApp instance;
    private AppDatabase database;
    private SharedPreferences preferences;

    public static final String PREFS_NAME = "cityscape_prefs";
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_SELECTED_CITY = "selected_city";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_PROFILE_COMPLETE = "profile_complete";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize preferences
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize database
        database = AppDatabase.getDatabase(this);

        // Apply theme
        applyTheme();
    }

    /**
     * Get application instance
     */
    public static CityScapeApp getInstance() {
        return instance;
    }

    /**
     * Get database instance
     */
    public AppDatabase getDatabase() {
        return database;
    }

    /**
     * Get shared preferences
     */
    public SharedPreferences getPreferences() {
        return preferences;
    }

    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return preferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }

    /**
     * Set current user
     */
    public void setCurrentUser(String userId) {
        preferences.edit()
                .putString(KEY_USER_ID, userId)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    /**
     * Log out user
     */
    public void logout() {
        preferences.edit()
                .remove(KEY_USER_ID)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .remove(KEY_SELECTED_CITY)
                .remove(KEY_PROFILE_COMPLETE)
                .apply();
    }

    /**
     * Get selected city ID
     */
    public String getSelectedCityId() {
        return preferences.getString(KEY_SELECTED_CITY, "bucuresti");
    }

    /**
     * Set selected city
     */
    public void setSelectedCity(String cityId) {
        preferences.edit()
                .putString(KEY_SELECTED_CITY, cityId)
                .apply();
    }

    /**
     * Check if dark mode is enabled
     */
    public boolean isDarkMode() {
        return preferences.getBoolean(KEY_DARK_MODE, true);
    }

    /**
     * Set dark mode
     */
    public void setDarkMode(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_DARK_MODE, enabled)
                .apply();
        applyTheme();
    }

    /**
     * Apply theme based on preference
     */
    private void applyTheme() {
        if (isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    /**
     * Check if this is first launch
     */
    public boolean isFirstLaunch() {
        return preferences.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    /**
     * Mark first launch complete
     */
    public void setFirstLaunchComplete() {
        preferences.edit()
                .putBoolean(KEY_FIRST_LAUNCH, false)
                .apply();
    }

    /**
     * Check if profile is complete
     */
    public boolean isProfileComplete() {
        return preferences.getBoolean(KEY_PROFILE_COMPLETE, false);
    }

    /**
     * Mark profile as complete
     */
    public void setProfileComplete() {
        preferences.edit()
                .putBoolean(KEY_PROFILE_COMPLETE, true)
                .apply();
    }

    /**
     * Set profile complete status
     */
    public void setProfileComplete(boolean complete) {
        preferences.edit()
                .putBoolean(KEY_PROFILE_COMPLETE, complete)
                .apply();
    }
}
