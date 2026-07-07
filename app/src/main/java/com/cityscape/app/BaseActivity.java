package com.cityscape.app;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.cityscape.app.data.LocaleHelper;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@androidx.annotation.Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }
}
