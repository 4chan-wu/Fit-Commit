package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class RefreshTokenRequest {
    @SerializedName("refresh_token")
    private final String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
