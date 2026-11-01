import NFCPassportReader
import SwiftUI

struct NfcView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        ScreenFrame(gap: 16) {
            TopBar(title: "Step 2 of 3") {
                RoundIconButton(icon: Ic.close, label: "Cancel", size: 16) { m.cancelRead() }
            }
            StepBar(step: 2)
            Illustration(reading: m.reading)
            VStack(spacing: 8) {
                Text(m.error != nil ? "Reading stopped" : m.progress != nil ? "Don't move your phone" : "Hold the document to your iPhone")
                    .font(T.h3).foregroundStyle(C.text)
                Text(m.docType == .passport
                     ? "Rest the top edge of your iPhone on the passport cover. The chip is usually in the back cover or the photo page."
                     : "Lay the card flat and hold the top of your iPhone against its middle.")
                    .font(T.body).foregroundStyle(C.muted)
            }
            .multilineTextAlignment(.center).frame(maxWidth: .infinity)

            if let error = m.error {
                Panel(color: C.dangerBg, border: C.dangerBorder, padding: 16) {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: Ic.alert).foregroundStyle(C.danger)
                        Text(error).font(T.body).foregroundStyle(C.dangerText)
                    }
                    if ChipReader.nfcAvailable {
                        PrimaryButton(text: "Try again") { m.read() }.padding(.top, 12)
                    }
                    #if DEBUG || targetEnvironment(simulator)
                    if !ChipReader.nfcAvailable {
                        PrimaryButton(text: "Open the demo document instead", icon: Ic.demo) { m.openDemo() }.padding(.top, 12)
                    }
                    #endif
                }
            } else if !m.reading {
                PrimaryButton(text: "Start reading", icon: Ic.nfc) { m.read() }
            }

            let fraction = Double(m.progress?.fraction ?? 0)
            VStack(spacing: 8) {
                HStack {
                    Text("Reading data").font(T.small).foregroundStyle(C.text2)
                    Spacer()
                    Text("\(Int(fraction * 100))%").font(T.monoSmall).foregroundStyle(C.primary)
                }
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(C.track2)
                        Capsule().fill(m.error != nil ? C.danger : C.primary).frame(width: g.size.width * fraction)
                    }
                }
                .frame(height: 8)
                .animation(.easeOut(duration: 0.25), value: fraction)
            }

            Panel(padding: 16) {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach([(ChipStage.found, "Chip found"), (.channel, "Secure channel opened"), (.data, "Personal data and photo"), (.verify, "Digital signature check")], id: \.0) { stage, label in
                        StageRow(text: label, state: state(of: stage))
                    }
                }
            }
        } bottom: {
            GhostButton(text: "Where is the chip? Help") { m.route = .help }
        }
        .onAppear { if m.progress == nil && m.error == nil { m.read() } }
    }

    /// 0 pending, 1 active, 2 done, 3 failed.
    private func state(of stage: ChipStage) -> Int {
        guard let current = m.progress?.stage else { return 0 }
        if stage.rawValue < current.rawValue { return 2 }
        if stage == current { return m.error != nil ? 3 : 1 }
        return 0
    }
}

private struct StageRow: View {
    let text: String
    let state: Int

    var body: some View {
        HStack(spacing: 12) {
            Group {
                switch state {
                case 2: Image(systemName: Ic.check).font(.system(size: 12, weight: .bold)).foregroundStyle(C.successBg).frame(width: 24, height: 24).background(C.success, in: Circle())
                case 1: ProgressView().tint(C.primary).frame(width: 24, height: 24)
                case 3: Image(systemName: Ic.close).font(.system(size: 11, weight: .bold)).foregroundStyle(C.dangerBg).frame(width: 24, height: 24).background(C.danger, in: Circle())
                default: Circle().stroke(C.outline, lineWidth: 2).frame(width: 24, height: 24)
                }
            }
            Text(text).font(T.inter(15, state == 1 ? .semibold : .regular)).foregroundStyle(state == 0 ? C.muted : C.text)
        }
    }
}

private struct Illustration: View {
    let reading: Bool
    @State private var pulse = false

    var body: some View {
        ZStack {
            Circle().stroke(C.ring1, lineWidth: 1).frame(width: 200, height: 200).scaleEffect(reading && pulse ? 1.05 : 0.9)
            Circle().stroke(C.ring2, lineWidth: 1).frame(width: 150, height: 150).scaleEffect(reading && pulse ? 1.05 : 0.9)
            RoundedRectangle(cornerRadius: 12).fill(C.paper).frame(width: 160, height: 108).rotationEffect(.degrees(-6)).offset(y: 32)
            RoundedRectangle(cornerRadius: 20).fill(C.control).frame(width: 84, height: 158)
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(C.outline, lineWidth: 3))
                .overlay(Image(systemName: Ic.nfc).font(.system(size: 30)).foregroundStyle(C.primary).rotationEffect(.degrees(-90)).offset(y: -50))
                .offset(y: -12)
        }
        .frame(maxWidth: .infinity).frame(height: 210)
        .onAppear { withAnimation(.easeInOut(duration: 1.2).repeatForever()) { pulse = true } }
    }
}

struct HelpView: View {
    @EnvironmentObject var m: AppModel

    var body: some View {
        ScreenFrame {
            TopBar(title: "Finding the chip") {
                RoundIconButton(icon: Ic.close, label: "Close", size: 16) { m.route = .nfc }
            }
            ForEach([
                ("Remove the phone case", "Thick or magnetic cases block NFC."),
                ("Use the top of the iPhone", "The iPhone's NFC antenna is at the top edge, next to the cameras."),
                ("Try both covers, then the photo page", "Passport chips are in the back cover, the front cover or the plastic data page, depending on the country."),
                ("Hold still for 10 seconds", "The photo is the largest file. Moving the document breaks the connection; slide slowly until it connects, then stop."),
            ], id: \.0) { t, s in
                Panel(padding: 16) {
                    Text(t).font(T.title).foregroundStyle(C.text)
                    Text(s).font(T.small).foregroundStyle(C.muted).padding(.top, 6)
                }
            }
            if m.docType == .idCard {
                Text("ID cards: lay the card flat and keep it still. If the chip refuses the key, check the CAN or switch to the MRZ details.")
                    .font(T.small).foregroundStyle(C.muted).padding(.horizontal, 4)
            }
        } bottom: {
            PrimaryButton(text: "Got it") { m.route = .nfc }
        }
    }
}
