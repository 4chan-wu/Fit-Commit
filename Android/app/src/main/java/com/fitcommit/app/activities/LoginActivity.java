package com.fitcommit.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.services.AuthService;
import com.fitcommit.app.services.TokenManager;
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
        handleAuthCallback(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthCallback(intent);
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
        findViewById(R.id.kakaoButton).setOnClickListener(v -> startSocialLogin("Kakao"));
        findViewById(R.id.naverButton).setOnClickListener(v -> startSocialLogin("Naver"));
        findViewById(R.id.googleButton).setOnClickListener(v -> startSocialLogin("Google"));
        findViewById(R.id.appleButton).setOnClickListener(v -> startSocialLogin("Apple"));
        findViewById(R.id.privacyPolicyLink).setOnClickListener(v ->
                startActivity(new Intent(this, LegalActivity.class)));
    }

    private void updateMode() {
        nameEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        phoneEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        passwordConfirmEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        subtitleText.setText(signUpMode ? R.string.signup_subtitle : R.string.login_subtitle);
        submitButton.setText(signUpMode ? R.string.signup_email : R.string.ui_072);
        modeSwitchText.setText(signUpMode ? R.string.login_switch_login : R.string.ui_073);
        feedbackText.setVisibility(View.GONE);
        setLoading(false);
    }

    private void submit() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (!ValidationUtils.isEmailValid(email)) {
            showFeedback(getString(R.string.validation_email));
            return;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            showFeedback(getString(R.string.validation_password));
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
            showFeedback(getString(R.string.validation_signup));
            return;
        }

        setLoading(true);
        authService.signUp(name, email, phone, password, confirm, new AuthService.AuthCallback<SignUpResponse>() {
            @Override
            public void onSuccess(SignUpResponse result) {
                // Supabase can intentionally return an empty 200 response when an already
                // registered address signs up again. It does not send another confirmation
                // email in that case, so do not tell the user to wait for a mail that will
                // never arrive.
                if (!result.hasUser()) {
                    signUpMode = false;
                    updateMode();
                    showFeedback(getString(R.string.signup_existing_email));
                    return;
                }
                if (result.requiresEmailConfirmation()) {
                    setLoading(false);
                    showFeedback(getString(R.string.signup_email_sent));
                } else {
                    submitLogin(email, password);
                }
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
        submitButton.setText(loading
                ? getString(R.string.loading)
                : getString(signUpMode ? R.string.signup_email : R.string.ui_072));
    }

    private void startSocialLogin(String provider) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.social_login_title, provider))
                .setMessage(getString(R.string.social_login_message, provider))
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }

    private void showFeedback(String message) {
        feedbackText.setText(message);
        feedbackText.setVisibility(View.VISIBLE);
    }

    private void handleAuthCallback(Intent intent) {
        Uri callback = intent == null ? null : intent.getData();
        if (callback == null
                || !"fitcommit".equals(callback.getScheme())
                || !"auth".equals(callback.getHost())
                || !"/callback".equals(callback.getPath())) {
            return;
        }

        Uri parameters = fragmentParameters(callback.getFragment());
        String errorDescription = parameters.getQueryParameter("error_description");
        if (errorDescription == null || errorDescription.trim().isEmpty()) {
            errorDescription = parameters.getQueryParameter("error");
        }
        if (errorDescription != null && !errorDescription.trim().isEmpty()) {
            showFeedback(getString(R.string.email_verification_failed, errorDescription));
            return;
        }

        String accessToken = parameters.getQueryParameter("access_token");
        String refreshToken = parameters.getQueryParameter("refresh_token");
        if (accessToken == null || accessToken.trim().isEmpty()
                || refreshToken == null || refreshToken.trim().isEmpty()) {
            showFeedback(getString(R.string.auth_details_missing));
            return;
        }

        long expiresAt = parseLong(parameters.getQueryParameter("expires_at"));
        if (expiresAt <= 0L) {
            long expiresIn = parseLong(parameters.getQueryParameter("expires_in"));
            expiresAt = System.currentTimeMillis() / 1000L + Math.max(expiresIn, 3600L);
        }

        new TokenManager(this).saveSupabaseSession(accessToken, refreshToken, expiresAt, null);
        UiUtils.toast(this, getString(R.string.email_verified));
        openMain();
    }

    private Uri fragmentParameters(String fragment) {
        String safeFragment = fragment == null ? "" : fragment;
        return Uri.parse("https://fitcommit.invalid/?" + safeFragment);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void openMain() {
        UiUtils.toast(this, getString(R.string.welcome));
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
