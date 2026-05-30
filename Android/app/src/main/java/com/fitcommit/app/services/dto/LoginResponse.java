package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    private String message;

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("token_type")
    private String tokenType;

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

    public UserDto getUser() {
        return user;
    }
}
