import Foundation
import LocalAuthentication
import Combine
import SwiftUI

final class AuthenticationManager: ObservableObject {
    static let shared = AuthenticationManager()

    private static let isLoggedInKey = "isLoggedIn"
    private let defaults: UserDefaults

    var isUnlocked = false

    @Published var isLoggedIn: Bool {
        didSet {
            defaults.set(isLoggedIn, forKey: Self.isLoggedInKey)
        }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.isLoggedIn = defaults.bool(forKey: Self.isLoggedInKey)
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

    func login() {
        DispatchQueue.main.async {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.86)) {
                self.isLoggedIn = true
            }
        }
    }

    func logout() {
        DispatchQueue.main.async {
            self.lock()
            withAnimation(.spring(response: 0.45, dampingFraction: 0.86)) {
                self.isLoggedIn = false
            }
        }
    }

    func refreshLoginStateFromStorage() {
        DispatchQueue.main.async {
            self.isLoggedIn = self.defaults.bool(forKey: Self.isLoggedInKey)
        }
    }
}
