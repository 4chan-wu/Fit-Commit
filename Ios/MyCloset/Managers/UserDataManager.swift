import Foundation
import Observation
import UIKit

@Observable
final class UserDataManager {
    static let shared = UserDataManager()
    
    var savedHeight: Float = 0.0
    var savedShoulderWidth: Float = 0.0
    var savedArmLength: Float = 0.0
    var savedLegLength: Float = 0.0
    var recommendedFit: String = "분석 전"
    var lastMeasurementDate: Date? = nil
    var capturedImage: UIImage? = nil
    
    private init() {}
    
    /// 측정된 신체 데이터를 기기 메모리에 저장합니다.
    func saveBodyProfile(height: Float, shoulder: Float, arm: Float, leg: Float, fit: String, image: UIImage?) {
        self.savedHeight = height
        self.savedShoulderWidth = shoulder
        self.savedArmLength = arm
        self.savedLegLength = leg
        self.recommendedFit = fit
        self.capturedImage = image
        self.lastMeasurementDate = Date()
    }
}