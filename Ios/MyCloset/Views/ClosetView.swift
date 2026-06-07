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
    @State private var backendItems: [ClosetItem] = []
    @State private var isUsingBackendItems = false
    @State private var wardrobes: [BackendWardrobeRow] = []
    @State private var selectedWardrobeID: String?
    @State private var wardrobeErrorMessage: String?
    private let initialFilter: ClosetInitialFilter

    init(initialFilter: ClosetInitialFilter = .all) {
        self.initialFilter = initialFilter
    }

    var body: some View {
        NavigationStack(path: $navigationPath) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                        .padding(.top, 18)

                    arFeatureHub

                    closetInventoryHeader

                    wardrobeFilterBar

                    AddClothingListRow(
                        onCameraTap: presentCamera,
                        onPhotoLibraryTap: presentPhotoLibrary
                    )

                    if displayedItems.isEmpty {
                        EmptyClosetPlaceholder()
                    } else {
                        closetItemsGrid
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 120)
            }
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
                    ClothesUploadView(
                        image: uploadImage.image,
                        selectedWardrobeID: selectedWardrobeID,
                        wardrobes: wardrobes
                    )
                case .detail(let item):
                    ClothingDetailView(item: item, wardrobes: wardrobes)
                case .wardrobes:
                    WardrobeManagementView(wardrobes: wardrobes) {
                        Task {
                            await refreshWardrobes()
                            await refreshBackendClothes()
                        }
                    }
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
                Task {
                    await refreshWardrobes()
                    await refreshBackendClothes()
                }
            }
            .onChange(of: isTabBarVisible) { _, isVisible in
                rootTabBarVisible.wrappedValue = isVisible
            }
            .onChange(of: selectedPhotoItem) { _, item in
                loadPhotoPickerItem(item)
            }
            .onChange(of: activeSheet?.id) { _, sheetID in
                if sheetID == nil {
                    Task {
                        await refreshBackendClothes()
                    }
                }
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
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(initialFilter == .unworn ? "안 입는 옷" : "내 옷 목록")
                    .font(.headline)
                    .foregroundStyle(.primary)

                Spacer()

                Text("\(displayedItems.count)개")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            Button {
                activeSheet = .wardrobes
            } label: {
                Label("옷장 관리", systemImage: "folder.badge.gearshape")
                    .font(.subheadline.weight(.semibold))
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .padding(16)
        .fitcommitCardStyle()
    }

    private var wardrobeFilterBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    wardrobeFilterButton(title: "전체", id: nil, isDefault: false)
                    ForEach(wardrobes) { wardrobe in
                        wardrobeFilterButton(title: wardrobe.name, id: wardrobe.id, isDefault: wardrobe.isDefault)
                    }
                }
                .padding(.vertical, 2)
            }

            if let wardrobeErrorMessage {
                Text(wardrobeErrorMessage)
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
        }
    }

    private func wardrobeFilterButton(title: String, id: String?, isDefault: Bool) -> some View {
        Button {
            selectedWardrobeID = id
            Task {
                await refreshBackendClothes()
            }
        } label: {
            HStack(spacing: 6) {
                Text(title)
                if isDefault {
                    Text("기본")
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.accentColor.opacity(0.12), in: Capsule())
                }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                selectedWardrobeID == id ? Color.accentColor.opacity(0.16) : Color(uiColor: .secondarySystemBackground),
                in: Capsule()
            )
        }
        .buttonStyle(.plain)
    }

    private var closetItemsGrid: some View {
        LazyVGrid(
            columns: [
                GridItem(.flexible(), spacing: 16),
                GridItem(.flexible(), spacing: 16),
            ],
            spacing: 16
        ) {
            ForEach(displayedItems) { item in
                ClosetItemCard(item: item)
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
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
        let sourceItems = mergedClosetItems
        let wardrobeFilteredItems: [ClosetItem]
        if let selectedWardrobeID {
            wardrobeFilteredItems = sourceItems.filter { $0.wardrobeID == selectedWardrobeID }
        } else {
            wardrobeFilteredItems = sourceItems
        }
        switch initialFilter {
        case .all:
            return wardrobeFilteredItems
        case .unworn:
            let cutoffDate = Calendar.current.date(byAdding: .month, value: -3, to: Date()) ?? Date()
            return wardrobeFilteredItems.filter { $0.timestamp < cutoffDate }
        }
    }

    private var mergedClosetItems: [ClosetItem] {
        guard isUsingBackendItems else {
            return items
        }

        var merged = items
        let localRemoteIDs = Set(items.compactMap(\.remoteID).filter { $0.isEmpty == false })
        let backendOnlyItems = backendItems.filter { item in
            guard let remoteID = item.remoteID, remoteID.isEmpty == false else {
                return false
            }
            return localRemoteIDs.contains(remoteID) == false
        }
        merged.append(contentsOf: backendOnlyItems)
        return merged.sorted { $0.timestamp > $1.timestamp }
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

    @MainActor
    private func refreshBackendClothes() async {
        do {
            let fetchedItems = try await BackendClothesService.fetchClothes(wardrobeID: selectedWardrobeID)
            backendItems = fetchedItems
            isUsingBackendItems = true
        } catch {
            print("[ClosetView] Backend clothes fetch failed, using local SwiftData: \(error.localizedDescription)")
            backendItems = []
            isUsingBackendItems = false
        }
    }

    @MainActor
    private func refreshWardrobes() async {
        do {
            wardrobes = try await WardrobeBackendService.fetchWardrobes()
            wardrobeErrorMessage = nil
        } catch {
            print("[ClosetView] Backend wardrobes fetch failed: \(error.localizedDescription)")
            wardrobes = []
            wardrobeErrorMessage = "옷장 목록을 불러오지 못했습니다. 저장 시 기본 옷장으로 연결됩니다."
        }
    }

}

private enum ClosetActiveSheet: Identifiable {
    case camera
    case upload(UploadImage)
    case detail(ClosetItem)
    case wardrobes

    var id: String {
        switch self {
        case .camera:
            return "camera"
        case .upload(let uploadImage):
            return uploadImage.id.uuidString
        case .detail(let item):
            return item.id.uuidString
        case .wardrobes:
            return "wardrobes"
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

enum ClosetItemEditOptions {
    static let categories = [
        "top",
        "bottom",
        "outer",
        "shoes",
        "hat",
        "bag",
        "dress",
    ]

    static let seasons = [
        "spring",
        "summer",
        "fall",
        "winter",
    ]

    static let colors = [
        "black",
        "white",
        "gray",
        "navy",
        "blue",
        "brown",
        "beige",
        "red",
        "pink",
        "green",
        "yellow",
        "purple",
    ]
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
                Text("내 옷 목록이 비어 있습니다")
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)

                Text("위의 옷 추가하기 버튼으로 옷을 등록할 수 있습니다.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
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

                    Text("사진 촬영 또는 앨범에서 등록")
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

private struct ClosetItemCard: View {
    let item: ClosetItem

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(uiColor: .tertiarySystemBackground))

                if let image = UIImage(data: item.imageData) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 150)
                        .clipped()
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: "tshirt")
                            .font(.system(size: 34, weight: .light))
                            .foregroundStyle(.secondary)

                        Text("이미지 없음")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }

                statusOverlay
            }
            .frame(height: 150)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 7) {
                Text(displayName)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Text(item.category)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                statusBadges

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
            .padding(.horizontal, 10)
            .padding(.top, 10)
            .padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fitcommitCardStyle(borderOpacity: 0.07)
    }

    private var displayName: String {
        if item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return item.name
        }
        if item.subcategory.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false {
            return item.subcategory
        }
        return item.category
    }

    private var statusOverlay: some View {
        VStack {
            HStack {
                Spacer()
                if let primaryBadge {
                    StatusBadge(text: primaryBadge.text, color: primaryBadge.color)
                }
            }
            Spacer()
        }
        .padding(8)
    }

    @ViewBuilder
    private var statusBadges: some View {
        let badges = allBadges
        if badges.isEmpty == false {
            VStack(alignment: .leading, spacing: 5) {
                ForEach(badges, id: \.text) { badge in
                    StatusBadge(text: badge.text, color: badge.color)
                }
            }
        }
    }

    private var primaryBadge: (text: String, color: Color)? {
        allBadges.first
    }

    private var allBadges: [(text: String, color: Color)] {
        var badges: [(text: String, color: Color)] = []
        if item.analysisStatus == "pending" {
            badges.append(("분석 대기", .secondary))
        } else if item.analysisStatus == "failed" {
            badges.append(("분석 실패", .orange))
        }
        if item.syncStatus == "sync_failed" {
            badges.append(("동기화 실패", .red))
        }
        return badges
    }
}

private struct StatusBadge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.82)
            .padding(.horizontal, 7)
            .padding(.vertical, 4)
            .background(color, in: Capsule())
    }
}

private enum AnalysisRequestState {
    case idle
    case loading
    case saved(String)
    case success(AnalysisResponse)
    case failure(String)
}

private struct ClothesUploadView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var analysisState: AnalysisRequestState = .idle
    @State private var isShowingAnalysisError = false
    @State private var alertMessage = ""
    @State private var loadingProgress = 0.08
    @State private var loadingPulse = false
    @State private var processedImage: UIImage?
    @State private var imageDataForSaving: Data
    @State private var selectedCategoryHint = "top"
    @State private var selectedWardrobeSelection = ""
    @State private var serverClothingID: String?
    @State private var backendImagePath: String?
    @State private var backendImageURL: String?
    @State private var localItem: ClosetItem?

    let image: UIImage
    let wardrobes: [BackendWardrobeRow]
    private let analysisService = AnalysisService()
    private let categoryOptions = ClosetItemEditOptions.categories

    init(image: UIImage, selectedWardrobeID: String?, wardrobes: [BackendWardrobeRow]) {
        self.image = image
        self.wardrobes = wardrobes
        _selectedWardrobeSelection = State(initialValue: selectedWardrobeID ?? wardrobes.first(where: \.isDefault)?.id ?? "")
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
                    case .saved(let message):
                        savedView(message: message)
                    case .success(let analysisResponse):
                        AnalysisResultView(
                            result: analysisResponse.analysis,
                            imageData: imageDataForSaving,
                            serverClothingID: serverClothingID,
                            backendImagePath: backendImagePath
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
            .alert("저장 상태", isPresented: $isShowingAnalysisError) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(alertMessage)
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
        VStack(alignment: .leading, spacing: 16) {
            Text("아이템 종류")
                .font(.headline)

            Picker("아이템 종류", selection: $selectedCategoryHint) {
                ForEach(categoryOptions, id: \.self) { category in
                    Text(categoryLabel(for: category)).tag(category)
                }
            }
            .pickerStyle(.menu)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .background(Color(uiColor: .tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 8) {
                Label("옷 한 벌만 화면 중앙에 놓고 촬영해주세요.", systemImage: "viewfinder")
                Label("배경은 단순할수록 분석 정확도가 높아집니다.", systemImage: "sparkles")
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)

            Text("선택한 카테고리는 서버 저장 시 main_category로 저장됩니다.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            if wardrobes.isEmpty == false {
                Text("저장할 옷장")
                    .font(.headline)

                Picker("저장할 옷장", selection: $selectedWardrobeSelection) {
                    Text("기본 옷장").tag("")
                    ForEach(wardrobes) { wardrobe in
                        Text(wardrobe.isDefault ? "\(wardrobe.name) · 기본" : wardrobe.name).tag(wardrobe.id)
                    }
                }
                .pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .background(Color(uiColor: .tertiarySystemBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            Text("AI 서버가 꺼져 있어도 Backend 저장은 먼저 진행됩니다.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Button {
                Task {
                    await registerClothing()
                }
            } label: {
                Label("저장하기", systemImage: "tray.and.arrow.down.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(18)
        .fitcommitCardStyle(borderOpacity: 0.07)
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

            Text("의류 정보를 저장 중이에요...")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.primary)

            Text("Backend /clothes 저장을 먼저 완료하고, AI 분석은 설정에 따라 후처리로 진행합니다.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

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

    private func savedView(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(.green)

            Text("저장 완료")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.primary)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                dismiss()
            } label: {
                Text("옷장으로 돌아가기")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .fitcommitCardStyle(borderOpacity: 0.07)
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
                    Text("저장 준비에 실패했어요")
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
                    await registerClothing()
                }
            } label: {
                Label("저장 다시 시도", systemImage: "arrow.clockwise")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            Text("사진과 기본 카테고리는 기기 옷장에 남아 있어 닫은 뒤에도 확인할 수 있습니다.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
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
    private func registerClothing() async {
        print("[ClothesUploadView] Registration started")
        loadingProgress = 0.08
        analysisState = .loading

        do {
            print("[ClothesUploadView] Preparing image")
            let imageForAnalysis = await prepareImageForAnalysis()
            print("[ClothesUploadView] Image prepared")
            processedImage = imageForAnalysis
            imageDataForSaving = await imageForAnalysis.imageDataForClosetStorageInBackground(compressionQuality: 0.82) ?? Data()
            try saveLocalPendingItem()
            loadingProgress = max(loadingProgress, 0.2)

            await syncPendingClothingRow()
            loadingProgress = max(loadingProgress, 0.62)

            guard BackendClothesService.isAIAnalysisEnabled else {
                print("[ClothesUploadView] AI analysis skipped ENABLE_AI_ANALYSIS=false")
                let message = serverClothingID == nil
                    ? "기기에 저장되었습니다. Backend 연결 실패 시에도 옷장에 남아 있습니다."
                    : "Backend에 저장되었습니다. AI 분석은 나중에 다시 분석하기로 진행할 수 있습니다."
                analysisState = .saved(message)
                loadingProgress = 1
                try? await Task.sleep(nanoseconds: 650_000_000)
                dismiss()
                return
            }

            print("[ClothesUploadView] AI analysis attempt started")
            do {
                let response = try await analysisService.uploadImage(imageForAnalysis, categoryHint: selectedCategoryHint)
                print("[ClothesUploadView] AI analysis succeeded")
                await markAnalysisCompleted(response.analysis)
                analysisState = .success(response)
            } catch {
                let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                print("[ClothesUploadView] AI analysis failed: \(message)")
                await markAnalysisFailed(message)
                analysisState = .saved("옷 데이터는 저장되었습니다. AI 분석은 실패 상태로 남겨두었고 나중에 다시 분석할 수 있습니다.")
                loadingProgress = 1
            }
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            print("[ClothesUploadView] Registration failed before local save: \(message)")
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
        image
    }

    private var localImageReference: String {
        guard let item = localItem else {
            return "local://pending-\(UUID().uuidString)"
        }
        return "local://\(item.id.uuidString)"
    }

    private var normalizedWardrobeID: String? {
        selectedWardrobeSelection.isEmpty ? nil : selectedWardrobeSelection
    }

    @MainActor
    private func saveLocalPendingItem() throws {
        guard localItem == nil else { return }
        let item = ClosetItem(
            id: UUID(),
            name: Self.defaultName(for: selectedCategoryHint),
            category: selectedCategoryHint,
            wardrobeID: normalizedWardrobeID,
            subcategory: "",
            color: activeImage.fitcommitDominantColorName(),
            season: "분석 대기",
            styleTags: [],
            confidence: 0,
            imageURL: nil,
            localImagePath: nil,
            imageData: imageDataForSaving,
            timestamp: Date(),
            remoteID: nil,
            syncStatus: "pending_sync",
            syncError: nil,
            analysisStatus: "pending",
            analysisError: nil,
            needsAnalysis: true
        )
        item.localImagePath = "local://\(item.id.uuidString)"
        modelContext.insert(item)
        try modelContext.save()
        localItem = item
        print("[ClothesUploadView] Local pending item saved id=\(item.id.uuidString)")
    }

    @MainActor
    private func syncPendingClothingRow() async {
        guard serverClothingID == nil else {
            return
        }
        guard BackendClothesService.isConfigured,
              let userID = BackendClothesService.configuredUserID else {
            localItem?.syncStatus = "local_only"
            localItem?.syncError = nil
            try? modelContext.save()
            print("[ClothesUploadView] Backend save skipped: Backend config missing")
            return
        }

        var imagePath = backendImagePath ?? localImageReference
        var imageURL = backendImageURL ?? ""

        if backendImagePath == nil, imageDataForSaving.isEmpty == false {
            do {
                print("[ClothesUploadView] Backend /clothes/upload request started")
                let upload = try await BackendClothesService.uploadImage(
                    imageData: imageDataForSaving,
                    userID: userID,
                    mainCategory: selectedCategoryHint
                )
                imagePath = upload.imagePath
                imageURL = upload.imageURL
                backendImagePath = upload.imagePath
                backendImageURL = upload.imageURL
                localItem?.imageURL = upload.imageURL
                print("[ClothesUploadView] Storage upload succeeded path=\(upload.imagePath)")
            } catch {
                print("[ClothesUploadView] Storage upload failed, falling back to local image reference: \(error.localizedDescription)")
            }
        }

        backendImagePath = imagePath
        localItem?.localImagePath = localImageReference
        print("[ClothesUploadView] Backend /clothes request started")
        do {
            let row = try await BackendClothesService.create(
                BackendClothesPayload(
                    userID: userID,
                    wardrobeID: normalizedWardrobeID,
                    mainCategory: selectedCategoryHint,
                    subcategory: nil,
                    mainColor: localItem?.color,
                    season: [],
                    styleTags: [],
                    imageURL: imageURL,
                    imagePath: imagePath,
                    localImagePath: localImageReference,
                    backgroundRemovedImagePath: nil,
                    backgroundRemovedImageURL: nil,
                    backgroundRemovalStatus: "not_requested",
                    backgroundRemovalError: nil,
                    confidence: nil,
                    analysisMethod: "manual",
                    analysisStatus: "pending",
                    analysisError: nil,
                    analyzedAt: nil,
                    needsAnalysis: true,
                    syncStatus: "synced",
                    memo: "created_before_ai_analysis"
                )
            )
            serverClothingID = row.id
            localItem?.remoteID = row.id
            localItem?.wardrobeID = row.wardrobeID ?? normalizedWardrobeID
            localItem?.imageURL = row.imageURL ?? backendImageURL
            localItem?.syncStatus = "synced"
            localItem?.syncError = nil
            try? modelContext.save()
            print("[ClothesUploadView] Backend save succeeded id=\(row.id ?? "unknown")")
        } catch {
            localItem?.syncStatus = "sync_failed"
            localItem?.syncError = error.localizedDescription
            try? modelContext.save()
            print("[ClothesUploadView] Backend save failed, local item retained: \(error.localizedDescription)")
        }
    }

    @MainActor
    private func markAnalysisCompleted(_ result: AnalysisResult) async {
        localItem?.name = result.subcategory.isEmpty ? result.mainCategory : result.subcategory
        localItem?.category = result.mainCategory
        localItem?.subcategory = result.subcategory
        localItem?.color = result.mainColor
        localItem?.season = result.season
        localItem?.styleTags = result.styleTags
        localItem?.confidence = result.confidence
        localItem?.imageURL = result.imageURL ?? backendImageURL
        localItem?.analysisStatus = "completed"
        localItem?.analysisError = nil
        localItem?.needsAnalysis = false
        try? modelContext.save()

        guard let serverClothingID else { return }
        do {
            _ = try await BackendClothesService.update(
                clothingID: serverClothingID,
                payload: BackendClothesUpdatePayload(
                    wardrobeID: nil,
                    mainCategory: result.mainCategory,
                    subcategory: result.subcategory.isEmpty ? nil : result.subcategory,
                    mainColor: result.mainColor,
                    season: seasonValues(from: result.season),
                    styleTags: result.styleTags,
                    imageURL: result.imageURL ?? backendImageURL,
                    imagePath: backendImagePath,
                    localImagePath: backendImagePath,
                    backgroundRemovedImagePath: nil,
                    backgroundRemovedImageURL: nil,
                    backgroundRemovalStatus: nil,
                    backgroundRemovalError: nil,
                    confidence: result.confidence,
                    analysisMethod: result.method,
                    analysisStatus: "completed",
                    analysisError: "",
                    analyzedAt: Date(),
                    needsAnalysis: false,
                    syncStatus: "synced",
                    memo: "ai_analysis_completed"
                )
            )
        } catch {
            print("[ClothesUploadView] Failed to mark completed: \(error.localizedDescription)")
        }
    }

    @MainActor
    private func markAnalysisFailed(_ message: String) async {
        localItem?.analysisStatus = "failed"
        localItem?.analysisError = message
        localItem?.needsAnalysis = true
        try? modelContext.save()

        guard let serverClothingID else { return }
        do {
            _ = try await BackendClothesService.update(
                clothingID: serverClothingID,
                payload: BackendClothesUpdatePayload(
                    wardrobeID: nil,
                    mainCategory: nil,
                    subcategory: nil,
                    mainColor: nil,
                    season: nil,
                    styleTags: nil,
                    imageURL: backendImageURL,
                    imagePath: backendImagePath,
                    localImagePath: backendImagePath,
                    backgroundRemovedImagePath: nil,
                    backgroundRemovedImageURL: nil,
                    backgroundRemovalStatus: nil,
                    backgroundRemovalError: nil,
                    confidence: nil,
                    analysisMethod: nil,
                    analysisStatus: "failed",
                    analysisError: message,
                    analyzedAt: nil,
                    needsAnalysis: true,
                    syncStatus: "synced",
                    memo: "ai_analysis_failed"
                )
            )
        } catch {
            print("[ClothesUploadView] Failed to mark failed: \(error.localizedDescription)")
        }
    }

    private func seasonValues(from season: String) -> [String] {
        switch season {
        case "Spring/Summer":
            return ["spring", "summer"]
        case "Fall/Winter":
            return ["fall", "winter"]
        case "All-Season":
            return ["spring", "summer", "fall", "winter"]
        default:
            return season
                .split(separator: "/")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                .filter { $0.isEmpty == false }
        }
    }

    private func categoryLabel(for value: String) -> String {
        switch value {
        case "top":
            return "상의 (top)"
        case "bottom":
            return "하의 (bottom)"
        case "outer":
            return "아우터 (outer)"
        case "shoes":
            return "신발 (shoes)"
        case "hat":
            return "모자 (hat)"
        case "bag":
            return "가방 (bag)"
        case "dress":
            return "원피스 (dress)"
        default:
            return value
        }
    }

    private static func defaultName(for category: String) -> String {
        switch category.lowercased() {
        case "top":
            return "새로운 상의"
        case "bottom":
            return "새로운 하의"
        case "outer":
            return "새로운 아우터"
        case "shoes":
            return "새로운 신발"
        case "hat":
            return "새로운 모자"
        case "bag":
            return "새로운 가방"
        case "dress":
            return "새로운 원피스"
        default:
            return "새로운 옷"
        }
    }
}

private struct ClothingDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Bindable var item: ClosetItem
    let wardrobes: [BackendWardrobeRow]
    @State private var isShowingDeleteAlert = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedSeasonValues: Set<String> = []
    @State private var selectedWardrobeSelection = ""
    @State private var isSaving = false
    @State private var isRemovingBackground = false
    @State private var saveMessage: String?
    @State private var changedImageData: Data?

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

                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Label("사진 변경", systemImage: "photo.on.rectangle.angled")
                    }
                }

                Section("기본 정보") {
                    TextField("옷 이름", text: $item.name)

                    if wardrobes.isEmpty == false {
                        Picker("옷장", selection: $selectedWardrobeSelection) {
                            Text("기본 옷장").tag("")
                            ForEach(wardrobes) { wardrobe in
                                Text(wardrobe.isDefault ? "\(wardrobe.name) · 기본" : wardrobe.name).tag(wardrobe.id)
                            }
                        }
                    }

                    Picker("카테고리", selection: $item.category) {
                        ForEach(ClosetItemEditOptions.categories, id: \.self) { category in
                            Text(category).tag(category)
                        }
                    }

                    TextField("세부 종류", text: $item.subcategory)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
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

                    Picker("대표 색상", selection: $item.color) {
                        ForEach(ClosetItemEditOptions.colors, id: \.self) { color in
                            Text(color).tag(color)
                        }
                    }
                }

                Section("계절") {
                    ForEach(ClosetItemEditOptions.seasons, id: \.self) { season in
                        Toggle(isOn: seasonBinding(for: season)) {
                            Label(season, systemImage: seasonIconName(for: season))
                        }
                    }
                    Text(selectedSeasonValues.isEmpty ? "선택된 계절 없음" : selectedSeasonValues.sorted().joined(separator: ", "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("상태") {
                    LabeledContent("분석 상태", value: analysisStatusLabel(item.analysisStatus))
                    LabeledContent("동기화 상태", value: syncStatusLabel(item.syncStatus))
                    LabeledContent("배경 제거", value: backgroundRemovalStatusLabel(item.backgroundRemovalStatus))
                    if let error = item.backgroundRemovalError, error.isEmpty == false {
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(.orange)
                    }
                    if let saveMessage {
                        Text(saveMessage)
                            .font(.footnote)
                            .foregroundStyle(item.syncStatus == "sync_failed" ? .orange : .secondary)
                    }
                }

                Section("배경 제거") {
                    Button {
                        Task {
                            await removeBackgroundAndUpload()
                        }
                    } label: {
                        Label(isRemovingBackground ? "배경 제거 중" : "배경 제거 다시 시도", systemImage: "wand.and.sparkles")
                    }
                    .disabled(isRemovingBackground || item.remoteID == nil)

                    Text("배경 제거는 선택 기능입니다. 실패해도 원본 이미지와 옷 데이터는 유지됩니다.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
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
            .onAppear {
                selectedSeasonValues = seasonValues(from: item.season)
                selectedWardrobeSelection = item.wardrobeID ?? wardrobes.first(where: \.isDefault)?.id ?? ""
            }
            .onChange(of: selectedPhotoItem) { _, newItem in
                loadChangedPhoto(newItem)
            }
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
                        Task {
                            await saveAndDismiss()
                        }
                    } label: {
                        Text(isSaving ? "저장 중" : "수정 완료")
                            .foregroundStyle(.primary)
                    }
                    .fontWeight(.semibold)
                    .disabled(isSaving)
                }
            }
        }
    }

    @MainActor
    private func saveAndDismiss() async {
        isSaving = true
        item.name = normalizedValue(item.name, fallback: "새로운 옷")
        item.category = normalizedValue(item.category, fallback: "top")
        item.subcategory = normalizedValue(item.subcategory, fallback: "")
        item.color = normalizedValue(item.color, fallback: "gray")
        item.season = seasonDisplayValue(from: selectedSeasonValues)
        item.wardrobeID = normalizedWardrobeID
        item.analysisMethod = "manual"
        item.syncStatus = item.remoteID == nil ? "local_only" : "pending_sync"
        try? modelContext.save()

        guard let remoteID = item.remoteID, remoteID.isEmpty == false else {
            isSaving = false
            dismiss()
            return
        }

        var imagePathForUpdate = item.localImagePath
        var imageURLForUpdate = item.imageURL

        if let changedImageData,
           let userID = BackendClothesService.configuredUserID {
            do {
                let upload = try await BackendClothesService.uploadImage(
                    imageData: changedImageData,
                    userID: userID,
                    mainCategory: item.category
                )
                imagePathForUpdate = upload.imagePath
                imageURLForUpdate = upload.imageURL
                item.localImagePath = upload.imagePath
                item.imageURL = upload.imageURL
            } catch {
                print("[ClothingDetailView] Image upload failed, keeping local edit: \(error.localizedDescription)")
                saveMessage = "이미지 업로드 실패, 기존 서버 이미지는 유지됩니다."
            }
        }

        do {
            let row = try await BackendClothesService.update(
                clothingID: remoteID,
                payload: BackendClothesUpdatePayload(
                    wardrobeID: normalizedWardrobeID,
                    mainCategory: item.category,
                    subcategory: item.subcategory.isEmpty ? nil : item.subcategory,
                    mainColor: item.color,
                    season: selectedSeasonValues.sorted(),
                    styleTags: item.styleTags,
                    imageURL: imageURLForUpdate,
                    imagePath: imagePathForUpdate,
                    localImagePath: item.localImagePath,
                    backgroundRemovedImagePath: item.backgroundRemovedImagePath,
                    backgroundRemovedImageURL: item.backgroundRemovedImageURL,
                    backgroundRemovalStatus: item.backgroundRemovalStatus,
                    backgroundRemovalError: item.backgroundRemovalError,
                    confidence: nil,
                    analysisMethod: "manual",
                    analysisStatus: item.analysisStatus,
                    analysisError: item.analysisError,
                    analyzedAt: nil,
                    needsAnalysis: item.needsAnalysis,
                    syncStatus: "synced",
                    memo: "manual_edit"
                )
            )
            apply(row: row)
            item.syncStatus = "synced"
            item.syncError = nil
            try? modelContext.save()
            saveMessage = "서버에 수정 사항이 반영되었습니다."
            isSaving = false
            dismiss()
        } catch {
            item.syncStatus = "sync_failed"
            item.syncError = error.localizedDescription
            try? modelContext.save()
            saveMessage = "서버 수정 실패, 기기에는 저장되었습니다."
            print("[ClothingDetailView] Backend update failed: \(error.localizedDescription)")
            isSaving = false
        }
    }

    private func deleteItem() {
        modelContext.delete(item)
        try? modelContext.save()
        dismiss()
    }

    private func seasonIconName(for season: String) -> String {
        switch season {
        case "spring":
            return "sun.max.fill"
        case "summer":
            return "thermometer.sun.fill"
        case "fall":
            return "leaf.fill"
        case "winter":
            return "snowflake"
        default:
            return "circle.lefthalf.filled"
        }
    }

    private func seasonBinding(for season: String) -> Binding<Bool> {
        Binding {
            selectedSeasonValues.contains(season)
        } set: { isSelected in
            if isSelected {
                selectedSeasonValues.insert(season)
            } else {
                selectedSeasonValues.remove(season)
            }
        }
    }

    private func loadChangedPhoto(_ photoItem: PhotosPickerItem?) {
        guard let photoItem else { return }
        Task {
            guard let data = try? await photoItem.loadTransferable(type: Data.self),
                  let image = UIImage(data: data),
                  let jpegData = image.jpegData(compressionQuality: 0.82) else {
                return
            }
            await MainActor.run {
                changedImageData = jpegData
                item.imageData = jpegData
                if item.color == "#CCCCCC" || item.color == "gray" {
                    item.color = image.fitcommitDominantColorName()
                }
                item.localImagePath = "local://\(item.id.uuidString)"
                try? modelContext.save()
            }
        }
    }

    private func apply(row: BackendClothesRow) {
        item.remoteID = row.id ?? item.remoteID
        item.wardrobeID = row.wardrobeID ?? item.wardrobeID
        item.category = row.mainCategory
        item.subcategory = row.subcategory ?? item.subcategory
        item.color = row.mainColor ?? item.color
        item.season = seasonDisplayValue(from: Set(row.season ?? []))
        item.imageURL = row.imageURL ?? item.imageURL
        item.localImagePath = row.localImagePath ?? row.imagePath ?? item.localImagePath
        item.backgroundRemovedImagePath = row.backgroundRemovedImagePath ?? item.backgroundRemovedImagePath
        item.backgroundRemovedImageURL = row.backgroundRemovedImageURL ?? item.backgroundRemovedImageURL
        item.backgroundRemovalStatus = row.backgroundRemovalStatus ?? item.backgroundRemovalStatus
        item.backgroundRemovalError = row.backgroundRemovalError
        item.syncStatus = row.syncStatus ?? "synced"
        item.analysisStatus = row.analysisStatus ?? item.analysisStatus
        item.analysisMethod = row.analysisMethod ?? item.analysisMethod
        item.analysisError = row.analysisError
        item.needsAnalysis = row.needsAnalysis ?? item.needsAnalysis
    }

    @MainActor
    private func removeBackgroundAndUpload() async {
        guard let remoteID = item.remoteID, remoteID.isEmpty == false else {
            saveMessage = "서버에 먼저 저장된 의류만 배경 제거 이미지를 업로드할 수 있습니다."
            return
        }
        guard let userID = BackendClothesService.configuredUserID else {
            saveMessage = "테스트 user_id 설정이 없어 배경 제거 이미지를 업로드할 수 없습니다."
            return
        }
        guard let originalImage = UIImage(data: item.imageData) else {
            saveMessage = "원본 이미지를 읽을 수 없습니다."
            return
        }

        isRemovingBackground = true
        item.backgroundRemovalStatus = "pending"
        item.backgroundRemovalError = nil
        try? modelContext.save()

        do {
            guard #available(iOS 17.0, *) else {
                throw VisionImageServiceError.renderedImageUnavailable
            }
            let removedImage = try await VisionImageService.shared.removeBackground(from: originalImage)
            guard let pngData = removedImage.pngData() else {
                throw VisionImageServiceError.renderedImageUnavailable
            }
            let upload = try await BackendClothesService.uploadImage(
                imageData: pngData,
                userID: userID,
                mainCategory: item.category,
                imageType: "background_removed",
                fileName: "background-removed-\(UUID().uuidString).png",
                mimeType: "image/png"
            )
            let row = try await BackendClothesService.update(
                clothingID: remoteID,
                payload: BackendClothesUpdatePayload(
                    wardrobeID: nil,
                    mainCategory: nil,
                    subcategory: nil,
                    mainColor: nil,
                    season: nil,
                    styleTags: nil,
                    imageURL: nil,
                    imagePath: nil,
                    localImagePath: nil,
                    backgroundRemovedImagePath: upload.imagePath,
                    backgroundRemovedImageURL: upload.imageURL,
                    backgroundRemovalStatus: "completed",
                    backgroundRemovalError: nil,
                    confidence: nil,
                    analysisMethod: nil,
                    analysisStatus: nil,
                    analysisError: nil,
                    analyzedAt: nil,
                    needsAnalysis: nil,
                    syncStatus: "synced",
                    memo: "background_removed_image_uploaded"
                )
            )
            apply(row: row)
            saveMessage = "배경 제거 이미지가 업로드되었습니다."
        } catch {
            let message = error.localizedDescription
            item.backgroundRemovalStatus = "failed"
            item.backgroundRemovalError = message
            saveMessage = "배경 제거 실패, 원본 이미지로 저장되었습니다."
            do {
                _ = try await BackendClothesService.update(
                    clothingID: remoteID,
                    payload: BackendClothesUpdatePayload(
                        wardrobeID: nil,
                        mainCategory: nil,
                        subcategory: nil,
                        mainColor: nil,
                        season: nil,
                        styleTags: nil,
                        imageURL: nil,
                        imagePath: nil,
                        localImagePath: nil,
                        backgroundRemovedImagePath: nil,
                        backgroundRemovedImageURL: nil,
                        backgroundRemovalStatus: "failed",
                        backgroundRemovalError: message,
                        confidence: nil,
                        analysisMethod: nil,
                        analysisStatus: nil,
                        analysisError: nil,
                        analyzedAt: nil,
                        needsAnalysis: nil,
                        syncStatus: "synced",
                        memo: "background_removal_failed"
                    )
                )
            } catch {
                print("[ClothingDetailView] Failed to update background removal failure: \(error.localizedDescription)")
            }
        }

        isRemovingBackground = false
        try? modelContext.save()
    }

    private func seasonValues(from value: String) -> Set<String> {
        let normalized = value.lowercased()
        if normalized == "spring/summer" {
            return ["spring", "summer"]
        }
        if normalized == "fall/winter" {
            return ["fall", "winter"]
        }
        if normalized == "all-season" {
            return Set(ClosetItemEditOptions.seasons)
        }
        return Set(
            normalized
                .split { $0 == "/" || $0 == "," || $0 == " " }
                .map(String.init)
                .filter { ClosetItemEditOptions.seasons.contains($0) }
        )
    }

    private var normalizedWardrobeID: String? {
        selectedWardrobeSelection.isEmpty ? nil : selectedWardrobeSelection
    }

    private func seasonDisplayValue(from values: Set<String>) -> String {
        if values.isEmpty {
            return "분석 대기"
        }
        return ClosetItemEditOptions.seasons.filter { values.contains($0) }.joined(separator: "/")
    }

    private func analysisStatusLabel(_ value: String) -> String {
        switch value {
        case "completed":
            return "분석 완료"
        case "failed":
            return "분석 실패"
        default:
            return "분석 대기"
        }
    }

    private func syncStatusLabel(_ value: String) -> String {
        switch value {
        case "synced":
            return "서버 동기화 완료"
        case "sync_failed":
            return "서버 동기화 실패"
        case "pending_sync":
            return "서버 동기화 대기"
        default:
            return "기기 저장"
        }
    }

    private func backgroundRemovalStatusLabel(_ value: String) -> String {
        switch value {
        case "completed":
            return "배경 제거 완료"
        case "pending":
            return "배경 제거 대기"
        case "failed":
            return "배경 제거 실패"
        default:
            return "배경 제거 안 함"
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

private struct WardrobeManagementView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var wardrobes: [BackendWardrobeRow]
    @State private var newName = ""
    @State private var newDescription = ""
    @State private var editingWardrobe: BackendWardrobeRow?
    @State private var editingName = ""
    @State private var editingDescription = ""
    @State private var statusMessage: String?
    @State private var isSaving = false
    let onChanged: () -> Void

    init(wardrobes: [BackendWardrobeRow], onChanged: @escaping () -> Void) {
        _wardrobes = State(initialValue: wardrobes)
        self.onChanged = onChanged
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("새 옷장 만들기") {
                    TextField("옷장 이름", text: $newName)
                    TextField("설명", text: $newDescription)

                    Button {
                        Task {
                            await createWardrobe()
                        }
                    } label: {
                        Label("옷장 생성", systemImage: "plus")
                    }
                    .disabled(isSaving || newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }

                Section("옷장 목록") {
                    if wardrobes.isEmpty {
                        Text("옷장 목록을 불러오지 못했습니다.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(wardrobes) { wardrobe in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(wardrobe.name)
                                        .font(.headline)
                                    if wardrobe.isDefault {
                                        Text("기본")
                                            .font(.caption2.weight(.bold))
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.accentColor.opacity(0.12), in: Capsule())
                                    }
                                    Spacer()
                                }

                                if let description = wardrobe.description, description.isEmpty == false {
                                    Text(description)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                HStack {
                                    Button("수정") {
                                        editingWardrobe = wardrobe
                                        editingName = wardrobe.name
                                        editingDescription = wardrobe.description ?? ""
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)

                                    Button("삭제", role: .destructive) {
                                        Task {
                                            await deleteWardrobe(wardrobe)
                                        }
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)
                                    .disabled(wardrobe.isDefault)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }

                if let statusMessage {
                    Section {
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("옷장 관리")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") {
                        dismiss()
                    }
                }
            }
            .alert("옷장 수정", isPresented: Binding(get: {
                editingWardrobe != nil
            }, set: { isPresented in
                if isPresented == false {
                    editingWardrobe = nil
                }
            })) {
                TextField("옷장 이름", text: $editingName)
                TextField("설명", text: $editingDescription)
                Button("저장") {
                    Task {
                        await updateWardrobe()
                    }
                }
                Button("취소", role: .cancel) {}
            }
        }
    }

    @MainActor
    private func createWardrobe() async {
        isSaving = true
        defer { isSaving = false }
        do {
            let wardrobe = try await WardrobeBackendService.create(
                name: newName.trimmingCharacters(in: .whitespacesAndNewlines),
                description: newDescription.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            )
            wardrobes.append(wardrobe)
            newName = ""
            newDescription = ""
            statusMessage = "옷장이 생성되었습니다."
            onChanged()
        } catch {
            statusMessage = "옷장 생성 실패: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func updateWardrobe() async {
        guard let editingWardrobe else { return }
        do {
            let updated = try await WardrobeBackendService.update(
                wardrobeID: editingWardrobe.id,
                name: editingName.trimmingCharacters(in: .whitespacesAndNewlines),
                description: editingDescription.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            )
            wardrobes = wardrobes.map { $0.id == updated.id ? updated : $0 }
            self.editingWardrobe = nil
            statusMessage = "옷장이 수정되었습니다."
            onChanged()
        } catch {
            statusMessage = "옷장 수정 실패: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func deleteWardrobe(_ wardrobe: BackendWardrobeRow) async {
        guard wardrobe.isDefault == false else {
            statusMessage = "기본 옷장은 삭제할 수 없습니다."
            return
        }
        do {
            try await WardrobeBackendService.delete(wardrobeID: wardrobe.id)
            wardrobes.removeAll { $0.id == wardrobe.id }
            statusMessage = "옷장이 삭제되었습니다. 포함된 옷은 기본 옷장으로 이동됩니다."
            onChanged()
        } catch {
            statusMessage = "옷장 삭제 실패: \(error.localizedDescription)"
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

#Preview {
    ClosetView()
        .modelContainer(for: ClosetItem.self, inMemory: true)
}
