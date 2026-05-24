import Foundation
import ActivityKit

/// 1. 신체 측정 시나리오를 위한 Activity Attributes
struct MeasurementActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        // 다이나믹 아일랜드에 실시간으로 표시될 동적 데이터
        var coachingMessage: String
        var currentHeight: Float
        var isStable: Bool
    }

    // 정적 데이터 (Live Activity 시작 시 한 번만 설정)
    // 이 시나리오에서는 특별한 정적 데이터가 필요하지 않습니다.
}

/// 2. 3D 공간 스캔 시나리오를 위한 Activity Attributes
struct ScanActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        // 실시간 스캔 상태 및 진행률
        var currentStep: String
        var progress: Float
    }

    // 정적 데이터
    var scanStartTime: Date
}