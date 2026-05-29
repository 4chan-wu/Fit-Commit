import Foundation
import RoomPlan
import Observation
import ActivityKit

@Observable
final class SpaceScanViewModel {
    
    enum State: Equatable {
        case ready
        case scanning
        case processing
        case completed(URL)
        case error(String)
    }
    
    var state: State = .ready
    var savedFiles: [URL] = []
    var currentRoom: CapturedRoom? // 실시간 스캔 데이터를 담을 변수
    
    private var scanActivity: Activity<ScanActivityAttributes>?
    
    func startScan() {
        state = .scanning
        startLiveActivity()
    }
    
    func stopScan() {
        // 스캔 종료 상태로 변경하면, View(RoomScannerView)에서 이를 감지하고 session.stop()을 호출
        state = .processing
    }
    
    func reset() {
        state = .ready
    }

    func cleanupSessionState() {
        currentRoom = nil
        if state == .scanning || state == .processing {
            state = .ready
        }

        Task {
            await endLiveActivity(finalStep: "스캔 종료")
        }
    }
    
    func handleError(_ error: Error) {
        DispatchQueue.main.async {
            self.state = .error(error.localizedDescription)
        }
        Task {
            await endLiveActivity(finalStep: "오류 발생")
        }
    }
    
    func save(room: CapturedRoom) {
        Task {
            await MainActor.run { state = .processing }
            await updateLiveActivity(step: "3D 모델 생성 및 저장 중...", progress: 0.5)
            
            let fileManager = FileManager.default
            guard let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
                await MainActor.run { state = .error("Document 디렉토리를 찾을 수 없습니다.") }
                return
            }
            
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyyMMdd_HHmmss"
            let fileName = "RoomScan_\(formatter.string(from: Date())).usdz"
            let destinationURL = documentsDirectory.appendingPathComponent(fileName)
            
            do {
                // UI 프레임 드롭 방지를 위해 백그라운드 스레드에서 내보내기가 실행
                try room.export(to: destinationURL)
                await MainActor.run {
                    self.state = .completed(destinationURL)
                    self.fetchSavedFiles()
                }
                await endLiveActivity(finalStep: "저장 완료: \(fileName)")
            } catch {
                await MainActor.run { self.state = .error(error.localizedDescription) }
                await endLiveActivity(finalStep: "오류 발생")
            }
        }
    }
    
    func fetchSavedFiles() {
        let fileManager = FileManager.default
        guard let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        
        do {
            let urls = try fileManager.contentsOfDirectory(at: documentsDirectory, includingPropertiesForKeys: nil)
            self.savedFiles = urls.filter { $0.pathExtension == "usdz" }.sorted(by: { $0.lastPathComponent > $1.lastPathComponent })
        } catch {
            print("파일 목록을 불러오지 못했습니다: \(error)")
        }
    }
    
    // MARK: - Live Activity Logic
    private func startLiveActivity() {
        do {
            // 최신 ActivityContent API를 사용하여 타입을 명확히 정의
            let attributes = ScanActivityAttributes(scanStartTime: .now)
            let contentState = ScanActivityAttributes.ContentState(currentStep: "스캔 준비 완료", progress: 0.0)
            let activityContent = ActivityContent(state: contentState, staleDate: nil)
            scanActivity = try Activity<ScanActivityAttributes>.request(
                attributes: attributes, 
                content: activityContent,
                pushType: nil as PushType?
            )
        } catch {
            print("Scan Live Activity 시작 실패: \(error.localizedDescription)")
        }
    }
    
    private func updateLiveActivity(step: String, progress: Float) async {
        let contentState = ScanActivityAttributes.ContentState(currentStep: step, progress: progress)
        let activityContent = ActivityContent(state: contentState, staleDate: nil)
        await scanActivity?.update(activityContent)
    }
    
    private func endLiveActivity(finalStep: String) async {
        let finalState = ScanActivityAttributes.ContentState(currentStep: finalStep, progress: 1.0)
        let finalContent = ActivityContent(state: finalState, staleDate: nil)
        await scanActivity?.end(finalContent, dismissalPolicy: .after(Date.now + 15)) // 15초 후 자동 해제
        scanActivity = nil
    }
}
