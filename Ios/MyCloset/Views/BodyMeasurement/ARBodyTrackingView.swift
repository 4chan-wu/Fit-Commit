import SwiftUI
import ARKit
import RealityKit

struct ARBodyTrackingView: UIViewControllerRepresentable {
    var viewModel: BodyMeasurementViewModel
    var isSessionActive: Bool
    
    func makeUIViewController(context: Context) -> ARBodyTrackingViewController {
        let viewController = ARBodyTrackingViewController()
        viewController.viewModel = viewModel
        viewController.setSessionActive(isSessionActive)
        return viewController
    }
    
    func updateUIViewController(_ uiViewController: ARBodyTrackingViewController, context: Context) {
        uiViewController.viewModel = viewModel
        uiViewController.setSessionActive(isSessionActive)
    }

    static func dismantleUIViewController(_ uiViewController: ARBodyTrackingViewController, coordinator: ()) {
        uiViewController.cleanupSession()
    }
}

class ARBodyTrackingViewController: UIViewController, ARSessionDelegate {
    private var arView: ARView?
    private var shouldRunSession = false
    private var isSessionRunning = false
    var viewModel: BodyMeasurementViewModel?
    private var didBuildSkeletonScene = false

    // 1. 실시간 관절 시각화를 위한 엔티티들
    private let skeletonAnchor = AnchorEntity()
    private var jointEntities: [ARSkeleton.JointName: Entity] = [:]
    private var boneEntities: [String: Entity] = [:]

    // 추적할 관절 정의
    // CRITICAL FIX: 존재하지 않는 프로퍼티 접근 에러 해결을 위해 rawValue 명시적 사용
    private let targetJoints: [ARSkeleton.JointName] = [
        .head,
        ARSkeleton.JointName(rawValue: "neck_1_joint"),
        ARSkeleton.JointName(rawValue: "spine_7_joint"),
        ARSkeleton.JointName(rawValue: "spine_4_joint"),
        .root, .leftHand, .rightHand, .leftFoot, .rightFoot,
        ARSkeleton.JointName(rawValue: "left_shoulder_1_joint"),
        ARSkeleton.JointName(rawValue: "right_shoulder_1_joint"),
        ARSkeleton.JointName(rawValue: "left_forearm_joint"),
        ARSkeleton.JointName(rawValue: "right_forearm_joint"),
        ARSkeleton.JointName(rawValue: "left_upLeg_joint"),
        ARSkeleton.JointName(rawValue: "right_upLeg_joint"),
        ARSkeleton.JointName(rawValue: "left_leg_joint"),
        ARSkeleton.JointName(rawValue: "right_leg_joint")
    ]

    // 관절을 잇는 뼈대 정의 (마찬가지로 rawValue를 활용해 크래시 방지)
    private let boneConnections: [(from: ARSkeleton.JointName, to: ARSkeleton.JointName)] = [
        // 척추 및 목 라인 (Spine & Neck)
        (.root, ARSkeleton.JointName(rawValue: "spine_4_joint")),
        (ARSkeleton.JointName(rawValue: "spine_4_joint"), ARSkeleton.JointName(rawValue: "spine_7_joint")),
        (ARSkeleton.JointName(rawValue: "spine_7_joint"), ARSkeleton.JointName(rawValue: "neck_1_joint")),
        (ARSkeleton.JointName(rawValue: "neck_1_joint"), .head),
        
        // 어깨 연결 (Shoulders to Spine)
        (ARSkeleton.JointName(rawValue: "spine_7_joint"), ARSkeleton.JointName(rawValue: "left_shoulder_1_joint")),
        (ARSkeleton.JointName(rawValue: "spine_7_joint"), ARSkeleton.JointName(rawValue: "right_shoulder_1_joint")),
        
        // 왼팔 (Left Arm)
        (ARSkeleton.JointName(rawValue: "left_shoulder_1_joint"), ARSkeleton.JointName(rawValue: "left_forearm_joint")),
        (ARSkeleton.JointName(rawValue: "left_forearm_joint"), .leftHand),
        
        // 오른팔 (Right Arm)
        (ARSkeleton.JointName(rawValue: "right_shoulder_1_joint"), ARSkeleton.JointName(rawValue: "right_forearm_joint")),
        (ARSkeleton.JointName(rawValue: "right_forearm_joint"), .rightHand),
        
        // 골반 연결 (Hips to Root)
        (.root, ARSkeleton.JointName(rawValue: "left_upLeg_joint")),
        (.root, ARSkeleton.JointName(rawValue: "right_upLeg_joint")),
        
        // 왼다리 (Left Leg)
        (ARSkeleton.JointName(rawValue: "left_upLeg_joint"), ARSkeleton.JointName(rawValue: "left_leg_joint")),
        (ARSkeleton.JointName(rawValue: "left_leg_joint"), .leftFoot),
        
        // 오른다리 (Right Leg)
        (ARSkeleton.JointName(rawValue: "right_upLeg_joint"), ARSkeleton.JointName(rawValue: "right_leg_joint")),
        (ARSkeleton.JointName(rawValue: "right_leg_joint"), .rightFoot)
    ]

    override func loadView() {
        view = UIView()
        view.backgroundColor = .black
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setSessionActive(shouldRunSession)
    }

    private func buildSkeletonSceneIfNeeded() {
        guard !didBuildSkeletonScene, let arView else { return }

        arView.scene.addAnchor(skeletonAnchor)

        // 관절 포인트(Sphere) 엔티티 생성
        let jointRadius: Float = 0.03 // 기존 0.01에서 3배 증가
        let dotRadius: Float = 0.005 // 중심 도트 크기

        for joint in targetJoints {
            let jointColor = getColorForJoint(joint)
            
            // 1. 외곽의 반투명 글로우 스피어
            var outerMaterial = PhysicallyBasedMaterial()
            outerMaterial.baseColor = .init(tint: jointColor.withAlphaComponent(0.4))
            outerMaterial.emissiveColor = .init(color: jointColor.withAlphaComponent(0.6))
            outerMaterial.emissiveIntensity = 5
            let outerSphere = ModelEntity(mesh: .generateSphere(radius: jointRadius), materials: [outerMaterial])
            
            // 2. 중심의 작은 도트
            var innerMaterial = PhysicallyBasedMaterial()
            innerMaterial.baseColor = .init(tint: jointColor)
            let innerDot = ModelEntity(mesh: .generateSphere(radius: dotRadius), materials: [innerMaterial])
            
            outerSphere.addChild(innerDot) // 도트를 스피어의 자식으로 추가
            jointEntities[joint] = outerSphere
            skeletonAnchor.addChild(outerSphere)
        }

        // 뼈대(Line) 엔티티 생성
        let boneThickness: Float = 0.015 // 두툼하고 세련된 실린더 두께
        for connection in boneConnections {
            let boneName = "\(connection.from.rawValue)-\(connection.to.rawValue)"
            let boneColor = getColorForBone(connection.from, connection.to)
            
            var boneMaterial = PhysicallyBasedMaterial()
            boneMaterial.baseColor = .init(tint: boneColor.withAlphaComponent(0.8))
            boneMaterial.emissiveColor = .init(color: boneColor)
            boneMaterial.emissiveIntensity = 4 // 네온 뼈대 글로우 효과 강화
            
            // 완벽한 원통형(Cylinder) 메쉬 생성 (기본 Y축 기준 생성)
            let cylinderMesh = MeshResource.generateCylinder(height: 1, radius: boneThickness / 2)
            let boneVisual = ModelEntity(mesh: cylinderMesh, materials: [boneMaterial])
            
            // Y축 실린더를 Z축 방향으로 90도 회전시킴 (-Z 방향으로 뻗게 됨)
            boneVisual.transform.rotation = simd_quatf(angle: .pi / 2, axis: [1, 0, 0])
            
            // 방향과 길이를 동적으로 조절할 부모 컨테이너 생성
            let boneContainer = Entity()
            boneContainer.addChild(boneVisual)
            
            boneEntities[boneName] = boneContainer
            skeletonAnchor.addChild(boneContainer)
        }

        didBuildSkeletonScene = true
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        setSessionActive(shouldRunSession)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopSession(releaseCameraFeed: true)
    }

    deinit {
        cleanupSession()
    }

    func setSessionActive(_ isActive: Bool) {
        shouldRunSession = isActive

        guard isViewLoaded else { return }

        if isActive {
            startSessionIfNeeded()
        } else {
            stopSession(releaseCameraFeed: true)
        }
    }

    private func startSessionIfNeeded() {
        guard isSessionRunning == false else { return }
        guard ARBodyTrackingConfiguration.isSupported else {
            print("이 기기는 3D Body Tracking을 지원하지 않습니다.")
            return
        }

        let arView = ensureARView()
        let configuration = ARBodyTrackingConfiguration()
        configuration.frameSemantics.insert(.bodyDetection) // 신체 감지 활성화
        arView.session.delegate = self
        viewModel?.getCurrentPixelBuffer = { [weak self] in self?.arView?.session.currentFrame?.capturedImage }
        arView.session.run(configuration)
        isSessionRunning = true
    }

    private func ensureARView() -> ARView {
        if let arView {
            return arView
        }

        let newARView = ARView(frame: view.bounds)
        newARView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(newARView)
        arView = newARView
        didBuildSkeletonScene = false
        buildSkeletonSceneIfNeeded()
        return newARView
    }

    func stopSession(releaseCameraFeed: Bool) {
        guard isViewLoaded, let arView else { return }

        arView.session.pause()
        isSessionRunning = false
        arView.session.delegate = nil
        skeletonAnchor.isEnabled = false
        viewModel?.getCurrentPixelBuffer = nil
        viewModel?.processBodyAnchor(nil)

        if releaseCameraFeed {
            arView.scene.anchors.removeAll()
            skeletonAnchor.children.removeAll()
            jointEntities.removeAll()
            boneEntities.removeAll()
            didBuildSkeletonScene = false
            arView.removeFromSuperview()
            self.arView = nil
        }
    }

    func cleanupSession() {
        stopSession(releaseCameraFeed: true)
    }

    func session(_ session: ARSession, didUpdate anchors: [ARAnchor]) {
        if let bodyAnchor = anchors.compactMap({ $0 as? ARBodyAnchor }).first {
            // ViewModel에 최신 BodyAnchor 전달
            viewModel?.processBodyAnchor(bodyAnchor)

            skeletonAnchor.transform = Transform(matrix: bodyAnchor.transform)

            // 관절 위치 업데이트
            for (jointName, jointEntity) in jointEntities {
                if let jointTransform = bodyAnchor.skeleton.modelTransform(for: jointName) {
                    jointEntity.transform = Transform(matrix: jointTransform)
                }
            }

            // 뼈대 위치 및 방향 업데이트
            for connection in boneConnections {
                let boneName = "\(connection.from.rawValue)-\(connection.to.rawValue)"
                guard let boneContainer = boneEntities[boneName],
                      let fromTransform = bodyAnchor.skeleton.modelTransform(for: connection.from),
                      let toTransform = bodyAnchor.skeleton.modelTransform(for: connection.to) else { continue }

                let fromPosition = simd_make_float3(fromTransform.columns.3)
                let toPosition = simd_make_float3(toTransform.columns.3)

                let centerPosition = (fromPosition + toPosition) / 2
                let distance = simd_distance(fromPosition, toPosition)
                
                // 두 관절의 정중앙에 배치
                boneContainer.position = centerPosition
                // 컨테이너가 목표 관절을 향하도록 회전 (-Z축이 목표를 바라보게 됨)
                boneContainer.look(at: toPosition, from: centerPosition, upVector: [0, 1, 0], relativeTo: skeletonAnchor)
                // Z축 방향(길이)으로만 스케일을 조절하여 두 관절 사이를 완벽히 연결
                boneContainer.scale = [1, 1, distance]
            }
            skeletonAnchor.isEnabled = true
        } else {
            // 사람이 감지되지 않으면 스켈레톤 숨김
            skeletonAnchor.isEnabled = false
            viewModel?.processBodyAnchor(nil) // ViewModel에도 사람이 없음을 알림
        }
    }
}

// MARK: - 스켈레톤 시각화 헬퍼 (부위별 색상 및 그룹화)
extension ARBodyTrackingViewController {
    // 관절을 논리적인 신체 부위 그룹으로 분류
    enum LimbGroup {
        case leftArm, rightArm, leftLeg, rightLeg, torso, head, unknown
    }

    // 특정 관절이 어느 신체 부위 그룹에 속하는지 반환
    func getLimbGroup(for joint: ARSkeleton.JointName) -> LimbGroup {
        switch joint {
        case .leftHand, ARSkeleton.JointName(rawValue: "left_forearm_joint"), ARSkeleton.JointName(rawValue: "left_shoulder_1_joint"):
            return .leftArm
        case .rightHand, ARSkeleton.JointName(rawValue: "right_forearm_joint"), ARSkeleton.JointName(rawValue: "right_shoulder_1_joint"):
            return .rightArm
        case .leftFoot, ARSkeleton.JointName(rawValue: "left_leg_joint"), ARSkeleton.JointName(rawValue: "left_upLeg_joint"):
            return .leftLeg
        case .rightFoot, ARSkeleton.JointName(rawValue: "right_leg_joint"), ARSkeleton.JointName(rawValue: "right_upLeg_joint"):
            return .rightLeg
        case .root,
             ARSkeleton.JointName(rawValue: "spine_4_joint"),
             ARSkeleton.JointName(rawValue: "spine_7_joint"),
             ARSkeleton.JointName(rawValue: "neck_1_joint"):
            return .torso
        case .head:
            return .head
        default:
            return .unknown
        }
    }

    // 관절에 해당하는 색상을 반환합니다. (SF Pro 팔레트의 네온 톤)
    func getColorForJoint(_ joint: ARSkeleton.JointName) -> UIColor {
        switch getLimbGroup(for: joint) {
        case .leftArm: return .systemBlue // 왼쪽 팔: 파란색
        case .rightArm: return .systemGreen // 오른쪽 팔: 초록색
        case .leftLeg, .rightLeg: return .magenta // 다리: 마젠타색
        case .torso, .head: return .systemYellow // 몸통 및 머리: 노란색
        case .unknown: return .white // 알 수 없는 관절: 흰색 (기본값)
        }
    }

    // 뼈대(라인)에 해당하는 색상을 반환합니다. (연결된 관절 그룹 기반)
    func getColorForBone(_ fromJoint: ARSkeleton.JointName, _ toJoint: ARSkeleton.JointName) -> UIColor {
        let fromGroup = getLimbGroup(for: fromJoint)
        let toGroup = getLimbGroup(for: toJoint)

        // 두 관절이 같은 팔/다리 그룹에 속하면 해당 팔/다리 색상 사용
        if fromGroup == toGroup {
            return getColorForJoint(fromJoint)
        }

        // 몸통과 팔/다리 연결 시 해당 팔/다리 색상 사용
        if fromGroup == .torso { return getColorForJoint(toJoint) }
        if toGroup == .torso { return getColorForJoint(fromJoint) }
        
        // 머리와 몸통 연결, 또는 어깨-어깨 연결 시 몸통 색상 사용
        if (fromGroup == .head && toGroup == .torso) || (toGroup == .head && fromGroup == .torso) ||
           (fromGroup == .leftArm && toGroup == .rightArm) || (fromGroup == .rightArm && toGroup == .leftArm) { return .systemYellow }

        return .white // 기본값
    }
}
