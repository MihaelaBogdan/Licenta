package com.example.licenta;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.example.licenta.data.LocaleHelper;

/**
 * Base activity that applies the saved locale.
 * All activities should extend this instead of AppCompatActivity.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }
}
