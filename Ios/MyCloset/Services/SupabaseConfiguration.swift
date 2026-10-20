import Foundation

enum SupabaseConfiguration {
    private static let defaultProjectURL = "https://iuplvspexcgvhonqgfag.supabase.co"

    static var projectURL: URL? {
        configuredURL(key: "FITCOMMIT_SUPABASE_URL") ?? URL(string: defaultProjectURL)
    }

    static var anonKey: String? {
        configuredString(key: "FITCOMMIT_SUPABASE_ANON_KEY")
    }

    static var clothingBucket: String {
        configuredString(key: "FITCOMMIT_SUPABASE_CLOTHING_BUCKET") ?? "clothing-images"
    }

    static var restURL: URL? { projectURL?.appending(path: "rest/v1") }
    static var authURL: URL? { projectURL?.appending(path: "auth/v1") }
    static var storageURL: URL? { projectURL?.appending(path: "storage/v1") }

    static var isConfigured: Bool {
        projectURL != nil && anonKey?.isEmpty == false
    }

    private static func configuredURL(key: String) -> URL? {
        configuredString(key: key).flatMap(URL.init(string:))
    }

    private static func configuredString(key: String) -> String? {
        if let value = ProcessInfo.processInfo.environment[key]?.trimmingCharacters(in: .whitespacesAndNewlines),
           !value.isEmpty {
            return value
        }
        if let value = Bundle.main.object(forInfoDictionaryKey: key) as? String {
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        return nil
    }
}
