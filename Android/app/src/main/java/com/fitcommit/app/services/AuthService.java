package com.fitcommit.app.services;

import android.content.Context;

import com.fitcommit.app.services.dto.LoginRequest;
import com.fitcommit.app.services.dto.LoginResponse;
import com.fitcommit.app.services.dto.RefreshTokenRequest;
import com.fitcommit.app.services.dto.SignUpRequest;
import com.fitcommit.app.services.dto.SignUpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.ResponseBody;
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
        if (!ApiClient.isConfigured()) {
            callback.onError("Supabase URL 또는 공개 키가 설정되지 않았습니다.");
            return;
        }
        authApi.login("password", new LoginRequest(email, password)).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.getAccessToken() != null) {
                    tokenManager.saveSupabaseSession(
                            body.getAccessToken(),
                            body.getRefreshToken(),
                            body.getExpiresAt(),
                            body.getUser() == null ? null : body.getUser().getId()
                    );
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
                phone == null ? "" : phone.replaceAll("[^0-9]", ""),
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

    public void refreshSession(AuthCallback<LoginResponse> callback) {
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            callback.onError("로그인 세션이 만료되었습니다. 다시 로그인해주세요.");
            return;
        }

        authApi.refresh("refresh_token", new RefreshTokenRequest(refreshToken)).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.getAccessToken() != null) {
                    tokenManager.saveSupabaseSession(
                            body.getAccessToken(),
                            body.getRefreshToken(),
                            body.getExpiresAt(),
                            body.getUser() == null ? tokenManager.getUserId() : body.getUser().getId()
                    );
                    callback.onSuccess(body);
                    return;
                }
                tokenManager.clear();
                callback.onError(parseError(response));
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable throwable) {
                callback.onError(networkErrorMessage(throwable));
            }
        });
    }

    public String getAccessToken() {
        return tokenManager.getAccessToken();
    }

    public void deleteAccount(AuthCallback<Void> callback) {
        String accessToken = tokenManager.getAccessToken();
        if (accessToken == null || accessToken.trim().isEmpty()) {
            callback.onError("로그인이 필요합니다.");
            return;
        }
        authApi.deleteAccount(
                ApiClient.FUNCTIONS_URL + "/delete-account",
                "Bearer " + accessToken,
                ApiClient.SUPABASE_ANON_KEY
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    tokenManager.clear();
                    callback.onSuccess(null);
                    return;
                }
                callback.onError(parseError(response));
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                callback.onError(networkErrorMessage(throwable));
            }
        });
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
            String message = jsonObject.optString("msg", jsonObject.optString("message", jsonObject.optString("error_description", errorJson)));
            return message;
        } catch (Exception ignored) {
            return "Request failed. HTTP " + response.code();
        }
    }

    private String networkErrorMessage(Throwable throwable) {
        if (throwable instanceof IOException) {
            return "Supabase에 연결할 수 없습니다. 인터넷 연결과 프로젝트 설정을 확인해주세요.";
        }
        return throwable.getMessage() == null ? "Unexpected error occurred." : throwable.getMessage();
    }
}
