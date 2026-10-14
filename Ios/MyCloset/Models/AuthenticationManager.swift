import Combine
import Foundation
import LocalAuthentication
import Security
import SwiftUI

final class AuthenticationManager: ObservableObject {
    static let shared = AuthenticationManager()

    private static let accessTokenKey = "supabase_access_token"
    private static let refreshTokenKey = "supabase_refresh_token"
    private static let userIDKey = "supabase_user_id"
    private static let expiresAtKey = "supabase_expires_at"
    var isUnlocked = false

    @Published private(set) var isLoggedIn: Bool

    var accessToken: String? {
        KeychainStore.read(Self.accessTokenKey)
    }

    var currentUserID: String? {
        KeychainStore.read(Self.userIDKey)
    }

    init() {
        self.isLoggedIn = KeychainStore.read(Self.accessTokenKey) != nil
    }

    func signIn(email: String, password: String) async throws {
        let response: LoginResponse = try await request(
            path: "users/login",
            payload: LoginRequest(email: email, password: password)
        )
        guard !response.accessToken.isEmpty, !response.user.id.isEmpty else {
            throw AuthenticationError.invalidResponse
        }

        KeychainStore.write(response.accessToken, key: Self.accessTokenKey)
        KeychainStore.write(response.refreshToken, key: Self.refreshTokenKey)
        KeychainStore.write(response.user.id, key: Self.userIDKey)
        KeychainStore.write(response.expiresAt.map(String.init), key: Self.expiresAtKey)

        await MainActor.run {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.86)) {
                self.isLoggedIn = true
            }
        }
    }

    func validAccessToken() async throws -> String? {
        guard let token = accessToken else { return nil }
        let expiresAt = KeychainStore.read(Self.expiresAtKey).flatMap(Int.init) ?? 0
        if expiresAt == 0 || expiresAt > Int(Date().timeIntervalSince1970) + 60 {
            return token
        }

        guard let refreshToken = KeychainStore.read(Self.refreshTokenKey) else {
            logout()
            return nil
        }
        let response: LoginResponse = try await request(
            path: "users/refresh",
            payload: RefreshRequest(refreshToken: refreshToken)
        )
        KeychainStore.write(response.accessToken, key: Self.accessTokenKey)
        KeychainStore.write(response.refreshToken, key: Self.refreshTokenKey)
        KeychainStore.write(response.user.id, key: Self.userIDKey)
        KeychainStore.write(response.expiresAt.map(String.init), key: Self.expiresAtKey)
        return response.accessToken
    }

    @discardableResult
    func signUp(
        name: String,
        email: String,
        phoneNumber: String,
        password: String
    ) async throws -> Bool {
        let nickname = email.split(separator: "@").first.map(String.init) ?? "user"
        let response: SignUpResponse = try await request(
            path: "users/signup",
            payload: SignUpRequest(
                email: email,
                name: name,
                nickname: nickname,
                phoneNumber: phoneNumber,
                password: password,
                passwordConfirm: password
            )
        )
        return response.requiresEmailConfirmation
    }

    func authenticate() {
        let context = LAContext()
        var error: NSError?

        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            let reason = "프로필 데이터를 안전하게 보호하기 위해 인증이 필요합니다."

            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authenticationError in
                Task { @MainActor in
                    if success {
                        withAnimation {
                            self.isUnlocked = true
                        }
                    } else {
                        print(authenticationError?.localizedDescription ?? "Failed to authenticate")
                    }
                }
            }
        } else {
            print("Biometric authentication is not available: \(error?.localizedDescription ?? "Unknown error")")
        }
    }

    func lock() {
        isUnlocked = false
    }

    func logout() {
        KeychainStore.delete(Self.accessTokenKey)
        KeychainStore.delete(Self.refreshTokenKey)
        KeychainStore.delete(Self.userIDKey)
        KeychainStore.delete(Self.expiresAtKey)

        DispatchQueue.main.async {
            self.lock()
            withAnimation(.spring(response: 0.45, dampingFraction: 0.86)) {
                self.isLoggedIn = false
            }
        }
    }

    func refreshLoginStateFromStorage() {
        DispatchQueue.main.async {
            self.isLoggedIn = KeychainStore.read(Self.accessTokenKey) != nil
        }
    }

    private func request<Response: Decodable, Payload: Encodable>(
        path: String,
        payload: Payload
    ) async throws -> Response {
        guard let baseURL = BackendClothesService.configuredBaseURL else {
            throw AuthenticationError.invalidBackendURL
        }

        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, urlResponse) = try await URLSession.shared.data(for: request)
        guard let httpResponse = urlResponse as? HTTPURLResponse else {
            throw AuthenticationError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let apiError = try? JSONDecoder().decode(APIError.self, from: data)
            throw AuthenticationError.server(apiError?.detail ?? "로그인 서버 요청에 실패했습니다.")
        }
        return try JSONDecoder().decode(Response.self, from: data)
    }
}

private struct LoginRequest: Encodable {
    let email: String
    let password: String
}

private struct LoginResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: Int?
    let user: AuthUser

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresAt = "expires_at"
        case user
    }
}

private struct AuthUser: Decodable {
    let id: String
}

private struct RefreshRequest: Encodable {
    let refreshToken: String

    enum CodingKeys: String, CodingKey {
        case refreshToken = "refresh_token"
    }
}

private struct SignUpRequest: Encodable {
    let email: String
    let name: String
    let nickname: String
    let phoneNumber: String
    let password: String
    let passwordConfirm: String

    enum CodingKeys: String, CodingKey {
        case email, name, nickname, password
        case phoneNumber = "phone_number"
        case passwordConfirm = "password_confirm"
    }
}

private struct SignUpResponse: Decodable {
    let requiresEmailConfirmation: Bool

    enum CodingKeys: String, CodingKey {
        case requiresEmailConfirmation = "requires_email_confirmation"
    }
}

private struct APIError: Decodable {
    let detail: String
}

enum AuthenticationError: LocalizedError {
    case invalidBackendURL
    case invalidResponse
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidBackendURL:
            return "백엔드 주소가 올바르지 않습니다."
        case .invalidResponse:
            return "로그인 서버 응답을 확인할 수 없습니다."
        case .server(let message):
            return message
        }
    }
}

private enum KeychainStore {
    private static let service = "com.fitcommit.auth"

    static func write(_ value: String?, key: String) {
        guard let value, let data = value.data(using: .utf8) else { return }
        delete(key)
        SecItemAdd([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key,
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ] as CFDictionary, nil)
    }

    static func read(_ key: String) -> String? {
        var result: AnyObject?
        let status = SecItemCopyMatching([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ] as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ key: String) {
        SecItemDelete([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key
        ] as CFDictionary)
    }
}
