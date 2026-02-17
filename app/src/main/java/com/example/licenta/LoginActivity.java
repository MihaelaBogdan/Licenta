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

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private Button loginButton;
    private TextView registerPrompt;
    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        // Check if already logged in
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        emailEditText = findViewById(R.id.input_email);
        passwordEditText = findViewById(R.id.input_password);
        loginButton = findViewById(R.id.btn_login);
        registerPrompt = findViewById(R.id.text_register_prompt);

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Authenticate user
            User user = db.userDao().login(email, password);
            if (user != null) {
                sessionManager.createSession(user);
                Toast.makeText(this, "Welcome back, " + user.name + "!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
            }
        });

        registerPrompt.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }
}
