import SwiftUI
import SwiftData
import PhotosUI
import UIKit

enum ClosetInitialFilter {
    case all
    case unworn
}

struct ClosetView: View {
    @Query(sort: \ClosetItem.timestamp, order: .reverse) private var items: [ClosetItem]
    @Environment(\.modelContext) private var modelContext
    @Environment(\.isFloatingTabBarVisible) private var rootTabBarVisible
    @State private var isTabBarVisible = true
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var activeSheet: ClosetActiveSheet?
    @State private var cameraErrorMessage: String?
    @State private var itemPendingDeletion: ClosetItem?
    @State private var navigationPath: [ClosetDestination] = []
    private let initialFilter: ClosetInitialFilter

    init(initialFilter: ClosetInitialFilter = .all) {
        self.initialFilter = initialFilter
    }

    var body: some View {
        NavigationStack(path: $navigationPath) {
            List {
                Section {
                    if displayedItems.isEmpty {
                        EmptyClosetPlaceholder(
                            onCameraTap: presentCamera,
                            onPhotoLibraryTap: presentPhotoLibrary
                        )
                        .listRowInsets(EdgeInsets(top: 18, leading: 20, bottom: 24, trailing: 20))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                    } else {
                        header
                            .listRowInsets(EdgeInsets(top: 18, leading: 20, bottom: 10, trailing: 20))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)

                        arFeatureHub
                            .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 10, trailing: 20))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)

                        closetInventoryHeader
                            .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 8, trailing: 20))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)

                        AddClothingListRow(
                            onCameraTap: presentCamera,
                            onPhotoLibraryTap: presentPhotoLibrary
                        )
                        .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 20))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)

                        closetItemsGrid
                            .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 18, trailing: 20))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Color(uiColor: .systemBackground))
            .navigationTitle("내 옷장")
            .navigationBarTitleDisplayMode(.large)
            .environment(\.isFloatingTabBarVisible, bridgedTabBarVisibility)
            .navigationDestination(for: ClosetDestination.self) { destination in
                switch destination {
                case .spaceScan:
                    SpaceScanView()
                        .navigationBarBackButtonHidden(true)
                        .toolbar {
                            ToolbarItem(placement: .topBarLeading) {
                                Button {
                                    navigationPath.removeAll()
                                } label: {
                                    Label("내 옷장", systemImage: "chevron.left")
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .onAppear {
                            setTabBarVisible(false)
                        }
                        .onDisappear {
                            setTabBarVisible(true)
                        }
                case .bodyMeasurement:
                    BodyMeasurementView()
                        .navigationBarBackButtonHidden(true)
                        .toolbar {
                            ToolbarItem(placement: .topBarLeading) {
                                Button {
                                    navigationPath.removeAll()
                                } label: {
                                    Label("내 옷장", systemImage: "chevron.left")
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .onAppear {
                            setTabBarVisible(false)
                        }
                        .onDisappear {
                            setTabBarVisible(true)
                        }
                }
            }
            .photosPicker(
                isPresented: $isPhotoPickerPresented,
                selection: $selectedPhotoItem,
                matching: .images
            )
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .camera:
                    CameraImagePicker { image in
                        activeSheet = nil
                        prepareAndPresentUploadSheet(with: image)
                    }
                    .ignoresSafeArea()
                case .upload(let uploadImage):
                    ClothesUploadView(image: uploadImage.image)
                case .detail(let item):
                    ClothingDetailView(item: item)
                }
            }
            .alert("의류를 삭제할까요?", isPresented: deleteConfirmationBinding) {
                Button("삭제하기", role: .destructive) {
                    deletePendingItem()
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text("삭제한 의류는 복구할 수 없습니다.")
            }
            .alert("카메라를 사용할 수 없습니다", isPresented: cameraErrorBinding) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(cameraErrorMessage ?? "")
            }
            .onAppear {
                setTabBarVisible(true)
            }
            .onChange(of: isTabBarVisible) { _, isVisible in
                rootTabBarVisible.wrappedValue = isVisible
            }
            .onChange(of: selectedPhotoItem) { _, item in
                loadPhotoPickerItem(item)
            }
        }
    }

    @State private var isPhotoPickerPresented = false

    private var bridgedTabBarVisibility: Binding<Bool> {
        Binding(
            get: { isTabBarVisible },
            set: { isVisible in
                setTabBarVisible(isVisible)
            }
        )
    }

    private var cameraErrorBinding: Binding<Bool> {
        Binding(
            get: { cameraErrorMessage != nil },
            set: { isPresented in
                if isPresented == false {
                    cameraErrorMessage = nil
                }
            }
        )
    }

    private var deleteConfirmationBinding: Binding<Bool> {
        Binding(
            get: { itemPendingDeletion != nil },
            set: { isPresented in
                if isPresented == false {
                    itemPendingDeletion = nil
                }
            }
        )
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("공간과 신체 데이터를 기반으로")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)

            Text("오늘의 핏을 더 정확하게 준비해요.")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var arFeatureHub: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("나의 공간 & 신체")
                .font(.headline)
                .foregroundStyle(.primary)

            VStack(spacing: 12) {
                Button {
                    navigationPath = [.spaceScan]
                } label: {
                    ClosetFeatureCard(
                        iconName: "cube.transparent",
                        title: "3D 공간 스캔 (Beta)",
                        subtitle: "방 구조를 스캔해 가상 피팅 공간을 준비합니다.",
                        tint: .secondary
                    )
                }
                .buttonStyle(.plain)

                Button {
                    navigationPath = [.bodyMeasurement]
                } label: {
                    ClosetFeatureCard(
                        iconName: "figure.stand",
                        title: "신체 비율 측정",
                        subtitle: "키와 어깨 비율을 측정해 코디 추천 정확도를 높입니다.",
                        tint: .secondary
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var closetInventoryHeader: some View {
        HStack {
            Text(initialFilter == .unworn ? "안 입는 옷" : "스캔된 옷")
                .font(.headline)
                .foregroundStyle(.primary)

            Spacer()

            Text("\(displayedItems.count)개")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .fitcommitCardStyle()
    }

    private var closetItemsGrid: some View {
        LazyVGrid(
            columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12),
            ],
            spacing: 12
        ) {
            ForEach(displayedItems) { item in
                ClosetItemGridCard(item: item)
                    .contentShape(RoundedRectangle(cornerRadius: ClosetGridCardMetrics.cornerRadius, style: .continuous))
                    .onTapGesture {
                        activeSheet = .detail(item)
                    }
                    .contextMenu {
                        Button {
                            activeSheet = .detail(item)
                        } label: {
                            Label("수정하기", systemImage: "pencil")
                        }

                        Button(role: .destructive) {
                            itemPendingDeletion = item
                        } label: {
                            Label("삭제하기", systemImage: "trash")
                        }
                    }
            }
        }
    }

    private var displayedItems: [ClosetItem] {
        switch initialFilter {
        case .all:
            return items
        case .unworn:
            let cutoffDate = Calendar.current.date(byAdding: .month, value: -3, to: Date()) ?? Date()
            return items.filter { $0.timestamp < cutoffDate }
        }
    }

    private func setTabBarVisible(_ isVisible: Bool) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            isTabBarVisible = isVisible
            rootTabBarVisible.wrappedValue = isVisible
        }
    }

    private func presentCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            cameraErrorMessage = "현재 기기에서는 사진 촬영 기능을 사용할 수 없습니다."
            return
        }

        activeSheet = .camera
    }

    private func presentPhotoLibrary() {
        selectedPhotoItem = nil
        activeSheet = nil
        isPhotoPickerPresented = true
    }

    private func loadPhotoPickerItem(_ item: PhotosPickerItem?) {
        guard let item else { return }

        Task {
            guard let data = try? await item.loadTransferable(type: Data.self),
                  let image = await Task.detached(priority: .userInitiated, operation: {
                      UIImage(data: data)
                  }).value else {
                return
            }

            await presentDownsampledUploadSheet(with: image)
        }
    }

    private func prepareAndPresentUploadSheet(with image: UIImage) {
        Task {
            await presentDownsampledUploadSheet(with: image)
        }
    }

    @MainActor
    private func presentDownsampledUploadSheet(with image: UIImage) async {
        let downsampledImage = await image.downsampledInBackground(maxPixelSize: 512)
        presentUploadSheet(with: downsampledImage)
    }

    private func presentUploadSheet(with image: UIImage) {
        let uploadImage = UploadImage(image: image)

        if activeSheet == nil {
            activeSheet = .upload(uploadImage)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                activeSheet = .upload(uploadImage)
            }
        }
    }

    private func deletePendingItem() {
        guard let itemPendingDeletion else { return }
        modelContext.delete(itemPendingDeletion)
        try? modelContext.save()
        self.itemPendingDeletion = nil
    }

}

private enum ClosetActiveSheet: Identifiable {
    case camera
    case upload(UploadImage)
    case detail(ClosetItem)

    var id: String {
        switch self {
        case .camera:
            return "camera"
        case .upload(let uploadImage):
            return uploadImage.id.uuidString
        case .detail(let item):
            return item.id.uuidString
        }
    }
}

private enum ClosetDestination: Hashable {
    case spaceScan
    case bodyMeasurement
}

private struct UploadImage {
    let id = UUID()
    let image: UIImage
}

private enum ClosetItemEditOptions {
    static let categories = [
        "T-shirt",
        "Shirt",
        "Hoodie",
        "Sweater",
        "Pants",
        "Skirt",
        "Dress",
        "Outerwear",
    ]

    static let seasons = [
        "Spring/Summer",
        "Fall/Winter",
        "All-Season",
    ]
}

private enum ClosetGridCardMetrics {
    static let mediaAspectRatio: CGFloat = 3.0 / 4.0
    static let mediaHeight: CGFloat = 180
    static let cardHeight: CGFloat = 274
    static let cornerRadius: CGFloat = 20
    static let metadataHeight: CGFloat = 52
}

private struct ClosetFeatureCard: View {
    let iconName: String
    let title: String
    let subtitle: String
    let tint: Color

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.primary.opacity(0.06))
                    .frame(width: 56, height: 56)

                Image(systemName: iconName)
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: 24, weight: .light))
                    .foregroundStyle(tint)
            }

            VStack(alignment: .leading, spacing: 5) {
                Text(title)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)

                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color(uiColor: .tertiaryLabel))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle(borderOpacity: 0.07)
    }
}

private struct EmptyClosetPlaceholder: View {
    let onCameraTap: () -> Void
    let onPhotoLibraryTap: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.primary.opacity(0.06))
                    .frame(width: 78, height: 78)

                Image(systemName: "hanger")
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: 34, weight: .light))
                    .foregroundStyle(.secondary)
            }

            VStack(spacing: 7) {
                Text("스캔 된 옷이 없습니다")
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)

                Text("사진을 추가하면 배경 제거와 AI 분석을 거쳐 옷장에 정리됩니다.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Menu {
                Button {
                    onCameraTap()
                } label: {
                    Label("사진 촬영하기", systemImage: "camera")
                }

                Button {
                    onPhotoLibraryTap()
                } label: {
                    Label("갤러리에서 선택하기", systemImage: "photo.on.rectangle")
                }
            } label: {
                Label("의류 등록하기", systemImage: "plus.circle.fill")
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)
                    .frame(maxWidth: 240)
                    .frame(height: 48)
                    .foregroundStyle(.white)
                    .background(Color.primary, in: Capsule())
                    .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 7)
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
            .accessibilityLabel("의류 등록하기")
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 26)
        .frame(maxWidth: .infinity)
        .fitcommitCardStyle(borderOpacity: 0.07)
    }
}

private struct AddClothingListRow: View {
    let onCameraTap: () -> Void
    let onPhotoLibraryTap: () -> Void

    var body: some View {
        Menu {
            Button {
                onCameraTap()
            } label: {
                Label("사진 촬영하기", systemImage: "camera")
            }

            Button {
                onPhotoLibraryTap()
            } label: {
                Label("갤러리에서 선택하기", systemImage: "photo.on.rectangle")
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.primary, in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text("옷 추가하기")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.primary)

                    Text("사진 촬영 또는 앨범에서 AI 분석")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 8)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(16)
            .fitcommitCardStyle(borderOpacity: 0.07)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("옷 추가하기")
    }
}

private struct ClosetItemGridCard: View {
    let item: ClosetItem

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: ClosetGridCardMetrics.cornerRadius, style: .continuous)
                    .fill(Color(uiColor: .tertiarySystemBackground))

                if let image = UIImage(data: item.imageData) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(maxWidth: .infinity)
                        .frame(height: ClosetGridCardMetrics.mediaHeight)
                        .clipped()
                } else {
                    Image(systemName: "tshirt")
                        .font(.system(size: 34, weight: .light))
                        .foregroundStyle(.secondary)
                }
            }
            .frame(height: ClosetGridCardMetrics.mediaHeight)
            .clipShape(RoundedRectangle(cornerRadius: ClosetGridCardMetrics.cornerRadius, style: .continuous))

            VStack(alignment: .leading, spacing: 5) {
                Text(item.name)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Text(item.category)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    Circle()
                        .fill(Color(hexString: item.color) ?? Color(uiColor: .secondarySystemBackground))
                        .frame(width: 13, height: 13)
                        .overlay {
                            Circle().stroke(Color.primary.opacity(0.12), lineWidth: 1)
                        }

                    Text(item.season)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(Color(uiColor: .tertiaryLabel))
                        .lineLimit(1)
                }
            }
            .frame(height: ClosetGridCardMetrics.metadataHeight, alignment: .topLeading)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: ClosetGridCardMetrics.cardHeight, alignment: .top)
        .fitcommitCardStyle(borderOpacity: 0.07)
    }
}

private enum AnalysisRequestState {
    case idle
    case loading
    case success(AnalysisResponse)
    case failure(String)
}

private struct ClothesUploadView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var analysisState: AnalysisRequestState = .idle
    @State private var isShowingAnalysisError = false
    @State private var alertMessage = ""
    @State private var didStartAutoAnalysis = false
    @State private var loadingProgress = 0.08
    @State private var loadingPulse = false
    @State private var processedImage: UIImage?
    @State private var backgroundRemovalMessage: String?
    @State private var imageDataForSaving: Data

    let image: UIImage
    private let analysisService = AnalysisService()

    init(image: UIImage) {
        self.image = image
        _imageDataForSaving = State(initialValue: image.jpegData(compressionQuality: 0.82) ?? Data())
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    imagePreview

                    switch analysisState {
                    case .idle:
                        uploadForm
                    case .loading:
                        loadingView
                    case .success(let analysisResponse):
                        AnalysisResultView(
                            result: analysisResponse.analysis,
                            imageData: imageDataForSaving
                        )
                    case .failure(let message):
                        failureView(message: message)
                    }
                }
                .padding(20)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemBackground))
            .navigationTitle("의류 등록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") {
                        dismiss()
                    }
                }
            }
            .alert("서버 연결 실패", isPresented: $isShowingAnalysisError) {
                Button("재시도") {
                    Task {
                        await analyzeImage()
                    }
                }
                Button("확인", role: .cancel) {}
            } message: {
                Text(alertMessage)
            }
            .task {
                await startAutoAnalysisIfNeeded()
            }
        }
    }

    private var imagePreview: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color(uiColor: .secondarySystemBackground))
                .frame(height: 260)

            Image(uiImage: activeImage)
                .resizable()
                .scaledToFit()
                .frame(maxHeight: 236)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(12)
        }
    }

    private var uploadForm: some View {
        VStack(spacing: 16) {
            Text("선택한 이미지를 AI 서버로 분석할 준비가 됐어요.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            Button {
                Task {
                    await analyzeImage()
                }
            } label: {
                Label("AI 분석하기", systemImage: "sparkles")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }

    private var loadingView: some View {
        VStack(spacing: 18) {
            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.12))
                    .frame(width: 86, height: 86)
                    .scaleEffect(loadingPulse ? 1.08 : 0.94)
                    .animation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true), value: loadingPulse)

                Image(systemName: "sparkles")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
            }

            Text("AI가 옷을 분석 중이에요...")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.primary)

            Text("카테고리, 색상, 계절감을 함께 확인하고 있습니다.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if let backgroundRemovalMessage {
                Text(backgroundRemovalMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            ProgressView(value: loadingProgress)
                .tint(Color.accentColor)
                .frame(maxWidth: 220)
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .fitcommitCardStyle(borderOpacity: 0.07)
        .onAppear {
            loadingPulse = true
        }
        .task {
            await animateLoadingProgress()
        }
    }

    private func failureView(message: String) -> some View {
        VStack(spacing: 16) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.orange)
                    .frame(width: 42, height: 42)
                    .background(Color.orange.opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 6) {
                    Text("분석 요청에 실패했어요")
                        .font(.headline)
                        .foregroundStyle(.primary)

                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)
            }

            Button {
                Task {
                    await analyzeImage()
                }
            } label: {
                Label("다시 시도", systemImage: "arrow.clockwise")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Color.orange.opacity(0.08), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.orange.opacity(0.22), lineWidth: 1)
        }
    }

    @MainActor
    private func startAutoAnalysisIfNeeded() async {
        guard didStartAutoAnalysis == false else { return }
        didStartAutoAnalysis = true
        await analyzeImage()
    }

    @MainActor
    private func analyzeImage() async {
        print("[DEBUG][ClothesUploadView] analyzeImage started")
        loadingProgress = 0.08
        backgroundRemovalMessage = "배경을 분리하는 중입니다."
        analysisState = .loading

        do {
            print("[DEBUG][ClothesUploadView] Preparing image")
            let imageForAnalysis = await prepareImageForAnalysis()
            print("[DEBUG][ClothesUploadView] Image prepared")
            processedImage = imageForAnalysis
            imageDataForSaving = await imageForAnalysis.imageDataForClosetStorageInBackground(compressionQuality: 0.82) ?? Data()
            backgroundRemovalMessage = "배경 분리 완료. 서버 분석 중입니다."
            loadingProgress = max(loadingProgress, 0.34)

            print("[DEBUG][ClothesUploadView] Calling AnalysisService.uploadImage")
            let response = try await analysisService.uploadImage(imageForAnalysis)
            print("[DEBUG][ClothesUploadView] AnalysisService returned success")
            analysisState = .success(response)
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            print("[DEBUG][ClothesUploadView] analyzeImage failed: \(message)")
            alertMessage = message
            isShowingAnalysisError = true
            analysisState = .failure(message)
        }
    }

    @MainActor
    private func animateLoadingProgress() async {
        while case .loading = analysisState {
            loadingProgress = min(loadingProgress + 0.08, 0.92)
            try? await Task.sleep(nanoseconds: 450_000_000)
        }
    }

    private var activeImage: UIImage {
        processedImage ?? image
    }

    @MainActor
    private func prepareImageForAnalysis() async -> UIImage {
        guard #available(iOS 17.0, *) else {
            backgroundRemovalMessage = "이 iOS 버전에서는 자동 배경 분리를 지원하지 않아 원본으로 분석합니다."
            return image
        }

        do {
            return try await VisionImageService.shared.removeBackground(from: image)
        } catch {
            backgroundRemovalMessage = "배경 분리에 실패해 원본 이미지로 분석합니다."
            return image
        }
    }
}

private struct ClothingDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Bindable var item: ClosetItem
    @State private var isShowingDeleteAlert = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let image = UIImage(data: item.imageData) {
                        HStack {
                            Spacer()
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFit()
                                .frame(height: 180)
                                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            Spacer()
                        }
                    }
                }

                Section("기본 정보") {
                    TextField("옷 이름", text: $item.name)

                    Picker("카테고리", selection: $item.category) {
                        ForEach(ClosetItemEditOptions.categories, id: \.self) { category in
                            Text(category).tag(category)
                        }
                    }
                }

                Section("색상") {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(Color(hexString: item.color) ?? Color(uiColor: .secondarySystemBackground))
                            .frame(width: 28, height: 28)
                            .overlay {
                                Circle()
                                    .stroke(Color(uiColor: .separator), lineWidth: 1)
                            }

                        TextField("HEX 색상", text: $item.color)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                    }
                }

                Section("계절") {
                    Picker("계절", selection: $item.season) {
                        ForEach(ClosetItemEditOptions.seasons, id: \.self) { season in
                            Label(season, systemImage: seasonIconName(for: season)).tag(season)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    Button {
                        isShowingDeleteAlert = true
                    } label: {
                        Label("의류데이터 삭제하기", systemImage: "trash")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("의류 정보")
            .navigationBarTitleDisplayMode(.inline)
            .alert("의류를 삭제할까요?", isPresented: $isShowingDeleteAlert) {
                Button("삭제하기", role: .destructive) {
                    deleteItem()
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text("삭제한 의류는 복구할 수 없습니다.")
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("닫기") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        saveAndDismiss()
                    } label: {
                        Text("수정 완료")
                            .foregroundStyle(.primary)
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }

    private func saveAndDismiss() {
        item.name = normalizedValue(item.name, fallback: "새로운 옷")
        item.category = normalizedValue(item.category, fallback: "T-shirt")
        item.color = normalizedValue(item.color, fallback: "#CCCCCC")
        item.season = normalizedValue(item.season, fallback: "All-Season")
        try? modelContext.save()
        dismiss()
    }

    private func deleteItem() {
        modelContext.delete(item)
        try? modelContext.save()
        dismiss()
    }

    private func seasonIconName(for season: String) -> String {
        switch season {
        case "Spring/Summer":
            return "sun.max.fill"
        case "Fall/Winter":
            return "snowflake"
        default:
            return "circle.lefthalf.filled"
        }
    }

    private func normalizedValue(_ value: String, fallback: String) -> String {
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmedValue.isEmpty ? fallback : trimmedValue
    }
}

private struct CameraImagePicker: UIViewControllerRepresentable {
    let onImagePicked: (UIImage) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        picker.allowsEditing = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onImagePicked: onImagePicked)
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let onImagePicked: (UIImage) -> Void

        init(onImagePicked: @escaping (UIImage) -> Void) {
            self.onImagePicked = onImagePicked
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                onImagePicked(image)
            } else {
                picker.dismiss(animated: true)
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}

#Preview {
    ClosetView()
        .modelContainer(for: ClosetItem.self, inMemory: true)
}
