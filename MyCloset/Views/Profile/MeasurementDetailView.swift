import SwiftUI
import SwiftData

struct MeasurementDetailView: View {
    let profile: BodyProfile
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // 상단: 측정 날짜 (yyyy mm dd 형식)
                Text(profile.date.formatted(date: .long, time: .omitted))
                    .font(.headline)
                    .foregroundColor(.secondary)
                    .padding(.top, 10)

                // 잡지 화보(Magazine) 스타일의 메인 사진 오버레이
                if let data = profile.imageData, let image = UIImage(data: data) {
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
                                        Text(profile.bodyShape.uppercased())
                                            .font(.caption.weight(.black))
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 4)
                                            .background(.ultraThinMaterial)
                                            .cornerRadius(8)
                                            .environment(\.colorScheme, .dark)
                                        
                                        Text(String(format: "%.1f cm", profile.height * 100))
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
                } else {
                    // 이미지가 없을 경우 대체 뷰
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(Color(uiColor: .secondarySystemGroupedBackground))
                        .frame(height: 300)
                        .overlay(Image(systemName: "person.crop.rectangle").font(.system(size: 60)).foregroundColor(.secondary))
                        .padding(.horizontal)
                }

                // Main Metrics (Apple Health Card Style)
                VStack(spacing: 0) {
                    HealthCardRow(icon: "arrow.left.and.right.square", label: "어깨 너비", value: String(format: "%.1f cm", profile.shoulderWidth * 100), showDivider: true)
                    HealthCardRow(icon: "point.topleft.down.curvedto.point.bottomright.up", label: "골반 너비", value: String(format: "%.1f cm", profile.hipWidth * 100), showDivider: true)
                    HealthCardRow(icon: "hand.raised.app", label: "팔 길이", value: String(format: "%.1f cm", profile.armLength * 100), showDivider: true)
                    HealthCardRow(icon: "figure.walk", label: "다리 길이", value: String(format: "%.1f cm", profile.legLength * 100), showDivider: false)
                }
                .background(Color(uiColor: .secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .padding(.horizontal)

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
                    Text(profile.recommendedFit)
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
                
                Spacer(minLength: 40)
            }
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("상세 리포트")
        .navigationBarTitleDisplayMode(.inline)
    }
}
