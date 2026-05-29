package com.fitcommit.app.services;

/**
 * 인증 API 호출을 한 곳으로 모으기 위한 서비스입니다.
 * 현재는 iOS 앱과 같은 더미 인증 흐름을 제공하고, 추후 Backend /users API와 연결합니다.
 */
public class AuthService {
    public boolean login(String email, String password) {
        // TODO: Backend POST /users/login 연동
        return email != null && email.contains("@") && password != null && password.length() >= 8;
    }

    public boolean signUp(String name, String email, String phone, String password) {
        // TODO: Backend POST /users/signup 연동
        return name != null && !name.trim().isEmpty() && login(email, password) && phone != null && phone.length() >= 11;
    }
}
