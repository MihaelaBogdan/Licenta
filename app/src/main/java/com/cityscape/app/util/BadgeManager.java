package com.cityscape.app.util;

import android.content.Context;
import android.widget.Toast;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SupabaseSyncManager;
import com.cityscape.app.model.UserBadge;

public class BadgeManager {

    private static final String TAG = "BadgeManager";

    /**
     * Awards XP and checks for level-ups.
     */
    public static void addExperience(Context context, String userId, int amount) {
        if (userId == null || context == null) return;
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            com.cityscape.app.model.User user = db.userDao().getUserById(userId);
            if (user != null) {
                int oldLevel = user.level;
                user.addXp(amount);
                db.userDao().update(user);
                
                if (user.level > oldLevel) {
                    showLevelUpToast(context, user.level);
                }
            }
        }).start();
    }

    /**
     * Generic method to award a badge by ID.
     */
    public static void awardBadge(Context context, String userId, String badgeId, String name, String desc, String icon) {
        if (userId == null || context == null || userId.isEmpty()) return;
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            com.cityscape.app.model.UserBadge badge = db.badgeDao().getBadge(userId, badgeId);

            if (badge == null) {
                badge = new com.cityscape.app.model.UserBadge(userId, badgeId, name, desc, icon);
                badge.unlock();
                db.badgeDao().insert(badge);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(context).pushBadgeToCloud(badge);
                
                // Add bonus XP for badge
                addExperience(context, userId, 250);
                showBadgeToast(context, name);
            } else if (!badge.isUnlocked) {
                badge.unlock();
                db.badgeDao().update(badge);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(context).pushBadgeToCloud(badge);
                
                addExperience(context, userId, 250);
                showBadgeToast(context, name);
            }
        }).start();
    }

    public static void awardPostBadge(Context context, String userId) {
        awardBadge(context, userId, "ic_badge_social", "Social Explorer", 
                  "Ai postat prima ta experiență în feed!", "ic_badge_social");
    }

    /**
     * Checks for visit-based badges (Early Bird, Night Owl, etc).
     */
    public static void checkVisitBadges(Context context, String userId, String placeType) {
        long hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        
        if (hour < 9) {
            awardBadge(context, userId, "early_bird", "Early Bird", "Ai vizitat locații înainte de 9 dimineața!", "ic_badge_early");
        } else if (hour >= 22) {
            awardBadge(context, userId, "night_owl", "Night Owl", "Ești un explorator nocturn!", "ic_badge_night");
        }

        if (placeType != null) {
            if (placeType.toLowerCase().contains("restaurant")) {
                 awardBadge(context, userId, "foodie", "Food Critic", "Ai început să explorezi gastronomia locală!", "ic_badge_generic");
            } else if (placeType.toLowerCase().contains("cafe") || placeType.toLowerCase().contains("cafenea")) {
                 awardBadge(context, userId, "caffeine", "Caffeine Addict", "Energie pură din cele mai bune cafenele!", "ic_badge_coffee");
            }
        }
    }

    private static void showBadgeToast(Context context, String name) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "🏆 Badge deblocat: " + name, Toast.LENGTH_LONG).show();
            });
        }
    }

    private static void showLevelUpToast(Context context, int newLevel) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "✨ FELICITĂRI! Ai ajuns la Nivelul " + newLevel + "!", Toast.LENGTH_LONG).show();
            });
        }
    }
}
