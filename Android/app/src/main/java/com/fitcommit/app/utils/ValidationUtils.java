package com.fitcommit.app.utils;

import java.util.regex.Pattern;

/**
 * LoginView의 입력 검증 규칙을 Android에서 재사용하기 위한 유틸리티입니다.
 */
public final class ValidationUtils {
    private ValidationUtils() {
    }

    public static boolean isEmailValid(String email) {
        return email != null && email.contains("@");
    }

    public static boolean isPhoneValid(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        return Pattern.matches("^010[0-9]{8}$", digits);
    }

    public static boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        int count = 0;
        if (Pattern.compile("[A-Z]").matcher(password).find()) count++;
        if (Pattern.compile("[a-z]").matcher(password).find()) count++;
        if (Pattern.compile("[0-9]").matcher(password).find()) count++;
        if (Pattern.compile("[^A-Za-z0-9]").matcher(password).find()) count++;
        return count >= 3;
    }
}
