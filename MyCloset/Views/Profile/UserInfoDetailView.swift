import SwiftUI
import SwiftData
import PhotosUI

struct UserInfoDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query private var userProfiles: [UserProfile]

    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var profileImage: UIImage?
    @State private var realName = ""
    @State private var nickname = ""
    @State private var height = ""
    @State private var weight = ""
    @State private var preferredStyle = ""

    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()

            BackgroundBlurView()
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    photoPicker

                    VStack(spacing: 12) {
                        UserInfoTextField(
                            title: "실명",
                            text: $realName,
                            systemImage: "person"
                        )

                        UserInfoTextField(
                            title: "닉네임",
                            text: $nickname,
                            systemImage: "person.text.rectangle"
                        )

                        UserInfoTextField(
                            title: "키(cm)",
                            text: $height,
                            systemImage: "ruler",
                            keyboardType: .decimalPad
                        )

                        UserInfoTextField(
                            title: "몸무게(kg)",
                            text: $weight,
                            systemImage: "scalemass",
                            keyboardType: .decimalPad
                        )

                        UserInfoTextField(
                            title: "선호 스타일",
                            text: $preferredStyle,
                            systemImage: "sparkles"
                        )
                    }

                    Button(action: saveProfile) {
                        Text("저장")
                            .font(.system(.headline, design: .rounded).weight(.bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 20, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.22), lineWidth: 1)
                            }
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 8)

                    Spacer().frame(height: 40)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
            }
        }
        .navigationTitle("내 정보")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: loadExistingProfile)
        .onChange(of: selectedPhotoItem) { _, item in
            Task {
                await loadSelectedPhoto(item)
            }
        }
    }

    private var photoPicker: some View {
        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
            VStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.primary.opacity(0.06))
                        .frame(width: 112, height: 112)

                    if let profileImage {
                        Image(uiImage: profileImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 112, height: 112)
                            .clipShape(Circle())
                    } else {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 72, weight: .regular))
                            .symbolRenderingMode(.hierarchical)
                            .foregroundStyle(.primary.opacity(0.82), .secondary.opacity(0.4))
                    }

                    Image(systemName: "camera.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(Color.accentColor, in: Circle())
                        .offset(x: 38, y: 38)
                }

                Text("프로필 사진 설정")
                    .font(.system(.subheadline, design: .rounded).weight(.bold))
                    .foregroundStyle(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding(20)
            .fitcommitCardStyle()
        }
        .buttonStyle(.plain)
    }

    private func loadExistingProfile() {
        guard let profile = userProfiles.first else { return }

        realName = profile.realName
        nickname = profile.nickname
        height = profile.height > 0 ? cleanNumber(profile.height) : ""
        weight = profile.weight > 0 ? cleanNumber(profile.weight) : ""
        preferredStyle = profile.preferredStyle

        if let imageData = profile.imageData {
            profileImage = UIImage(data: imageData)
        }
    }

    @MainActor
    private func loadSelectedPhoto(_ item: PhotosPickerItem?) async {
        guard let item,
              let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else {
            return
        }

        profileImage = image
    }

    private func saveProfile() {
        let profile = userProfiles.first ?? UserProfile()

        profile.realName = realName.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.nickname = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.height = Double(height.replacingOccurrences(of: ",", with: ".")) ?? 0
        profile.weight = Double(weight.replacingOccurrences(of: ",", with: ".")) ?? 0
        profile.preferredStyle = preferredStyle.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.updateImage(profileImage)

        if userProfiles.isEmpty {
            modelContext.insert(profile)
        }

        do {
            try modelContext.save()
            dismiss()
        } catch {
            print("UserProfile 저장 실패: \(error)")
        }
    }

    private func cleanNumber(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(format: "%.1f", value)
    }
}

private struct UserInfoTextField: View {
    let title: String
    @Binding var text: String
    let systemImage: String
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .medium, design: .rounded))
                .foregroundStyle(.secondary)
                .frame(width: 24)

            TextField(title, text: $text)
                .font(.system(.body, design: .rounded))
                .keyboardType(keyboardType)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 18)
        .frame(maxWidth: .infinity)
        .frame(height: 56)
        .fitcommitCardStyle()
    }
}

#Preview {
    NavigationStack {
        UserInfoDetailView()
    }
    .modelContainer(for: [UserProfile.self, BodyProfile.self, ClosetItem.self], inMemory: true)
}
