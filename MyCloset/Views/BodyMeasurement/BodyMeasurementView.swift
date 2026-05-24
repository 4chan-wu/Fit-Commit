import SwiftUI
import SwiftData

// 4. 결과 리포트 뷰 (새로운 뷰)
struct MeasurementResultView: View {
    // CRITICAL: ViewModel이 @Observable 매크로를 사용하므로 @ObservedObject 래퍼를 제거합니다.
    var viewModel: BodyMeasurementViewModel
    @Environment(\.dismiss) var dismiss
    @Environment(\.modelContext) private var modelContext

    // 스태거 애니메이션을 위한 상태 배열 (이미지, 수치 카드, 제안 카드, 버튼)
    @State private var appearStagger: [Bool] = [false, false, false, false]

    var body: some View {
        ZStack {
            // 애플 순정 앱처럼 깔끔한 시스템 배경색
            Color(uiColor: .systemGroupedBackground).ignoresSafeArea()

            ScrollView {
                VStack(spacing: 24) {
                    // 잡지 화보(Magazine) 스타일의 메인 사진 오버레이
                    if let image = viewModel.capturedImage {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(height: 400)
                            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                            .overlay {
                                ZStack(alignment: .bottomLeading) {
                                    LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .center, endPoint: .bottom)
                                    
                                    HStack(alignment: .bottom) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            Text(viewModel.bodyShape.uppercased())
                                                .font(.caption.weight(.black))
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 4)
                                                .background(.ultraThinMaterial)
                                                .cornerRadius(8)
                                                .environment(\.colorScheme, .dark)
                                            
                                            Text(String(format: "%.1f cm", viewModel.finalHeight * 100))
                                                .font(.system(size: 52, weight: .heavy, design: .rounded))
                                                .foregroundColor(.white)
                                        }
                                        Spacer()
                                        Image(systemName: "ruler.fill")
                                            .font(.title)
                                            .foregroundColor(.white.opacity(0.5))
                                    }
                                    .padding(20)
                                }
                            }
                            .padding(.horizontal)
                            .padding(.top, 20)
                            .opacity(appearStagger[0] ? 1 : 0)
                            .offset(y: appearStagger[0] ? 0 : 40)
                    }

                    // Main Metrics (Apple Health Card Style)
                    VStack(spacing: 0) {
                        HealthCardRow(icon: "arrow.left.and.right.square", label: "어깨 너비", value: String(format: "%.1f cm", viewModel.shoulderWidth * 100), showDivider: true)
                        HealthCardRow(icon: "point.topleft.down.curvedto.point.bottomright.up", label: "골반 너비", value: String(format: "%.1f cm", viewModel.hipWidth * 100), showDivider: true)
                        HealthCardRow(icon: "hand.raised.app", label: "팔 길이", value: String(format: "%.1f cm", viewModel.armLength * 100), showDivider: true)
                        HealthCardRow(icon: "figure.walk", label: "다리 길이", value: String(format: "%.1f cm", viewModel.legLength * 100), showDivider: false)
                    }
                    .background(Color(uiColor: .secondarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(.horizontal)
                    .opacity(appearStagger[1] ? 1 : 0)
                    .offset(y: appearStagger[1] ? 0 : 40)

                    // Fit Recommendation
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "sparkles")
                                .font(.headline)
                                .foregroundColor(.accentColor)
                            Text("스타일 제안")
                                .font(.headline)
                                .foregroundColor(.primary)
                        }
                        Text(viewModel.recommendedFit)
                            .font(.system(size: 15, weight: .regular))
                            .foregroundColor(.secondary)
                            .lineSpacing(5)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .shadow(color: Color.black.opacity(0.05), radius: 10, x: 0, y: 4)
                    .padding(.horizontal)
                    .opacity(appearStagger[2] ? 1 : 0)
                    .offset(y: appearStagger[2] ? 0 : 40)
                    
                    Spacer(minLength: 100) // 하단 닫기 버튼을 위한 여백
                }
            }
            
            // 바텀 액션 버튼 (오버레이)
            VStack {
                Spacer()
                HStack(spacing: 16) {
                    // 1. 다시 측정 (Lean Style)
                    Button(action: {
                        viewModel.reset()
                        dismiss()
                    }) {
                        Text("다시 측정")
                            .font(.headline).bold()
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.clear)
                            .foregroundColor(.accentColor)
                            .overlay(RoundedRectangle(cornerRadius: 15).stroke(Color.accentColor, lineWidth: 2))
                    }
                    
                    // 2. 저장 및 완료 (Accent Style)
                    Button(action: {
                        let success = viewModel.saveProfile(context: modelContext)
                        if success {
                            viewModel.reset()
                            dismiss() // 성공적으로 저장되면 부드럽게 창을 닫아 메인으로 이동
                        }
                    }) {
                        Text("저장 및 완료")
                            .font(.headline).bold()
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.accentColor)
                            .foregroundColor(.white)
                            .cornerRadius(15)
                    }
                }
                .padding(.horizontal, 30)
                .padding(.bottom, 40)
                .opacity(appearStagger[3] ? 1 : 0)
                .offset(y: appearStagger[3] ? 0 : 40)
            }
        }
        .accentColor(.blue)
        .onAppear {
            // 0.15초 간격으로 요소들이 아래에서 위로 올라오는 스태거(Stagger) 애니메이션
            for i in 0..<appearStagger.count {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.8, blendDuration: 0.5).delay(Double(i) * 0.15)) {
                    appearStagger[i] = true
                }
            }
        }
    }
}

// 애플 건강 앱 스타일의 카드 컴포넌트
struct HealthCardRow: View {
    let icon: String
    let label: String
    let value: String
    let showDivider: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(.accentColor)
                    .frame(width: 30)
                
                Text(label)
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Spacer()
                
                Text(value)
                    .font(.system(.title3, design: .rounded).weight(.bold))
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
            
            if showDivider {
                Divider()
                    .padding(.leading, 66)
            }
        }
    }
}

struct BodyMeasurementView: View {
    @State private var viewModel: BodyMeasurementViewModel?
    @State private var showResultView = false
    @State private var isARSessionActive = false
    @Environment(\.modelContext) private var modelContext
    @Environment(\.isFloatingTabBarVisible) private var isFloatingTabBarVisible

    var body: some View {
        Group {
            if let viewModel {
                measurementContent(viewModel: viewModel)
            } else {
                Color(uiColor: .systemBackground)
                    .ignoresSafeArea()
            }
        }
        .navigationTitle("신체 측정")
        .onAppear {
            setFloatingTabBarVisible(false)
            isARSessionActive = true
            if viewModel == nil {
                viewModel = BodyMeasurementViewModel()
            }
        }
        .onDisappear {
            isARSessionActive = false
            setFloatingTabBarVisible(true)
            viewModel?.cleanupSessionState()
            viewModel = nil
            showResultView = false
        }
    }

    private func measurementContent(viewModel: BodyMeasurementViewModel) -> some View {
        ZStack {
            if showResultView == false {
                ARBodyTrackingView(viewModel: viewModel, isSessionActive: isARSessionActive)
                    .ignoresSafeArea()
            } else {
                Color.black.ignoresSafeArea()
            }

            VStack {
                if viewModel.isPersonDetected {
                    Text(String(format: "%.1f cm", viewModel.currentHeight * 100))
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background {
                            Color.black.opacity(0.5)
                        }
                        .cornerRadius(20)
                        .transition(.opacity.animation(.easeInOut))
                }

                Spacer()

                if viewModel.measurementState == .analyzing {
                    ProgressView("신체 비율 분석 중...")
                        .padding()
                        .background(.ultraThinMaterial)
                        .cornerRadius(15)
                        .padding(.bottom, 40)
                } else {
                    Button(action: {
                        viewModel.captureAndAnalyze()
                    }) {
                        Text("측정 및 분석")
                            .font(.headline).bold()
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(15)
                    }
                    .disabled(viewModel.measurementState != .scanning)
                    .padding(.horizontal, 30)
                    .padding(.bottom, 40)
                    .opacity(viewModel.measurementState == .scanning ? 1 : 0)
                    .animation(.easeInOut, value: viewModel.measurementState)
                }
            }
            .padding(.top, 20)
        }
        .onChange(of: showResultView) { _, isPresented in
            if !isPresented {
                setFloatingTabBarVisible(false)
            }
        }
        .onChange(of: viewModel.measurementState) { oldValue, newValue in
            if newValue == .finished {
                showResultView = true
            }
        }
        .fullScreenCover(isPresented: $showResultView) {
            MeasurementResultView(viewModel: viewModel)
        }
    }

    private func setFloatingTabBarVisible(_ isVisible: Bool) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            isFloatingTabBarVisible.wrappedValue = isVisible
        }
    }
}
