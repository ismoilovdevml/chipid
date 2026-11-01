import ChipCore
import SwiftUI

private enum FieldState { case empty, ok, error }

/// Manual entry of the chip key. Prefill comes from a camera scan the user wants to correct.
/// Plain @State only: key material is never persisted or restored.
struct ManualView: View {
    @EnvironmentObject var m: AppModel
    @State private var useCan = false
    @State private var doc = ""
    @State private var dob = ""
    @State private var exp = ""
    @State private var can = ""
    @State private var loaded = false

    var body: some View {
        let docOk = AccessInput.normalizeDocumentNumber(doc) != nil
        let dobDate = AccessInput.toDate(dob)
        let dobState: FieldState = dob.count < 8 ? .empty : (dobDate == nil || AccessInput.isFuture(dobDate!)) ? .error : .ok
        let expState: FieldState = exp.count < 8 ? .empty : AccessInput.toDate(exp) == nil ? .error : .ok
        let key: AccessKey? = useCan
            ? AccessInput.normalizeCan(can).map { .can($0) }
            : (docOk && dobState == .ok && expState == .ok)
                ? .mrz(documentNumber: AccessInput.normalizeDocumentNumber(doc)!, dateOfBirth: AccessInput.toYymmdd(dob)!, dateOfExpiry: AccessInput.toYymmdd(exp)!)
                : nil

        ScreenFrame {
            TopBar(title: "Enter manually") {
                RoundIconButton(icon: Ic.back, label: "Back") { m.route = m.prefill != nil ? .scan : .home }
            } right: {
                RoundIconButton(icon: Ic.camera, label: "Open camera", tint: C.primary) { m.route = .scan }
            }
            if m.docType == .idCard {
                HStack(spacing: 0) {
                    Segment(text: "CAN", selected: useCan) { useCan = true }
                    Segment(text: "MRZ details", selected: !useCan) { useCan = false }
                }
                .padding(4).background(C.surface, in: RoundedRectangle(cornerRadius: 14))
            }
            HStack(spacing: 14) {
                DocThumbnail(highlightMrz: !useCan)
                Text(useCan ? "The CAN is a 6-digit number printed on the front of many ID cards."
                     : m.docType == .passport ? "Everything is on the photo page, and also in the two lines inside the blue frame."
                     : "Everything is in the three lines on the back of the card.")
                    .font(T.small).foregroundStyle(C.text2)
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(C.surface, in: RoundedRectangle(cornerRadius: 16))

            if useCan {
                Field(label: "Card access number (CAN)", text: $can, state: can.count == 6 ? .ok : .empty, helper: "6 digits",
                      keyboard: .numberPad, big: true) { $0.filter(\.isNumber).prefix(6).description }
            } else {
                Field(label: m.docType == .passport ? "Passport number" : "Document number", text: $doc,
                      state: doc.isEmpty ? .empty : docOk ? .ok : .error, helper: "Letters and digits, for example FA1234567",
                      keyboard: .asciiCapable, big: true) { AccessInput.documentDigits($0) }
                HStack(alignment: .top, spacing: 12) {
                    Field(label: "Date of birth", text: $dob, state: dobState, placeholder: "DD.MM.YYYY", keyboard: .numberPad, date: true) { AccessInput.dateDigits($0) }
                    Field(label: "Date of expiry", text: $exp, state: expState, placeholder: "DD.MM.YYYY", keyboard: .numberPad, date: true) { AccessInput.dateDigits($0) }
                }
                Text(dobState == .error ? "That date of birth is not possible" : expState == .error ? "That date does not exist" : "Digits only — the dots are added for you")
                    .font(T.tiny).foregroundStyle(dobState == .error || expState == .error ? C.danger : C.muted)
            }
        } bottom: {
            LockNote(text: "These details are the key that unlocks the chip. They stay in memory and are never saved.")
            PrimaryButton(text: "Continue", enabled: key != nil) { if let key { m.startRead(key) } }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            useCan = m.docType == .idCard && m.prefill == nil
            if case let .mrz(d, b, e)? = m.prefill {
                doc = d
                dob = ReportModel.date(b, birth: true).replacingOccurrences(of: ".", with: "")
                exp = ReportModel.date(e).replacingOccurrences(of: ".", with: "")
            }
        }
    }
}

private struct Segment: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(T.label).foregroundStyle(selected ? C.primary : C.text2)
                .frame(maxWidth: .infinity).frame(height: 40)
                .background(selected ? C.selectedBg : C.surface, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? C.primary : C.surface, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct DocThumbnail: View {
    let highlightMrz: Bool

    var body: some View {
        VStack(alignment: .leading) {
            HStack(alignment: .top, spacing: 5) {
                RoundedRectangle(cornerRadius: 2).fill(C.paperInk).frame(width: 18, height: 24)
                VStack(alignment: .leading, spacing: 3) {
                    Capsule().fill(C.paperInk).frame(height: 3)
                    Capsule().fill(highlightMrz ? C.paperInk : C.warn).frame(width: 36, height: 3)
                    Capsule().fill(C.paperInk).frame(width: 48, height: 3)
                }
            }
            Spacer()
            RoundedRectangle(cornerRadius: 2).stroke(highlightMrz ? C.primary : C.paperInk, lineWidth: 1.5).frame(height: 12)
        }
        .padding(6).frame(width: 96, height: 66)
        .background(C.paper, in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct Field: View {
    let label: String
    @Binding var text: String
    let state: FieldState
    var helper: String?
    var placeholder: String?
    var keyboard: UIKeyboardType
    var big = false
    /// Dates are stored as digits and shown as DD.MM.YYYY.
    var date = false
    let filter: (String) -> String
    @FocusState private var focused: Bool

    init(label: String, text: Binding<String>, state: FieldState, helper: String? = nil, placeholder: String? = nil,
         keyboard: UIKeyboardType, big: Bool = false, date: Bool = false, filter: @escaping (String) -> String) {
        self.label = label; _text = text; self.state = state; self.helper = helper; self.placeholder = placeholder
        self.keyboard = keyboard; self.big = big; self.date = date; self.filter = filter
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(T.label).foregroundStyle(C.text2)
            HStack {
                TextField("", text: Binding(
                    get: { date ? Self.dotted(text) : text },
                    set: { text = filter($0) }
                ), prompt: placeholder.map { Text($0).foregroundStyle(C.faint) })
                .font(T.mono(big ? 18 : 16)).tracking(big ? 1.4 : 0)
                .foregroundStyle(C.text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .textContentType(.none)
                .focused($focused)
                if state == .ok {
                    Image(systemName: Ic.check).font(.system(size: 16, weight: .bold)).foregroundStyle(C.success)
                }
            }
            .padding(.horizontal, big ? 16 : 14).frame(height: 56)
            .background(focused ? C.selectedBg : C.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(state == .error ? C.danger : focused ? C.primary : C.fieldBorder, lineWidth: 2))
            if let helper { Text(helper).font(T.tiny).foregroundStyle(C.muted) }
        }
    }

    /// "15031990" -> "15.03.1990" while typing.
    static func dotted(_ d: String) -> String {
        var out = ""
        for (i, c) in d.enumerated() {
            out.append(c)
            if (i == 1 || i == 3) && i < d.count - 1 { out.append(".") }
        }
        return out
    }
}
