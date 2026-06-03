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
    let confidence: Double?
    let analysisMethod: String?
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
        case confidence
        case analysisMethod = "analysis_method"
        case memo
    }
}

enum BackendClothesService {
    private static let defaultBackendBaseURL = "http://127.0.0.1:8000"
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

    static func save(_ payload: BackendClothesPayload) async throws {
        guard let baseURL else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: baseURL.appending(path: "clothes"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown backend error"
            print("[BackendClothesService] Save failed status=\(httpResponse.statusCode) body=\(message)")
            throw URLError(.badServerResponse)
        }
        print("[BackendClothesService] Save succeeded")
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

private struct BackendClothesRow: Decodable {
    let id: String?
    let mainCategory: String
    let subcategory: String?
    let mainColor: String?
    let season: [String]?
    let styleTags: [String]?
    let imageURL: String?
    let imagePath: String?
    let confidence: Double?
    let analysisMethod: String?
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
        case confidence
        case analysisMethod = "analysis_method"
        case memo
        case createdAt = "created_at"
    }

    var closetItem: ClosetItem {
        ClosetItem(
            name: subcategory?.isEmpty == false ? subcategory! : mainCategory,
            category: mainCategory,
            subcategory: subcategory ?? "",
            color: mainColor ?? "#CCCCCC",
            season: seasonLabel(from: season ?? []),
            styleTags: styleTags ?? [],
            confidence: confidence ?? 0,
            imageURL: imageURL,
            imageData: Data(),
            timestamp: parseDate(createdAt) ?? Date()
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

    private func parseDate(_ value: String?) -> Date? {
        guard let value else { return nil }
        return ISO8601DateFormatter().date(from: value)
    }
}
