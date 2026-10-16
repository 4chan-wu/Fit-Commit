package com.fitcommit.app.services;

import com.fitcommit.app.services.dto.LoginRequest;
import com.fitcommit.app.services.dto.LoginResponse;
import com.fitcommit.app.services.dto.RefreshTokenRequest;
import com.fitcommit.app.services.dto.SignUpRequest;
import com.fitcommit.app.services.dto.SignUpResponse;

import retrofit2.Call;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface AuthApi {
    @POST("signup")
    Call<SignUpResponse> signUp(
            @Query("redirect_to") String redirectTo,
            @Body SignUpRequest request
    );

    @POST("token")
    Call<LoginResponse> login(@Query("grant_type") String grantType, @Body LoginRequest request);

    @POST("token")
    Call<LoginResponse> refresh(@Query("grant_type") String grantType, @Body RefreshTokenRequest request);

    @POST
    Call<ResponseBody> deleteAccount(
            @Url String url,
            @Header("Authorization") String authorization,
            @Header("apikey") String apiKey
    );
}
