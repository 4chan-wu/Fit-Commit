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
        if (hex != null) {
            switch (hex.trim().toLowerCase()) {
                case "black":
                    return Color.BLACK;
                case "white":
                    return Color.WHITE;
                case "gray":
                case "grey":
                    return Color.GRAY;
                case "red":
                    return Color.rgb(220, 40, 40);
                case "orange":
                    return Color.rgb(235, 130, 40);
                case "yellow":
                    return Color.rgb(230, 210, 50);
                case "green":
                    return Color.rgb(50, 150, 80);
                case "blue":
                    return Color.rgb(50, 100, 210);
                case "navy":
                    return Color.rgb(20, 35, 90);
                case "purple":
                    return Color.rgb(130, 80, 180);
                case "pink":
                    return Color.rgb(230, 130, 170);
                case "brown":
                    return Color.rgb(120, 80, 50);
                case "beige":
                    return Color.rgb(210, 190, 150);
            }
        }
        try {
            return Color.parseColor(hex);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
