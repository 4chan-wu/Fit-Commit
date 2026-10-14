package com.fitcommit.app.services.dto;

import com.google.gson.annotations.SerializedName;

public class UserDto {
    private String id;
    private String email;
    private String nickname;
    private String name;

    @SerializedName("phone_number")
    private String phoneNumber;

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}
