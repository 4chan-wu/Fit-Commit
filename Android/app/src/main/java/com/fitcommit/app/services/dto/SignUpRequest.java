package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class SignUpRequest {
    private final String email;
    private final String password;
    private final UserMetadata data;

    public SignUpRequest(
            String email,
            String name,
            String nickname,
            String phoneNumber,
            String password,
            String passwordConfirm
    ) {
        this.email = email;
        this.password = password;
        this.data = new UserMetadata(name, nickname, phoneNumber);
    }

    private static class UserMetadata {
        private final String name;
        private final String nickname;

        @SerializedName("phone_number")
        private final String phoneNumber;

        UserMetadata(String name, String nickname, String phoneNumber) {
            this.name = name;
            this.nickname = nickname;
            this.phoneNumber = phoneNumber;
        }
    }
}
