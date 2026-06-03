import Foundation
import SwiftData

@Model
final class ClosetItem {
    @Attribute(.unique) var id: UUID
    var name: String
    var category: String
    var subcategory: String
    var color: String
    var season: String
    var styleTags: [String]
    var confidence: Double
    var imageURL: String?
    var localImagePath: String?
    @Attribute(.externalStorage) var imageData: Data
    var timestamp: Date
    var remoteID: String?
    var syncStatus: String
    var syncError: String?
    var analysisStatus: String
    var analysisError: String?
    var needsAnalysis: Bool

    init(
        id: UUID = UUID(),
        name: String,
        category: String,
        subcategory: String = "",
        color: String,
        season: String,
        styleTags: [String] = [],
        confidence: Double = 0,
        imageURL: String? = nil,
        localImagePath: String? = nil,
        imageData: Data,
        timestamp: Date = Date(),
        remoteID: String? = nil,
        syncStatus: String = "local_only",
        syncError: String? = nil,
        analysisStatus: String = "completed",
        analysisError: String? = nil,
        needsAnalysis: Bool = false
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.subcategory = subcategory
        self.color = color
        self.season = season
        self.styleTags = styleTags
        self.confidence = confidence
        self.imageURL = imageURL
        self.localImagePath = localImagePath
        self.imageData = imageData
        self.timestamp = timestamp
        self.remoteID = remoteID
        self.syncStatus = syncStatus
        self.syncError = syncError
        self.analysisStatus = analysisStatus
        self.analysisError = analysisError
        self.needsAnalysis = needsAnalysis
    }
}
