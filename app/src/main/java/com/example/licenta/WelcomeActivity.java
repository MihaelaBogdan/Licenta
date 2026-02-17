package com.example.licenta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btnGetStarted = findViewById(R.id.btn_get_started);
        TextView textAlreadyAccount = findViewById(R.id.text_already_account);

        btnGetStarted.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class));
        });

        textAlreadyAccount.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });
    }
}
