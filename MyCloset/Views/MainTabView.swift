import SwiftUI
import SwiftData

enum AppTheme: String, CaseIterable {
    case system = "시스템"
    case light = "라이트"
    case dark = "다크"

    var colorScheme: ColorScheme? {
        switch self {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }
}

enum FitcommitTab: CaseIterable, Identifiable {
    case home
    case closet
    case aiChat
    case profile

    var id: Self { self }

    var title: String {
        switch self {
        case .home:
            return "홈"
        case .closet:
            return "내 옷장"
        case .aiChat:
            return "AI 코디"
        case .profile:
            return "마이 페이지"
        }
    }

    var icon: String {
        switch self {
        case .home:
            return "house"
        case .closet:
            return "hanger"
        case .aiChat:
            return "message"
        case .profile:
            return "person"
        }
    }
}

private struct FloatingTabBarVisibilityKey: EnvironmentKey {
    static let defaultValue: Binding<Bool> = .constant(true)
}

extension EnvironmentValues {
    var isFloatingTabBarVisible: Binding<Bool> {
        get { self[FloatingTabBarVisibilityKey.self] }
        set { self[FloatingTabBarVisibilityKey.self] = newValue }
    }
}

struct MainTabView: View {
    @AppStorage("appTheme") private var appTheme: AppTheme = .system
    @Namespace private var liquidSelectionNamespace

    @State private var selectedTab: FitcommitTab = .home
    @State private var isTabBarHidden: Bool = false

    var body: some View {
        ZStack(alignment: .bottom) {
            BackgroundBlurView()
                .ignoresSafeArea()

            activeTabView
                .id(selectedTab)

            if !isTabBarHidden {
                LiquidGlassFloatingBar(
                    selectedTab: $selectedTab,
                    namespace: liquidSelectionNamespace
                )
                .padding(.horizontal, 18)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
        .environment(\.isFloatingTabBarVisible, floatingTabBarVisibility)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .animation(.spring(response: 0.4, dampingFraction: 0.86), value: selectedTab)
        .animation(.easeInOut(duration: 0.22), value: isTabBarHidden)
        .onChange(of: selectedTab) { _, _ in
            withAnimation(.easeInOut(duration: 0.22)) {
                isTabBarHidden = false
            }
        }
        .preferredColorScheme(appTheme.colorScheme)
    }

    private var floatingTabBarVisibility: Binding<Bool> {
        Binding(
            get: { !isTabBarHidden },
            set: { isVisible in
                withAnimation(.easeInOut(duration: 0.22)) {
                    isTabBarHidden = !isVisible
                }
            }
        )
    }

    @ViewBuilder
    private var activeTabView: some View {
        switch selectedTab {
        case .home:
            HomeView()
        case .closet:
            ClosetView()
        case .aiChat:
            NavigationStack {
                AICodiLandingView(isTabBarHidden: $isTabBarHidden)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .profile:
            NavigationStack {
                MyProfileView(isTabBarHidden: $isTabBarHidden)
            }
        }
    }
}

private struct LiquidGlassFloatingBar: View {
    @Binding var selectedTab: FitcommitTab
    let namespace: Namespace.ID

    @State private var feedbackTrigger = 0

    var body: some View {
        HStack(spacing: 4) {
            ForEach(FitcommitTab.allCases) { tab in
                LiquidGlassTabItem(
                    tab: tab,
                    selectedTab: $selectedTab,
                    namespace: namespace,
                    feedbackTrigger: $feedbackTrigger
                )
            }
        }
        .padding(6)
        .frame(maxWidth: 390)
        .background {
            Capsule(style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay {
                    Capsule(style: .continuous)
                        .strokeBorder(Color.white.opacity(0.2), lineWidth: 0.8)
                }
        }
        .frame(maxWidth: .infinity, alignment: .bottom)
        .sensoryFeedback(.impact(weight: .light, intensity: 0.68), trigger: feedbackTrigger)
    }
}

private struct LiquidGlassTabItem: View {
    let tab: FitcommitTab
    @Binding var selectedTab: FitcommitTab
    let namespace: Namespace.ID
    @Binding var feedbackTrigger: Int

    private var isSelected: Bool {
        selectedTab == tab
    }

    var body: some View {
        Button {
            feedbackTrigger += 1
            withAnimation(.spring(response: 0.48, dampingFraction: 0.72, blendDuration: 0.08)) {
                selectedTab = tab
            }
        } label: {
            HStack(spacing: isSelected ? 6 : 0) {
                Image(systemName: tab.icon)
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 20, weight: .regular, design: .rounded))
                    .scaleEffect(isSelected ? 1.12 : 1)
                    .symbolEffect(.bounce, value: isSelected)

                if isSelected {
                    Text(tab.title)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .transition(.opacity.combined(with: .scale(scale: 0.92, anchor: .leading)))
                }
            }
            .foregroundStyle(isSelected ? Color.primary : Color.secondary)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background {
                if isSelected {
                    Capsule(style: .continuous)
                        .fill(.regularMaterial)
                        .overlay {
                            Capsule(style: .continuous)
                                .fill(Color.blue.opacity(0.13))
                        }
                        .overlay {
                            Capsule(style: .continuous)
                                .strokeBorder(Color.white.opacity(0.28), lineWidth: 0.8)
                        }
                        .matchedGeometryEffect(
                            id: "liquid-glass-selection",
                            in: namespace,
                            properties: .frame,
                            anchor: .center
                        )
                }
            }
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(LiquidTabPressButtonStyle())
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

private struct LiquidTabPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.68), value: configuration.isPressed)
    }
}

#Preview {
    MainTabView()
        .environmentObject(AuthenticationManager())
        .modelContainer(for: [UserProfile.self, BodyProfile.self, ClosetItem.self], inMemory: true)
}
