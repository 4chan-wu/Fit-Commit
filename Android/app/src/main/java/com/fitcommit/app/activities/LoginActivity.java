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
        findViewById(R.id.kakaoButton).setOnClickListener(v -> startSocialLogin("카카오"));
        findViewById(R.id.naverButton).setOnClickListener(v -> startSocialLogin("네이버"));
        findViewById(R.id.googleButton).setOnClickListener(v -> startSocialLogin("Google"));
        findViewById(R.id.appleButton).setOnClickListener(v -> startSocialLogin("Apple"));
        findViewById(R.id.privacyPolicyLink).setOnClickListener(v ->
                startActivity(new Intent(this, LegalActivity.class)));
    }

    private void updateMode() {
        nameEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        phoneEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        passwordConfirmEditText.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        subtitleText.setText(signUpMode
                ? "기본 정보를 입력하고 나만의 스마트 옷장을 시작하세요."
                : "내 체형과 옷장을 연결해 매일 더 나은 코디를 만나보세요.");
        submitButton.setText(signUpMode ? "이메일로 회원가입" : "이메일로 로그인");
        modeSwitchText.setText(signUpMode
                ? "이미 계정이 있나요?  로그인"
                : "처음이신가요?  회원가입");
        feedbackText.setVisibility(View.GONE);
        setLoading(false);
    }

    private void submit() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (!ValidationUtils.isEmailValid(email)) {
            showFeedback("올바른 이메일 주소를 입력해주세요.");
            return;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            showFeedback("비밀번호는 8자 이상이며 문자 유형 3가지를 포함해야 합니다.");
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
            showFeedback("이름, 휴대전화 번호와 비밀번호 확인을 다시 확인해주세요.");
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
                    showFeedback("이미 가입된 이메일일 수 있어요. 입력한 이메일과 비밀번호로 로그인해주세요.");
                    return;
                }
                if (result.requiresEmailConfirmation()) {
                    setLoading(false);
                    showFeedback("인증 메일을 보냈어요. 받은편지함과 스팸함을 확인한 뒤 로그인해주세요.");
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
        submitButton.setText(loading ? "처리 중..." : (signUpMode ? "이메일로 회원가입" : "이메일로 로그인"));
    }

    private void startSocialLogin(String provider) {
        new AlertDialog.Builder(this)
                .setTitle(provider + " 로그인 준비 중")
                .setMessage(provider + " 개발자 앱과 Supabase OAuth 설정이 완료되면 사용할 수 있습니다. "
                        + "계정 인증 없이 홈으로 이동하던 테스트 동작은 안전을 위해 제거했습니다.")
                .setPositiveButton("확인", null)
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
            showFeedback("이메일 인증에 실패했습니다. " + errorDescription);
            return;
        }

        String accessToken = parameters.getQueryParameter("access_token");
        String refreshToken = parameters.getQueryParameter("refresh_token");
        if (accessToken == null || accessToken.trim().isEmpty()
                || refreshToken == null || refreshToken.trim().isEmpty()) {
            showFeedback("인증 정보를 확인할 수 없습니다. 인증 메일을 다시 요청해주세요.");
            return;
        }

        long expiresAt = parseLong(parameters.getQueryParameter("expires_at"));
        if (expiresAt <= 0L) {
            long expiresIn = parseLong(parameters.getQueryParameter("expires_in"));
            expiresAt = System.currentTimeMillis() / 1000L + Math.max(expiresIn, 3600L);
        }

        new TokenManager(this).saveSupabaseSession(accessToken, refreshToken, expiresAt, null);
        UiUtils.toast(this, "이메일 인증이 완료되었습니다.");
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
        UiUtils.toast(this, "Fitcommit에 오신 것을 환영합니다.");
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
