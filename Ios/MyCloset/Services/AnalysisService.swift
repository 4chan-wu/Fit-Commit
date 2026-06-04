import Foundation
import SwiftUI
import UIKit

struct AnalysisResponse: Decodable, Equatable, Sendable {
    let status: String
    let analysis: AnalysisResult
}

struct AnalysisResult: Decodable, Equatable, Sendable {
    let mainCategory: String
    let subcategory: String
    let mainColor: String
    let season: String
    let styleTags: [String]
    let confidence: Double
    let method: String
    let imageURL: String?

    var category: String { mainCategory }
    var color: String { mainColor }

    var swiftUIColor: Color {
        Color(hexString: mainColor) ?? Color(uiColor: .secondarySystemBackground)
    }

    private enum CodingKeys: String, CodingKey {
        case mainCategory = "main_category"
        case subcategory
        case mainColor = "main_color"
        case season
        case styleTags = "style_tags"
        case confidence
        case method
        case imageURL = "image_url"

        case legacyCategory = "category"
        case legacyColor = "color"
        case legacyCategoryConfidence = "confidence_cat"
        case legacySeasonConfidence = "confidence_sea"
        case legacyImageRef = "image_ref"
    }

    init(
        mainCategory: String,
        subcategory: String,
        mainColor: String,
        season: String,
        styleTags: [String],
        confidence: Double,
        method: String = "clip",
        imageURL: String? = nil
    ) {
        self.mainCategory = mainCategory
        self.subcategory = subcategory
        self.mainColor = mainColor
        self.season = season
        self.styleTags = styleTags
        self.confidence = confidence
        self.method = method
        self.imageURL = imageURL
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        mainCategory = try container.decodeIfPresent(String.self, forKey: .mainCategory)
            ?? container.decodeIfPresent(String.self, forKey: .legacyCategory)
            ?? ""
        subcategory = try container.decodeIfPresent(String.self, forKey: .subcategory) ?? ""
        mainColor = try container.decodeIfPresent(String.self, forKey: .mainColor)
            ?? container.decodeIfPresent(String.self, forKey: .legacyColor)
            ?? ""
        season = try Self.decodeSeason(from: container)
        styleTags = try container.decodeIfPresent([String].self, forKey: .styleTags) ?? []
        confidence = try container.decodeIfPresent(Double.self, forKey: .confidence)
            ?? container.decodeIfPresent(Double.self, forKey: .legacyCategoryConfidence)
            ?? container.decodeIfPresent(Double.self, forKey: .legacySeasonConfidence)
            ?? 0
        method = try container.decodeIfPresent(String.self, forKey: .method) ?? "clip"
        imageURL = try container.decodeIfPresent(String.self, forKey: .imageURL)
            ?? container.decodeIfPresent(String.self, forKey: .legacyImageRef)
    }

    private static func decodeSeason(from container: KeyedDecodingContainer<CodingKeys>) throws -> String {
        if let seasons = try container.decodeIfPresent([String].self, forKey: .season) {
            if seasons.contains("fall") && seasons.contains("winter") {
                return "Fall/Winter"
            }
            if seasons.contains("spring") && seasons.contains("summer") {
                return "Spring/Summer"
            }
            if seasons.count >= 4 {
                return "All-Season"
            }
            return seasons.joined(separator: "/")
        }
        return try container.decodeIfPresent(String.self, forKey: .season) ?? ""
    }
}

enum AnalysisServiceError: LocalizedError {
    case invalidImageData
    case invalidResponse
    case server(statusCode: Int, message: String?)
    case decoding(Error)
    case network(URLError)
    case requestFailed(Error)
    case analysisFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidImageData:
            return "이미지를 업로드 가능한 JPEG 데이터로 변환하지 못했습니다."
        case .invalidResponse:
            return "서버 응답 형식이 올바르지 않습니다."
        case .server(let statusCode, let message):
            if statusCode == 404 {
                return "서버에서 /predict 경로를 찾지 못했습니다. 앱 URL, trailing slash, FastAPI 라우트(prefix)를 확인해주세요. \(message ?? "")"
            }
            if let message {
                return "서버 오류가 발생했습니다. (\(statusCode)) \(message)"
            }
            return "서버 오류가 발생했습니다. (\(statusCode))"
        case .decoding(let error):
            return "분석 결과를 해석하지 못했습니다: \(error.localizedDescription)"
        case .network(let error):
            return Self.networkErrorMessage(for: error)
        case .requestFailed(let error):
            return "네트워크 연결에 실패했습니다: \(error.localizedDescription)"
        case .analysisFailed(let status):
            return "이미지 분석에 실패했습니다. 상태: \(status)"
        }
    }

    private static func networkErrorMessage(for error: URLError) -> String {
        switch error.code {
        case .timedOut:
            return "서버 응답 시간이 초과되었습니다. 서버가 켜져 있는지, IP와 포트가 맞는지 확인해주세요."
        case .cannotConnectToHost:
            return "서버에 연결할 수 없습니다. 아이폰과 서버가 같은 네트워크에 있는지, 방화벽/포트 8000이 열려 있는지 확인해주세요."
        case .cannotFindHost:
            return "서버 주소를 찾을 수 없습니다. 입력한 IP 주소가 올바른지 확인해주세요."
        case .notConnectedToInternet:
            return "아이폰이 인터넷 또는 Wi-Fi에 연결되어 있지 않습니다."
        case .networkConnectionLost:
            return "요청 중 네트워크 연결이 끊어졌습니다. Wi-Fi 상태를 확인한 뒤 다시 시도해주세요."
        case .appTransportSecurityRequiresSecureConnection:
            return "iOS App Transport Security가 HTTP 연결을 차단했습니다. HTTPS를 쓰거나 Info.plist에 예외 설정을 추가해주세요."
        default:
            return "네트워크 오류가 발생했습니다: \(error.localizedDescription) (\(error.code.rawValue))"
        }
    }
}

final class AnalysisService {
    private enum Constants {
        static let requestTimeout: TimeInterval = 30
        static let watchdogTimeout: UInt64 = 35_000_000_000
    }
    

    private let endpointURL: URL
    private var session: URLSession
    private let decoder: JSONDecoder

    init(
        endpointURL: URL = URL(string: "http://169.254.32.10:8000/predict")!,
        session: URLSession = AnalysisService.makeDefaultSession(),
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.endpointURL = Self.normalizedPredictURL(endpointURL)
        self.session = session
        self.decoder = decoder

        if self.endpointURL.host == "127.0.0.1" || self.endpointURL.host == "localhost" {
            print("[AnalysisService][Warning] Physical iPhone cannot reach your Mac with localhost/127.0.0.1. Use the Mac's LAN IP instead.")
        }
        if self.endpointURL.scheme == "http" {
            print("[AnalysisService][Warning] Using HTTP. Ensure ATS allows this endpoint in Info.plist or use HTTPS.")
        }
    }

    func uploadImage(_ image: UIImage, categoryHint: String) async throws -> AnalysisResponse {
        print("[DEBUG][AnalysisService] uploadImage entered")
        print("[DEBUG][AnalysisService] Endpoint: \(endpointURL.absoluteString)")

        let imageData = await image.downsampled(maxPixelSize: 512)
            .jpegDataInBackground(compressionQuality: 0.82)
        let mimeType = "image/jpeg"
        let fileName = "clothing.jpg"

        guard let imageData else {
            print("[DEBUG][AnalysisService] Failed to encode JPEG data")
            throw AnalysisServiceError.invalidImageData
        }
        print("[DEBUG][AnalysisService] JPEG ready: \(imageData.count) bytes")

        let boundary = "Boundary-\(UUID().uuidString)"
        let body = makeMultipartBody(
            imageData: imageData,
            boundary: boundary,
            fieldName: "file",
            fileName: fileName,
            mimeType: mimeType,
            categoryHint: categoryHint
        )
        print("[DEBUG][AnalysisService] Multipart body ready: \(body.count) bytes, boundary=\(boundary)")

        var request = URLRequest(url: endpointURL)
        request.httpMethod = "POST"
        request.timeoutInterval = Constants.requestTimeout
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(String(body.count), forHTTPHeaderField: "Content-Length")
        request.setValue("close", forHTTPHeaderField: "Connection")
        request.httpBody = body

        print("[DEBUG][AnalysisService] Request prepared")
        print("[DEBUG][AnalysisService] Method: \(request.httpMethod ?? "nil")")
        print("[DEBUG][AnalysisService] Content-Type: \(request.value(forHTTPHeaderField: "Content-Type") ?? "nil")")
        print("[DEBUG][AnalysisService] Content-Length: \(request.value(forHTTPHeaderField: "Content-Length") ?? "nil")")
        print("[DEBUG][AnalysisService] Starting URLSession request")

        return try await upload(request: request, didResetSession: false)
    }

    private func upload(request: URLRequest, didResetSession: Bool) async throws -> AnalysisResponse {
        do {
            let startedAt = Date()
            let (data, response) = try await performRequestWithWatchdog(request)
            let elapsed = String(format: "%.2f", Date().timeIntervalSince(startedAt))
            print("[DEBUG][AnalysisService] URLSession completed in \(elapsed)s")

            if let httpResponse = response as? HTTPURLResponse {
                print("[DEBUG][AnalysisService] HTTP status: \(httpResponse.statusCode)")
            } else {
                print("[DEBUG][AnalysisService] Non-HTTP response: \(response)")
            }
            print("[DEBUG][AnalysisService] Response bytes: \(data.count)")

            try validateHTTPResponse(response, data: data)

            let analysis = try decoder.decode(AnalysisResponse.self, from: data)
            guard analysis.status == "success" else {
                print("[DEBUG][AnalysisService] Server returned non-success status: \(analysis.status)")
                throw AnalysisServiceError.analysisFailed(analysis.status)
            }
            print("[DEBUG][AnalysisService] Decode success: \(analysis.analysis.mainCategory), \(analysis.analysis.mainColor), \(analysis.analysis.season)")
            return analysis
        } catch let error as AnalysisServiceError {
            print("[DEBUG][AnalysisService] AnalysisServiceError: \(error.localizedDescription)")
            throw error
        } catch let error as DecodingError {
            print("[DEBUG][AnalysisService] DecodingError: \(error)")
            throw AnalysisServiceError.decoding(error)
        } catch let error as URLError {
            print("[DEBUG][AnalysisService] URLError: \(error.code.rawValue) \(error.localizedDescription)")
            if didResetSession == false, shouldResetSession(for: error) {
                print("[DEBUG][AnalysisService] Resetting URLSession and retrying once")
                await resetSession()
                return try await upload(request: request, didResetSession: true)
            }
            throw AnalysisServiceError.network(error)
        } catch {
            print("[DEBUG][AnalysisService] Request failed: \(error.localizedDescription)")
            throw AnalysisServiceError.requestFailed(error)
        }
    }

    private func performRequestWithWatchdog(_ request: URLRequest) async throws -> (Data, URLResponse) {
        try await withThrowingTaskGroup(of: (Data, URLResponse).self) { group in
            group.addTask { [session] in
                try await session.data(for: request)
            }

            group.addTask {
                try await Task.sleep(nanoseconds: Constants.watchdogTimeout)
                print("[DEBUG][AnalysisService] Watchdog timeout fired before URLSession returned")
                throw URLError(.timedOut)
            }

            guard let result = try await group.next() else {
                throw URLError(.unknown)
            }
            group.cancelAll()
            return result
        }
    }

    private func shouldResetSession(for error: URLError) -> Bool {
        switch error.code {
        case .networkConnectionLost,
             .cannotConnectToHost,
             .cannotFindHost,
             .timedOut,
             .dnsLookupFailed,
             .notConnectedToInternet:
            return true
        default:
            return false
        }
    }

    private func resetSession() async {
        print("[DEBUG][AnalysisService] URLSession reset begin")
        let oldSession = session
        await withCheckedContinuation { continuation in
            oldSession.reset {
                continuation.resume()
            }
        }
        oldSession.invalidateAndCancel()
        session = Self.makeDefaultSession()
        print("[DEBUG][AnalysisService] URLSession reset complete")
    }

    private func makeMultipartBody(
        imageData: Data,
        boundary: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        categoryHint: String
    ) -> Data {
        var body = Data()
        body.appendString("--\(boundary)\r\n")
        body.appendString("Content-Disposition: form-data; name=\"category_hint\"\r\n\r\n")
        body.appendString(categoryHint)
        body.appendString("\r\n")
        body.appendString("--\(boundary)\r\n")
        body.appendString("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(fileName)\"\r\n")
        body.appendString("Content-Type: \(mimeType)\r\n\r\n")
        body.append(imageData)
        body.appendString("\r\n")
        body.appendString("--\(boundary)--\r\n")
        return body
    }

    private func validateHTTPResponse(_ response: URLResponse, data: Data) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw AnalysisServiceError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            let serverMessage = try? decoder.decode(AnalysisErrorResponse.self, from: data).bestMessage
            throw AnalysisServiceError.server(
                statusCode: httpResponse.statusCode,
                message: serverMessage
            )
        }
    }

    private static func makeDefaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = Constants.requestTimeout
        configuration.timeoutIntervalForResource = 120
        configuration.waitsForConnectivity = true
        configuration.allowsCellularAccess = false
        configuration.allowsConstrainedNetworkAccess = true
        configuration.allowsExpensiveNetworkAccess = true
        configuration.httpMaximumConnectionsPerHost = 1
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.networkServiceType = .responsiveData
        return URLSession(configuration: configuration)
    }

    private static func normalizedPredictURL(_ url: URL) -> URL {
        let absoluteString = url.absoluteString
        if absoluteString.hasSuffix("/predict/") {
            let normalizedString = String(absoluteString.dropLast())
            return URL(string: normalizedString) ?? url
        }

        return url
    }
}

private struct AnalysisErrorResponse: Codable {
    let detail: String?
    let message: String?
    let error: String?

    var bestMessage: String? {
        detail ?? message ?? error
    }
}

extension Color {
    init?(hexString: String) {
        let trimmedHex = hexString
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")

        switch trimmedHex.lowercased() {
        case "black":
            self = .black
            return
        case "white":
            self = .white
            return
        case "gray", "grey":
            self = .gray
            return
        case "red":
            self = .red
            return
        case "orange":
            self = .orange
            return
        case "yellow":
            self = .yellow
            return
        case "green":
            self = .green
            return
        case "blue":
            self = .blue
            return
        case "navy":
            self = Color(red: 20 / 255, green: 35 / 255, blue: 90 / 255)
            return
        case "purple":
            self = .purple
            return
        case "pink":
            self = .pink
            return
        case "brown":
            self = .brown
            return
        case "beige":
            self = Color(red: 210 / 255, green: 190 / 255, blue: 150 / 255)
            return
        default:
            break
        }

        guard trimmedHex.count == 6,
              let value = UInt64(trimmedHex, radix: 16) else {
            return nil
        }

        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: 1
        )
    }
}

private extension Data {
    mutating func appendString(_ string: String) {
        append(Data(string.utf8))
    }
}

extension UIImage {
    func downsampled(maxPixelSize: CGFloat = 512) -> UIImage {
        let longestSide = max(size.width, size.height)
        guard longestSide > maxPixelSize, longestSide > 0 else {
            return normalizedForProcessing()
        }

        let scaleRatio = maxPixelSize / longestSide
        let targetSize = CGSize(
            width: max(1, floor(size.width * scaleRatio)),
            height: max(1, floor(size.height * scaleRatio))
        )

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = false

        return UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    func jpegDataInBackground(compressionQuality: CGFloat = 0.82) async -> Data? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: self.jpegData(compressionQuality: compressionQuality))
            }
        }
    }

    func imageDataForClosetStorageInBackground(compressionQuality: CGFloat = 0.82) async -> Data? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let imageData = self.pngData() ?? self.jpegData(compressionQuality: compressionQuality)
                continuation.resume(returning: imageData)
            }
        }
    }

    func downsampledInBackground(maxPixelSize: CGFloat = 512) async -> UIImage {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: self.downsampled(maxPixelSize: maxPixelSize))
            }
        }
    }

    func fitcommitDominantColorName() -> String {
        let sampleSize = CGSize(width: 1, height: 1)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true

        let image = UIGraphicsImageRenderer(size: sampleSize, format: format).image { _ in
            self.normalizedForProcessing().draw(in: CGRect(origin: .zero, size: sampleSize))
        }

        guard let cgImage = image.cgImage,
              let data = cgImage.dataProvider?.data,
              let bytes = CFDataGetBytePtr(data) else {
            return "gray"
        }

        let red = Double(bytes[0])
        let green = Double(bytes[1])
        let blue = Double(bytes[2])
        let candidates: [(name: String, rgb: (Double, Double, Double))] = [
            ("black", (20, 20, 20)),
            ("white", (245, 245, 245)),
            ("gray", (140, 140, 140)),
            ("navy", (20, 35, 90)),
            ("blue", (50, 100, 210)),
            ("brown", (120, 80, 50)),
            ("beige", (210, 190, 150)),
            ("red", (220, 40, 40)),
            ("pink", (230, 130, 170)),
            ("green", (50, 150, 80)),
            ("yellow", (230, 210, 50)),
            ("purple", (130, 80, 180)),
        ]

        return candidates.min { lhs, rhs in
            squaredDistance(red, green, blue, lhs.rgb) < squaredDistance(red, green, blue, rhs.rgb)
        }?.name ?? "gray"
    }

    private func squaredDistance(
        _ red: Double,
        _ green: Double,
        _ blue: Double,
        _ target: (Double, Double, Double)
    ) -> Double {
        pow(red - target.0, 2) + pow(green - target.1, 2) + pow(blue - target.2, 2)
    }

    private func normalizedForProcessing() -> UIImage {
        guard imageOrientation != .up else { return self }

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = false

        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
