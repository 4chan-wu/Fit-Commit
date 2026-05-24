import Foundation
import SwiftData
import UIKit

@Model
final class UserProfile {
    @Attribute(.unique) var id: UUID
    var realName: String
    var nickname: String
    var height: Double
    var weight: Double
    var preferredStyle: String

    @Attribute(.externalStorage) var imageData: Data?

    init(
        id: UUID = UUID(),
        realName: String = "",
        nickname: String = "",
        height: Double = 0,
        weight: Double = 0,
        preferredStyle: String = "",
        image: UIImage? = nil
    ) {
        self.id = id
        self.realName = realName
        self.nickname = nickname
        self.height = height
        self.weight = weight
        self.preferredStyle = preferredStyle
        self.imageData = image?.jpegData(compressionQuality: 0.82)
    }

    func updateImage(_ image: UIImage?) {
        imageData = image?.jpegData(compressionQuality: 0.82)
    }
}
