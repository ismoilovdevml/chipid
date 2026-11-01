import ChipCore
import SwiftUI

struct ResultView: View {
    @EnvironmentObject var m: AppModel
    let report: ReadReport

    var body: some View {
        let r = report
        let pa = r.passive
        // The chain ends at a certificate the user added, not one from the verified master list.
        let userAnchor = m.trust?.isUserAdded(pa.csca) == true
        let signature = combine(pa.dataGroupHashes, pa.sodSignature)
        let flags = ReportModel.signerFlags(r)
        let foreignSigner = flags.countryMatches == false
        let verdict: Check = pa.overall == .fail || r.chipGenuine == .fail ? .fail
            : (pa.overall == .unknown || userAnchor || flags.testPki || foreignSigner) ? .unknown : .pass
        let country = ReportModel.signerCountry(r)
        let issuing = r.dg1.map { Countries.name($0.issuingState) } ?? country
        let warning: (String, String)? = verdict != .unknown ? nil
            : flags.testPki ? ("Signed with a test certificate", "The chip data is intact but signed by a development certificate (\(flags.organisation ?? "?")), not by an authority of \(issuing). Its origin cannot be confirmed.")
            : foreignSigner ? ("Signed by another country", "The signer belongs to \(country), but the document is issued by \(issuing).")
            : userAnchor ? ("Verified with a certificate you added", "It is not from the verified master list. Trust this only if you know where that certificate came from.")
            : nil

        ScreenFrame(gap: 18) {
            TopBar(title: "Result") {
                RoundIconButton(icon: Ic.close, label: "Close", size: 16) { m.resetAll() }
            }
            Banner(verdict: verdict, warning: warning, country: country)
            PersonCard(report: r)
            if let d = r.dg1 {
                HStack(spacing: 10) {
                    ValueTile(label: "Date of birth", value: ReportModel.date(d.dateOfBirth, birth: true))
                    ValueTile(label: "Valid until", value: ReportModel.date(d.dateOfExpiry))
                }
            }
            VStack(spacing: 0) {
                CheckRow(text: "Data signature intact (Passive Auth)", check: signature)
                Divider().overlay(C.divider)
                CheckRow(text: r.chipAuth.check == .pass ? "Chip is not a copy (Chip Auth)"
                         : r.activeAuth?.check == .pass ? "Chip is not a copy (Active Auth)"
                         : r.chipGenuine == .unknown ? "Clone check not offered by this chip" : "Chip is not a copy (Chip Auth)",
                         check: r.chipGenuine)
                Divider().overlay(C.divider)
                let cc = pa.dsc?.issuerCountry ?? r.dg1?.issuingState ?? "?"
                CheckRow(text: userAnchor ? "Chain of trust: certificate you added (\(cc))" : "Chain of trust: CSCA (\(cc))",
                         check: userAnchor && pa.cscaChain == .pass ? .unknown : pa.cscaChain) { m.route = .details }
            }
            .padding(.horizontal, 16).padding(.vertical, 6)
            .background(C.surface, in: RoundedRectangle(cornerRadius: 20))
        } bottom: {
            PrimaryButton(text: "New check") { m.resetAll(); m.route = .scan }
            GhostButton(text: "Technical details (DG, SOD, certificate)", height: 44) { m.route = .details }
        }
    }
}

private struct Banner: View {
    let verdict: Check
    /// Title and subtitle for the unknown case, chosen by the caller.
    let warning: (String, String)?
    let country: String

    var body: some View {
        let (bg, border, fg, sub, icon, title, text): (Color, Color, Color, Color, String, String, String) = switch verdict {
        case .pass: (C.successBg, C.successBorder, C.success, C.successText, Ic.check, "Document is genuine", "Chip data signed by the issuing authority of \(country)")
        case .unknown, .skipped: (C.warnBg, C.warnBorder, C.warn, C.warnText, "info", warning?.0 ?? "Signature intact, issuer not verified",
                                  warning?.1 ?? "No trusted certificate for \(country) in the master list")
        case .fail: (C.dangerBg, C.dangerBorder, C.danger, C.dangerText, Ic.alert, "Verification failed", "The chip data or its signature did not check out")
        }
        Panel(radius: 20, color: bg, border: border) {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.system(size: 22, weight: .bold)).foregroundStyle(bg)
                    .frame(width: 48, height: 48).background(fg, in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(T.inter(19, .bold)).foregroundStyle(fg)
                    Text(text).font(T.small).foregroundStyle(sub).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

private struct PersonCard: View {
    let report: ReadReport
    @State private var reveal = false

    var body: some View {
        let (sur, given) = ReportModel.name(report)
        let face = report.faces.first
        // UIImage decodes JPEG and, through ImageIO, JPEG 2000.
        let image = face.flatMap { UIImage(data: Data($0.bytes)) }
        HStack(alignment: .top, spacing: 16) {
            ZStack {
                C.border
                if let image {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    VStack(spacing: 4) {
                        Image(systemName: Ic.user).font(.system(size: 32)).foregroundStyle(C.faint)
                        if let face { Text(face.mimeType.replacingOccurrences(of: "image/", with: "")).font(T.inter(10)).foregroundStyle(C.muted) }
                    }
                }
            }
            .frame(width: 92, height: 118).clipShape(RoundedRectangle(cornerRadius: 12))
            .accessibilityLabel("Photo stored on the chip")
            VStack(alignment: .leading, spacing: 6) {
                Text("Name").font(T.tiny).foregroundStyle(C.muted)
                Text("\(sur)\n\(given)".trimmingCharacters(in: .whitespacesAndNewlines)).font(T.inter(18, .bold)).foregroundStyle(C.text)
                if let d = report.dg1 {
                    Text("Nationality: \(Countries.name(d.nationality))").font(T.small).foregroundStyle(C.text2)
                    Button { reveal.toggle() } label: {
                        HStack(spacing: 6) {
                            Text(reveal ? d.documentNumber : ReportModel.mask(d.documentNumber)).font(T.monoSmall).foregroundStyle(C.text2)
                            Image(systemName: Ic.eye).font(.system(size: 12)).foregroundStyle(C.faint)
                        }
                    }
                    .accessibilityLabel("Show document number")
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16).background(C.surface, in: RoundedRectangle(cornerRadius: 20))
    }
}

private struct CheckRow: View {
    let text: String
    let check: Check
    var action: (() -> Void)?

    var body: some View {
        Button { action?() } label: {
            HStack(spacing: 12) {
                StatusIcon(check: check)
                Text(text).font(T.inter(14)).foregroundStyle(C.text).frame(maxWidth: .infinity, alignment: .leading)
                if action != nil { Image(systemName: Ic.chevron).font(.system(size: 13)).foregroundStyle(C.faint) }
            }
            .frame(minHeight: 48).contentShape(Rectangle())
        }
        .buttonStyle(.plain).disabled(action == nil)
    }
}

struct DetailsView: View {
    @EnvironmentObject var m: AppModel
    let report: ReadReport

    var body: some View {
        ScreenFrame(gap: 14) {
            TopBar(title: "Technical details") {
                RoundIconButton(icon: Ic.back, label: "Back") { m.route = .result }
            }
            ForEach(ReportModel.sections(report)) { s in
                Panel(padding: 16) {
                    Text(s.title.uppercased()).font(T.overline).tracking(0.8).foregroundStyle(C.muted)
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(s.rows) { row in
                            HStack(alignment: .top, spacing: 10) {
                                if let c = row.check { StatusIcon(check: c, size: 15).padding(.top, 2) }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(row.label).font(T.tiny).foregroundStyle(C.muted)
                                    Text(row.value).font(s.mono ? T.mono(12) : T.inter(14)).foregroundStyle(s.mono ? C.text2 : C.text)
                                        .textSelection(.enabled).fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                    }
                    .padding(.top, 10)
                }
            }
        }
    }
}
