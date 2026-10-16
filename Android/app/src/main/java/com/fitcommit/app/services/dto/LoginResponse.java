package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    private String message;

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("expires_at")
    private long expiresAt;

    private UserDto user;

    public String getMessage() {
        return message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public UserDto getUser() {
        return user;
    }
}
