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
        components?.queryItems = [URLQueryItem(name: "user_id", value: userID)]
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        applyWardrobeAuthHeaders(to: &request)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
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
        applyWardrobeAuthHeaders(to: &request)
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
        return try await send(request, response: BackendWardrobeRow.self)
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

        var components = URLComponents(url: baseURL.appending(path: "wardrobes/\(wardrobeID)"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "user_id", value: userID)]
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        applyWardrobeAuthHeaders(to: &request)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try wardrobeJSONEncoder.encode(
            WardrobeUpdatePayload(
                name: name,
                description: description,
                isDefault: isDefault
            )
        )
        return try await send(request, response: BackendWardrobeRow.self)
    }

    static func delete(wardrobeID: String) async throws {
        guard let baseURL = BackendClothesService.configuredBaseURL,
              let userID = BackendClothesService.configuredUserID else {
            throw URLError(.badURL)
        }

        var components = URLComponents(url: baseURL.appending(path: "wardrobes/\(wardrobeID)"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "user_id", value: userID)]
        guard let url = components?.url else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        applyWardrobeAuthHeaders(to: &request)
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
}

private func applyWardrobeAuthHeaders(to request: inout URLRequest) {
    if let token = BackendClothesService.configuredAccessToken {
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }
    if let userID = BackendClothesService.configuredUserID {
        request.setValue(userID, forHTTPHeaderField: "X-Test-User-Id")
    }
}

private let wardrobeJSONEncoder: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    return encoder
}()
