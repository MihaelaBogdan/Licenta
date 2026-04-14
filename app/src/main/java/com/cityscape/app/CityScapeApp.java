package com.cityscape.app;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.cityscape.app.data.LocaleHelper;

/**
 * Custom Application class that ensures the saved locale is applied
 * globally across the entire app from the very start.
 */
public class CityScapeApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.applyLocale(this);
    }
}
