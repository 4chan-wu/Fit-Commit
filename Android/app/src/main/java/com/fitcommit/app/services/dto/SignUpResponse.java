package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class SignUpResponse {
    private String message;

    @SerializedName("user_id")
    private long userId;

    public String getMessage() {
        return message;
    }

    public long getUserId() {
        return userId;
    }
}
