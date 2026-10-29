package com.fitcommit.app.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

public class TokenManager {
    private static final String PREFS_NAME = "fitcommit_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_TOKEN_TYPE = "token_type";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_AUTH_SOURCE = "auth_source";
    private static final String KEY_USER_ID = "user_id";
    private static final String AUTH_SOURCE_SUPABASE = "supabase";

    private final SharedPreferences preferences;

    public TokenManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSupabaseSession(
            String accessToken,
            String refreshToken,
            long expiresAtEpochSeconds,
            String userId
    ) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_TOKEN_TYPE, "bearer")
                .putLong(KEY_EXPIRES_AT, expiresAtEpochSeconds)
                .putString(KEY_AUTH_SOURCE, AUTH_SOURCE_SUPABASE)
                .putString(KEY_USER_ID, userId)
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

    public String getUserId() {
        String stored = preferences.getString(KEY_USER_ID, null);
        if (stored != null && !stored.trim().isEmpty()) return stored;
        try {
            String token = getAccessToken();
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) return null;
            String json = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP));
            return new JSONObject(json).optString("sub", null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean hasSupabaseSession() {
        String accessToken = getAccessToken();
        return AUTH_SOURCE_SUPABASE.equals(preferences.getString(KEY_AUTH_SOURCE, ""))
                && accessToken != null
                && !accessToken.trim().isEmpty();
    }

    /**
     * Returns whether this device has enough session data to sign the user in again.
     * An expired access token is still restorable as long as a refresh token exists.
     */
    public boolean hasRestorableSession() {
        String refreshToken = getRefreshToken();
        return AUTH_SOURCE_SUPABASE.equals(preferences.getString(KEY_AUTH_SOURCE, ""))
                && refreshToken != null
                && !refreshToken.trim().isEmpty();
    }

    /** Returns true when the current access token remains valid beyond the safety window. */
    public boolean isAccessTokenUsable(long minimumValiditySeconds) {
        if (!hasSupabaseSession()) return false;
        long nowEpochSeconds = System.currentTimeMillis() / 1000L;
        long safetyWindow = Math.max(0L, minimumValiditySeconds);
        return getExpiresAtEpochSeconds() > nowEpochSeconds + safetyWindow;
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
