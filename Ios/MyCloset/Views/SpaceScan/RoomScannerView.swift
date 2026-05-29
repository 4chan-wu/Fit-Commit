import SwiftUI
import RoomPlan

struct RoomScannerView: UIViewControllerRepresentable {
    var viewModel: SpaceScanViewModel
    var isSessionActive: Bool
    
    func makeUIViewController(context: Context) -> RoomScannerViewController {
        let viewController = RoomScannerViewController()
        context.coordinator.parent = self
        viewController.loadViewIfNeeded()
        viewController.update(viewModel: viewModel, isSessionActive: isSessionActive, coordinator: context.coordinator)
        return viewController
    }
    
    func updateUIViewController(_ uiViewController: RoomScannerViewController, context: Context) {
        context.coordinator.parent = self
        uiViewController.update(viewModel: viewModel, isSessionActive: isSessionActive, coordinator: context.coordinator)
    }

    static func dismantleUIViewController(_ uiViewController: RoomScannerViewController, coordinator: RoomScannerCoordinator) {
        uiViewController.cleanup(coordinator: coordinator)
    }
    
    func makeCoordinator() -> RoomScannerCoordinator {
        RoomScannerCoordinator(self)
    }
}

final class RoomScannerViewController: UIViewController {
    private var captureView: RoomCaptureView?

    override func loadView() {
        view = UIView()
        view.backgroundColor = .black
    }

    func update(viewModel: SpaceScanViewModel, isSessionActive: Bool, coordinator: RoomScannerCoordinator) {
        guard isViewLoaded else { return }

        coordinator.isActive = isSessionActive

        guard isSessionActive else {
            cleanup(coordinator: coordinator)
            return
        }

        // 뷰모델의 상태에 따라 세션을 강제로 시작하거나 종료합니다.
        switch viewModel.state {
        case .scanning:
            let captureView = ensureCaptureView(coordinator: coordinator)
            coordinator.startSession(in: captureView)
        case .processing:
            if let captureView {
                coordinator.stopSession(in: captureView, ignoreCallbacks: false)
            }
        default:
            cleanup(coordinator: coordinator)
        }
    }

    private func ensureCaptureView(coordinator: RoomScannerCoordinator) -> RoomCaptureView {
        if let captureView {
            captureView.delegate = coordinator
            captureView.captureSession.delegate = coordinator
            return captureView
        }

        let newCaptureView = RoomCaptureView(frame: view.bounds)
        newCaptureView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        // ViewDelegate: 스캔 완료 후 3D 데이터 처리를 담당
        newCaptureView.delegate = coordinator
        // SessionDelegate: 실시간 스캔 업데이트(진행 상황)를 담당
        newCaptureView.captureSession.delegate = coordinator
        view.addSubview(newCaptureView)
        captureView = newCaptureView
        return newCaptureView
    }

    func cleanup(coordinator: RoomScannerCoordinator) {
        guard let captureView else {
            coordinator.isActive = false
            coordinator.isScanning = false
            return
        }

        coordinator.stopSession(in: captureView, ignoreCallbacks: true)
        captureView.captureSession.delegate = nil
        captureView.delegate = nil
        captureView.removeFromSuperview()
        self.captureView = nil
    }
}

// MARK: - Coordinator (최상위 클래스 분리)
class RoomScannerCoordinator: NSObject, NSCoding, RoomCaptureViewDelegate, RoomCaptureSessionDelegate {
    var parent: RoomScannerView
    var isScanning: Bool = false
    var isActive: Bool = true
    
    init(_ parent: RoomScannerView) {
        self.parent = parent
        super.init() // NSObject 상속에 따른 초기화
    }
    
    // MARK: - NSCoding 프로토콜 준수를 위한 스텁(Stub) 구현
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    func encode(with coder: NSCoder) {
        // 직렬화 로직이 필요 없으므로 비워둡니다.
    }

    func startSession(in captureView: RoomCaptureView) {
        guard !isScanning else { return }

        isActive = true
        let configuration = RoomCaptureSession.Configuration()
        captureView.captureSession.run(configuration: configuration)
        isScanning = true
    }

    func stopSession(in captureView: RoomCaptureView, ignoreCallbacks: Bool) {
        if ignoreCallbacks {
            isActive = false
        }

        guard isScanning else { return }
        captureView.captureSession.stop()
        isScanning = false
    }
    
    // MARK: - RoomCaptureSessionDelegate (실시간 데이터 바인딩)
    func captureSession(_ session: RoomCaptureSession, didUpdate room: CapturedRoom) {
        guard isActive else { return }

        DispatchQueue.main.async {
            guard self.isActive else { return }
            self.parent.viewModel.currentRoom = room
        }
    }
    
    // MARK: - RoomCaptureViewDelegate (스캔 프로세스 완료 처리)
    func captureView(shouldPresent roomDataForProcessing: CapturedRoomData, error: Error?) -> Bool {
        guard isActive else { return false }
        return true // 최종 3D 모델(평면도 형태) 표시 허용
    }
    
    func captureView(didPresent processedResult: CapturedRoom, error: Error?) {
        guard isActive else { return }

        if let error = error {
            parent.viewModel.handleError(error)
            return
        }
        // 성공 시 스캔된 방 객체를 넘겨 USDZ 저장 로직 실행
        parent.viewModel.save(room: processedResult)
    }
}
