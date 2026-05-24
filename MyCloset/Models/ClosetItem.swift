import Foundation
import SwiftData

@Model
final class ClosetItem {
    @Attribute(.unique) var id: UUID
    var name: String
    var category: String
    var color: String
    var season: String
    @Attribute(.externalStorage) var imageData: Data
    var timestamp: Date

    init(
        id: UUID = UUID(),
        name: String,
        category: String,
        color: String,
        season: String,
        imageData: Data,
        timestamp: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.color = color
        self.season = season
        self.imageData = imageData
        self.timestamp = timestamp
    }
}
