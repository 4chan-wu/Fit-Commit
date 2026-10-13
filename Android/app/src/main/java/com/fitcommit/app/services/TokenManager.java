package com.fitcommit.app.services;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREFS_NAME = "fitcommit_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_TOKEN_TYPE = "token_type";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_AUTH_SOURCE = "auth_source";
    private static final String AUTH_SOURCE_BACKEND = "backend";
    private static final String AUTH_SOURCE_SUPABASE = "supabase";

    private final SharedPreferences preferences;

    public TokenManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String accessToken, String tokenType) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_TOKEN_TYPE, tokenType)
                .putString(KEY_AUTH_SOURCE, AUTH_SOURCE_BACKEND)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .apply();
    }

    public void saveSupabaseSession(
            String accessToken,
            String refreshToken,
            long expiresAtEpochSeconds
    ) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_TOKEN_TYPE, "bearer")
                .putLong(KEY_EXPIRES_AT, expiresAtEpochSeconds)
                .putString(KEY_AUTH_SOURCE, AUTH_SOURCE_SUPABASE)
                .apply();
    }

    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getTokenType() {
        return preferences.getString(KEY_TOKEN_TYPE, "bearer");
    }

    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public long getExpiresAtEpochSeconds() {
        return preferences.getLong(KEY_EXPIRES_AT, 0L);
    }

    public boolean hasSupabaseSession() {
        String accessToken = getAccessToken();
        return AUTH_SOURCE_SUPABASE.equals(preferences.getString(KEY_AUTH_SOURCE, ""))
                && accessToken != null
                && !accessToken.trim().isEmpty();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
