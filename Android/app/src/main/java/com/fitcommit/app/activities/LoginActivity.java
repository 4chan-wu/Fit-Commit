package com.fitcommit.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.services.AuthService;
import com.fitcommit.app.services.dto.LoginResponse;
import com.fitcommit.app.services.dto.SignUpResponse;
import com.fitcommit.app.utils.UiUtils;
import com.fitcommit.app.utils.ValidationUtils;

public class LoginActivity extends Activity {
    private AuthService authService;

    private boolean signUpMode = false;
    private EditText nameEditText;
    private EditText emailEditText;
    private EditText phoneEditText;
    private EditText passwordEditText;
    private EditText passwordConfirmEditText;
    private TextView subtitleText;
    private TextView feedbackText;
    private TextView modeSwitchText;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService(this);
        bindViews();
        setupActions();
        updateMode();
    }

    private void bindViews() {
        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        passwordConfirmEditText = findViewById(R.id.passwordConfirmEditText);
        subtitleText = findViewById(R.id.loginSubtitleText);
        feedbackText = findViewById(R.id.feedbackText);
        modeSwitchText = findViewById(R.id.modeSwitchText);
        submitButton = findViewById(R.id.submitButton);
    }

    private void setupActions() {
        submitButton.setOnClickListener(v -> submit());
        modeSwitchText.setOnClickListener(v -> {
            signUpMode = !signUpMode;
            updateMode();
        });
        findViewById(R.id.appleButton).setOnClickListener(v -> openMain());
        findViewById(R.id.googleButton).setOnClickListener(v -> openMain());
    }

    private void updateMode() {
        nameEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        phoneEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        passwordConfirmEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        subtitleText.setText(signUpMode
                ? "Create your Fitcommit account."
                : "Log in to continue your Fitcommit record.");
        submitButton.setText(signUpMode ? "Sign up" : "Log in");
        modeSwitchText.setText(signUpMode
                ? "Already have an account? Log in"
                : "No account yet? Sign up");
        feedbackText.setVisibility(View.GONE);
        setLoading(false);
    }

    private void submit() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (!ValidationUtils.isEmailValid(email)) {
            showFeedback("Enter a valid email address.");
            return;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            showFeedback("Password must be at least 8 characters and include 3 character types.");
            return;
        }

        if (signUpMode) {
            submitSignUp(email, password);
        } else {
            submitLogin(email, password);
        }
    }

    private void submitSignUp(String email, String password) {
        String name = nameEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String confirm = passwordConfirmEditText.getText().toString();

        if (name.isEmpty() || !ValidationUtils.isPhoneValid(phone) || !password.equals(confirm)) {
            showFeedback("Check name, phone number, and password confirmation.");
            return;
        }

        setLoading(true);
        authService.signUp(name, email, phone, password, confirm, new AuthService.AuthCallback<SignUpResponse>() {
            @Override
            public void onSuccess(SignUpResponse result) {
                submitLogin(email, password);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showFeedback(message);
            }
        });
    }

    private void submitLogin(String email, String password) {
        setLoading(true);
        authService.login(email, password, new AuthService.AuthCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                setLoading(false);
                openMain();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showFeedback(message);
            }
        });
    }

    private void setLoading(boolean loading) {
        submitButton.setEnabled(!loading);
        modeSwitchText.setEnabled(!loading);
        submitButton.setText(loading ? "Please wait..." : (signUpMode ? "Sign up" : "Log in"));
    }

    private void showFeedback(String message) {
        feedbackText.setText(message);
        feedbackText.setVisibility(View.VISIBLE);
    }

    private void openMain() {
        UiUtils.toast(this, "Welcome to Fitcommit.");
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
