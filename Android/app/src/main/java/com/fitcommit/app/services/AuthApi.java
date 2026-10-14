package com.fitcommit.app.services;

import com.fitcommit.app.services.dto.LoginRequest;
import com.fitcommit.app.services.dto.LoginResponse;
import com.fitcommit.app.services.dto.RefreshTokenRequest;
import com.fitcommit.app.services.dto.SignUpRequest;
import com.fitcommit.app.services.dto.SignUpResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("users/signup")
    Call<SignUpResponse> signUp(@Body SignUpRequest request);

    @POST("users/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("users/refresh")
    Call<LoginResponse> refresh(@Body RefreshTokenRequest request);
}
