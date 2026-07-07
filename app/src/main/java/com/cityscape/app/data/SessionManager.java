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
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_PREFERRED_CITY = "preferred_city";
    private static final String KEY_PREFERRED_LAT  = "preferred_lat";
    private static final String KEY_PREFERRED_LNG  = "preferred_lng";

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
        editor.putString(KEY_USER_NAME, user.name);
        editor.putString(KEY_USER_EMAIL, user.email);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();

        
        new Thread(() -> {
            if (db.userDao().getUserById(user.id) == null) {
                db.userDao().insert(user);
            }
        }).start();

        
        initializeBadgesForUser(user.id);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserId() {
        try {
            return prefs.getString(KEY_USER_ID, null);
        } catch (ClassCastException e) {
            
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
        String name = prefs.getString(KEY_USER_NAME, null);
        if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Explorer")) {
            return name;
        }
        
        User user = getCurrentUser();
        if (user != null && user.name != null && !user.name.isEmpty()) {
            
            editor.putString(KEY_USER_NAME, user.name).apply();
            return user.name;
        }
        
        
        if (user != null && user.email != null && user.email.contains("@")) {
            return user.email.split("@")[0];
        }
        
        return "Explorer";
    }

    public void updateUserName(String newName) {
        editor.putString(KEY_USER_NAME, newName);
        editor.apply();
    }

    public String getEmail() {
        String email = prefs.getString(KEY_USER_EMAIL, null);
        if (email != null) return email;
        User user = getCurrentUser();
        return (user != null) ? user.email : null;
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

    public void setInterestsCompleted(boolean completed) {
        editor.putBoolean("interests_completed", completed);
        editor.apply();
    }

    public boolean isInterestsCompleted() {
        return prefs.getBoolean("interests_completed", false);
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

    /** Store the lat/lng of the manually selected city. Pass Double.NaN to clear. */
    public void setPreferredLocation(double lat, double lng) {
        if (editor != null) {
            if (Double.isNaN(lat) || Double.isNaN(lng)) {
                editor.remove(KEY_PREFERRED_LAT).remove(KEY_PREFERRED_LNG);
            } else {
                editor.putString(KEY_PREFERRED_LAT, String.valueOf(lat));
                editor.putString(KEY_PREFERRED_LNG, String.valueOf(lng));
            }
            editor.apply();
        }
    }

    /** Returns the stored preferred latitude, or Double.NaN if not set. */
    public double getPreferredLat() {
        String v = prefs.getString(KEY_PREFERRED_LAT, null);
        if (v == null) return Double.NaN;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** Returns the stored preferred longitude, or Double.NaN if not set. */
    public double getPreferredLng() {
        String v = prefs.getString(KEY_PREFERRED_LNG, null);
        if (v == null) return Double.NaN;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** True if user has manually selected a city (has stored coords). */
    public boolean hasManualLocation() {
        return !Double.isNaN(getPreferredLat()) && !Double.isNaN(getPreferredLng());
    }

    private void initializeBadgesForUser(String userId) {
        
        if (db.badgeDao().getBadgesForUser(userId).isEmpty()) {
            
            db.badgeDao().insert(new UserBadge(userId, "first_steps", "First Steps", "Vizitează prima ta locație", "ic_badge_first", "Vizitează o locație de pe hartă"));
            db.badgeDao().insert(new UserBadge(userId, "coffee_lover", "Coffee Lover", "Ești un fan al cafelei", "ic_badge_coffee", "Vizitează 5 cafenele diferite"));
            db.badgeDao().insert(new UserBadge(userId, "culture_seeker", "Culture Seeker", "Explorator cultural", "ic_badge_culture", "Vizitează 3 muzee sau galerii de artă"));
            db.badgeDao().insert(new UserBadge(userId, "night_owl", "Night Owl", "Explorator de noapte", "ic_badge_night", "Vizitează o locație după ora 22:00"));
            db.badgeDao().insert(new UserBadge(userId, "explorer", "Explorer", "Mare explorator urban", "ic_badge_explorer", "Vizitează 50 de locații în total"));
            db.badgeDao().insert(new UserBadge(userId, "foodie", "Foodie", "Pasionat de gastronomie", "ic_badge_default", "Vizitează 10 restaurante recomandate"));
            db.badgeDao().insert(new UserBadge(userId, "social", "Social Butterfly", "Activ în comunitate", "ic_badge_default", "Postează 5 experiențe în feed"));
            db.badgeDao().insert(new UserBadge(userId, "reviewer", "Top Reviewer", "Criticul orașului", "ic_badge_default", "Scrie 10 recenzii detaliate"));
            db.badgeDao().insert(new UserBadge(userId, "hype_master", "Hype Master", "Votant activ în confruntări", "ic_badge_generic", "Votează în bătăliile live Hype Battles"));
        }
    }

    public void awardAchievement(String title, int xpReward) {
        String userId = getUserId();
        if (userId == null)
            return;

        
        db.achievementDao().insert(new UserAchievement(userId, title, xpReward));

        
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

            
            User user = getCurrentUser();
            if (user != null) {
                user.badgesEarned++;
                db.userDao().update(user);
            }

            
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

            
            awardAchievement("Visited " + placeName, 50);

            
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
