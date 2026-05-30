package com.fitcommit.app.services;

import android.content.Context;

import com.fitcommit.app.services.dto.LoginRequest;
import com.fitcommit.app.services.dto.LoginResponse;
import com.fitcommit.app.services.dto.SignUpRequest;
import com.fitcommit.app.services.dto.SignUpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthService {
    public interface AuthCallback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    private final AuthApi authApi;
    private final TokenManager tokenManager;

    public AuthService(Context context) {
        authApi = ApiClient.getRetrofit().create(AuthApi.class);
        tokenManager = new TokenManager(context);
    }

    public void login(String email, String password, AuthCallback<LoginResponse> callback) {
        authApi.login(new LoginRequest(email, password)).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.getAccessToken() != null) {
                    tokenManager.saveToken(body.getAccessToken(), body.getTokenType());
                    callback.onSuccess(body);
                    return;
                }
                callback.onError(parseError(response));
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable throwable) {
                callback.onError(networkErrorMessage(throwable));
            }
        });
    }

    public void signUp(
            String name,
            String email,
            String phone,
            String password,
            String passwordConfirm,
            AuthCallback<SignUpResponse> callback
    ) {
        String nickname = nicknameFromEmail(email);
        SignUpRequest request = new SignUpRequest(
                email,
                name,
                nickname,
                phone,
                password,
                passwordConfirm
        );

        authApi.signUp(request).enqueue(new Callback<SignUpResponse>() {
            @Override
            public void onResponse(Call<SignUpResponse> call, Response<SignUpResponse> response) {
                SignUpResponse body = response.body();
                if (response.isSuccessful() && body != null) {
                    callback.onSuccess(body);
                    return;
                }
                callback.onError(parseError(response));
            }

            @Override
            public void onFailure(Call<SignUpResponse> call, Throwable throwable) {
                callback.onError(networkErrorMessage(throwable));
            }
        });
    }

    public String getAccessToken() {
        return tokenManager.getAccessToken();
    }

    private String nicknameFromEmail(String email) {
        if (email == null) {
            return "user";
        }
        int atIndex = email.indexOf("@");
        String nickname = atIndex > 0 ? email.substring(0, atIndex) : email;
        return nickname.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String parseError(Response<?> response) {
        if (response.errorBody() == null) {
            return "Request failed. HTTP " + response.code();
        }

        try {
            String errorJson = response.errorBody().string();
            JSONObject jsonObject = new JSONObject(errorJson);
            Object detail = jsonObject.opt("detail");

            if (detail instanceof String) {
                return (String) detail;
            }
            if (detail instanceof JSONArray) {
                JSONArray details = (JSONArray) detail;
                if (details.length() > 0) {
                    JSONObject firstError = details.optJSONObject(0);
                    if (firstError != null) {
                        return firstError.optString("msg", firstError.toString());
                    }
                }
            }
            return errorJson;
        } catch (Exception ignored) {
            return "Request failed. HTTP " + response.code();
        }
    }

    private String networkErrorMessage(Throwable throwable) {
        if (throwable instanceof IOException) {
            return "Cannot connect to FastAPI server. Check http://10.0.2.2:8001/health.";
        }
        return throwable.getMessage() == null ? "Unexpected error occurred." : throwable.getMessage();
    }
}
