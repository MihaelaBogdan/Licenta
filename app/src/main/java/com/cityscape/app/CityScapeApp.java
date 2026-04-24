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
        
        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(this);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            sessionManager.isDarkMode() ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : 
                                         androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );


    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.applyLocale(this);
    }
}
