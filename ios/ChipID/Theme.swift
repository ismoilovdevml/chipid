import SwiftUI

/// Palette from the ChipID design (dark only), same values as the Android app.
enum C {
    static let bg = Color(hex: 0x0E1014)
    static let scanBg = Color(hex: 0x07080A)
    static let surface = Color(hex: 0x171A20)
    static let surface2 = Color(hex: 0x15181D)
    static let control = Color(hex: 0x1A1D23)
    static let border = Color(hex: 0x262A33)
    static let fieldBorder = Color(hex: 0x2D3440)
    static let divider = Color(hex: 0x23272F)
    static let outline = Color(hex: 0x3A404B)
    static let primary = Color(hex: 0x7DB4FF)
    static let onPrimary = Color(hex: 0x0B1A2E)
    static let selectedBg = Color(hex: 0x16202E)
    static let iconBg = Color(hex: 0x22324A)
    static let iconBgMuted = Color(hex: 0x20242C)
    static let track = Color(hex: 0x2A2F38)
    static let track2 = Color(hex: 0x20242C)
    static let text = Color(hex: 0xF2F4F7)
    static let text2 = Color(hex: 0xC3CAD5)
    static let muted = Color(hex: 0x9AA3B2)
    static let faint = Color(hex: 0x6B7380)
    static let success = Color(hex: 0x4ADE9B)
    static let successBg = Color(hex: 0x12261C)
    static let successBorder = Color(hex: 0x1F4A33)
    static let successText = Color(hex: 0xA7D9BF)
    static let warn = Color(hex: 0xF5B24B)
    static let warnBg = Color(hex: 0x2A2112)
    static let warnBorder = Color(hex: 0x4A3A1C)
    static let warnText = Color(hex: 0xE9CF9E)
    static let danger = Color(hex: 0xF87171)
    static let dangerBg = Color(hex: 0x2A1416)
    static let dangerBorder = Color(hex: 0x4E2328)
    static let dangerText = Color(hex: 0xF1B5B5)
    static let paper = Color(hex: 0x2A2620)
    static let paperInk = Color(hex: 0x4A443A)
    static let ring1 = Color(hex: 0x1F2A3A)
    static let ring2 = Color(hex: 0x2A3B55)
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// Text styles from the design: Inter for text, JetBrains Mono for document data.
enum T {
    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font { .custom("Inter", size: size).weight(weight) }
    static func mono(_ size: CGFloat, _ weight: Font.Weight = .medium) -> Font { .custom("JetBrains Mono", size: size).weight(weight) }

    static let h1 = inter(28, .bold)
    static let h2 = inter(22, .bold)
    static let h3 = inter(24, .bold)
    static let title = inter(17, .semibold)
    static let nav = inter(15, .semibold)
    static let body = inter(15)
    static let small = inter(13)
    static let tiny = inter(12)
    static let label = inter(14, .medium)
    static let overline = inter(13, .semibold)
    static let monoBody = mono(15)
    static let monoSmall = mono(13)
}

/// SF Symbols standing in for the Android vector icons.
enum Ic {
    static let passport = "book.closed.fill"
    static let idCard = "person.text.rectangle.fill"
    static let lock = "lock.fill"
    static let scan = "viewfinder"
    static let info = "info.circle"
    static let back = "chevron.left"
    static let close = "xmark"
    static let camera = "camera.fill"
    static let flash = "bolt.fill"
    static let pencil = "pencil"
    static let nfc = "wave.3.right"
    static let check = "checkmark"
    static let alert = "exclamationmark.triangle.fill"
    static let user = "person.fill"
    static let eye = "eye"
    static let chevron = "chevron.right"
    static let demo = "doc.text.magnifyingglass"
}
