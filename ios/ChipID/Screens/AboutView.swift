import ChipCore
import SwiftUI

struct AboutView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        ScreenFrame(gap: 14) {
            TopBar(title: "About & privacy") {
                RoundIconButton(icon: Ic.back, label: "Back") { m.route = .home }
            }
            Block(title: "Privacy") {
                Line(check: .pass, text: "No network code: ChipID never opens a connection. iOS has no per-app network permission, so this is a property of the open-source code rather than an OS switch.")
                Line(check: .pass, text: "Camera frames are recognised on the phone and never stored.")
                Line(check: .pass, text: "Chip data stays in memory only. Nothing is written to storage, and it is cleared when you start a new check.")
                Line(check: .pass, text: "The screen is hidden in the app switcher.")
            }
            Block(title: "Trust list") {
                if let t = m.trust {
                    Line(check: t.bundledTrusted ? .pass : .fail, text: t.bundledTrusted
                         ? "Master list signature chains to the pinned German CSCA (BSI)."
                         : "Master list rejected or missing: no bundled anchors are trusted.")
                    Text("\(t.anchors.count) country signing certificates (CSCA) from \(t.countries) countries.").font(T.inter(14)).foregroundStyle(C.text)
                    if t.userAddedCount > 0 {
                        Line(check: .unknown, text: "\(t.userAddedCount) user-added certificates. Documents verified only through them are flagged.")
                    }
                    ForEach(t.sources, id: \.self) { Text($0).font(T.mono(11)).foregroundStyle(C.muted) }
                    Text("Add more anchors by copying .ml, .der, .cer, .crt or .pem files to Files → On My iPhone → ChipID → csca.")
                        .font(T.tiny).foregroundStyle(C.muted)
                } else {
                    Text("Loading…").font(T.small).foregroundStyle(C.muted)
                }
            }
            Block(title: "How verification works") {
                Text("PACE or BAC opens an encrypted channel with the key from the MRZ or CAN. Passive Authentication checks every data group against the signed security object (SOD) and the signer certificate against the issuing country's CSCA. Chip or Active Authentication proves the chip holds a private key that cannot be copied. Standards: ICAO Doc 9303, BSI TR-03110.")
                    .font(T.small).foregroundStyle(C.text2)
            }
            Block(title: "Open source") {
                Text("NFCPassportReader by Andy Qua (MIT), OpenSSL (Apache 2.0), Inter and JetBrains Mono (SIL OFL 1.1). CSCA master list: German Federal Office for Information Security (BSI).")
                    .font(T.small).foregroundStyle(C.muted)
            }
        }
    }
}

private struct Block<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        Panel(padding: 16) {
            Text(title.uppercased()).font(T.overline).tracking(0.8).foregroundStyle(C.muted)
            VStack(alignment: .leading, spacing: 10) { content }.padding(.top, 10)
        }
    }
}

private struct Line: View {
    let check: Check
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            StatusIcon(check: check, size: 15).padding(.top, 2)
            Text(text).font(T.inter(14)).foregroundStyle(C.text).fixedSize(horizontal: false, vertical: true)
        }
    }
}
