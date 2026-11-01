import SwiftUI

struct HomeView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        ScreenFrame {
            HStack(spacing: 10) {
                Image("Mark").resizable().renderingMode(.template).foregroundStyle(C.onPrimary)
                    .frame(width: 24, height: 24).frame(width: 36, height: 36)
                    .background(C.primary, in: RoundedRectangle(cornerRadius: 10))
                Text("ChipID").font(T.inter(18, .bold)).foregroundStyle(C.text)
                Spacer()
                Button { m.route = .about } label: {
                    Image(systemName: Ic.info).font(.system(size: 20)).foregroundStyle(C.muted).frame(width: 44, height: 44)
                }
                .accessibilityLabel("About & privacy")
            }
            VStack(alignment: .leading, spacing: 8) {
                Text("Which document are we checking?").font(T.h1).foregroundStyle(C.text)
                Text("ChipID reads the chip and confirms the document is genuine.").font(T.body).foregroundStyle(C.muted)
            }
            VStack(spacing: 12) {
                DocOption(icon: Ic.passport, title: "Biometric passport", sub: "Scan the photo page", selected: m.docType == .passport) { m.docType = .passport }
                DocOption(icon: Ic.idCard, title: "ID card", sub: "Scan the back or use the CAN", selected: m.docType == .idCard) { m.docType = .idCard }
            }
            Panel {
                Text("HOW IT WORKS").font(T.overline).tracking(0.8).foregroundStyle(C.muted)
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(Array(["Scan the document", "Hold your iPhone to the chip", "See the verified result"].enumerated()), id: \.offset) { i, s in
                        HStack(spacing: 12) {
                            Text("\(i + 1)").font(T.inter(13, .bold)).foregroundStyle(C.primary)
                                .frame(width: 28, height: 28).background(C.iconBg, in: Circle())
                            Text(s).font(T.body).foregroundStyle(C.text)
                        }
                    }
                }
                .padding(.top, 14)
            }
        } bottom: {
            HStack(spacing: 8) {
                Image(systemName: Ic.lock).font(.system(size: 13)).foregroundStyle(C.success)
                Text("Your data never leaves this phone").font(T.small).foregroundStyle(C.muted)
            }
            .padding(.bottom, 4)
            PrimaryButton(text: "Start scanning", icon: Ic.scan) { m.route = .scan }
            #if DEBUG || targetEnvironment(simulator)
            GhostButton(text: "Try a demo document", icon: Ic.demo, height: 40) { m.openDemo() }
            #endif
        }
    }
}

private struct DocOption: View {
    let icon: String
    let title: String
    let sub: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                IconTile(icon: icon, bg: selected ? C.iconBg : C.iconBgMuted, tint: selected ? C.primary : C.text2)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(T.title).foregroundStyle(C.text)
                    Text(sub).font(T.small).foregroundStyle(C.muted)
                }
                Spacer()
                if selected {
                    Image(systemName: Ic.check).font(.system(size: 12, weight: .bold)).foregroundStyle(C.onPrimary)
                        .frame(width: 24, height: 24).background(C.primary, in: Circle())
                } else {
                    Circle().stroke(C.outline, lineWidth: 2).frame(width: 24, height: 24)
                }
            }
            .padding(18)
            .background(selected ? C.selectedBg : C.surface, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selected ? C.primary : C.border, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
