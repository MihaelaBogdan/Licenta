package com.cityscape.app.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.cityscape.app.R;
import com.cityscape.app.activities.CalendarActivity;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.ActivityInvite;
import com.cityscape.app.database.entities.PlannedActivity;
import com.cityscape.app.database.entities.User;

import java.util.concurrent.Executors;

/**
 * Service for sending notifications and email invitations
 */
public class InviteService {

    private static final String CHANNEL_ID = "cityscape_invites";
    private static final String CHANNEL_NAME = "Activity Invites";
    private static final int NOTIFICATION_ID_BASE = 1000;

    private final Context context;
    private final AppDatabase database;

    public InviteService(Context context) {
        this.context = context;
        this.database = AppDatabase.getDatabase(context);
        createNotificationChannel();
    }

    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications for activity invitations");

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Send invitation for an activity
     */
    public void sendInvite(String activityId, String senderUserId,
            String inviteeEmail, String message) {

        Executors.newSingleThreadExecutor().execute(() -> {
            // Create invite record
            String inviteId = java.util.UUID.randomUUID().toString();
            ActivityInvite invite = new ActivityInvite(
                    inviteId, activityId, senderUserId, inviteeEmail);
            invite.setMessage(message);

            // Check if invitee is already a user
            User invitee = database.userDao().getUserByEmailSync(inviteeEmail);
            if (invitee != null) {
                invite.setInviteeUserId(invitee.getId());
                // Send push notification
                sendNotification(invite, senderUserId);
            }

            // Save invite
            database.activityInviteDao().insert(invite);

            // Send email (simulated - in real app would use API)
            sendEmailInvite(invite, senderUserId);
        });
    }

    /**
     * Send in-app notification for invite
     */
    private void sendNotification(ActivityInvite invite, String senderUserId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            User sender = database.userDao().getUserByIdSync(senderUserId);
            PlannedActivity activity = database.plannedActivityDao()
                    .getActivityByIdSync(invite.getActivityId());

            if (sender == null || activity == null)
                return;

            String title = "Activity Invitation";
            String text = sender.getName() + " invited you to " +
                    (activity.getPlaceName() != null ? activity.getPlaceName() : "an activity");

            // Create notification intent
            Intent intent = new Intent(context, CalendarActivity.class);
            intent.putExtra("inviteId", invite.getId());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_calendar)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_check, "Accept",
                            createActionIntent(invite.getId(), "accept"))
                    .addAction(R.drawable.ic_close, "Decline",
                            createActionIntent(invite.getId(), "decline"));

            // Show notification
            try {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.notify(
                        NOTIFICATION_ID_BASE + invite.getId().hashCode(),
                        builder.build());

                // Mark as sent
                database.activityInviteDao().markNotificationSent(invite.getId());
            } catch (SecurityException e) {
                // Notification permission not granted
            }
        });
    }

    /**
     * Send email invitation (simulated)
     */
    private void sendEmailInvite(ActivityInvite invite, String senderUserId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            User sender = database.userDao().getUserByIdSync(senderUserId);
            PlannedActivity activity = database.plannedActivityDao()
                    .getActivityByIdSync(invite.getActivityId());

            if (sender == null || activity == null)
                return;

            // In a real app, this would call an email API
            // For now, we just mark it as sent
            String emailSubject = sender.getName() + " invited you to explore " +
                    (activity.getPlaceName() != null ? activity.getPlaceName() : "a place");
            String emailBody = "Hi!\n\n" + sender.getName() +
                    " has invited you to join them at " +
                    (activity.getPlaceName() != null ? activity.getPlaceName() : "an activity") +
                    " on " + activity.getDate() + " at " + activity.getTime() + ".\n\n" +
                    (invite.getMessage() != null ? "Message: " + invite.getMessage() + "\n\n" : "") +
                    "Download CityScape to respond!\n\n" +
                    "Best,\nThe CityScape Team";

            // Log email (in real app, send via API)
            android.util.Log.d("InviteService", "Email to: " + invite.getInviteeEmail());
            android.util.Log.d("InviteService", "Subject: " + emailSubject);

            // Mark as sent
            database.activityInviteDao().markEmailSent(invite.getId());
        });
    }

    private PendingIntent createActionIntent(String inviteId, String action) {
        Intent intent = new Intent(context, CalendarActivity.class);
        intent.putExtra("inviteId", inviteId);
        intent.putExtra("action", action);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int requestCode = (action + inviteId).hashCode();
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Accept an invite
     */
    public void acceptInvite(String inviteId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            database.activityInviteDao().updateStatus(inviteId, ActivityInvite.STATUS_ACCEPTED);
        });
    }

    /**
     * Decline an invite
     */
    public void declineInvite(String inviteId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            database.activityInviteDao().updateStatus(inviteId, ActivityInvite.STATUS_DECLINED);
        });
    }
}
