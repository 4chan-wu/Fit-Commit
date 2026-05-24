import SwiftUI

struct FitcommitCardBackground: View {
    var cornerRadius: CGFloat = 20
    var borderOpacity: Double = 0.08

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(.regularMaterial)
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(Color.primary.opacity(borderOpacity), lineWidth: 1)
            }
    }
}

private struct FitcommitCardStyleModifier: ViewModifier {
    var cornerRadius: CGFloat
    var borderOpacity: Double

    func body(content: Content) -> some View {
        content
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(Color.primary.opacity(borderOpacity), lineWidth: 1)
            }
    }
}

extension View {
    func fitcommitCardStyle(
        cornerRadius: CGFloat = 20,
        borderOpacity: Double = 0.08
    ) -> some View {
        modifier(FitcommitCardStyleModifier(cornerRadius: cornerRadius, borderOpacity: borderOpacity))
    }
}
