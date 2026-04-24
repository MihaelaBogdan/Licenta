package com.cityscape.app.data;

import android.content.Context;
import android.util.Log;

import com.cityscape.app.model.ActivityGroup;
import com.cityscape.app.model.PlannedActivity;
import com.cityscape.app.model.GroupMember;
import com.cityscape.app.model.MemberSchedule;
import com.cityscape.app.model.Invitation;

import java.util.List;

public class SupabaseSyncManager {
    private static final String TAG = "SupabaseSyncManager";
    private static SupabaseSyncManager instance;
    private Context context;
    private AppDatabase db;
    private SupabaseDataManager cloudData;
    private SessionManager sessionManager;

    private SupabaseSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
        this.cloudData = SupabaseDataManager.getInstance(context);
        this.sessionManager = new SessionManager(context);
    }

    public static synchronized SupabaseSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseSyncManager(context);
        }
        return instance;
    }

    public void initialSync() {
        String userId = sessionManager.getUserId();
        if (userId == null)
            return;

        Log.d(TAG, "Starting Initial Sync with Supabase Cloud...");
        syncUserProfile(userId);
        syncGroups(userId);
    }

    public void syncUserProfile(String userId) {
        cloudData.getUserProfile(userId, new SupabaseDataManager.DataCallback<com.cityscape.app.model.User>() {
            @Override
            public void onSuccess(com.cityscape.app.model.User cloudUser) {
                if (cloudUser != null) {
                    db.userDao().insert(cloudUser); // insert/replace
                    Log.d(TAG, "Successfully synced profile for: " + cloudUser.name);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to sync profile: " + error);
            }
        });
    }

    private void syncGroups(String userId) {
        cloudData.getGroupsForUser(userId, new SupabaseDataManager.DataCallback<List<ActivityGroup>>() {
            @Override
            public void onSuccess(List<ActivityGroup> cloudGroups) {
                if (cloudGroups != null) {
                    for (ActivityGroup group : cloudGroups) {
                        try {
                            db.groupDao().insertGroup(group);
                        } catch (Exception e) {
                            db.groupDao().updateGroup(group);
                        }
                    }
                    Log.d(TAG, "Successfully synced " + cloudGroups.size() + " groups from cloud.");
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to sync groups: " + error);
            }
        });
    }

    // =======================================================
    // PUSH NOTIFICATIONS TO CLOUD (UPLOAD)
    // =======================================================

    public void pushActivityToCloud(PlannedActivity activity) {
        cloudData.insertActivity(activity, null);
    }

    public void updateActivityInCloud(PlannedActivity activity) {
        cloudData.updateActivity(activity, null);
    }

    public void pushGroupToCloud(ActivityGroup group) {
        cloudData.insertGroup(group, null);
    }

    // Auto-sync hooks for others (mocked to cloud interface until expanded)
    public void pushMemberToCloud(GroupMember member) {
        Log.d(TAG, "Group membership pushed");
    }

    public void pushScheduleToCloud(MemberSchedule schedule) {
        Log.d(TAG, "Schedule pushed");
    }

    public void pushInvitationToCloud(Invitation invitation) {
        Log.d(TAG, "Invitation pushed");
    }

    public void updateInvitationInCloud(Invitation invitation) {
        Log.d(TAG, "Invitation updated");
    }

    public void pushBadgeToCloud(com.cityscape.app.model.UserBadge badge) {
        cloudData.insertBadge(badge, null);
    }
}
