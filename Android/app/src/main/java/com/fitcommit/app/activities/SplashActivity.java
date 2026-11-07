package com.fitcommit.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.fitcommit.app.R;
import com.fitcommit.app.services.TokenManager;

/**
 * 앱 시작 시 로고를 약 2초간 보여준 뒤 다음 화면으로 이동합니다.
 * 처음 설치한 사용자에게는 튜토리얼을, 그 외에는 로그인(또는 저장된 세션 복원) 화면을 보여줍니다.
 */
public class SplashActivity extends Activity {
    private static final long SPLASH_DURATION_MS = 2000L;
    private static final long FADE_IN_DURATION_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openNext = this::openNext;
    private boolean opened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View content = findViewById(R.id.splashContent);
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(FADE_IN_DURATION_MS).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 백그라운드에서 화면 전환이 막히지 않도록 화면이 보이는 동안에만 타이머를 돌립니다.
        handler.removeCallbacks(openNext);
        handler.postDelayed(openNext, SPLASH_DURATION_MS);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(openNext);
        super.onStop();
    }

    private void openNext() {
        if (opened || isFinishing()) return;
        opened = true;

        boolean showTutorial = !OnboardingActivity.isCompleted(this)
                && !new TokenManager(this).hasRestorableSession();
        Class<?> next = showTutorial ? OnboardingActivity.class : LoginActivity.class;
        startActivity(new Intent(this, next));
        finish();
    }
}
