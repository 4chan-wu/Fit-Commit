package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class SignUpResponse {
    @SerializedName("access_token")
    private String accessToken;
    private UserDto user;

    public String getMessage() {
        return requiresEmailConfirmation() ? "이메일 인증이 필요합니다." : "회원가입되었습니다.";
    }

    public String getUserId() {
        return user == null ? null : user.getId();
    }

    public boolean requiresEmailConfirmation() {
        return accessToken == null || accessToken.trim().isEmpty();
    }
}
