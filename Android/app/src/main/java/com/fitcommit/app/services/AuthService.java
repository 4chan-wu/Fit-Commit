package com.fitcommit.app.services;

import android.content.Context;

import com.fitcommit.app.R;
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

    private final Context context;
    private final AuthApi authApi;
    private final TokenManager tokenManager;

    public AuthService(Context context) {
        this.context = context.getApplicationContext();
        authApi = ApiClient.getRetrofit().create(AuthApi.class);
        tokenManager = new TokenManager(context);
    }

    public void login(String email, String password, AuthCallback<LoginResponse> callback) {
        if (!ApiClient.isConfigured()) {
            callback.onError(context.getString(R.string.auth_error_not_configured));
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

        authApi.signUp(ApiClient.SIGN_UP_REDIRECT_URL, request).enqueue(new Callback<SignUpResponse>() {
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
            callback.onError(context.getString(R.string.auth_error_session_expired));
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
                if (isSessionRejected(response.code())) {
                    tokenManager.clear();
                }
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
            callback.onError(context.getString(R.string.auth_error_login_required));
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
        String errorCode = "";
        String message = "";
        try {
            if (response.errorBody() != null) {
                JSONObject jsonObject = new JSONObject(response.errorBody().string());
                errorCode = firstNonEmpty(
                        jsonObject.optString("error_code", ""),
                        jsonObject.optString("error", "")
                );
                message = detailMessage(jsonObject.opt("detail"));
                if (message.isEmpty()) {
                    message = firstNonEmpty(
                            jsonObject.optString("msg", ""),
                            jsonObject.optString("message", ""),
                            jsonObject.optString("error_description", "")
                    );
                }
            }
        } catch (Exception ignored) {
            // 본문을 읽을 수 없으면 HTTP 상태 코드만으로 안내 문구를 고릅니다.
        }
        return AuthErrorMessages.from(context, response.code(), errorCode, message);
    }

    private String detailMessage(Object detail) {
        if (detail instanceof String) {
            return (String) detail;
        }
        if (detail instanceof JSONArray) {
            JSONArray details = (JSONArray) detail;
            if (details.length() > 0) {
                JSONObject firstError = details.optJSONObject(0);
                if (firstError != null) {
                    return firstError.optString("msg", "");
                }
            }
        }
        return "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private String networkErrorMessage(Throwable throwable) {
        if (throwable instanceof IOException) {
            return context.getString(R.string.auth_error_network);
        }
        return context.getString(R.string.auth_error_generic);
    }

    private boolean isSessionRejected(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403;
    }
}
