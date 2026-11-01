import ChipCore
import SwiftUI

struct RoundIconButton: View {
    let icon: String
    let label: String
    var tint: Color = C.text
    var size: CGFloat = 18
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: size, weight: .semibold)).foregroundStyle(tint)
                .frame(width: 44, height: 44).background(C.control, in: Circle())
        }
        .accessibilityLabel(label)
    }
}

struct TopBar<Left: View, Right: View>: View {
    let title: String
    @ViewBuilder var left: Left
    @ViewBuilder var right: Right

    var body: some View {
        HStack {
            left.frame(width: 44, alignment: .leading)
            Text(title).font(T.nav).foregroundStyle(C.text).frame(maxWidth: .infinity)
            right.frame(width: 44, alignment: .trailing)
        }
    }
}

extension TopBar where Right == EmptyView {
    init(title: String, @ViewBuilder left: () -> Left) {
        self.title = title
        self.left = left()
        self.right = EmptyView()
    }
}

struct StepBar: View {
    let step: Int
    var of = 3

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<of, id: \.self) { i in
                RoundedRectangle(cornerRadius: 2).fill(i < step ? C.primary : C.track).frame(height: 4)
            }
        }
    }
}

struct PrimaryButton: View {
    let text: String
    var enabled = true
    var icon: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Image(systemName: icon).font(.system(size: 18, weight: .semibold)) }
                Text(text).font(T.title)
            }
            .foregroundStyle(enabled ? C.onPrimary : C.muted)
            .frame(maxWidth: .infinity).frame(height: 56)
            .background(enabled ? C.primary : C.track, in: RoundedRectangle(cornerRadius: 16))
        }
        .disabled(!enabled)
    }
}

struct GhostButton: View {
    let text: String
    var icon: String?
    var height: CGFloat = 48
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 15)) }
                Text(text).font(T.inter(15, .medium))
            }
            .foregroundStyle(C.text2).frame(maxWidth: .infinity).frame(height: height).contentShape(Rectangle())
        }
    }
}

struct Panel<Content: View>: View {
    var radius: CGFloat = 18
    var color: Color = C.surface
    var border: Color?
    var padding: CGFloat = 18
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background(color, in: RoundedRectangle(cornerRadius: radius))
            .overlay { if let border { RoundedRectangle(cornerRadius: radius).stroke(border, lineWidth: 1) } }
    }
}

struct IconTile: View {
    let icon: String
    let bg: Color
    let tint: Color
    var box: CGFloat = 52

    var body: some View {
        Image(systemName: icon).font(.system(size: box * 0.42)).foregroundStyle(tint)
            .frame(width: box, height: box).background(bg, in: RoundedRectangle(cornerRadius: 14))
    }
}

struct LockNote: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: Ic.lock).font(.system(size: 13)).foregroundStyle(C.success).padding(.top, 1)
            Text(text).font(T.tiny).foregroundStyle(C.muted).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(C.surface, in: RoundedRectangle(cornerRadius: 14))
    }
}

struct ValueTile: View {
    let label: String
    let value: String
    var valueColor: Color = C.text
    var compact = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(T.inter(compact ? 11 : 12)).foregroundStyle(C.muted)
            Text(value).font(compact ? T.monoSmall : T.monoBody).foregroundStyle(valueColor).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, compact ? 10 : 14).padding(.vertical, compact ? 10 : 12)
        .background(compact ? C.surface2 : C.surface, in: RoundedRectangle(cornerRadius: compact ? 12 : 14))
    }
}

/// Status mark used in technical lists.
struct StatusIcon: View {
    let check: Check
    var size: CGFloat = 18

    var body: some View {
        let (icon, tint): (String, Color) = switch check {
        case .pass: ("checkmark.circle.fill", C.success)
        case .fail: ("xmark.circle.fill", C.danger)
        case .unknown: ("exclamationmark.circle.fill", C.warn)
        case .skipped: ("minus.circle", C.faint)
        }
        Image(systemName: icon).font(.system(size: size)).foregroundStyle(tint).accessibilityLabel(check.rawValue)
    }
}

/// Full-screen layout: scrollable body, pinned bottom actions, design gutters.
struct ScreenFrame<Content: View, Bottom: View>: View {
    var bg: Color = C.bg
    var gap: CGFloat = 20
    var scroll = true
    @ViewBuilder var content: Content
    @ViewBuilder var bottom: Bottom

    var body: some View {
        VStack(spacing: 12) {
            if scroll {
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: gap) { content }.padding(.top, 8)
                }
            } else {
                VStack(alignment: .leading, spacing: gap) { content }.padding(.top, 8)
            }
            VStack(spacing: 10) { bottom }
        }
        .padding(.horizontal, 20).padding(.bottom, 16)
        .background(bg.ignoresSafeArea())
    }
}

extension ScreenFrame where Bottom == EmptyView {
    init(bg: Color = C.bg, gap: CGFloat = 20, scroll: Bool = true, @ViewBuilder content: () -> Content) {
        self.bg = bg; self.gap = gap; self.scroll = scroll
        self.content = content()
        self.bottom = EmptyView()
    }
}
