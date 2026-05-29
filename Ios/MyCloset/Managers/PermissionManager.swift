import AVFoundation
import ARKit
import Observation

@Observable
final class PermissionManager {
    static let shared = PermissionManager()
    
    var isCameraAuthorized: Bool = false
    var isLiDARSupported: Bool = false
    
    private init() {
        checkLiDARSupport()
    }
    
    func requestCameraPermission() async {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            await MainActor.run { self.isCameraAuthorized = true }
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            await MainActor.run { self.isCameraAuthorized = granted }
        default:
            await MainActor.run { self.isCameraAuthorized = false }
        }
    }
    
    private func checkLiDARSupport() {
        // Scene Reconstruction(.mesh) 기능을 지원하는지 확인하여 LiDAR 탑재 여부 판별
        if ARWorldTrackingConfiguration.supportsSceneReconstruction(.mesh) {
            isLiDARSupported = true
        } else {
            isLiDARSupported = false
        }
    }
}