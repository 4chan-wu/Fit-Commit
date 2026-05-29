package com.fitcommit.app.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 반복되는 Android UI 보조 작업을 모은 유틸리티입니다.
 */
public final class UiUtils {
    private UiUtils() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void setTextColor(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
    }

    public static int parseColorOrDefault(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
