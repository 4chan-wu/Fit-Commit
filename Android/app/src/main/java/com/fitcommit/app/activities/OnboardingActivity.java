package com.fitcommit.app.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.utils.UiUtils;

/**
 * 처음 실행했을 때 한 번만 보여주는 앱 소개 화면입니다.
 * 버튼 또는 좌우 스와이프로 넘길 수 있고, 건너뛰어도 다시 표시되지 않습니다.
 */
public class OnboardingActivity extends Activity {
    private static final String PREFS_NAME = "fitcommit_onboarding";
    private static final String KEY_COMPLETED = "completed";
    private static final int SWIPE_MIN_VELOCITY = 500;
    private static final int SWIPE_MIN_DISTANCE_DP = 60;
    private static final long PAGE_FADE_MS = 120L;

    private static final int[] ICONS = {
            R.drawable.ic_garment,
            R.drawable.ic_tab_sparkles,
            R.drawable.ic_tab_user
    };
    private static final int[] TITLES = {
            R.string.onboarding_1_title,
            R.string.onboarding_2_title,
            R.string.onboarding_3_title
    };
    private static final int[] BODIES = {
            R.string.onboarding_1_body,
            R.string.onboarding_2_body,
            R.string.onboarding_3_body
    };

    private View pageContent;
    private ImageView iconView;
    private TextView titleText;
    private TextView bodyText;
    private LinearLayout dotRow;
    private Button nextButton;
    private TextView skipButton;
    private GestureDetector swipeDetector;
    private int page;

    public static boolean isCompleted(Context context) {
        return prefs(context).getBoolean(KEY_COMPLETED, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pageContent = findViewById(R.id.onboardingPageContent);
        iconView = findViewById(R.id.onboardingIcon);
        titleText = findViewById(R.id.onboardingTitle);
        bodyText = findViewById(R.id.onboardingBody);
        dotRow = findViewById(R.id.onboardingDots);
        nextButton = findViewById(R.id.onboardingNextButton);
        skipButton = findViewById(R.id.onboardingSkipButton);

        if (savedInstanceState != null) {
            page = Math.max(0, Math.min(ICONS.length - 1, savedInstanceState.getInt("page", 0)));
        }

        nextButton.setOnClickListener(v -> goNext());
        skipButton.setOnClickListener(v -> finishOnboarding());
        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent start, MotionEvent end, float velocityX, float velocityY) {
                if (start == null || end == null) return false;
                float dx = end.getX() - start.getX();
                float dy = end.getY() - start.getY();
                boolean horizontal = Math.abs(dx) > Math.abs(dy) * 1.5f;
                boolean farEnough = Math.abs(dx) > UiUtils.dp(OnboardingActivity.this, SWIPE_MIN_DISTANCE_DP);
                if (!horizontal || !farEnough || Math.abs(velocityX) < SWIPE_MIN_VELOCITY) return false;
                if (dx < 0) {
                    goNext();
                } else {
                    goPrevious();
                }
                return true;
            }
        });

        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("page", page);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        swipeDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void goNext() {
        if (page >= ICONS.length - 1) {
            finishOnboarding();
            return;
        }
        changePage(page + 1);
    }

    private void goPrevious() {
        if (page > 0) changePage(page - 1);
    }

    private void changePage(int newPage) {
        page = newPage;
        pageContent.animate().cancel();
        pageContent.animate().alpha(0f).setDuration(PAGE_FADE_MS).withEndAction(() -> {
            render();
            pageContent.animate().alpha(1f).setDuration(PAGE_FADE_MS).start();
        }).start();
    }

    private void render() {
        boolean last = page == ICONS.length - 1;
        iconView.setImageResource(ICONS[page]);
        titleText.setText(TITLES[page]);
        bodyText.setText(BODIES[page]);
        nextButton.setText(last ? R.string.onboarding_start : R.string.onboarding_next);
        skipButton.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        renderDots();
    }

    private void renderDots() {
        dotRow.removeAllViews();
        for (int i = 0; i < ICONS.length; i++) {
            boolean selected = i == page;
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(UiUtils.dp(this, 4));
            shape.setColor(getColor(selected ? R.color.fit_primary : R.color.fit_border));
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    UiUtils.dp(this, selected ? 22 : 8),
                    UiUtils.dp(this, 8)
            );
            params.setMargins(UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4), 0);
            dotRow.addView(dot, params);
        }
    }

    private void finishOnboarding() {
        prefs(this).edit().putBoolean(KEY_COMPLETED, true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
