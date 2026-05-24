import SwiftUI
import RealityKit

struct RoomPreviewView: View {
    let fileURL: URL
    
    var body: some View {
        UsdzModelView(url: fileURL)
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(fileURL.lastPathComponent)
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct UsdzModelView: UIViewRepresentable {
    let url: URL
    
    func makeUIView(context: Context) -> ARView {
        // AR 기능 없이 3D 모델만 렌더링하기 위해 .nonAR 모드 사용
        let arView = ARView(frame: .zero, cameraMode: .nonAR, automaticallyConfigureSession: false)
        
        // 카메라 셋업 (모델 전체가 잘 보이도록 약간 뒤쪽 및 위쪽에 배치)
        let cameraEntity = PerspectiveCamera()
        cameraEntity.camera.fieldOfViewInDegrees = 60
        let cameraAnchor = AnchorEntity(world: .zero)
        cameraAnchor.addChild(cameraEntity)
        cameraEntity.look(at: [0, 0, 0], from: [0, 3, 6], relativeTo: nil)
        arView.scene.addAnchor(cameraAnchor)
        
        // 환경광 및 조명 추가
        let lightEntity = DirectionalLight()
        lightEntity.light.color = .white
        lightEntity.light.intensity = 2000
        lightEntity.look(at: [0, 0, 0], from: [2, 4, 2], relativeTo: nil)
        cameraAnchor.addChild(lightEntity)
        
        // 모델 비동기 로드 및 제스처 추가
        do {
            let modelEntity = try Entity.loadModel(contentsOf: url)
            // 제스처 인식을 위해 충돌 영역 자동 생성
            modelEntity.generateCollisionShapes(recursive: true)
            
            let modelAnchor = AnchorEntity(world: .zero)
            modelAnchor.addChild(modelEntity)
            arView.scene.addAnchor(modelAnchor)
            
            // RealityKit 내장 제스처 (회전, 확대/축소) 적용
            arView.installGestures([.rotation, .scale], for: modelEntity)
        } catch {
            print("모델을 불러오는데 실패했습니다: \(error)")
        }
        
        return arView
    }
    
    func updateUIView(_ uiView: ARView, context: Context) {}
}