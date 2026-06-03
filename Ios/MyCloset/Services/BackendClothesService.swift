import Foundation

struct BackendClothesPayload: Encodable {
    let userID: String
    let mainCategory: String
    let subcategory: String?
    let mainColor: String?
    let season: [String]
    let styleTags: [String]
    let imageURL: String?
    let imagePath: String?
    let localImagePath: String?
    let confidence: Double?
    let analysisMethod: String?
    let analysisStatus: String?
    let analysisError: String?
    let analyzedAt: Date?
    let needsAnalysis: Bool?
    let syncStatus: String?
    let memo: String?

    private enum CodingKeys: String, CodingKey {
        case userID = "user_id"
        case mainCategory = "main_category"
        case subcategory
        case mainColor = "main_color"
        case season
        case styleTags = "style_tags"
        case imageURL = "image_url"
        case imagePath = "image_path"
        case localImagePath = "local_image_path"
        case confidence
        case analysisMethod = "analysis_method"
        case analysisStatus = "analysis_status"
        case analysisError = "analysis_error"
        case analyzedAt = "analyzed_at"
        case needsAnalysis = "needs_analysis"
        case syncStatus = "sync_status"
        case memo
    }
}

struct BackendClothesUpdatePayload: Encodable {
    let mainCategory: String?
    let subcategory: String?
    let mainColor: String?
    let season: [String]?
    let styleTags: [String]?
    let imageURL: String?
    let imagePath: String?
    let localImagePath: String?
    let confidence: Double?
    let analysisMethod: String?
    let analysisStatus: String?
    let analysisError: String?
    let analyzedAt: Date?
    let needsAnalysis: Bool?
    let syncStatus: String?
    let memo: String?

    private enum CodingKeys: String, CodingKey {
        case mainCategory = "main_category"
        case subcategory
        case mainColor = "main_color"
        case season
        case styleTags = "style_tags"
        case imageURL = "image_url"
        case imagePath = "image_path"
        case localImagePath = "local_image_path"
        case confidence
        case analysisMethod = "analysis_method"
        case analysisStatus = "analysis_status"
        case analysisError = "analysis_error"
        case analyzedAt = "analyzed_at"
        case needsAnalysis = "needs_analysis"
        case syncStatus = "sync_status"
        case memo
    }
}

enum BackendClothesService {
    private static let defaultBackendBaseURL = "http://13.125.252.131:8000"
    private static let defaultSupabaseUserID = "45482f0f-42d0-401e-a18a-77abeb0492cc"

    private static var baseURL: URL? {
        if let value = ProcessInfo.processInfo.environment["FITCOMMIT_BACKEND_BASE_URL"],
           value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return URL(string: value)
        }
        if let value = Bundle.main.object(forInfoDictionaryKey: "FITCOMMIT_BACKEND_BASE_URL") as? String,
           value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return URL(string: value)
        }
        return URL(string: defaultBackendBaseURL)
    }

    static var configuredUserID: String? {
        if let value = ProcessInfo.processInfo.environment["FITCOMMIT_SUPABASE_USER_ID"],
           value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return value
        }
        if let value = Bundle.main.object(forInfoDictionaryKey: "FITCOMMIT_SUPABASE_USER_ID") as? String,
           value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return value
        }
        return defaultSupabaseUserID
    }

    static var isConfigured: Bool {
        baseURL != nil && configuredUserID != nil
    }

    static var isAIAnalysisEnabled: Bool {
        if let value = ProcessInfo.processInfo.environment["ENABLE_AI_ANALYSIS"] {
            return boolValue(value)
        }
        if let value = Bundle.main.object(forInfoDictionaryKey: "ENABLE_AI_ANALYSIS") as? String {
            return boolValue(value)
        }
        return false
    }

    static func save(_ payload: BackendClothesPayload) async throws {
        _ = try await create(payload)
    }

    static func create(_ payload: BackendClothesPayload) async throws -> BackendClothesRow {
        guard let baseURL else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: baseURL.appending(path: "clothes"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try jsonEncoder.encode(payload)

        print("[BackendClothesService] Backend /clothes request started method=POST category=\(payload.mainCategory) status=\(payload.analysisStatus ?? "nil")")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown backend error"
            print("[BackendClothesService] Backend save failed status=\(httpResponse.statusCode) body=\(message)")
            throw URLError(.badServerResponse)
        }
        let row = try JSONDecoder().decode(BackendClothesRow.self, from: data)
        print("[BackendClothesService] Backend save succeeded id=\(row.id ?? "unknown") status=\(row.analysisStatus ?? "unknown")")
        return row
    }

    static func update(
        clothingID: String,
        payload: BackendClothesUpdatePayload
    ) async throws -> BackendClothesRow {
        guard let baseURL else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: baseURL.appending(path: "clothes/\(clothingID)"))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try jsonEncoder.encode(payload)

        print("[BackendClothesService] Backend /clothes request started method=PUT id=\(clothingID) status=\(payload.analysisStatus ?? "nil")")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown backend error"
            print("[BackendClothesService] Update failed id=\(clothingID) status=\(httpResponse.statusCode) body=\(message)")
            throw URLError(.badServerResponse)
        }
        let row = try JSONDecoder().decode(BackendClothesRow.self, from: data)
        print("[BackendClothesService] Update succeeded id=\(row.id ?? clothingID) status=\(row.analysisStatus ?? "unknown")")
        return row
    }

    static func fetchClothes() async throws -> [ClosetItem] {
        guard let baseURL,
              let userID = configuredUserID else {
            throw URLError(.badURL)
        }

        var components = URLComponents(url: baseURL.appending(path: "clothes"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "user_id", value: userID)]

        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown backend error"
            print("[BackendClothesService] Fetch failed status=\(httpResponse.statusCode) body=\(message)")
            throw URLError(.badServerResponse)
        }

        let rows = try JSONDecoder().decode([BackendClothesRow].self, from: data)
        print("[BackendClothesService] Fetch succeeded count=\(rows.count)")
        return rows.map { $0.closetItem }
    }
}

private func boolValue(_ value: String) -> Bool {
    switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "1", "true", "yes", "y", "on":
        return true
    default:
        return false
    }
}

struct BackendClothesRow: Decodable {
    let id: String?
    let mainCategory: String
    let subcategory: String?
    let mainColor: String?
    let season: [String]?
    let styleTags: [String]?
    let imageURL: String?
    let imagePath: String?
    let localImagePath: String?
    let confidence: Double?
    let analysisMethod: String?
    let analysisStatus: String?
    let analysisError: String?
    let analyzedAt: String?
    let needsAnalysis: Bool?
    let syncStatus: String?
    let memo: String?
    let createdAt: String?

    private enum CodingKeys: String, CodingKey {
        case id
        case mainCategory = "main_category"
        case subcategory
        case mainColor = "main_color"
        case season
        case styleTags = "style_tags"
        case imageURL = "image_url"
        case imagePath = "image_path"
        case localImagePath = "local_image_path"
        case confidence
        case analysisMethod = "analysis_method"
        case analysisStatus = "analysis_status"
        case analysisError = "analysis_error"
        case analyzedAt = "analyzed_at"
        case needsAnalysis = "needs_analysis"
        case syncStatus = "sync_status"
        case memo
        case createdAt = "created_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let stringID = try? container.decode(String.self, forKey: .id) {
            id = stringID
        } else if let intID = try? container.decode(Int.self, forKey: .id) {
            id = String(intID)
        } else {
            id = nil
        }
        mainCategory = try container.decode(String.self, forKey: .mainCategory)
        subcategory = try container.decodeIfPresent(String.self, forKey: .subcategory)
        mainColor = try container.decodeIfPresent(String.self, forKey: .mainColor)
        season = try container.decodeIfPresent([String].self, forKey: .season)
        styleTags = try container.decodeIfPresent([String].self, forKey: .styleTags)
        imageURL = try container.decodeIfPresent(String.self, forKey: .imageURL)
        imagePath = try container.decodeIfPresent(String.self, forKey: .imagePath)
        localImagePath = try container.decodeIfPresent(String.self, forKey: .localImagePath)
        confidence = try container.decodeIfPresent(Double.self, forKey: .confidence)
        analysisMethod = try container.decodeIfPresent(String.self, forKey: .analysisMethod)
        analysisStatus = try container.decodeIfPresent(String.self, forKey: .analysisStatus)
        analysisError = try container.decodeIfPresent(String.self, forKey: .analysisError)
        analyzedAt = try container.decodeIfPresent(String.self, forKey: .analyzedAt)
        needsAnalysis = try container.decodeIfPresent(Bool.self, forKey: .needsAnalysis)
        syncStatus = try container.decodeIfPresent(String.self, forKey: .syncStatus)
        memo = try container.decodeIfPresent(String.self, forKey: .memo)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
    }

    var closetItem: ClosetItem {
        let status = analysisStatus ?? "completed"
        let displayName = subcategory?.isEmpty == false ? subcategory! : mainCategory
        let displaySeason = status == "completed" ? seasonLabel(from: season ?? []) : analysisStatusLabel(status)
        return ClosetItem(
            name: displayName,
            category: mainCategory,
            subcategory: subcategory ?? "",
            color: mainColor ?? "#CCCCCC",
            season: displaySeason,
            styleTags: styleTags ?? [],
            confidence: confidence ?? 0,
            imageURL: imageURL,
            localImagePath: localImagePath ?? imagePath,
            imageData: Data(),
            timestamp: parseDate(createdAt) ?? Date(),
            remoteID: id,
            syncStatus: syncStatus ?? "synced",
            syncError: nil,
            analysisStatus: status,
            analysisError: analysisError,
            needsAnalysis: needsAnalysis ?? (status != "completed")
        )
    }

    private func seasonLabel(from values: [String]) -> String {
        if values.contains("fall") && values.contains("winter") {
            return "Fall/Winter"
        }
        if values.contains("spring") && values.contains("summer") {
            return "Spring/Summer"
        }
        if values.count >= 4 {
            return "All-Season"
        }
        return values.joined(separator: "/")
    }

    private func analysisStatusLabel(_ status: String) -> String {
        switch status {
        case "pending":
            return "분석 대기"
        case "failed":
            return "분석 실패"
        default:
            return "분석 필요"
        }
    }

    private func parseDate(_ value: String?) -> Date? {
        guard let value else { return nil }
        return ISO8601DateFormatter().date(from: value)
    }
}

private let jsonEncoder: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    return encoder
}()
