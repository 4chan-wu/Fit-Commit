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
    private static var baseURL: URL? {
        if let value = ProcessInfo.processInfo.environment["FITCOMMIT_BACKEND_BASE_URL"],
           value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return URL(string: value)
        }
        if let value = Bundle.main.object(forInfoDictionaryKey: "FITCOMMIT_BACKEND_BASE_URL") as? String,
           value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return URL(string: value)
        }
        return nil
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
        return nil
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
}
