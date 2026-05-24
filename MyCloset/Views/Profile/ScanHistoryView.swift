import SwiftUI
import SwiftData
import Charts

private struct RoomFile: Hashable {
    let url: URL
    let creationDate: Date
}

private enum ChartMetrics: String, CaseIterable {
    case height = "키"
    case shoulder = "어깨"
}

private struct DummyScanItem: Identifiable {
    let id = UUID()
    let title: String
    let category: String
    let date: Date
    let systemImage: String
    let summary: String
    let confidence: Int

    static let samples: [DummyScanItem] = [
        DummyScanItem(
            title: "네이비 오버핏 블레이저",
            category: "아우터",
            date: Calendar.current.date(byAdding: .hour, value: -3, to: Date()) ?? Date(),
            systemImage: "jacket",
            summary: "어깨 라인이 여유롭고 세미포멀 코디에 적합",
            confidence: 96
        ),
        DummyScanItem(
            title: "화이트 코튼 셔츠",
            category: "상의",
            date: Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date(),
            systemImage: "tshirt",
            summary: "밝은 톤 하의와 매칭 시 깔끔한 인상",
            confidence: 94
        ),
        DummyScanItem(
            title: "라이트 워시 데님",
            category: "하의",
            date: Calendar.current.date(byAdding: .day, value: -2, to: Date()) ?? Date(),
            systemImage: "figure.stand",
            summary: "캐주얼 룩에 활용도가 높은 스트레이트 핏",
            confidence: 91
        ),
        DummyScanItem(
            title: "블랙 로퍼",
            category: "신발",
            date: Calendar.current.date(byAdding: .day, value: -4, to: Date()) ?? Date(),
            systemImage: "shoeprints.fill",
            summary: "졸업식, 면접 등 단정한 스타일에 추천",
            confidence: 89
        ),
        DummyScanItem(
            title: "그레이 니트 베스트",
            category: "상의",
            date: Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date(),
            systemImage: "square.grid.2x2",
            summary: "셔츠 레이어드로 계절감 있는 프레피 무드",
            confidence: 87
        )
    ]
}

struct ScanHistoryView: View {
    @Query(sort: \BodyProfile.date, order: .reverse) private var profiles: [BodyProfile]
    @Environment(\.modelContext) private var modelContext

    @State private var savedRooms: [RoomFile] = []
    @State private var selectedMetric: ChartMetrics = .height

    private var bestProfileID: UUID? {
        profiles.max {
            ($0.shoulderWidth / max($0.hipWidth, 0.01)) < ($1.shoulderWidth / max($1.hipWidth, 0.01))
        }?.id
    }

    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()

            BackgroundBlurView()
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 22) {
                    if profiles.count > 1 {
                        sectionTitle("신체 변화 추이")
                        growthChartCard
                    }

                    sectionTitle("스캔한 옷")

                    VStack(spacing: 10) {
                        ForEach(DummyScanItem.samples) { item in
                            DummyScanCard(item: item)
                        }
                    }

                    sectionTitle("신체 스캔 기록")

                    if profiles.isEmpty {
                        EmptyHistoryCard()
                    } else {
                        VStack(spacing: 10) {
                            ForEach(Array(profiles.enumerated()), id: \.element.id) { index, profile in
                                let previousProfile = index + 1 < profiles.count ? profiles[index + 1] : nil

                                NavigationLink {
                                    MeasurementDetailView(profile: profile)
                                } label: {
                                    BodyScanHistoryCard(
                                        profile: profile,
                                        previousProfile: previousProfile,
                                        isBest: profile.id == bestProfileID
                                    )
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button(role: .destructive) {
                                        deleteProfile(profile)
                                    } label: {
                                        Label("삭제", systemImage: "trash")
                                    }
                                }
                            }
                        }
                    }

                    if !savedRooms.isEmpty {
                        sectionTitle("공간 스캔 데이터")

                        VStack(spacing: 10) {
                            ForEach(savedRooms, id: \.url) { room in
                                NavigationLink {
                                    RoomPreviewView(fileURL: room.url)
                                } label: {
                                    RoomScanHistoryCard(room: room)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    Spacer().frame(height: 32)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 34)
            }
        }
        .navigationTitle("스캔 히스토리")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            fetchSavedRooms()
        }
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 4)
    }

    private var growthChartCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Picker("지표", selection: $selectedMetric) {
                ForEach(ChartMetrics.allCases, id: \.self) { metric in
                    Text(metric.rawValue).tag(metric)
                }
            }
            .pickerStyle(.segmented)

            Chart {
                ForEach(profiles.prefix(5).reversed()) { profile in
                    let value = selectedMetric == .height ? profile.height * 100 : profile.shoulderWidth * 100

                    LineMark(
                        x: .value("날짜", profile.date, unit: .day),
                        y: .value("cm", value)
                    )
                    .symbol(.circle)
                    .interpolationMethod(.monotone)
                    .foregroundStyle(Color.accentColor)

                    AreaMark(
                        x: .value("날짜", profile.date, unit: .day),
                        y: .value("cm", value)
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color.accentColor.opacity(0.26), .clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                }
            }
            .chartYScale(domain: .automatic(includesZero: false))
            .frame(height: 180)
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .fitcommitCardStyle()
    }

    private func deleteProfile(_ profile: BodyProfile) {
        modelContext.delete(profile)

        do {
            try modelContext.save()
        } catch {
            print("SwiftData 삭제 실패: \(error)")
        }
    }

    private func fetchSavedRooms() {
        let fileManager = FileManager.default
        guard let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return
        }

        do {
            let urls = try fileManager.contentsOfDirectory(
                at: documentsURL,
                includingPropertiesForKeys: [.creationDateKey]
            )

            savedRooms = urls
                .filter { $0.pathExtension.lowercased() == "usdz" }
                .compactMap { url in
                    let values = try? url.resourceValues(forKeys: [.creationDateKey])
                    return RoomFile(url: url, creationDate: values?.creationDate ?? Date())
                }
                .sorted { $0.creationDate > $1.creationDate }
        } catch {
            savedRooms = []
        }
    }
}

private struct DummyScanCard: View {
    let item: DummyScanItem

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: item.systemImage)
                .font(.system(size: 24, weight: .medium, design: .rounded))
                .foregroundStyle(.primary)
                .frame(width: 48, height: 48)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(item.title)
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Spacer(minLength: 8)

                    Text("\(item.confidence)%")
                        .font(.system(.caption, design: .rounded).weight(.bold))
                        .foregroundStyle(Color.accentColor)
                }

                Text(item.summary)
                    .font(.system(.caption, design: .rounded).weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)

                HStack(spacing: 8) {
                    Text(item.category)
                        .font(.system(.caption2, design: .rounded).weight(.bold))
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06), in: Capsule())

                    Text(item.date.formatted(date: .abbreviated, time: .shortened))
                        .font(.system(.caption2, design: .rounded).weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle()
    }
}

private struct BodyScanHistoryCard: View {
    let profile: BodyProfile
    let previousProfile: BodyProfile?
    let isBest: Bool

    var body: some View {
        HStack(spacing: 14) {
            thumbnail

            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(profile.date.formatted(date: .abbreviated, time: .shortened))
                        .font(.system(.subheadline, design: .rounded).weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Spacer(minLength: 8)

                    if isBest {
                        Image(systemName: "crown.fill")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(.yellow)
                    }
                }

                HStack(spacing: 6) {
                    Text(profile.bodyShape.components(separatedBy: " ").first ?? "체형")
                    Text("·")
                    Text("키 \(String(format: "%.1f", profile.height * 100))cm")
                }
                .font(.system(.caption, design: .rounded).weight(.semibold))
                .foregroundStyle(.secondary)

                Text(differenceText)
                    .font(.system(.caption2, design: .rounded).weight(.semibold))
                    .foregroundStyle(previousProfile == nil ? Color.secondary : Color.accentColor)
                    .lineLimit(1)
            }

            Image(systemName: "chevron.right")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle()
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let imageData = profile.imageData, let image = UIImage(data: imageData) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 54, height: 54)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        } else {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.primary.opacity(0.06))
                .frame(width: 54, height: 54)
                .overlay {
                    Image(systemName: "figure.stand")
                        .font(.system(size: 23, weight: .medium))
                        .foregroundStyle(.secondary)
                }
        }
    }

    private var differenceText: String {
        guard let previousProfile else {
            return "최초 스캔 기록입니다."
        }

        let legDiff = (profile.legLength - previousProfile.legLength) * 100
        let shoulderDiff = (profile.shoulderWidth - previousProfile.shoulderWidth) * 100

        if abs(legDiff) >= abs(shoulderDiff), abs(legDiff) >= 0.1 {
            return "이전보다 다리가 \(String(format: "%.1f", abs(legDiff)))cm \(legDiff > 0 ? "길게" : "짧게") 측정됨"
        }

        if abs(shoulderDiff) >= 0.1 {
            return "이전보다 어깨가 \(String(format: "%.1f", abs(shoulderDiff)))cm \(shoulderDiff > 0 ? "넓게" : "좁게") 측정됨"
        }

        return "이전과 비슷한 체형 밸런스 유지 중"
    }
}

private struct EmptyHistoryCard: View {
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "camera.viewfinder")
                .font(.system(size: 24, weight: .medium, design: .rounded))
                .foregroundStyle(.secondary)
                .frame(width: 48, height: 48)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 6) {
                Text("아직 신체 스캔 기록이 없습니다.")
                    .font(.system(.subheadline, design: .rounded).weight(.bold))
                    .foregroundStyle(.primary)

                Text("AR 카메라로 측정하면 이곳에 히스토리가 쌓입니다.")
                    .font(.system(.caption, design: .rounded).weight(.medium))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle()
    }
}

private struct RoomScanHistoryCard: View {
    let room: RoomFile

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "cube.transparent")
                .font(.system(size: 24, weight: .medium, design: .rounded))
                .foregroundStyle(.primary)
                .frame(width: 48, height: 48)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 6) {
                Text(room.url.lastPathComponent)
                    .font(.system(.subheadline, design: .rounded).weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Text("생성일: \(room.creationDate.formatted(date: .abbreviated, time: .shortened))")
                    .font(.system(.caption, design: .rounded).weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle()
    }
}

#Preview {
    NavigationStack {
        ScanHistoryView()
    }
    .modelContainer(for: [BodyProfile.self, ClosetItem.self], inMemory: true)
}
