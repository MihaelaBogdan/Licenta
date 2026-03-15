package com.example.licenta;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.example.licenta.data.LocaleHelper;

/**
 * Custom Application class that ensures the saved locale is applied
 * globally across the entire app from the very start.
 */
public class LicentaApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupPeriodicWork();
    }

    private void setupPeriodicWork() {
        androidx.work.PeriodicWorkRequest workRequest = new androidx.work.PeriodicWorkRequest.Builder(
                com.example.licenta.worker.ScheduleWorker.class,
                30, java.util.concurrent.TimeUnit.MINUTES)
                .build();

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ScheduleMonitor",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.applyLocale(this);
    }
}
