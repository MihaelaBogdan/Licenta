package com.example.licenta;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.model.User;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText nameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private Button registerButton;
    private TextView loginPrompt;
    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        nameEditText = findViewById(R.id.input_name);
        emailEditText = findViewById(R.id.input_email);
        passwordEditText = findViewById(R.id.input_password);
        registerButton = findViewById(R.id.btn_register);
        loginPrompt = findViewById(R.id.text_login_prompt);

        registerButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check if email already exists
            if (db.userDao().getUserByEmail(email) != null) {
                Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create new user
            User newUser = new User(name, email, password);
            long userId = db.userDao().insert(newUser);
            newUser.id = (int) userId;

            // Create session and award first achievement
            sessionManager.createSession(newUser);
            sessionManager.awardAchievement("Account Created", 100);

            Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        loginPrompt.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
