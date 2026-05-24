import Foundation
import SwiftData
import UIKit

@Model
final class BodyProfile {
    @Attribute(.unique) var id: UUID
    var date: Date
    var height: Float
    var shoulderWidth: Float
    var hipWidth: Float
    var armLength: Float
    var legLength: Float
    var recommendedFit: String
    var bodyShape: String
    
    @Attribute(.externalStorage) var imageData: Data?
    
    init(id: UUID = UUID(), date: Date = Date(), height: Float, shoulderWidth: Float, hipWidth: Float, armLength: Float, legLength: Float, recommendedFit: String, bodyShape: String, image: UIImage?) {
        self.id = id
        self.date = date
        self.height = height
        self.shoulderWidth = shoulderWidth
        self.hipWidth = hipWidth
        self.armLength = armLength
        self.legLength = legLength
        self.recommendedFit = recommendedFit
        self.bodyShape = bodyShape
        // 해상도를 최적화하여 압축 저장 (약 80% 품질)
        self.imageData = image?.jpegData(compressionQuality: 0.8)
    }
}