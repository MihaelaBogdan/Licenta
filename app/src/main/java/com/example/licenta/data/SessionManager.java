package com.example.licenta.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.licenta.model.User;
import com.example.licenta.model.UserBadge;
import com.example.licenta.model.UserAchievement;

public class SessionManager {
    private static final String PREF_NAME = "LicentaSession";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

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
        editor.putInt(KEY_USER_ID, user.id);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();

        // Initialize badges for user if not exists
        initializeBadgesForUser(user.id);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public User getCurrentUser() {
        int userId = getUserId();
        if (userId == -1)
            return null;
        return db.userDao().getUserById(userId);
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }

    private void initializeBadgesForUser(int userId) {
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
        int userId = getUserId();
        if (userId == -1)
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
        int userId = getUserId();
        if (userId == -1)
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
        int userId = getUserId();
        if (userId == -1)
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
}
