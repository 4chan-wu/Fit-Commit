package com.fitcommit.app.services;

import android.content.Context;

import com.fitcommit.app.R;

import java.util.Locale;

/**
 * Supabase/백엔드가 돌려주는 영어 오류 메시지를 사용자가 이해할 수 있는 현재 언어의 문구로 바꿉니다.
 * 알려진 오류 코드·메시지는 직접 매핑하고, 모르는 오류는 영어 원문 대신 상태 코드별 일반 문구를 보여줍니다.
 */
public final class AuthErrorMessages {
    private AuthErrorMessages() {
    }

    public static String from(Context context, int httpStatus, String errorCode, String rawMessage) {
        String code = normalize(errorCode);
        String message = normalize(rawMessage);

        if (has(code, message, "invalid_credentials", "invalid login credentials")) {
            return context.getString(R.string.auth_error_invalid_credentials);
        }
        if (has(code, message, "email_not_confirmed", "email not confirmed")) {
            return context.getString(R.string.auth_error_email_not_confirmed);
        }
        if (has(code, message, "user_already_exists", "email_exists", "user already registered",
                "already been registered")) {
            return context.getString(R.string.auth_error_user_exists);
        }
        if (has(code, message, "weak_password", "password should be", "weak and easy to guess",
                "password is too short")) {
            return context.getString(R.string.auth_error_weak_password);
        }
        if (has(code, message, "over_email_send_rate_limit", "over_request_rate_limit",
                "over_sms_send_rate_limit", "rate limit", "for security purposes",
                "too many requests") || httpStatus == 429) {
            return context.getString(R.string.auth_error_rate_limit);
        }
        if (has(code, message, "email_address_invalid", "unable to validate email",
                "invalid format")) {
            return context.getString(R.string.validation_email);
        }
        if (has(code, message, "signup_disabled", "email_provider_disabled", "signups not allowed",
                "signups are disabled", "signup is disabled")) {
            return context.getString(R.string.auth_error_signup_disabled);
        }
        if (has(code, message, "user_banned", "user is banned")) {
            return context.getString(R.string.auth_error_user_banned);
        }
        if (has(code, message, "refresh_token_not_found", "refresh_token_already_used",
                "session_not_found", "session_expired", "bad_jwt", "invalid refresh token",
                "refresh token not found", "jwt expired", "invalid jwt", "missing sub claim",
                "token has expired", "token is expired")) {
            return context.getString(R.string.auth_error_session_expired);
        }
        if (has(code, message, "otp_expired", "email link is invalid")) {
            return context.getString(R.string.auth_error_link_expired);
        }

        // 서버가 이미 한국어로 알려준 메시지는 그대로 보여줍니다.
        if (containsHangul(rawMessage)) {
            return rawMessage.trim();
        }

        if (httpStatus == 401 || httpStatus == 403) {
            return context.getString(R.string.auth_error_session_expired);
        }
        if (httpStatus >= 500) {
            return context.getString(R.string.auth_error_server);
        }
        return context.getString(R.string.auth_error_generic);
    }

    private static boolean has(String code, String message, String... needles) {
        for (String needle : needles) {
            if (code.equals(needle)) return true;
            if (!message.isEmpty() && message.contains(needle)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsHangul(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) return true;
        }
        return false;
    }
}
