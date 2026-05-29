import SwiftUI

struct ChatRoom: Identifiable, Hashable {
    let id: UUID
    let title: String
    let lastMessage: String
    let timestamp: Date
    let unreadCount: Int

    init(
        id: UUID = UUID(),
        title: String,
        lastMessage: String,
        timestamp: Date,
        unreadCount: Int
    ) {
        self.id = id
        self.title = title
        self.lastMessage = lastMessage
        self.timestamp = timestamp
        self.unreadCount = unreadCount
    }
}

struct ChatHistoryView: View {
    @State private var searchText = ""
    @State private var hasAppeared = false

    private let chatRooms: [ChatRoom] = ChatRoom.dummyRooms

    private var filteredRooms: [ChatRoom] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return chatRooms }

        return chatRooms.filter {
            $0.title.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()

            BackgroundBlurView()
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(Array(filteredRooms.enumerated()), id: \.element.id) { index, room in
                        NavigationLink {
                            AIChatView(chatRoomID: room.id)
                        } label: {
                            ChatRoomCard(room: room)
                                .opacity(hasAppeared ? 1 : 0)
                                .offset(y: hasAppeared ? 0 : 28)
                                .animation(
                                    .spring(response: 0.5, dampingFraction: 0.86)
                                        .delay(Double(index) * 0.045),
                                    value: hasAppeared
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }
            .searchable(text: $searchText, prompt: "채팅 제목 검색")
        }
        .navigationTitle("채팅 기록")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    AIChatView()
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .symbolRenderingMode(.hierarchical)
                        .font(.system(size: 24, weight: .light))
                        .foregroundStyle(.primary)
                        .accessibilityLabel("새 채팅")
                }
            }
        }
        .onAppear {
            hasAppeared = true
        }
    }
}

struct BackgroundBlurView: View {
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.18))
                    .frame(width: 300, height: 300)
                    .blur(radius: 88)
                    .position(x: geometry.size.width * 0.16, y: geometry.size.height * 0.16)

                Circle()
                    .fill(Color.purple.opacity(0.17))
                    .frame(width: 340, height: 340)
                    .blur(radius: 98)
                    .position(x: geometry.size.width * 0.88, y: geometry.size.height * 0.38)

                Circle()
                    .fill(Color.cyan.opacity(0.1))
                    .frame(width: 240, height: 240)
                    .blur(radius: 76)
                    .position(x: geometry.size.width * 0.34, y: geometry.size.height * 0.78)
            }
        }
    }
}

private struct ChatRoomCard: View {
    let room: ChatRoom
    @Environment(\.colorScheme) private var colorScheme

    private var borderColor: Color {
        colorScheme == .dark ? .white.opacity(0.18) : .black.opacity(0.08)
    }

    private var unreadBackground: Color {
        colorScheme == .dark ? .white.opacity(0.16) : .black.opacity(0.08)
    }

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            Image(systemName: "message")
                .font(.system(size: 22, weight: .light))
                .foregroundStyle(.primary)
                .frame(width: 42, height: 42)
                .background(.ultraThinMaterial, in: Circle())
                .overlay {
                    Circle()
                        .stroke(borderColor, lineWidth: 0.8)
                }

            VStack(alignment: .leading, spacing: 7) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(room.title)
                        .font(.system(.headline, design: .rounded).weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Spacer(minLength: 8)

                    Text(room.timestamp.relativeChatTime)
                        .font(.system(.caption, design: .rounded))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Text(room.lastMessage)
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }

            if room.unreadCount > 0 {
                Text("\(room.unreadCount)")
                    .font(.system(.caption2, design: .rounded).weight(.bold))
                    .foregroundStyle(.primary)
                    .frame(minWidth: 24, minHeight: 24)
                    .background(unreadBackground, in: Capsule())
                    .overlay {
                        Capsule()
                            .stroke(borderColor, lineWidth: 0.8)
                    }
                    .accessibilityLabel("읽지 않은 메시지 \(room.unreadCount)개")
            }

            Image(systemName: "chevron.right")
                .font(.system(.footnote, design: .rounded).weight(.light))
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(borderColor, lineWidth: 0.8)
        }
        .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

private extension ChatRoom {
    static let dummyRooms: [ChatRoom] = [
        ChatRoom(
            title: "Test0",
            lastMessage: "아아",
            timestamp: Calendar.current.date(byAdding: .minute, value: -18, to: Date()) ?? Date(),
            unreadCount: 2
        ),
        ChatRoom(
            title: "Test2",
            lastMessage: "피카피카!",
            timestamp: Calendar.current.date(byAdding: .hour, value: -3, to: Date()) ?? Date(),
            unreadCount: 0
        ),
        ChatRoom(
            title: "Test3",
            lastMessage: "하5",
            timestamp: Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date(),
            unreadCount: 4
        ),
        ChatRoom(
            title: "Test4",
            lastMessage: "싫어",
            timestamp: Calendar.current.date(byAdding: .day, value: -3, to: Date()) ?? Date(),
            unreadCount: 0
        ),
        ChatRoom(
            title: "Test5",
            lastMessage: "사실 좋아해",
            timestamp: Calendar.current.date(byAdding: .day, value: -6, to: Date()) ?? Date(),
            unreadCount: 1
        ),
        ChatRoom(
            title: "Test6",
            lastMessage: "바2",
            timestamp: Calendar.current.date(byAdding: .day, value: -9, to: Date()) ?? Date(),
            unreadCount: 0
        )
    ]
}

private extension Date {
    var relativeChatTime: String {
        let calendar = Calendar.current

        if calendar.isDateInToday(self) {
            return formatted(date: .omitted, time: .shortened)
        }

        if calendar.isDateInYesterday(self) {
            return "어제"
        }

        return formatted(.dateTime.month(.defaultDigits).day(.defaultDigits))
    }
}

#Preview {
    NavigationStack {
        ChatHistoryView()
    }
}
