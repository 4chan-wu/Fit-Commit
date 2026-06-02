import SwiftUI
import SwiftData

struct AnalysisResultView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext

    let result: AnalysisResult
    let imageData: Data

    @State private var clothingName = ""
    @State private var selectedCategory = ""
    @State private var subcategory = ""
    @State private var selectedColor = ""
    @State private var selectedSeason = ""
    @State private var styleTagsText = ""
    @State private var saveErrorMessage: String?
    @State private var isSaving = false

    private let categoryOptions = ClosetItemEditOptions.categories
    private let seasonOptions = ["Spring/Summer", "Fall/Winter", "All-Season"]

    init(result: AnalysisResult, imageData: Data) {
        self.result = result
        self.imageData = imageData
        _clothingName = State(initialValue: Self.defaultName(for: result.mainCategory))
        _selectedCategory = State(initialValue: result.mainCategory)
        _subcategory = State(initialValue: result.subcategory)
        _selectedColor = State(initialValue: result.mainColor)
        _selectedSeason = State(initialValue: result.season)
        _styleTagsText = State(initialValue: result.styleTags.joined(separator: ", "))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            headerSection
            editableFields
            confidenceSection

            if let saveErrorMessage {
                Text(saveErrorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            HStack(spacing: 12) {
                Button {
                    resetFields()
                } label: {
                    Label("수정하기", systemImage: "pencil")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)

                Button {
                    Task {
                        await saveItem()
                    }
                } label: {
                    Label("저장하기", systemImage: "checkmark")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isSaving)
            }
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
        HStack(spacing: 12) {
            Image(systemName: categoryIconName)
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(Color.accentColor, in: Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text("AI 분석 결과 확인")
                    .font(.headline)
                Text("저장 전 최종 값을 확인하고 수정해주세요.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 0)
        }
    }

    private var editableFields: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("의류 이름", text: $clothingName)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .modifier(ResultInputStyle())

            Picker("종류", selection: $selectedCategory) {
                ForEach(effectiveCategoryOptions, id: \.self) { category in
                    Text(categoryLabel(for: category)).tag(category)
                }
            }
            .pickerStyle(.menu)
            .modifier(ResultInputStyle())

            TextField("세부 종류", text: $subcategory)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .modifier(ResultInputStyle())

            HStack(spacing: 12) {
                Circle()
                    .fill(Color(hexString: selectedColor) ?? result.swiftUIColor)
                    .frame(width: 34, height: 34)
                    .overlay {
                        Circle().stroke(Color.primary.opacity(0.12), lineWidth: 1)
                    }

                TextField("대표 색상 HEX", text: $selectedColor)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .modifier(ResultInputStyle())
            }

            Picker("계절", selection: $selectedSeason) {
                ForEach(effectiveSeasonOptions, id: \.self) { season in
                    Text(season).tag(season)
                }
            }
            .pickerStyle(.segmented)

            TextField("스타일 태그, 쉼표로 구분", text: $styleTagsText)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .modifier(ResultInputStyle())
        }
    }

    private var confidenceSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Divider()

            HStack {
                Text("신뢰도")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text("\(confidencePercent)%")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            if let imageURL = result.imageURL, imageURL.isEmpty == false {
                Text("이미지 참조: \(imageURL)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
    }

    private var effectiveCategoryOptions: [String] {
        optionList(categoryOptions, including: selectedCategory.isEmpty ? result.mainCategory : selectedCategory)
    }

    private var effectiveSeasonOptions: [String] {
        optionList(seasonOptions, including: selectedSeason.isEmpty ? result.season : selectedSeason)
    }

    private var confidencePercent: Int {
        Int((result.confidence * 100).rounded())
    }

    private var categoryIconName: String {
        switch selectedCategory.lowercased() {
        case "top":
            return "tshirt.fill"
        case "bottom":
            return "pants.fill"
        case "outer":
            return "coat.fill"
        case "shoes":
            return "shoeprints.fill"
        case "hat":
            return "graduationcap.fill"
        case "bag":
            return "handbag.fill"
        case "dress":
            return "figure.dress.line.vertical.figure"
        default:
            return "tag.fill"
        }
    }

    private func resetFields() {
        clothingName = Self.defaultName(for: result.mainCategory)
        selectedCategory = result.mainCategory
        subcategory = result.subcategory
        selectedColor = result.mainColor
        selectedSeason = result.season
        styleTagsText = result.styleTags.joined(separator: ", ")
        saveErrorMessage = nil
    }

    @MainActor
    private func saveItem() async {
        guard isSaving == false else { return }
        isSaving = true
        defer { isSaving = false }

        let name = clothingName.trimmingCharacters(in: .whitespacesAndNewlines)
        let category = selectedCategory.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalSubcategory = subcategory.trimmingCharacters(in: .whitespacesAndNewlines)
        let color = selectedColor.trimmingCharacters(in: .whitespacesAndNewlines)
        let season = selectedSeason.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalStyleTags = styleTagsText
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.isEmpty == false }

        guard name.isEmpty == false,
              category.isEmpty == false,
              color.isEmpty == false,
              season.isEmpty == false else {
            saveErrorMessage = "의류 이름, 종류, 대표 색상, 계절을 모두 입력해주세요."
            return
        }

        if BackendClothesService.isConfigured,
           let userID = BackendClothesService.configuredUserID {
            do {
                try await BackendClothesService.save(
                    BackendClothesPayload(
                        userID: userID,
                        mainCategory: category,
                        subcategory: finalSubcategory.isEmpty ? nil : finalSubcategory,
                        mainColor: color,
                        season: seasonValues(from: season),
                        styleTags: finalStyleTags,
                        imageURL: result.imageURL,
                        imagePath: nil,
                        confidence: result.confidence,
                        analysisMethod: result.method,
                        memo: ""
                    )
                )
                dismiss()
                return
            } catch {
                print("[AnalysisResultView] Backend save failed, falling back to SwiftData: \(error.localizedDescription)")
                saveErrorMessage = "서버 저장에 실패해 기기에 임시 저장합니다."
            }
        }

        let item = ClosetItem(
            name: name,
            category: category,
            subcategory: finalSubcategory,
            color: color,
            season: season,
            styleTags: finalStyleTags,
            confidence: result.confidence,
            imageURL: result.imageURL,
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

    private func optionList(_ options: [String], including value: String) -> [String] {
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmedValue.isEmpty == false,
              options.contains(trimmedValue) == false else {
            return options
        }
        return [trimmedValue] + options
    }

    private func categoryLabel(for value: String) -> String {
        switch value {
        case "top":
            return "상의 (top)"
        case "bottom":
            return "하의 (bottom)"
        case "outer":
            return "아우터 (outer)"
        case "shoes":
            return "신발 (shoes)"
        case "hat":
            return "모자 (hat)"
        case "bag":
            return "가방 (bag)"
        case "dress":
            return "원피스 (dress)"
        default:
            return value
        }
    }

    private func seasonValues(from season: String) -> [String] {
        switch season {
        case "Spring/Summer":
            return ["spring", "summer"]
        case "Fall/Winter":
            return ["fall", "winter"]
        case "All-Season":
            return ["spring", "summer", "fall", "winter"]
        default:
            return season
                .split(separator: "/")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                .filter { $0.isEmpty == false }
        }
    }

    private static func defaultName(for category: String) -> String {
        switch category.lowercased() {
        case "top":
            return "새로운 상의"
        case "bottom":
            return "새로운 하의"
        case "outer":
            return "새로운 아우터"
        case "shoes":
            return "새로운 신발"
        case "hat":
            return "새로운 모자"
        case "bag":
            return "새로운 가방"
        case "dress":
            return "새로운 원피스"
        default:
            return "새로운 옷"
        }
    }
}

private struct ResultInputStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.subheadline)
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .background(Color(uiColor: .tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

#Preview {
    AnalysisResultView(
        result: AnalysisResult(
            mainCategory: "top",
            subcategory: "T-shirt",
            mainColor: "#1A237E",
            season: "Spring/Summer",
            styleTags: ["casual", "daily"],
            confidence: 0.85,
            method: "clip",
            imageURL: nil
        ),
        imageData: Data()
    )
    .padding()
    .modelContainer(for: ClosetItem.self, inMemory: true)
}
