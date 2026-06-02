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
    @Attribute(.externalStorage) var imageData: Data
    var timestamp: Date

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
        imageData: Data,
        timestamp: Date = Date()
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
        self.imageData = imageData
        self.timestamp = timestamp
    }
}
