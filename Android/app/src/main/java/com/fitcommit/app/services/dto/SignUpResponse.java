package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class SignUpResponse {
    private String message;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("requires_email_confirmation")
    private boolean requiresEmailConfirmation;

    public String getMessage() {
        return message;
    }

    public String getUserId() {
        return userId;
    }

    public boolean requiresEmailConfirmation() {
        return requiresEmailConfirmation;
    }
}
