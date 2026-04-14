package com.cityscape.app;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.cityscape.app.data.LocaleHelper;

/**
 * Base activity that applies the saved locale.
 * All activities should extend this instead of AppCompatActivity.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@androidx.annotation.Nullable android.os.Bundle savedInstanceState) {
        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(this);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            sessionManager.isDarkMode() ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : 
                                         androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }
}
