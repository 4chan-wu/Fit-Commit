import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

struct AIChatView: View {
    let chatRoomID: UUID?
    @Binding private var isTabBarHidden: Bool
    private let hidesTabBarOnAppear: Bool

    @State private var messages: [Message] = [.patriWelcome]
    @State private var draftMessage = ""
    @State private var pendingAttachments: [ChatAttachment] = []
    @State private var selectedPhotoItems: [PhotosPickerItem] = []
    @State private var isPhotoPickerPresented = false
    @State private var isFileImporterPresented = false
    @State private var isCameraPresented = false
    @State private var feedbackTrigger = 0

    init(
        chatRoomID: UUID? = nil,
        isTabBarHidden: Binding<Bool> = .constant(false),
        hidesTabBarOnAppear: Bool = false
    ) {
        self.chatRoomID = chatRoomID
        self._isTabBarHidden = isTabBarHidden
        self.hidesTabBarOnAppear = hidesTabBarOnAppear
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    ForEach(messages) { message in
                        if message.id == messages.first?.id && !message.isUser {
                            WelcomeMessageBubble(
                                message: message,
                                showsSuggestions: messages.count == 1
                            ) { suggestion in
                                sendSuggestion(suggestion)
                            }
                            .id(message.id)
                            .messageTransition()
                        } else {
                            MessageBubble(message: message)
                                .id(message.id)
                                .messageTransition()
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 18)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom) {
                ComposerDock(
                    text: $draftMessage,
                    attachments: $pendingAttachments,
                    onCamera: presentCamera,
                    onPhotoLibrary: { isPhotoPickerPresented = true },
                    onFile: { isFileImporterPresented = true },
                    onSend: sendDraftMessage
                )
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 8)
            }
            .onChange(of: messages.count) { _, _ in
                scrollToBottom(with: proxy)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.clear)
        .onAppear {
            DispatchQueue.main.async {
                withAnimation(.easeInOut(duration: 0.22)) {
                    isTabBarHidden = hidesTabBarOnAppear
                }
            }
        }
        .onDisappear {
            guard hidesTabBarOnAppear else { return }
            withAnimation(.easeInOut(duration: 0.22)) {
                isTabBarHidden = false
            }
        }
        .navigationTitle("AI 패트리")
        .navigationBarTitleDisplayMode(.inline)
        .photosPicker(
            isPresented: $isPhotoPickerPresented,
            selection: $selectedPhotoItems,
            maxSelectionCount: 8,
            matching: .images
        )
        .onChange(of: selectedPhotoItems) { _, items in
            Task {
                await loadPhotoAttachments(from: items)
            }
        }
        .fileImporter(
            isPresented: $isFileImporterPresented,
            allowedContentTypes: [.image, .pdf, .text, .plainText, .item],
            allowsMultipleSelection: true
        ) { result in
            handleImportedFiles(result)
        }
        .sheet(isPresented: $isCameraPresented) {
            CameraPicker { image in
                pendingAttachments.append(ChatAttachment(image: image, fileName: "카메라 사진"))
            }
            .ignoresSafeArea()
        }
        .sensoryFeedback(.impact(weight: .light, intensity: 0.65), trigger: feedbackTrigger)
    }

    private func presentCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { return }
        isCameraPresented = true
    }

    private func sendDraftMessage() {
        let content = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty || !pendingAttachments.isEmpty else { return }

        let attachments = pendingAttachments
        draftMessage = ""
        pendingAttachments.removeAll()

        appendConversation(
            userContent: content,
            attachments: attachments,
            patriContent: response(for: content, attachmentCount: attachments.count)
        )
    }

    private func sendSuggestion(_ suggestion: PatriSuggestion) {
        appendConversation(userContent: suggestion.title, patriContent: suggestion.response)
    }

    private func appendConversation(
        userContent: String,
        attachments: [ChatAttachment] = [],
        patriContent: String
    ) {
        feedbackTrigger += 1

        withAnimation(.spring(response: 0.42, dampingFraction: 0.82)) {
            messages.append(Message(content: userContent, isUser: true, attachments: attachments))
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
            withAnimation(.spring(response: 0.48, dampingFraction: 0.84)) {
                messages.append(Message(content: patriContent, isUser: false))
            }
        }
    }

    private func response(for content: String, attachmentCount: Int) -> String {
        if attachmentCount > 0 {
            return "첨부해주신 \(attachmentCount)개 자료를 확인했어요. 사진의 실루엣과 색감을 기준으로 보면, 전체 톤을 하나로 묶고 작은 포인트만 남기는 쪽이 가장 세련돼 보여요."
        }

        return "좋은 질문이에요. 지금 옷장 데이터와 오늘의 분위기를 함께 보면, 한 가지 포인트만 선명하게 잡는 스타일이 가장 좋아 보여요. 원하시면 상의, 하의, 신발까지 바로 조합해드릴게요."
    }

    @MainActor
    private func loadPhotoAttachments(from items: [PhotosPickerItem]) async {
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self),
                  let image = UIImage(data: data) else { continue }

            pendingAttachments.append(ChatAttachment(image: image, fileName: "사진"))
        }

        selectedPhotoItems.removeAll()
    }

    private func handleImportedFiles(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result else { return }

        for url in urls {
            let isAccessing = url.startAccessingSecurityScopedResource()
            defer {
                if isAccessing {
                    url.stopAccessingSecurityScopedResource()
                }
            }

            let image = UIImage(contentsOfFile: url.path)
            pendingAttachments.append(
                ChatAttachment(
                    image: image,
                    fileURL: url,
                    fileName: url.lastPathComponent
                )
            )
        }
    }

    private func scrollToBottom(with proxy: ScrollViewProxy) {
        guard let lastID = messages.last?.id else { return }

        withAnimation(.spring(response: 0.45, dampingFraction: 0.82)) {
            proxy.scrollTo(lastID, anchor: .bottom)
        }
    }
}

struct Message: Identifiable {
    let id = UUID()
    let content: String
    let isUser: Bool
    let attachments: [ChatAttachment]

    init(content: String, isUser: Bool, attachments: [ChatAttachment] = []) {
        self.content = content
        self.isUser = isUser
        self.attachments = attachments
    }

    static let patriWelcome = Message(
        content: "안녕하세요! 저는 패션 전문 AI 패트리에요. 오늘 어떤 스타일을 완성해볼까요? 궁금하신 것을 편하게 물어보세요.",
        isUser: false
    )
}

struct ChatAttachment: Identifiable {
    let id = UUID()
    var image: UIImage?
    var fileURL: URL?
    var fileName: String

    init(image: UIImage? = nil, fileURL: URL? = nil, fileName: String) {
        self.image = image
        self.fileURL = fileURL
        self.fileName = fileName
    }
}

private struct PatriSuggestion: Identifiable {
    let id = UUID()
    let iconName: String
    let title: String
    let response: String

    static let all: [PatriSuggestion] = [
        PatriSuggestion(
            iconName: "calendar",
            title: "내일 일정 맞춤 코디",
            response: "좋아요. 내일 일정과 날씨를 기준으로 차분하게 맞춰볼게요.\n\n오전 이동이 있고 저녁 약속까지 이어진다면, 구김이 적은 셔츠형 아우터에 와이드 슬랙스, 가벼운 스니커즈 조합을 추천해요. 낮에는 산뜻하고, 밤에는 너무 캐주얼해 보이지 않는 균형이 좋아요."
        ),
        PatriSuggestion(
            iconName: "lightbulb",
            title: "패션 상식 퀴즈",
            response: "좋아요, 패트리의 첫 번째 패션 퀴즈입니다.\n\nQ. 같은 컬러 계열 안에서 명도와 소재감을 다르게 조합해 깊이를 만드는 스타일링을 무엇이라고 부를까요?\n\n1. 톤온톤\n2. 컬러블로킹\n3. 애슬레저\n\n정답 번호를 골라주세요."
        )
    ]
}

private struct PatriAmbientBackground: View {
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.16))
                    .frame(width: 280, height: 280)
                    .blur(radius: 82)
                    .position(x: geometry.size.width * 0.18, y: geometry.size.height * 0.18)

                Circle()
                    .fill(Color.purple.opacity(0.16))
                    .frame(width: 320, height: 320)
                    .blur(radius: 92)
                    .position(x: geometry.size.width * 0.86, y: geometry.size.height * 0.42)
            }
        }
    }
}

private struct PatriAvatar: View {
    var size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [.blue.opacity(0.82), .purple.opacity(0.82)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Circle()
                .fill(.ultraThinMaterial)
                .padding(size * 0.08)

            LucideSymbol("sparkles", size: size * 0.36, color: .primary)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

private struct MessageBubble: View {
    let message: Message

    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            if message.isUser {
                Spacer(minLength: 60)
            } else {
                PatriAvatar(size: 34)
            }

            VStack(alignment: message.isUser ? .trailing : .leading, spacing: 4) {
                if !message.attachments.isEmpty {
                    MessageAttachmentGrid(attachments: message.attachments)
                        .mediaCardBackground()
                        .frame(
                            maxWidth: ChatBubbleLayout.maxBubbleWidth,
                            alignment: message.isUser ? .trailing : .leading
                        )
                }

                if !message.content.isEmpty {
                    Text(message.content)
                        .font(.system(.body, design: .rounded))
                        .foregroundStyle(message.isUser ? .white : .primary)
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(message.isUser ? .trailing : .leading)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 13)
                        .bubbleBackground(isUser: message.isUser)
                        .frame(
                            maxWidth: ChatBubbleLayout.maxBubbleWidth,
                            alignment: message.isUser ? .trailing : .leading
                        )
                }
            }
            .frame(
                maxWidth: ChatBubbleLayout.maxBubbleWidth,
                alignment: message.isUser ? .trailing : .leading
            )

            if !message.isUser {
                Spacer(minLength: 60)
            }
        }
        .frame(maxWidth: .infinity, alignment: message.isUser ? .trailing : .leading)
    }
}

private struct WelcomeMessageBubble: View {
    let message: Message
    let showsSuggestions: Bool
    var onSelect: (PatriSuggestion) -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            PatriAvatar(size: 34)

            VStack(alignment: .leading, spacing: 14) {
                Text(message.content)
                    .font(.system(.body, design: .rounded))
                    .foregroundStyle(.primary)
                    .lineSpacing(4)

                if showsSuggestions {
                    VStack(spacing: 8) {
                        ForEach(PatriSuggestion.all) { suggestion in
                            SuggestionRow(suggestion: suggestion) {
                                onSelect(suggestion)
                            }
                        }
                    }
                    .transition(.opacity.combined(with: .scale(scale: 0.98, anchor: .top)))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .frame(maxWidth: ChatBubbleLayout.maxBubbleWidth, alignment: .leading)
            .bubbleBackground(isUser: false)

            Spacer(minLength: 44)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SuggestionRow: View {
    let suggestion: PatriSuggestion
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                LucideSymbol(suggestion.iconName, size: 16, color: .secondary)
                    .frame(width: 22)

                Text(suggestion.title)
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .foregroundStyle(.primary)

                Spacer(minLength: 8)

                LucideSymbol("arrow.up.circle", size: 18, color: .secondary)
            }
            .padding(16)
            .fitcommitCardStyle()
        }
        .buttonStyle(PressButtonStyle())
    }
}

private struct ComposerDock: View {
    @Binding var text: String
    @Binding var attachments: [ChatAttachment]
    var onCamera: () -> Void
    var onPhotoLibrary: () -> Void
    var onFile: () -> Void
    var onSend: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            if !attachments.isEmpty {
                AttachmentPreviewBar(attachments: $attachments)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            ChatInputBar(
                text: $text,
                hasAttachments: !attachments.isEmpty,
                onCamera: onCamera,
                onPhotoLibrary: onPhotoLibrary,
                onFile: onFile,
                onSend: onSend
            )
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.82), value: attachments.count)
    }
}

private struct AttachmentPreviewBar: View {
    @Binding var attachments: [ChatAttachment]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(attachments) { attachment in
                    AttachmentPreviewItem(attachment: attachment) {
                        attachments.removeAll { $0.id == attachment.id }
                    }
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
        }
    }
}

private struct AttachmentPreviewItem: View {
    let attachment: ChatAttachment
    var onRemove: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            AttachmentThumbnail(attachment: attachment)
                .frame(width: 74, height: 74)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.09), lineWidth: 1)
                }

            Button(action: onRemove) {
                LucideSymbol("xmark.circle", size: 18, color: .primary)
                    .padding(5)
                    .background(.ultraThinMaterial)
                    .clipShape(Circle())
            }
            .buttonStyle(PressButtonStyle())
            .offset(x: 7, y: -7)
        }
        .padding(.top, 7)
        .padding(.trailing, 7)
    }
}

private struct AttachmentThumbnail: View {
    let attachment: ChatAttachment

    var body: some View {
        ZStack {
            if let image = attachment.image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(.ultraThinMaterial)

                VStack(spacing: 7) {
                    LucideSymbol("doc", size: 22, color: .secondary)

                    Text(attachment.fileName)
                        .font(.system(size: 9, weight: .medium, design: .rounded))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 6)
                }
            }
        }
    }
}

private struct MessageAttachmentGrid: View {
    let attachments: [ChatAttachment]

    private var columns: [GridItem] {
        Array(
            repeating: GridItem(.fixed(ChatBubbleLayout.attachmentItemWidth), spacing: 8),
            count: columnCount
        )
    }

    private var columnCount: Int {
        min(max(attachments.count, 1), 2)
    }

    private var gridWidth: CGFloat {
        let itemWidth = ChatBubbleLayout.attachmentItemWidth * CGFloat(columnCount)
        let spacing = CGFloat(columnCount - 1) * 8
        return itemWidth + spacing
    }

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
            ForEach(attachments) { attachment in
                AttachmentThumbnail(attachment: attachment)
                    .frame(
                        width: ChatBubbleLayout.attachmentItemWidth,
                        height: attachment.image == nil ? 82 : 104
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 1)
                    }
            }
        }
        .frame(width: gridWidth, alignment: .leading)
    }
}

private struct ChatInputBar: View {
    @Binding var text: String
    let hasAttachments: Bool
    var onCamera: () -> Void
    var onPhotoLibrary: () -> Void
    var onFile: () -> Void
    var onSend: () -> Void

    private var canSend: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || hasAttachments
    }

    var body: some View {
        HStack(spacing: 10) {
            AttachmentMenuButton(
                onCamera: onCamera,
                onPhotoLibrary: onPhotoLibrary,
                onFile: onFile
            )

            TextField("패트리에게 물어보기", text: $text, axis: .vertical)
                .font(.system(.body, design: .rounded))
                .lineLimit(1...4)
                .padding(.vertical, 12)
                .submitLabel(.send)
                .onSubmit {
                    guard canSend else { return }
                    onSend()
                }

            Button(action: onSend) {
                LucideSymbol("arrow.up.circle", size: 31, color: canSend ? .primary : .secondary)
            }
            .buttonStyle(PressButtonStyle())
            .disabled(!canSend)
            .opacity(canSend ? 1 : 0.48)
        }
        .padding(.leading, 8)
        .padding(.trailing, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial)
        .clipShape(Capsule(style: .continuous))
        .overlay {
            Capsule(style: .continuous)
                .strokeBorder(Color.primary.opacity(0.09), lineWidth: 1)
        }
    }
}

private struct AttachmentMenuButton: View {
    var onCamera: () -> Void
    var onPhotoLibrary: () -> Void
    var onFile: () -> Void

    var body: some View {
        Menu {
            Button(action: onCamera) {
                Label("카메라 촬영", systemImage: "camera")
            }

            Button(action: onPhotoLibrary) {
                Label("사진 보관함", systemImage: "photo.on.rectangle")
            }

            Button(action: onFile) {
                Label("파일 첨부", systemImage: "doc")
            }
        } label: {
            LucideSymbol("plus", size: 20, color: .secondary)
                .frame(width: 34, height: 34)
                .contentShape(Circle())
        }
        .menuOrder(.fixed)
        .buttonStyle(PressButtonStyle())
        .accessibilityLabel("첨부 메뉴 열기")
    }
}

private struct LucideSymbol: View {
    let name: String
    let size: CGFloat
    let color: Color

    init(_ name: String, size: CGFloat, color: Color) {
        self.name = name
        self.size = size
        self.color = color
    }

    var body: some View {
        Image(systemName: name)
            .symbolRenderingMode(.hierarchical)
            .font(.system(size: size, weight: .light, design: .rounded))
            .foregroundStyle(color, color.opacity(0.62))
    }
}

private enum ChatBubbleLayout {
    static let maxBubbleWidth: CGFloat = 300
    static let attachmentItemWidth: CGFloat = 118
}

private struct PressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.74), value: configuration.isPressed)
    }
}

private struct BubbleBackgroundModifier: ViewModifier {
    let isUser: Bool

    func body(content: Content) -> some View {
        content
            .background {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(.regularMaterial)
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(isUser ? Color.blue.opacity(0.42) : Color.clear)
                    }
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(
                                isUser ? Color.blue.opacity(0.34) : Color.primary.opacity(0.09),
                                lineWidth: 1
                            )
                    }
            }
    }
}

private struct MediaCardBackgroundModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(3)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.09), lineWidth: 1)
            }
    }
}

private extension View {
    func bubbleBackground(isUser: Bool) -> some View {
        modifier(BubbleBackgroundModifier(isUser: isUser))
    }

    func mediaCardBackground() -> some View {
        modifier(MediaCardBackgroundModifier())
    }

    func messageTransition() -> some View {
        transition(.asymmetric(
            insertion: .move(edge: .bottom).combined(with: .opacity),
            removal: .opacity
        ))
    }
}

private struct CameraPicker: UIViewControllerRepresentable {
    var onImagePicked: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        picker.allowsEditing = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) { }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let parent: CameraPicker

        init(parent: CameraPicker) {
            self.parent = parent
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                parent.onImagePicked(image)
            }

            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

#Preview {
    NavigationStack {
        AIChatView()
    }
}
