import SwiftUI
import SwiftData

struct AnalysisResultView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext

    let result: AnalysisResult
    let imageData: Data

    @State private var clothingName = ""
    @State private var selectedCategory = ""
    @State private var selectedColor = ""
    @State private var selectedSeason = ""
    @State private var saveErrorMessage: String?
    @State private var hasInitializedFields = false

    private let categoryOptions = [
        "T-shirt",
        "Shirt",
        "Hoodie",
        "Sweater",
        "Pants",
        "Skirt",
        "Dress",
        "Outerwear",
    ]

    private let seasonOptions = [
        "Spring/Summer",
        "Fall/Winter",
        "All-Season",
    ]

    init(result: AnalysisResult, imageData: Data) {
        self.result = result
        self.imageData = imageData
        _clothingName = State(initialValue: Self.defaultName(for: result.category))
        _selectedCategory = State(initialValue: result.category)
        _selectedColor = State(initialValue: result.color)
        _selectedSeason = State(initialValue: result.season)
    }

    var body: some View {
        Group {
            if result.category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                placeholderView
            } else {
                resultForm
            }
        }
        .onAppear {
            initializeEditableFieldsIfNeeded()
        }
        .onChange(of: result) { _, newResult in
            clothingName = Self.defaultName(for: newResult.category)
            selectedCategory = newResult.category
            selectedColor = newResult.color
            selectedSeason = newResult.season
            hasInitializedFields = true
        }
    }

    private var resultForm: some View {
        VStack(alignment: .leading, spacing: 18) {
            headerSection

            VStack(alignment: .leading, spacing: 12) {
                TextField(namePlaceholder, text: $clothingName)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .font(.headline)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 11)
                    .background(Color(uiColor: .tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Picker("Category", selection: $selectedCategory) {
                    ForEach(effectiveCategoryOptions, id: \.self) { category in
                        Text(category).tag(category)
                    }
                }
                .pickerStyle(.menu)

                HStack(spacing: 12) {
                    Circle()
                        .fill(Color(hexString: selectedColor) ?? result.swiftUIColor)
                        .frame(width: 34, height: 34)
                        .overlay {
                            Circle()
                                .stroke(Color.primary.opacity(0.12), lineWidth: 1)
                        }
                        .shadow(
                            color: (Color(hexString: selectedColor) ?? result.swiftUIColor).opacity(0.28),
                            radius: 6,
                            x: 0,
                            y: 3
                        )

                    TextField("HEX color", text: $selectedColor)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(Color(uiColor: .tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                Picker("Season", selection: $selectedSeason) {
                    ForEach(effectiveSeasonOptions, id: \.self) { season in
                        Label(season, systemImage: iconName(forSeason: season)).tag(season)
                    }
                }
                .pickerStyle(.segmented)
            }

            Divider()

            HStack(spacing: 12) {
                Circle()
                    .fill(Color(hexString: selectedColor) ?? result.swiftUIColor)
                    .frame(width: 34, height: 34)
                    .overlay {
                        Circle()
                            .stroke(Color.primary.opacity(0.12), lineWidth: 1)
                    }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Color")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text(selectedColor.isEmpty ? result.color : selectedColor)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                }

                Spacer(minLength: 0)
            }

            HStack {
                Label(selectedSeason.isEmpty ? result.season : selectedSeason, systemImage: seasonIconName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(seasonTint)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(seasonTint.opacity(0.12), in: Capsule())

                Spacer(minLength: 0)

                Text("\(seasonConfidence)%")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
            }

            if let saveErrorMessage {
                Text(saveErrorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.leading)
            }

            Button {
                saveItem()
            } label: {
                Label("저장", systemImage: "checkmark")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(uiColor: .secondarySystemBackground))
        )
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.primary.opacity(0.08), lineWidth: 1)
        }
        .shadow(color: Color.black.opacity(0.08), radius: 14, x: 0, y: 8)
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                Image(systemName: categoryIconName)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 48, height: 48)
                    .background(Color.accentColor, in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(selectedCategory.isEmpty ? result.category : selectedCategory)
                        .font(.headline)
                        .foregroundStyle(.primary)

                    Text("\(categoryConfidence)% confidence")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 0)
            }

            Divider()
        }
    }

    private var placeholderView: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(.orange)

            Text("분석 결과를 표시할 수 없습니다")
                .font(.headline)
                .foregroundStyle(.primary)

            Text("서버 응답 데이터가 비어 있습니다. 다시 분석을 시도해주세요.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(18)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.primary.opacity(0.08), lineWidth: 1)
        }
    }

    private var effectiveCategoryOptions: [String] {
        optionList(categoryOptions, including: selectedCategory.isEmpty ? result.category : selectedCategory)
    }

    private var effectiveSeasonOptions: [String] {
        optionList(seasonOptions, including: selectedSeason.isEmpty ? result.season : selectedSeason)
    }

    private var namePlaceholder: String {
        Self.defaultName(for: selectedCategory.isEmpty ? result.category : selectedCategory)
    }

    private func optionList(_ options: [String], including value: String) -> [String] {
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmedValue.isEmpty == false,
              options.contains(trimmedValue) == false else {
            return options
        }

        return [trimmedValue] + options
    }

    private var categoryIconName: String {
        switch (selectedCategory.isEmpty ? result.category : selectedCategory).lowercased() {
        case "t-shirt", "shirt":
            return "tshirt.fill"
        case "hoodie", "sweater":
            return "jacket.fill"
        case "pants":
            return "pants.fill"
        case "skirt", "dress":
            return "figure.dress.line.vertical.figure"
        case "outerwear":
            return "coat.fill"
        default:
            return "tag.fill"
        }
    }

    private var seasonIconName: String {
        iconName(forSeason: selectedSeason.isEmpty ? result.season : selectedSeason)
    }

    private var seasonTint: Color {
        switch selectedSeason.isEmpty ? result.season : selectedSeason {
        case "Spring/Summer":
            return .orange
        case "Fall/Winter":
            return .blue
        default:
            return .green
        }
    }

    private var categoryConfidence: Int {
        Int((result.confidenceCat * 100).rounded())
    }

    private var seasonConfidence: Int {
        Int((result.confidenceSea * 100).rounded())
    }

    private func iconName(forSeason season: String) -> String {
        switch season {
        case "Spring/Summer":
            return "sun.max.fill"
        case "Fall/Winter":
            return "snowflake"
        default:
            return "circle.lefthalf.filled"
        }
    }

    private func initializeEditableFieldsIfNeeded() {
        guard hasInitializedFields == false else { return }
        clothingName = clothingName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? Self.defaultName(for: result.category)
            : clothingName
        selectedCategory = result.category
        selectedColor = result.color
        selectedSeason = result.season
        hasInitializedFields = true
    }

    private func saveItem() {
        let name = clothingName.trimmingCharacters(in: .whitespacesAndNewlines)
        let category = selectedCategory.trimmingCharacters(in: .whitespacesAndNewlines)
        let color = selectedColor.trimmingCharacters(in: .whitespacesAndNewlines)
        let season = selectedSeason.trimmingCharacters(in: .whitespacesAndNewlines)

        guard name.isEmpty == false,
              category.isEmpty == false,
              color.isEmpty == false,
              season.isEmpty == false else {
            saveErrorMessage = "옷 이름, 카테고리, 색상, 계절을 모두 입력해주세요."
            return
        }

        let item = ClosetItem(
            name: name,
            category: category,
            color: color,
            season: season,
            imageData: imageData
        )

        modelContext.insert(item)

        do {
            try modelContext.save()
            dismiss()
        } catch {
            saveErrorMessage = "저장하지 못했습니다: \(error.localizedDescription)"
        }
    }

    private static func defaultName(for category: String) -> String {
        switch category.lowercased() {
        case "t-shirt", "shirt", "hoodie", "sweater":
            return "새로운 상의"
        case "pants", "skirt":
            return "새로운 하의"
        case "dress":
            return "새로운 원피스"
        case "outerwear":
            return "새로운 아우터"
        default:
            return "새로운 옷"
        }
    }
}

#Preview {
    AnalysisResultView(
        result: AnalysisResult(
            category: "T-shirt",
            confidenceCat: 0.85,
            color: "#1A237E",
            season: "Spring/Summer",
            confidenceSea: 0.72,
            methodSea: "heuristic"
        ),
        imageData: Data()
    )
    .padding()
    .modelContainer(for: ClosetItem.self, inMemory: true)
}
