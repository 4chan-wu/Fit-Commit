package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class SignUpRequest {
    private final String email;
    private final String name;
    private final String nickname;

    @SerializedName("phone_number")
    private final String phoneNumber;

    private final String password;

    @SerializedName("password_confirm")
    private final String passwordConfirm;

    public SignUpRequest(
            String email,
            String name,
            String nickname,
            String phoneNumber,
            String password,
            String passwordConfirm
    ) {
        this.email = email;
        this.name = name;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.passwordConfirm = passwordConfirm;
    }
}
