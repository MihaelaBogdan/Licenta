package com.example.licenta.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.licenta.MainActivity;
import com.example.licenta.R;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.model.PlannedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleWorker extends Worker {
    private static final String TAG = "ScheduleWorker";
    private static final String CHANNEL_ID = "itinerary_optimizer";

    public ScheduleWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Checking schedule for re-scheduling opportunities...");

        Context context = getApplicationContext();
        SessionManager session = new SessionManager(context);
        String userId = session.getUserId();

        if (userId == null)
            return Result.success();

        AppDatabase db = AppDatabase.getInstance(context);
        long today = getTodayTimestamp();

        // Get today's activities that aren't completed yet
        List<PlannedActivity> activities = db.activityDao().getActivitiesForDate(userId, today);
        if (activities.isEmpty())
            return Result.success();

        PlannedActivity currentActivity = null;
        PlannedActivity nextActivity = null;

        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        for (int i = 0; i < activities.size(); i++) {
            PlannedActivity act = activities.get(i);
            if (isTimeAfter(currentTime, act.scheduledTime)) {
                currentActivity = act;
                if (i + 1 < activities.size()) {
                    nextActivity = activities.get(i + 1);
                }
            }
        }

        if (currentActivity != null && nextActivity != null) {
            // Check if user is still at the 'currentActivity' location but it's almost time
            // for 'nextActivity'
            // For now, let's simulate the delay if current time is past nextActivity's
            // start - 15 mins
            if (isTimeClose(currentTime, nextActivity.scheduledTime)) {
                checkLocationAndNotify(context, currentActivity, nextActivity);
            }
        }

        return Result.success();
    }

    private void checkLocationAndNotify(Context context, PlannedActivity current, PlannedActivity next) {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        try {
            Location location = Tasks.await(fusedLocationClient.getLastLocation());
            if (location != null) {
                float[] results = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                        current.latitude, current.longitude, results);

                float distanceInMeters = results[0];
                Log.d(TAG, "User distance from current activity: " + distanceInMeters + "m");

                // If user is within 200m of the current (past-due) activity
                if (distanceInMeters < 200) {
                    sendOptimizationNotification(context, current, next);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting location", e);
            // Fallback: Notify based on time alone if GPS fails for demo purposes
            sendOptimizationNotification(context, current, next);
        }
    }

    private void sendOptimizationNotification(Context context, PlannedActivity current, PlannedActivity next) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Optimizar Itinerariu",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("ACTION", "OPTIMIZE_ITINERARY");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("⌚ Încă te bucuri de " + current.placeName + "?")
                .setContentText("Pare că ești încă aici! Vrei să reorganizăm restul zilei pentru tine?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(1, builder.build());
    }

    private long getTodayTimestamp() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private boolean isTimeAfter(String current, String scheduled) {
        return current.compareTo(scheduled) >= 0;
    }

    private boolean isTimeClose(String current, String target) {
        // Simple logic: within 15 mins of target
        return isTimeAfter(current, target); // For now, if past target
    }
}
