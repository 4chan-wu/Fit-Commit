import SwiftUI
import SwiftData
import LocalAuthentication

struct MyProfileView: View {
    @Query private var userProfiles: [UserProfile]
    @AppStorage("appTheme") private var appTheme: AppTheme = .system
    @AppStorage("useFaceID") private var useFaceID: Bool = false
    @AppStorage("autoLogin") private var autoLogin: Bool = true
    @EnvironmentObject private var authManager: AuthenticationManager
    @Binding private var isTabBarHidden: Bool
    
    @State private var showHistory = false
    @State private var authFailed = false
    @State private var authErrorMessage = ""

    init(isTabBarHidden: Binding<Bool> = .constant(false)) {
        self._isTabBarHidden = isTabBarHidden
    }
    
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                profileHeader

                Spacer().frame(height: 28)

                profileSection(title: "내 활동") {
                    VStack(spacing: 7) {
                        Button(action: handleHistoryTapped) {
                            ProfileActionRow(
                                title: "스캔 히스토리",
                                systemImage: "clock.arrow.circlepath",
                                showsChevron: true
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            tabBarHiddenDestination {
                                ChatHistoryView()
                            }
                        } label: {
                            ProfileActionRow(
                                title: "채팅 기록",
                                systemImage: "message",
                                showsChevron: true
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                Spacer().frame(height: 24)

                profileSection(title: "앱 설정") {
                    VStack(spacing: 7) {
                        ProfileCardContainer {
                            HStack {
                                Label("화면 모드", systemImage: "circle.lefthalf.filled")

                                Spacer()

                                Picker("화면 모드", selection: $appTheme) {
                                    Text("시스템(자동)").tag(AppTheme.system)
                                    Text("라이트").tag(AppTheme.light)
                                    Text("다크").tag(AppTheme.dark)
                                }
                                .labelsHidden()
                            }
                        }

                        ProfileCardContainer {
                            Toggle(isOn: $useFaceID) {
                                Label("측정 기록 접근 시 Face ID 사용", systemImage: "faceid")
                            }
                            .tint(.accentColor)
                        }
                    }
                }

                Spacer().frame(height: 24)

                profileSection(title: "기타") {
                    VStack(spacing: 7) {
                        ProfileCardContainer {
                            Toggle(isOn: $autoLogin) {
                                Label("자동 로그인", systemImage: "person.badge.key")
                            }
                            .tint(.accentColor)
                        }

                        ProfileCardContainer {
                            HStack {
                                Label("앱 버전 정보", systemImage: "info.circle")
                                Spacer()
                                Text("2.3.1")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }

                Spacer().frame(height: 32)

                Button(action: handleLogoutTapped) {
                    HStack(spacing: 12) {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                        Text("로그아웃")
                            .fontWeight(.semibold)
                        Spacer()
                    }
                    .foregroundColor(.red)
                    .padding(.horizontal, 18)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .fitcommitCardStyle()
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 120)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
        }
        .navigationTitle("마이 페이지")
        .navigationDestination(isPresented: $showHistory) {
            tabBarHiddenDestination {
                ScanHistoryView()
            }
        }
        .onAppear {
            setTabBarHidden(false)
        }
        .alert("인증 실패", isPresented: $authFailed) {
            Button("취소", role: .cancel) { }
            Button("다시 시도") {
                handleHistoryTapped() // 알럿에서 다시 시도를 누르면 즉시 재인증 실행
            }
        } message: {
            Text(authErrorMessage)
        }
    }

    private func tabBarHiddenDestination<Content: View>(
        @ViewBuilder content: () -> Content
    ) -> some View {
        content()
            .onAppear {
                setTabBarHidden(true)
            }
            .onDisappear {
                setTabBarHidden(false)
            }
    }

    private func setTabBarHidden(_ hidden: Bool) {
        withAnimation(.easeInOut(duration: 0.22)) {
            isTabBarHidden = hidden
        }
    }

    private var profileHeader: some View {
        NavigationLink {
            tabBarHiddenDestination {
                UserInfoDetailView()
            }
        } label: {
            VStack(spacing: 18) {
                profileImage

                VStack(spacing: 10) {
                    Text(displayNickname)
                        .font(.title3.bold())
                        .foregroundStyle(isNicknameMissing ? Color.accentColor : Color.primary)

                    ProfileEditCTA(isProminent: isNicknameMissing)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(20)
            .fitcommitCardStyle()
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }

    private var currentUserProfile: UserProfile? {
        userProfiles.first
    }

    private var displayNickname: String {
        let nickname = currentUserProfile?.nickname.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return nickname.isEmpty ? "닉네임을 설정해주세요" : nickname
    }

    private var isNicknameMissing: Bool {
        let nickname = currentUserProfile?.nickname.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return nickname.isEmpty
    }

    @ViewBuilder
    private var profileImage: some View {
        if let imageData = currentUserProfile?.imageData,
           let image = UIImage(data: imageData) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 80, height: 80)
                .clipShape(Circle())
                .overlay {
                    Circle()
                        .strokeBorder(Color.white.opacity(0.24), lineWidth: 1)
                }
        } else {
            Image(systemName: "person.circle.fill")
                .resizable()
                .frame(width: 80, height: 80)
                .foregroundColor(.accentColor)
                .background(Circle().fill(Color.accentColor.opacity(0.1)))
        }
    }

    private func profileSection<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundColor(.secondary)
                .padding(.horizontal, 4)

            Spacer().frame(height: 12)

            VStack(spacing: 7) {
                content()
            }
            .frame(maxWidth: .infinity)
        }
    }
    
    private func handleHistoryTapped() {
        if useFaceID {
            let context = LAContext()
            var error: NSError?
            
            // 1. 생체 인식(Face ID) 강제 정책 사용 (.deviceOwnerAuthenticationWithBiometrics)
            if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
                context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "스캔 히스토리에 접근하기 위해 Face ID 인증이 필요합니다.") { success, _ in
                    DispatchQueue.main.async {
                        if success {
                            // 2. 인증 성공 시에만 State를 변경하여 Navigation 작동 (UI 바인딩)
                            showHistory = true
                        } else {
                            // 3. 실패 또는 취소 시 재시도를 묻는 알럿 호출
                            authErrorMessage = "인증에 실패했습니다. 다시 시도하시겠습니까?"
                            authFailed = true
                        }
                    }
                }
            } else {
                authErrorMessage = "Face ID를 사용할 수 없습니다. 아이폰 설정에서 앱의 Face ID 접근 권한을 허용해주세요."
                authFailed = true
            }
        } else {
            showHistory = true
        }
    }

    private func handleLogoutTapped() {
        print("로그아웃 실행됨")
        authManager.logout()
    }
}

private struct ProfileActionRow: View {
    let title: String
    let systemImage: String
    var showsChevron: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            Label(title, systemImage: systemImage)
                .foregroundColor(.primary)

            Spacer()

            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 18)
        .frame(maxWidth: .infinity)
        .frame(height: 54)
        .fitcommitCardStyle()
    }
}

private struct ProfileEditCTA: View {
    let isProminent: Bool

    var body: some View {
        HStack(spacing: 8) {
            Text(isProminent ? "내 정보 설정하기" : "프로필 정보 수정하기")
                .font(.subheadline.weight(.semibold))

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
        }
        .foregroundColor(isProminent ? .white : .accentColor)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background {
            Capsule()
                .fill(isProminent ? Color.accentColor : Color.accentColor.opacity(0.15))
        }
        .overlay {
            Capsule()
                .strokeBorder(Color.accentColor.opacity(isProminent ? 0 : 0.18), lineWidth: 1)
        }
        .accessibilityLabel(isProminent ? "내 정보 설정하기" : "프로필 정보 수정하기")
    }
}

private struct ProfileCardContainer<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(.horizontal, 18)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .fitcommitCardStyle()
    }
}
