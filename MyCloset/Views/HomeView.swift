import SwiftUI
import SwiftData

struct HomeView: View {
    @State private var feedbackTrigger = 0
    @Environment(\.colorScheme) private var colorScheme

    @Query(sort: \ClosetItem.timestamp, order: .reverse) private var items: [ClosetItem]
    @Query private var userProfiles: [UserProfile]
    
    // 더미 추천 코디 데이터 (향후 백엔드 연동 시 교체)
    @State private var dummyOutfits = [
        ("오피스 캐주얼", "네이비 리넨 셔츠", "그레이 슬랙스", Color.blue),
        ("주말 데이트", "화이트 오버핏 티셔츠", "연청 데님", Color.pink),
        ("운동 러닝", "블랙 기능성 반팔", "조거 팬츠", Color.green)
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                Color(uiColor: .systemBackground)
                    .ignoresSafeArea()

                AnimatedMeshBackground()
                    .ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 36) {

                        // 1. 커스텀 헤더 (인사말 & 날씨 코디)
                        VStack(alignment: .leading, spacing: 18) {
                            Text("안녕하세요, \(displayNickname)님!")
                                .font(.system(size: 29, weight: .heavy, design: .rounded))
                                .tracking(1.2)
                                .foregroundColor(.primary)

                            WeatherInsightCard()
                        }
                        .padding(.top, 20)
                        .padding(.horizontal, 20)

                        // 2. AI 스타일 인사이트 (Daily Tip)
                        Button {
                            feedbackTrigger += 1
                        } label: {
                            VStack(alignment: .leading, spacing: 14) {
                                HStack(spacing: 8) {
                                    Image(systemName: "sparkles")
                                        .symbolRenderingMode(.monochrome)
                                        .font(.system(.subheadline, design: .rounded).weight(.light))
                                        .foregroundStyle(.secondary)

                                    Text("Daily Insight")
                                        .font(.system(.subheadline, design: .rounded).weight(.heavy))
                                        .tracking(1.4)
                                        .foregroundColor(.primary)
                                }

                                Text("자외선이 강하고 다소 덥습니다.\n통기성이 좋은 밝은 톤의 셔츠로\n시원하고 세련된 스타일을 연출해보세요.")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(.primary)
                                    .lineSpacing(6)
                                    .multilineTextAlignment(.leading)
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fitcommitCardStyle()
                        }
                        .buttonStyle(PremiumPressButtonStyle())
                        .sensoryFeedback(.impact(weight: .light, intensity: 0.65), trigger: feedbackTrigger)
                        .padding(.horizontal, 20)

                        // 3. 베스트 코디 제안 (Carousel)
                        VStack(alignment: .leading, spacing: 16) {
                            SectionIconTitle(
                                iconName: "wand.and.stars",
                                title: "AI 베스트 코디",
                                tint: .secondary
                            )

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 16) {
                                    ForEach(dummyOutfits.indices, id: \.self) { index in
                                        let outfit = dummyOutfits[index]
                                        Button {
                                            feedbackTrigger += 1
                                        } label: {
                                            OutfitCardView(title: outfit.0, top: outfit.1, bottom: outfit.2, tint: outfit.3)
                                        }
                                        .buttonStyle(PremiumPressButtonStyle())
                                    }
                                }
                                .padding(.horizontal, 24)
                                .padding(.bottom, 10)
                            }
                        }

                        // 4. 내 옷장 리포트 (My Closet Analytics)
                        VStack(alignment: .leading, spacing: 16) {
                            SectionIconTitle(
                                iconName: "chart.pie",
                                title: "내 옷장 리포트",
                                tint: .secondary
                            )

                            ClosetAnalyticsCard(
                                frequentItems: frequentClosetItems,
                                unwornRatio: unwornRatio,
                                feedbackTrigger: $feedbackTrigger
                            )
                            .padding(.horizontal, 20)
                        }

                        Spacer(minLength: 120)
                    }
                    .padding(.bottom, 24)
                }
            }
        }
    }

    private var frequentClosetItems: [ClosetFrequencyItem] {
        let fallbackItems = [
            ClosetFrequencyItem(name: "네이비 셔츠", iconName: "tshirt", tint: .blue, count: 12),
            ClosetFrequencyItem(name: "그레이 슬랙스", iconName: "hanger", tint: .indigo, count: 9),
            ClosetFrequencyItem(name: "러닝 탑", iconName: "figure.run", tint: .green, count: 7)
        ]

        let generatedItems = items.prefix(3).enumerated().map { index, item in
            ClosetFrequencyItem(
                name: item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? item.category : item.name,
                iconName: iconName(for: item.category),
                tint: tint(for: item, index: index),
                count: max(3, 12 - index * 3)
            )
        }

        return generatedItems.isEmpty ? fallbackItems : Array(generatedItems)
    }

    private var displayNickname: String {
        let nickname = userProfiles.first?.nickname.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return nickname.isEmpty ? "핏커밋" : nickname
    }

    private var unwornRatio: Double {
        guard !items.isEmpty else {
            return 0.34
        }

        let cutoffDate = Calendar.current.date(byAdding: .month, value: -3, to: Date()) ?? Date()
        let staleItemCount = items.filter { $0.timestamp < cutoffDate }.count
        return Double(staleItemCount) / Double(items.count)
    }

    private func iconName(for category: String) -> String {
        switch category.lowercased() {
        case "pants":
            return "pants.fill"
        case "skirt", "dress":
            return "figure.dress.line.vertical.figure"
        case "outerwear":
            return "coat"
        case "hoodie", "sweater":
            return "jacket"
        default:
            return "tshirt"
        }
    }

    private func tint(for item: ClosetItem, index: Int) -> Color {
        Color(hexString: item.color) ?? [.blue, .purple, .green][index % 3]
    }
}

// MARK: - Subviews

struct AnimatedMeshBackground: View {
    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30)) { timeline in
            GeometryReader { geometry in
                let time = timeline.date.timeIntervalSinceReferenceDate
                let cycle = time / 20
                let width = max(geometry.size.width, 1)
                let height = max(geometry.size.height, 1)

                ZStack {
                    Circle()
                        .fill(Color.blue.opacity(0.28))
                        .frame(width: 260, height: 260)
                        .blur(radius: 92)
                        .position(
                            x: width * (0.20 + 0.10 * sin(cycle * .pi * 2)),
                            y: height * (0.18 + 0.08 * cos(cycle * .pi * 2))
                        )

                    Circle()
                        .fill(Color.purple.opacity(0.26))
                        .frame(width: 320, height: 320)
                        .blur(radius: 104)
                        .position(
                            x: width * (0.72 + 0.12 * cos(cycle * .pi * 2 + 0.9)),
                            y: height * (0.44 + 0.10 * sin(cycle * .pi * 2 + 0.9))
                        )

                    Circle()
                        .fill(Color.pink.opacity(0.24))
                        .frame(width: 280, height: 280)
                        .blur(radius: 96)
                        .position(
                            x: width * (0.48 + 0.16 * sin(cycle * .pi * 2 + 1.8)),
                            y: height * (0.78 + 0.08 * cos(cycle * .pi * 2 + 1.8))
                        )
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

struct WeatherInsightCard: View {
    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.primary.opacity(0.06))
                    .frame(width: 52, height: 52)

                Image(systemName: "sun.max")
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: 23, weight: .light, design: .rounded))
                    .foregroundStyle(.primary)
            }

            VStack(alignment: .leading, spacing: 5) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("24°C")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(.primary)

                    Text("맑음")
                        .font(.system(.subheadline, design: .rounded).weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                Text("외출 시 추천 아이템\n 썬크림")
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle()
    }
}

struct ClosetFrequencyItem {
    let name: String
    let iconName: String
    let tint: Color
    let count: Int
}

struct ClosetAnalyticsCard: View {
    var frequentItems: [ClosetFrequencyItem]
    var unwornRatio: Double
    @Binding var feedbackTrigger: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 6) {
                Text("방치된 옷을 정리하고 옷장을 가볍게 만드세요.")
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "chart.pie")
                        .symbolRenderingMode(.monochrome)
                        .font(.system(.subheadline, design: .rounded).weight(.light))
                        .foregroundStyle(.secondary)

                    Text("요즘 자주 찾는 옷")
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .foregroundStyle(.primary)
                }

                HStack(spacing: 10) {
                    ForEach(frequentItems.indices, id: \.self) { index in
                        FrequentClosetItemView(item: frequentItems[index])
                    }
                }
            }

            Divider()
                .opacity(0.45)

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "archivebox")
                        .symbolRenderingMode(.monochrome)
                        .font(.system(.subheadline, design: .rounded).weight(.light))
                        .foregroundStyle(.secondary)

                    Text("정리가 필요한 옷")
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .foregroundStyle(.primary)

                    Spacer()

                    Text("\(Int((unwornRatio * 100).rounded()))%")
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .foregroundStyle(.secondary)
                }

                ProgressView(value: unwornRatio)
                    .tint(.secondary)

                NavigationLink {
                    ClosetView(initialFilter: .unworn)
                } label: {
                    HStack(spacing: 6) {
                        Text("비우기 제안")
                            .font(.system(.subheadline, design: .rounded).weight(.bold))

                        Image(systemName: "arrow.right")
                            .font(.footnote.weight(.light))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.accentColor)
                    .clipShape(Capsule(style: .continuous))
                }
                .buttonStyle(PremiumPressButtonStyle())
                .simultaneousGesture(TapGesture().onEnded {
                    feedbackTrigger += 1
                })
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle()
    }
}

struct FrequentClosetItemView: View {
    var item: ClosetFrequencyItem

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(item.tint.opacity(0.12))
                    .frame(height: 54)

                Image(systemName: item.iconName)
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: 23, weight: .light, design: .rounded))
                    .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(item.name)
                    .font(.system(.caption, design: .rounded).weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                Text("\(item.count)회")
                    .font(.system(.caption2, design: .rounded).weight(.bold))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle(borderOpacity: 0.06)
    }
}

// AI 코디 추천 카드 (가로 스크롤용)
struct OutfitCardView: View {
    var title: String
    var top: String
    var bottom: String
    var tint: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 3D 옷장 썸네일 플레이스홀더
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(tint.opacity(0.1))
                    .frame(width: 140, height: 140)
                
                Image(systemName: "tshirt")
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: 40, weight: .light))
                    .foregroundStyle(.secondary)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(.caption, design: .rounded).weight(.bold))
                    .foregroundColor(tint)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(tint.opacity(0.15))
                    .clipShape(Capsule())
                
                Text(top)
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                
                Text(bottom)
                    .font(.system(.caption, design: .rounded))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(16)
        .fitcommitCardStyle()
    }
}

struct SectionIconTitle: View {
    var iconName: String
    var title: String
    var tint: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: iconName)
                .symbolRenderingMode(.monochrome)
                .font(.system(.subheadline, design: .rounded).weight(.light))
                .foregroundStyle(tint)

            Text(title)
                .font(.system(.headline, design: .rounded).weight(.semibold))
                .foregroundColor(.primary)
        }
        .padding(.horizontal, 24)
    }
}

struct PremiumPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.72), value: configuration.isPressed)
    }
}

struct PremiumGlassCardModifier: ViewModifier {
    var cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .fitcommitCardStyle(cornerRadius: cornerRadius)
    }
}

extension View {
    func premiumGlassCard(cornerRadius: CGFloat) -> some View {
        modifier(PremiumGlassCardModifier(cornerRadius: cornerRadius))
    }

    func minimalMaterialCard(cornerRadius: CGFloat) -> some View {
        fitcommitCardStyle(cornerRadius: cornerRadius, borderOpacity: 0.07)
    }
}

#Preview {
    NavigationStack {
        HomeView()
    }
    .modelContainer(for: [UserProfile.self, BodyProfile.self, ClosetItem.self], inMemory: true)
}
