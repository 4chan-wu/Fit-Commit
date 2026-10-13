import SwiftUI
import Combine

struct LoginView: View {
    @EnvironmentObject private var authenticationManager: AuthenticationManager

    @State private var isSignUpMode = false
    @State private var email = ""
    @State private var password = ""
    @State private var name = ""
    @State private var phoneNumber = ""
    @State private var passwordConfirm = ""
    @State private var errorMessage = ""
    @State private var failedLoginCount = 0
    @State private var lockoutEndDate: Date?
    @State private var now = Date()
    @State private var pendingSocialProvider: String?

    private let lockoutDuration: TimeInterval = 60

    private var isLocked: Bool {
        guard let lockoutEndDate else { return false }
        return now < lockoutEndDate
    }

    private var lockoutRemainingSeconds: Int {
        guard let lockoutEndDate else { return 0 }
        return max(Int(ceil(lockoutEndDate.timeIntervalSince(now))), 0)
    }

    private var isEmailValid: Bool {
        email.contains("@")
    }

    private var isNameValid: Bool {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return false }
        return name.range(of: #"^[\p{L}\p{M} ]+$"#, options: .regularExpression) != nil
    }

    private var phoneDigits: String {
        phoneNumber.filter { $0.isNumber }
    }

    private var isPhoneNumberValid: Bool {
        phoneDigits.range(of: "^010[0-9]{8}$", options: .regularExpression) != nil
    }

    private var passwordRuleCount: Int {
        [
            password.range(of: "[A-Z]", options: .regularExpression) != nil,
            password.range(of: "[a-z]", options: .regularExpression) != nil,
            password.range(of: "[0-9]", options: .regularExpression) != nil,
            password.range(of: "[^A-Za-z0-9]", options: .regularExpression) != nil
        ].filter { $0 }.count
    }

    private var isPasswordValid: Bool {
        password.count >= 8 && passwordRuleCount >= 3
    }

    private var isPasswordConfirmationValid: Bool {
        !passwordConfirm.isEmpty && password == passwordConfirm
    }

    private var canSubmit: Bool {
        guard !isLocked, isEmailValid, isPasswordValid else { return false }

        if isSignUpMode {
            return isNameValid && isPhoneNumberValid && isPasswordConfirmationValid
        }

        return true
    }

    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()

            BackgroundBlurView()
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 26) {
                    header

                    VStack(spacing: 14) {
                        if isSignUpMode {
                            AuthTextField(
                                title: "이름",
                                text: $name,
                                systemImage: "person",
                                keyboardType: .default,
                                validationText: name.isEmpty || isNameValid ? nil : "문자와 공백만 입력할 수 있습니다."
                            )
                        }

                        AuthTextField(
                            title: "이메일",
                            text: $email,
                            systemImage: "envelope",
                            keyboardType: .emailAddress,
                            textInputAutocapitalization: .never,
                            validationText: email.isEmpty || isEmailValid ? nil : "@가 포함된 이메일을 입력해주세요."
                        )

                        if isSignUpMode {
                            AuthTextField(
                                title: "휴대전화",
                                text: $phoneNumber,
                                systemImage: "phone",
                                keyboardType: .numberPad,
                                validationText: phoneNumber.isEmpty || isPhoneNumberValid ? nil : "010으로 시작하는 11자리 번호를 입력해주세요."
                            )
                            .onChange(of: phoneNumber) { _, value in
                                let formatted = formatPhoneNumber(value)
                                if formatted != value {
                                    phoneNumber = formatted
                                }
                            }
                        }

                        AuthSecureField(
                            fieldID: "password",
                            title: "비밀번호",
                            text: $password,
                            systemImage: "lock",
                            validationText: password.isEmpty || isPasswordValid ? nil : "8자 이상, 대/소문자/숫자/특수문자 중 3가지 이상"
                        )

                        if isSignUpMode {
                            AuthSecureField(
                                fieldID: "passwordConfirm",
                                title: "비밀번호 확인",
                                text: $passwordConfirm,
                                systemImage: "checkmark.shield",
                                validationText: passwordConfirm.isEmpty || isPasswordConfirmationValid ? nil : "비밀번호가 일치하지 않습니다."
                            )
                        }
                    }

                    feedbackArea

                    Button(action: submitCredentials) {
                        Text(isSignUpMode ? "회원가입" : "로그인")
                            .font(.system(.headline, design: .rounded).weight(.bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 17)
                            .background(canSubmit ? Color.accentColor : Color.secondary.opacity(0.35))
                            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 20, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.22), lineWidth: 1)
                            }
                    }
                    .buttonStyle(AuthPressButtonStyle())
                    .disabled(!canSubmit)

                    modeSwitchLink

                    socialSection
                }
                .padding(.horizontal, 24)
                .padding(.top, 42)
                .padding(.bottom, 42)
            }
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { date in
            now = date
            if let lockoutEndDate, date >= lockoutEndDate {
                self.lockoutEndDate = nil
                failedLoginCount = 0
                if errorMessage.contains("잠깁니다") {
                    errorMessage = ""
                }
            }
        }
        .alert(
            "\(pendingSocialProvider ?? "소셜") 로그인 준비 중",
            isPresented: Binding(
                get: { pendingSocialProvider != nil },
                set: { if !$0 { pendingSocialProvider = nil } }
            )
        ) {
            Button("확인", role: .cancel) {
                pendingSocialProvider = nil
            }
        } message: {
            Text("개발자 앱과 Supabase OAuth 설정이 완료되면 사용할 수 있습니다. 계정 인증 없이 앱에 진입하는 테스트 동작은 제거했습니다.")
        }
    }

    private var header: some View {
        VStack(spacing: 16) {
            FitcommitIdentityIcon()

            VStack(spacing: 8) {
                Text("Fitcommit")
                    .font(.system(size: 36, weight: .heavy, design: .rounded))
                    .foregroundStyle(.primary)

                Text(isSignUpMode ? "나만의 핏 데이터를 안전하게 시작하세요." : "오늘의 핏을 기록하고, 내일의 스타일을 더 선명하게.")
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }
        }
    }

    @ViewBuilder
    private var feedbackArea: some View {
        if isLocked {
            AuthFeedbackMessage(
                text: "1분간 계정이 잠깁니다. \(lockoutRemainingSeconds)초 후 다시 시도해주세요.",
                systemImage: "exclamationmark.lock",
                color: .red
            )
        } else if !errorMessage.isEmpty {
            AuthFeedbackMessage(
                text: errorMessage,
                systemImage: "exclamationmark.circle",
                color: .red
            )
        } else if isSignUpMode && isPasswordConfirmationValid {
            AuthFeedbackMessage(
                text: "입력한 비밀번호가 일치합니다.",
                systemImage: "checkmark.circle",
                color: .green
            )
        }
    }

    private var modeSwitchLink: some View {
        Button {
            withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                isSignUpMode.toggle()
                errorMessage = ""
            }
        } label: {
            Text(isSignUpMode ? "이미 계정이 있나요? 로그인하기" : "아직 계정이 없나요? 회원가입하기")
                .font(.system(.subheadline, design: .rounded).weight(.bold))
                .foregroundStyle(.primary)
        }
        .buttonStyle(.plain)
    }

    private var socialSection: some View {
        VStack(spacing: 14) {
            HStack(spacing: 10) {
                Rectangle()
                    .fill(Color.primary.opacity(0.12))
                    .frame(height: 1)

                Text("또는 소셜 계정으로 시작하기")
                    .font(.system(.caption, design: .rounded).weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Rectangle()
                    .fill(Color.primary.opacity(0.12))
                    .frame(height: 1)
            }

            VStack(spacing: 10) {
                SocialAuthButton(title: "Apple로 시작하기", systemImage: "apple.logo") {
                    beginSocialLogin("Apple")
                }

                SocialAuthButton(title: "Google로 시작하기", systemImage: "g.circle") {
                    beginSocialLogin("Google")
                }

                SocialAuthButton(
                    title: "카카오톡으로 시작하기",
                    systemImage: "bubble.left",
                    backgroundColor: Color(hex: 0xFEE500),
                    foregroundColor: .black
                ) {
                    beginSocialLogin("카카오톡")
                }

                SocialAuthButton(
                    title: "네이버로 시작하기",
                    systemImage: "n.circle",
                    backgroundColor: Color(hex: 0x03C75A),
                    foregroundColor: .white
                ) {
                    beginSocialLogin("네이버")
                }
            }
        }
    }

    private func submitCredentials() {
        errorMessage = ""

        if isSignUpMode {
            authenticationManager.login()
            return
        }

        // FastAPI 연결 시 /auth/login 요청으로 교체
        let isSimulatedLoginSuccess = email.lowercased() == "test@fitcommit.com" && password == "Fitcommit1!"

        if isSimulatedLoginSuccess {
            failedLoginCount = 0
            authenticationManager.login()
        } else {
            failedLoginCount += 1
            if failedLoginCount >= 10 {
                lockoutEndDate = Date().addingTimeInterval(lockoutDuration)
                errorMessage = "1분간 계정이 잠깁니다."
            } else {
                errorMessage = "이메일 또는 비밀번호가 올바르지 않습니다. (\(failedLoginCount)/10)"
            }
        }
    }

    private func beginSocialLogin(_ provider: String) {
        pendingSocialProvider = provider
    }

    private func formatPhoneNumber(_ value: String) -> String {
        let digits = String(value.filter { $0.isNumber }.prefix(11))

        if digits.count <= 3 {
            return digits
        }

        let firstBreak = digits.index(digits.startIndex, offsetBy: 3)
        let firstPart = String(digits[..<firstBreak])
        let remaining = String(digits[firstBreak...])

        if digits.count <= 7 {
            return "\(firstPart)-\(remaining)"
        }

        let secondBreak = remaining.index(remaining.startIndex, offsetBy: 4)
        let secondPart = String(remaining[..<secondBreak])
        let thirdPart = String(remaining[secondBreak...])
        return "\(firstPart)-\(secondPart)-\(thirdPart)"
    }
}

private struct AuthTextField: View {
    let title: String
    @Binding var text: String
    let systemImage: String
    var keyboardType: UIKeyboardType = .default
    var textInputAutocapitalization: TextInputAutocapitalization? = nil
    var validationText: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)
                    .frame(width: 24)

                TextField(title, text: $text)
                    .font(.system(.body, design: .rounded))
                    .keyboardType(keyboardType)
                    .textInputAutocapitalization(textInputAutocapitalization)
                    .autocorrectionDisabled()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .fitcommitCardStyle()

            if let validationText {
                Text(validationText)
                    .font(.system(.caption, design: .rounded).weight(.semibold))
                    .foregroundStyle(.red)
                    .padding(.horizontal, 6)
            }
        }
    }
}

private struct AuthSecureField: View {
    let fieldID: String
    let title: String
    @Binding var text: String
    let systemImage: String
    var validationText: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)

                SecureField(title, text: $text)
                    .id(fieldID)
                    .font(.system(.body, design: .rounded))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .fitcommitCardStyle()

            if let validationText {
                Text(validationText)
                    .font(.system(.caption, design: .rounded).weight(.semibold))
                    .foregroundStyle(.red)
                    .padding(.horizontal, 6)
            }
        }
    }
}

private struct AuthFeedbackMessage: View {
    let text: String
    let systemImage: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(.caption, design: .rounded).weight(.medium))

            Text(text)
                .font(.system(.caption, design: .rounded).weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(color)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }
}

private struct FitcommitIdentityIcon: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.55),
                            Color.blue.opacity(0.22),
                            Color.purple.opacity(0.20)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .background(.ultraThinMaterial, in: Circle())
                .overlay {
                    Circle()
                        .strokeBorder(Color.white.opacity(0.28), lineWidth: 1)
                }
                .frame(width: 108, height: 108)
                .blur(radius: 0.2)

            Circle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 82, height: 82)

            Image(systemName: "person.fill")
                .font(.system(size: 52, weight: .regular, design: .rounded))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.primary.opacity(0.9), .secondary.opacity(0.5))
                .offset(y: 3)

            Image(systemName: "sparkles")
                .font(.system(size: 22, weight: .medium, design: .rounded))
                .foregroundStyle(.primary.opacity(0.72))
                .offset(x: 21, y: -23)
        }
        .accessibilityHidden(true)
    }
}

private struct SocialAuthButton: View {
    let title: String
    let systemImage: String
    var backgroundColor: Color?
    var foregroundColor: Color = .primary
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 20, weight: .medium, design: .rounded))
                    .frame(width: 26)

                Text(title)
                    .font(.system(.subheadline, design: .rounded).weight(.bold))

                Spacer(minLength: 0)

                Image(systemName: "arrow.right")
                    .font(.system(.caption, design: .rounded).weight(.medium))
                    .foregroundStyle(foregroundColor.opacity(0.65))
            }
            .foregroundStyle(foregroundColor)
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(backgroundColor ?? Color.clear, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .fitcommitCardStyle(borderOpacity: backgroundColor == nil ? 0.08 : 0.16)
        }
        .buttonStyle(AuthPressButtonStyle())
    }
}

private struct AuthPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.72), value: configuration.isPressed)
    }
}

private extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

#Preview {
    LoginView()
        .environmentObject(AuthenticationManager())
}
