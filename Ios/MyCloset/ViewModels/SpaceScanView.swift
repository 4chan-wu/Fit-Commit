import SwiftUI
import RoomPlan

struct SpaceScanView: View {
    @State private var viewModel = SpaceScanViewModel()
    @State private var isSupported = RoomCaptureSession.isSupported // LiDAR 센서 체크
    @State private var isRoomSessionActive = false
    @Environment(\.isFloatingTabBarVisible) private var isFloatingTabBarVisible
    
    var body: some View {
        ZStack {
            if isSupported {
                RoomScannerView(viewModel: viewModel, isSessionActive: isRoomSessionActive)
                    .ignoresSafeArea(edges: .all)
                
                VStack {
                    Spacer()
                    
                    // ViewModel의 상태에 따른 하단 UI 분기처리
                    switch viewModel.state {
                    case .ready:
                        // onAppear에서 자동 시작되므로 버튼 대신 안내 문구만 표시
                        Text("스캔 준비 중...")
                            .padding()
                            .background(.ultraThinMaterial)
                            .cornerRadius(10)
                            .padding(.bottom, 30)
                        
                    case .scanning:
                        Button("스캔 종료 및 저장") {
                            viewModel.stopScan()
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                        .padding(.bottom, 30)
                        
                    case .processing:
                        ProgressView("3D 모델 처리 및 저장 중...")
                            .padding()
                            .background(.ultraThinMaterial)
                            .cornerRadius(10)
                            .padding(.bottom, 30)
                        
                    case .completed(let url):
                        VStack(spacing: 12) {
                            Text("스캔 완료!").font(.headline)
                            Text("저장됨: \(url.lastPathComponent)").font(.caption2)
                            Button("새로운 스캔 시작하기") {
                                viewModel.reset()
                                viewModel.startScan()
                            }
                            .buttonStyle(.bordered)
                        }
                        .padding()
                        .background(.ultraThinMaterial)
                        .cornerRadius(10)
                        .padding(.bottom, 30)
                        
                    case .error(let message):
                        Text("오류 발생: \(message)")
                            .foregroundColor(.red)
                            .padding(.bottom, 30)
                    }
                }
            } else {
                // LiDAR 센서 미지원 기기 대응 화면
                VStack(spacing: 20) {
                    Image(systemName: "sensor.tag.radiowaves.forward.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.gray)
                    
                    Text("LiDAR 센서 미지원 기기")
                        .font(.title2.bold())
                    
                    Text("이 기능은 LiDAR 센서가 탑재된\nPro 기기(iPhone 12 Pro 이상)에서만 사용 가능합니다.")
                        .multilineTextAlignment(.center)
                        .foregroundColor(.secondary)
                }
                .padding()
            }
        }
        .navigationTitle("공간 스캔")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            setFloatingTabBarVisible(false)
            isRoomSessionActive = true

            if isSupported {
                viewModel.fetchSavedFiles()
                // 뷰가 나타나면 자동으로 스캔 세션 Run
                if viewModel.state == .ready {
                    viewModel.startScan()
                }
            }
        }
        .onDisappear {
            isRoomSessionActive = false
            setFloatingTabBarVisible(true)
            viewModel.cleanupSessionState()
        }
    }

    private func setFloatingTabBarVisible(_ isVisible: Bool) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            isFloatingTabBarVisible.wrappedValue = isVisible
        }
    }
}
