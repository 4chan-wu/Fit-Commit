import Foundation
import Observation
import ARKit
import UIKit // For UIImage
import simd
import ActivityKit
import SwiftData

enum MeasurementState: Equatable {
    case idle
    case scanning
    case analyzing
    case finished
}

@MainActor @Observable
final class BodyMeasurementViewModel {
    // 측정 상태 관리
    var measurementState: MeasurementState = .idle

    // 실시간 측정값
    var currentHeight: Float = 0.0
    var isPersonDetected: Bool = false

    // 분석 결과값
    var capturedImage: UIImage?
    var finalHeight: Float = 0.0
    var shoulderWidth: Float = 0.0
    var hipWidth: Float = 0.0
    var armLength: Float = 0.0
    var legLength: Float = 0.0
    var recommendedFit: String = "분석 중..."
    var bodyShape: String = ""

    // AR 데이터
    private var latestBodyAnchor: ARBodyAnchor?
    var getCurrentPixelBuffer: (() -> CVPixelBuffer?)?
    
    // Live Activity
    private var measurementActivity: Activity<MeasurementActivityAttributes>?
    private var isLiveActivitySetupFailed: Bool = false
    
    // 성능 최적화 (스로틀링)
    private var lastUpdateTime = Date()

    // 정수리 끝부분 보정을 위한 오프셋 (약 6cm)
    private let headTopOffset: Float = 0.06

    func processBodyAnchor(_ anchor: ARBodyAnchor?) {
        // 분석 중이거나 완료 상태일 때는 AR 데이터 추적을 중단하여 안정성 확보
        guard measurementState == .idle || measurementState == .scanning else { return }

        // 성능 최적화: 초당 10번(0.1초 간격)만 계산 및 Live Activity 업데이트 수행
        let now = Date()
        guard now.timeIntervalSince(lastUpdateTime) >= 0.1 else { return }
        self.lastUpdateTime = now
        
        self.latestBodyAnchor = anchor
        guard let anchor = anchor else {
            self.isPersonDetected = false
            self.measurementState = .idle
            if self.measurementActivity != nil {
                self.endLiveActivity()
            }
            return
        }

        // 실시간 키 측정
        guard let headTransform = anchor.skeleton.modelTransform(for: .head),
              let leftFootTransform = anchor.skeleton.modelTransform(for: .leftFoot),
              let rightFootTransform = anchor.skeleton.modelTransform(for: .rightFoot) else {
            return
        }

        let headY = headTransform.columns.3.y
        let floorY = min(leftFootTransform.columns.3.y, rightFootTransform.columns.3.y)
        let height = abs(headY - floorY) + headTopOffset

        self.currentHeight = height
        self.isPersonDetected = true
        
        // 사람이 처음 감지되었을 때 가벼운 햅틱 피드백
        if self.measurementState == .idle {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            self.measurementState = .scanning
        }
        
        // Live Activity 시작 또는 업데이트
        let coachingMessage = self.getCoachingMessage(from: anchor.transform)
        self.updateLiveActivity(height: height, coachingMessage: coachingMessage.message, isStable: coachingMessage.isStable)
    }

    func captureAndAnalyze() {
        guard measurementState == .scanning, let pixelBuffer = getCurrentPixelBuffer?(), let anchor = latestBodyAnchor else { return }
        self.measurementState = .analyzing
        
        // 2. 신체 비율 계산
        let skeleton = anchor.skeleton
        func position(for joint: ARSkeleton.JointName) -> SIMD3<Float>? {
            guard let transform = skeleton.modelTransform(for: joint) else { return nil }
            return simd_make_float3(transform.columns.3)
        }

        guard let headPos = position(for: .head),
              let leftFootPos = position(for: .leftFoot),
              let rightFootPos = position(for: .rightFoot),
              let leftShoulderPos = position(for: ARSkeleton.JointName(rawValue: "left_shoulder_1_joint")),
              let rightShoulderPos = position(for: ARSkeleton.JointName(rawValue: "right_shoulder_1_joint")),
              let leftHandPos = position(for: .leftHand),
              let rightHandPos = position(for: .rightHand),
              let leftUpLegPos = position(for: ARSkeleton.JointName(rawValue: "left_upLeg_joint")),
              let rightUpLegPos = position(for: ARSkeleton.JointName(rawValue: "right_upLeg_joint")),
              let leftAnklePos = position(for: .leftFoot),
              let rightAnklePos = position(for: .rightFoot) else { return }

        // 최종 수치 확정
        self.finalHeight = abs(headPos.y - min(leftFootPos.y, rightFootPos.y)) + headTopOffset
        self.shoulderWidth = simd_distance(leftShoulderPos, rightShoulderPos)
        self.hipWidth = simd_distance(leftUpLegPos, rightUpLegPos)
        
        // 팔 길이: 어깨 관절에서 손목(hand) 관절까지의 거리
        let leftArm = simd_distance(leftShoulderPos, leftHandPos)
        let rightArm = simd_distance(rightShoulderPos, rightHandPos)
        self.armLength = (leftArm + rightArm) / 2
        
        // 다리 길이: 골반 하단(upLeg)에서 발목(foot)까지의 거리
        let leftLeg = simd_distance(leftUpLegPos, leftAnklePos)
        let rightLeg = simd_distance(rightUpLegPos, rightAnklePos)
        self.legLength = (leftLeg + rightLeg) / 2
        
        // 무거운 이미지 변환 작업을 메인 스레드에서 분리하여 렉 방지
        Task {
            let processedImage = await Task.detached(priority: .userInitiated) { () -> UIImage? in
                let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
                let context = CIContext(options: nil)
                guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { return nil }
                // ARFrame의 기본 방향은 가로이므로, 세로(right)로 회전 보정
                return UIImage(cgImage: cgImage, scale: 1.0, orientation: .right)
            }.value
            
            // 3. 추천 핏 로직 (위트 있는 멘트 추가)
            let analysis = self.generateFitRecommendation(
                height: self.finalHeight,
                shoulderWidth: self.shoulderWidth,
                hipWidth: self.hipWidth
            )
            
            // 4. 메인 스레드에서 UI 업데이트 및 상태 변경
            await MainActor.run {
                self.bodyShape = analysis.shape
                self.recommendedFit = analysis.fit
                self.capturedImage = processedImage
                self.measurementState = .finished
                
                // 분석 완료 시 성공 햅틱 피드백
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }
    
    func saveProfile(context: ModelContext) -> Bool {
        guard measurementState == .finished else { return false }
        
        let newProfile = BodyProfile(
            height: self.finalHeight,
            shoulderWidth: self.shoulderWidth,
            hipWidth: self.hipWidth,
            armLength: self.armLength,
            legLength: self.legLength,
            recommendedFit: self.recommendedFit,
            bodyShape: self.bodyShape,
            image: self.capturedImage
        )
        context.insert(newProfile)
        
        do {
            try context.save() // 확실한 물리적 커밋
            print("✅ SwiftData 저장 성공: \(newProfile.id)")
            return true
        } catch {
            print("❌ SwiftData 저장 실패: \(error)")
            return false
        }
    }
    
    func reset() {
        capturedImage = nil
        recommendedFit = "분석 중..."
        bodyShape = ""
        currentHeight = 0.0
        isPersonDetected = false
        latestBodyAnchor = nil
        measurementState = .idle
    }

    func cleanupSessionState() {
        getCurrentPixelBuffer = nil
        latestBodyAnchor = nil
        isPersonDetected = false
        currentHeight = 0.0

        if measurementState == .scanning || measurementState == .analyzing {
            measurementState = .idle
        }

        endLiveActivity()
    }
    
    private func generateFitRecommendation(height: Float, shoulderWidth: Float, hipWidth: Float) -> (shape: String, fit: String) {
        guard height > 0, hipWidth > 0 else { return ("측정 불가", "신체 비율 분석에 실패했습니다.") }

        let ratio = shoulderWidth / hipWidth
        var shape = ""
        var recommendation = ""

        // 어깨 vs 골반 비율을 통한 체형 분석 (Style Analysis)
        if ratio > 1.25 {
            shape = "역삼각형 (Inverted Triangle)"
            recommendation = "어깨가 넓고 골반이 좁은 역삼각형 체형입니다. 시선을 아래로 분산시키고 하체 볼륨을 살려주는 와이드 팬츠나 카고 팬츠 코디를 적극 추천합니다. 상의는 브이넥이나 깊게 파인 U넥을 매치하면 훨씬 세련되어 보입니다."
        } else if ratio < 0.95 {
            shape = "삼각형 (Triangle)"
            recommendation = "어깨보다 하체에 안정감이 있는 체형입니다. 상체에 볼륨감을 더해주는 크롭 자켓이나 어깨선이 드롭된 오버핏 셔츠를 매치하면 전체적인 밸런스가 완벽해집니다."
        } else {
            shape = "직사각형/스탠다드 (Rectangle)"
            recommendation = "상하체 밸런스가 균일한 스탠다드 체형입니다. 허리선을 강조하는 벨티드 자켓이나 크롭 기장의 상의를 활용해 비율을 더욱 좋아 보이게 연출해 보세요. 대부분의 레귤러 핏이 잘 어울립니다."
        }

        return (shape, "💡 스타일 분석 리포트\n\n" + recommendation)
    }
    
    // MARK: - Live Activity Logic
    
    private func getCoachingMessage(from transform: simd_float4x4) -> (message: String, isStable: Bool) {
        let distance = transform.columns.3.z
        if distance > -1.2 {
            return ("⚠️ 너무 가깝습니다", false)
        } else if distance < -2.5 { 
            return ("⚠️ 너무 멉니다", false)
        } else {
            return ("✅ 적정 거리입니다", true)
        }
    }
    
    private func updateLiveActivity(height: Float, coachingMessage: String, isStable: Bool) {
        // 에러 루프 방지
        guard !isLiveActivitySetupFailed else { return }
        
        // 1. 상태 객체 생성
        let initialState = MeasurementActivityAttributes.ContentState(
            coachingMessage: coachingMessage,
            currentHeight: height,
            isStable: isStable
        )
        
        let activityContent = ActivityContent(state: initialState, staleDate: nil)
        
        if measurementActivity == nil {
            do {
                measurementActivity = try Activity<MeasurementActivityAttributes>.request(
                    attributes: MeasurementActivityAttributes(),
                    content: activityContent,
                    pushType: .none
                )
            } catch {
                print("Live Activity 시작 실패: \(error.localizedDescription)")
                self.isLiveActivitySetupFailed = true
            }
        } else {
            Task {
                await measurementActivity?.update(activityContent)
            }
        }
    }
    
    private func endLiveActivity() {
        Task {
            let finalState = MeasurementActivityAttributes.ContentState(
                coachingMessage: "측정 종료",
                currentHeight: currentHeight,
                isStable: false
            )
            let finalContent = ActivityContent(state: finalState, staleDate: nil)
            await measurementActivity?.end(finalContent, dismissalPolicy: .immediate)
            measurementActivity = nil
        }
    }
}
