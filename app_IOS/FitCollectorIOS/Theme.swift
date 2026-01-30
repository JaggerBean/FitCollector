import SwiftUI

enum AppColors {
    static let healthGreen = Color(hex: 0xFF2E7D32)
    static let healthLightGreen = Color(hex: 0xFFE8F5E9)
    static let healthBlue = Color(hex: 0xFF1565C0)
    static let healthLightBlue = Color(hex: 0xFFE3F2FD)
    static let minecraftDirt = Color(hex: 0xFF795548)
    static let minecraftGrass = Color(hex: 0xFF4CAF50)

    static let darkBackground = Color(hex: 0xFF121212)
    static let darkSurface = Color(hex: 0xFF1E1E1E)
    static let darkOnSurface = Color(hex: 0xFFE1E1E1)
}

extension Color {
    init(hex: UInt32) {
        let a = Double((hex >> 24) & 0xFF) / 255.0
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

struct CardSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

extension View {
    func cardSurface() -> some View { modifier(CardSurface()) }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(AppColors.healthGreen.opacity(configuration.isPressed ? 0.85 : 1))
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Color(.secondarySystemBackground))
            .foregroundColor(AppColors.healthGreen)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
