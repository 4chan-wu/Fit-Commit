import Foundation

struct WardrobePayload: Encodable {
    let userID: String?
    let name: String
    let description: String?
    let isDefault: Bool

    private enum CodingKeys: String, CodingKey {
        case userID = "user_id"
        case name
        case description
        case isDefault = "is_default"
    }
}

struct WardrobeUpdatePayload: Encodable {
    let name: String?
    let description: String?
    let isDefault: Bool?

    private enum CodingKeys: String, CodingKey {
        case name
        case description
        case isDefault = "is_default"
    }
}

enum WardrobeBackendService {
    static func fetchWardrobes() async throws -> [BackendWardrobeRow] {
        guard let baseURL = BackendClothesService.configuredBaseURL,
              let userID = BackendClothesService.configuredUserID else {
            throw URLError(.badURL)
        }

        var components = URLComponents(url: baseURL.appending(path: "wardrobes"), resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "select", value: "*"),
            URLQueryItem(name: "order", value: "is_default.desc,created_at.asc")
        ]
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        try await applyWardrobeAuthHeaders(to: &request)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("return=representation", forHTTPHeaderField: "Prefer")
        return try await send(request, response: [BackendWardrobeRow].self)
    }

    static func fetchDefaultWardrobe() async throws -> BackendWardrobeRow {
        try await BackendClothesService.fetchDefaultWardrobe()
    }

    static func create(name: String, description: String?, isDefault: Bool = false) async throws -> BackendWardrobeRow {
        guard let baseURL = BackendClothesService.configuredBaseURL,
              let userID = BackendClothesService.configuredUserID else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: baseURL.appending(path: "wardrobes"))
        request.httpMethod = "POST"
        try await applyWardrobeAuthHeaders(to: &request)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try wardrobeJSONEncoder.encode(
            WardrobePayload(
                userID: userID,
                name: name,
                description: description,
                isDefault: isDefault
            )
        )
        return try await sendSingle(request, response: BackendWardrobeRow.self)
    }

    static func update(
        wardrobeID: String,
        name: String?,
        description: String?,
        isDefault: Bool? = nil
    ) async throws -> BackendWardrobeRow {
        guard let baseURL = BackendClothesService.configuredBaseURL,
              let userID = BackendClothesService.configuredUserID else {
            throw URLError(.badURL)
        }

        var components = URLComponents(url: baseURL.appending(path: "wardrobes"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "id", value: "eq.\(wardrobeID)")]
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        try await applyWardrobeAuthHeaders(to: &request)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("return=representation", forHTTPHeaderField: "Prefer")
        request.httpBody = try wardrobeJSONEncoder.encode(
            WardrobeUpdatePayload(
                name: name,
                description: description,
                isDefault: isDefault
            )
        )
        return try await sendSingle(request, response: BackendWardrobeRow.self)
    }

    static func delete(wardrobeID: String) async throws {
        guard let baseURL = BackendClothesService.configuredBaseURL,
              let userID = BackendClothesService.configuredUserID else {
            throw URLError(.badURL)
        }

        var components = URLComponents(url: baseURL.appending(path: "wardrobes"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "id", value: "eq.\(wardrobeID)")]
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        try await applyWardrobeAuthHeaders(to: &request)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown wardrobe delete error"
            print("[WardrobeBackendService] Delete failed body=\(message)")
            throw URLError(.badServerResponse)
        }
    }

    private static func send<T: Decodable>(_ request: URLRequest, response type: T.Type) async throws -> T {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? "Unknown wardrobe error"
            print("[WardrobeBackendService] Request failed status=\(httpResponse.statusCode) body=\(message)")
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    private static func sendSingle<T: Decodable>(_ request: URLRequest, response type: T.Type) async throws -> T {
        let rows: [T] = try await send(request, response: [T].self)
        guard let first = rows.first else { throw URLError(.cannotParseResponse) }
        return first
    }
}

private func applyWardrobeAuthHeaders(to request: inout URLRequest) async throws {
    if let token = try await AuthenticationManager.shared.validAccessToken()
        ?? BackendClothesService.configuredAccessToken {
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }
    request.setValue(SupabaseConfiguration.anonKey, forHTTPHeaderField: "apikey")
}

private let wardrobeJSONEncoder: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    return encoder
}()
