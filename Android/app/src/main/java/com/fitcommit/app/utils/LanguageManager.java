package com.fitcommit.app.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public final class LanguageManager {
    public static final String SYSTEM = "";
    public static final String KOREAN = "ko";
    public static final String ENGLISH = "en";

    private static final String PREFS_NAME = "fitcommit_language";
    private static final String KEY_LANGUAGE = "language_tag";

    private LanguageManager() {
    }

    public static void applySavedLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        applyLegacyLocale(context, savedLanguage(context));
    }

    public static String currentLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            if (localeManager == null || localeManager.getApplicationLocales().isEmpty()) {
                return SYSTEM;
            }
            return normalize(localeManager.getApplicationLocales().get(0).getLanguage());
        }
        return savedLanguage(context);
    }

    public static void setLanguage(Activity activity, String languageTag) {
        String normalized = normalize(languageTag);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = activity.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                localeManager.setApplicationLocales(
                        normalized.isEmpty() ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(normalized)
                );
            }
            return;
        }

        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, normalized)
                .apply();
        applyLegacyLocale(activity.getApplicationContext(), normalized);
        activity.recreate();
    }

    private static String savedLanguage(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return normalize(preferences.getString(KEY_LANGUAGE, SYSTEM));
    }

    private static String normalize(String languageTag) {
        if (KOREAN.equals(languageTag)) return KOREAN;
        if (ENGLISH.equals(languageTag)) return ENGLISH;
        return SYSTEM;
    }

    @SuppressLint("AppBundleLocaleChanges")
    @SuppressWarnings("deprecation")
    private static void applyLegacyLocale(Context context, String languageTag) {
        Locale locale = languageTag.isEmpty()
                ? android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0)
                : Locale.forLanguageTag(languageTag);
        Locale.setDefault(locale);

        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
    }
}
