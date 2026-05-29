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
import com.fitcommit.app.utils.UiUtils;
import com.fitcommit.app.utils.ValidationUtils;

/**
 * iOS LoginView에 대응하는 로그인/회원가입 화면입니다.
 * 현재는 로컬 검증 후 MainActivity로 이동하며, API 연결은 AuthService에 위임합니다.
 */
public class LoginActivity extends Activity {
    private final AuthService authService = new AuthService();

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
        subtitleText.setText(signUpMode ? "나만의 핏 데이터를 안전하게 시작하세요." : "오늘의 핏을 기록하고, 내일의 스타일을 더 선명하게.");
        submitButton.setText(signUpMode ? "회원가입" : "로그인");
        modeSwitchText.setText(signUpMode ? "이미 계정이 있나요? 로그인하기" : "아직 계정이 없나요? 회원가입하기");
        feedbackText.setVisibility(View.GONE);
    }

    /**
     * SwiftUI LoginView의 입력 검증과 비슷한 규칙으로 제출 가능 여부를 확인합니다.
     */
    private void submit() {
        String email = emailEditText.getText().toString();
        String password = passwordEditText.getText().toString();

        if (!ValidationUtils.isEmailValid(email)) {
            showFeedback("@가 포함된 이메일을 입력해주세요.");
            return;
        }
        if (!ValidationUtils.isPasswordValid(password)) {
            showFeedback("8자 이상, 대/소문자/숫자/특수문자 중 3가지 이상 입력해주세요.");
            return;
        }

        if (signUpMode) {
            String name = nameEditText.getText().toString();
            String phone = phoneEditText.getText().toString();
            String confirm = passwordConfirmEditText.getText().toString();
            if (name.trim().isEmpty() || !ValidationUtils.isPhoneValid(phone) || !password.equals(confirm)) {
                showFeedback("이름, 휴대전화, 비밀번호 확인을 다시 확인해주세요.");
                return;
            }
            if (authService.signUp(name, email, phone, password)) {
                openMain();
            }
        } else if (authService.login(email, password)) {
            openMain();
        }
    }

    private void showFeedback(String message) {
        feedbackText.setText(message);
        feedbackText.setVisibility(View.VISIBLE);
    }

    private void openMain() {
        UiUtils.toast(this, "Fitcommit에 오신 것을 환영합니다.");
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
