import SwiftUI

struct AICodiLandingView: View {
    @Binding private var isTabBarHidden: Bool

    init(isTabBarHidden: Binding<Bool>) {
        self._isTabBarHidden = isTabBarHidden
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 28) {
                header

                VStack(spacing: 14) {
                    NavigationLink {
                        tabBarHiddenDestination {
                            AIChatView(
                                isTabBarHidden: $isTabBarHidden,
                                hidesTabBarOnAppear: true
                            )
                        }
                    } label: {
                        AICodiLandingActionCard(
                            title: "새로운 코디 시작하기",
                            subtitle: "패트리에게 오늘의 상황과 스타일을 물어보세요.",
                            systemImage: "message"
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        tabBarHiddenDestination {
                            ChatHistoryView()
                        }
                    } label: {
                        AICodiLandingActionCard(
                            title: "채팅 히스토리 보기",
                            subtitle: "이전에 나눈 스타일 상담을 다시 확인해요.",
                            systemImage: "clock.arrow.circlepath"
                        )
                    }
                    .buttonStyle(.plain)
                }

                Spacer(minLength: 120)
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.clear)
        .navigationTitle("AI 코디")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            setTabBarHidden(false)
        }
    }

    private var header: some View {
        VStack(spacing: 18) {
            ZStack {
                Circle()
                    .fill(.ultraThinMaterial)
                    .frame(width: 104, height: 104)
                    .overlay {
                        Circle()
                            .strokeBorder(Color.white.opacity(0.24), lineWidth: 1)
                    }

                Image(systemName: "sparkles")
                    .font(.system(size: 34, weight: .medium, design: .rounded))
                    .foregroundStyle(.primary)
                    .offset(x: 20, y: -22)

                Image(systemName: "person.fill")
                    .font(.system(size: 54, weight: .regular, design: .rounded))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(.primary.opacity(0.92), .secondary.opacity(0.45))
                    .offset(y: 7)
            }
            .accessibilityHidden(true)

            VStack(spacing: 10) {
                Text("패트리(AI 코디)")
                    .font(.system(size: 30, weight: .heavy, design: .rounded))
                    .foregroundStyle(.primary)

                Text("AI 패션 어시스턴트, 패트리에게 물어보세요!")
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 18)
        .padding(.bottom, 12)
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
}

private struct AICodiLandingActionCard: View {
    let title: String
    let subtitle: String
    let systemImage: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 24, weight: .medium, design: .rounded))
                .foregroundStyle(.primary)
                .frame(width: 46, height: 46)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 5) {
                Text(title)
                    .font(.system(.headline, design: .rounded).weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Text(subtitle)
                    .font(.system(.caption, design: .rounded).weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 18)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 68)
        .fitcommitCardStyle()
    }
}

#Preview {
    NavigationStack {
        AICodiLandingView(isTabBarHidden: .constant(false))
    }
}
