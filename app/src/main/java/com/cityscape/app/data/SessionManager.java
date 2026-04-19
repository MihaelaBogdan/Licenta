package com.cityscape.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.cityscape.app.model.User;
import com.cityscape.app.model.UserBadge;
import com.cityscape.app.model.UserAchievement;
import java.util.List;

public class SessionManager {
    private static final String PREF_NAME = "CityScapeSession";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_PREFERRED_CITY = "preferred_city";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context context;
    private AppDatabase db;

    public SessionManager(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
        db = AppDatabase.getInstance(context);
    }

    public void createSession(User user) {
        editor.putString(KEY_USER_ID, user.id);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();

        // Initialize badges for user if not exists
        initializeBadgesForUser(user.id);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserId() {
        try {
            return prefs.getString(KEY_USER_ID, null);
        } catch (ClassCastException e) {
            // Legacy int ID detected, clear it to force a fresh login with Supabase UUID
            editor.remove(KEY_USER_ID);
            editor.remove(KEY_IS_LOGGED_IN);
            editor.apply();
            return null;
        }
    }

    public User getCurrentUser() {
        String userId = getUserId();
        if (userId == null)
            return null;
        return db.userDao().getUserById(userId);
    }

    public String getUserName() {
        User user = getCurrentUser();
        return user != null ? user.name : "Explorer";
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }

    public void setDarkMode(boolean enabled) {
        editor.putBoolean(KEY_DARK_MODE, enabled);
        editor.apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public void setPreferredCity(String city) {
        if (editor != null) {
            editor.putString(KEY_PREFERRED_CITY, city);
            editor.apply();
        }
    }

    public String getPreferredCity() {
        return prefs.getString(KEY_PREFERRED_CITY, null);
    }

    private void initializeBadgesForUser(String userId) {
        // Check if badges already exist
        if (db.badgeDao().getBadgesForUser(userId).isEmpty()) {
            // Create default badges
            db.badgeDao().insert(
                    new UserBadge(userId, "first_steps", "First Steps", "Visit your first place", "ic_badge_first"));
            db.badgeDao()
                    .insert(new UserBadge(userId, "coffee_lover", "Coffee Lover", "Visit 5 cafes", "ic_badge_coffee"));
            db.badgeDao().insert(
                    new UserBadge(userId, "culture_seeker", "Culture Seeker", "Visit 3 museums", "ic_badge_culture"));
            db.badgeDao()
                    .insert(new UserBadge(userId, "night_owl", "Night Owl", "Check in after 10 PM", "ic_badge_night"));
            db.badgeDao().insert(new UserBadge(userId, "explorer", "Explorer", "Visit 50 places", "ic_badge_explorer"));
            db.badgeDao().insert(new UserBadge(userId, "foodie", "Foodie", "Visit 10 restaurants", "ic_badge_default"));
            db.badgeDao()
                    .insert(new UserBadge(userId, "social", "Social Butterfly", "Share 5 places", "ic_badge_default"));
            db.badgeDao()
                    .insert(new UserBadge(userId, "reviewer", "Top Reviewer", "Write 10 reviews", "ic_badge_default"));
        }
    }

    public void awardAchievement(String title, int xpReward) {
        String userId = getUserId();
        if (userId == null)
            return;

        // Add achievement
        db.achievementDao().insert(new UserAchievement(userId, title, xpReward));

        // Update user XP
        User user = getCurrentUser();
        if (user != null) {
            user.addXp(xpReward);
            db.userDao().update(user);
        }
    }

    public void unlockBadge(String badgeId) {
        String userId = getUserId();
        if (userId == null)
            return;

        UserBadge badge = db.badgeDao().getBadge(userId, badgeId);
        if (badge != null && !badge.isUnlocked) {
            badge.unlock();
            db.badgeDao().update(badge);

            // Update user badge count
            User user = getCurrentUser();
            if (user != null) {
                user.badgesEarned++;
                db.userDao().update(user);
            }

            // Award XP for unlocking badge
            awardAchievement("Unlocked: " + badge.name, 100);
        }
    }

    public void recordPlaceVisit(String placeName) {
        String userId = getUserId();
        if (userId == null)
            return;

        User user = getCurrentUser();
        if (user != null) {
            user.placesVisited++;
            db.userDao().update(user);

            // Award XP
            awardAchievement("Visited " + placeName, 50);

            // Check for badge unlocks
            if (user.placesVisited == 1) {
                unlockBadge("first_steps");
            }
            if (user.placesVisited >= 50) {
                unlockBadge("explorer");
            }
        }
    }

    public boolean isPlaceFavorite(String placeId) {
        return prefs.getBoolean("fav_" + placeId, false);
    }

    public void setPlaceFavorite(String placeId, boolean favorite) {
        editor.putBoolean("fav_" + placeId, favorite);
        editor.apply();
    }

    public java.util.Map<String, Integer> getPreferredCategories(
            java.util.List<com.cityscape.app.model.Place> allPlaces) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (com.cityscape.app.model.Place p : allPlaces) {
            if (isPlaceFavorite(p.id)) {
                counts.put(p.type, counts.getOrDefault(p.type, 0) + 1);
            }
        }
        return counts;
    }

    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LNG = "last_lng";

    public void saveLastLocation(double lat, double lng) {
        editor.putFloat(KEY_LAST_LAT, (float) lat);
        editor.putFloat(KEY_LAST_LNG, (float) lng);
        editor.apply();
    }

    public double getLastLat() {
        return (double) prefs.getFloat(KEY_LAST_LAT, 0.0f);
    }

    public double getLastLng() {
        return (double) prefs.getFloat(KEY_LAST_LNG, 0.0f);
    }
}
