package com.cityscape.app.util;

import android.content.Context;
import android.widget.Toast;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SupabaseSyncManager;
import com.cityscape.app.model.UserBadge;

public class BadgeManager {

    public static void awardPostBadge(Context context, String userId) {
        if (userId == null || context == null) return;

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            // Badge ID for social: ic_badge_social
            UserBadge badge = db.badgeDao().getBadge(userId, "ic_badge_social");

            if (badge == null) {
                // Create badge if not exists
                badge = new UserBadge(userId, "ic_badge_social", "Social Explorer", 
                                     "Ai postat prima ta experiență în feed!", "ic_badge_social");
                badge.unlock();
                db.badgeDao().insert(badge);
                SupabaseSyncManager.getInstance(context).pushBadgeToCloud(badge);
                
                showBadgeToast(context, "Social Explorer");
            } else if (!badge.isUnlocked) {
                // Unlock if exists but locked
                badge.isUnlocked = true;
                db.badgeDao().update(badge);
                SupabaseSyncManager.getInstance(context).pushBadgeToCloud(badge);
                
                showBadgeToast(context, "Social Explorer");
            }
        }).start();
    }

    private static void showBadgeToast(Context context, String name) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "🏆 Badge deblocat: " + name, Toast.LENGTH_LONG).show();
            });
        }
    }
}
