import Foundation

public struct ChipAuthOutcome {
    public let check: Check
    public let detail: String
    public init(_ check: Check, _ detail: String) { self.check = check; self.detail = detail }
}

/// Everything one read produced. Built by the NFC layer, consumed by the result screens.
public struct ReadReport {
    public let accessProtocol: String
    public let paceNote: String?
    public let cardAccess: [UInt8]?
    public let sod: [UInt8]
    public let dataGroups: [Int: [UInt8]]
    public let notRead: [Int: String]
    public let dg1: Lds.Dg1?
    public let faces: [Lds.Face]
    public let dg11: Lds.Dg11?
    public let dg12: Lds.Dg12?
    public let passive: PassiveAuth.Result
    public let chipAuth: ChipAuthOutcome
    public let activeAuth: ActiveAuth.Result?
    public let tagInfo: [(String, String)]
    public let millis: Int
    public let log: [String]

    public init(accessProtocol: String, paceNote: String?, cardAccess: [UInt8]?, sod: [UInt8], dataGroups: [Int: [UInt8]],
                notRead: [Int: String], passive: PassiveAuth.Result, chipAuth: ChipAuthOutcome, activeAuth: ActiveAuth.Result?,
                tagInfo: [(String, String)], millis: Int, log: [String]) {
        self.accessProtocol = accessProtocol; self.paceNote = paceNote; self.cardAccess = cardAccess; self.sod = sod
        self.dataGroups = dataGroups; self.notRead = notRead; self.passive = passive; self.chipAuth = chipAuth
        self.activeAuth = activeAuth; self.tagInfo = tagInfo; self.millis = millis; self.log = log
        dg1 = dataGroups[1].flatMap { try? Lds.dg1($0) }
        faces = dataGroups[2].flatMap { try? Lds.dg2Faces($0) } ?? []
        dg11 = dataGroups[11].flatMap { try? Lds.dg11($0) }
        dg12 = dataGroups[12].flatMap { try? Lds.dg12($0) }
    }

    /// Clone detection passes if either chip-genuineness protocol succeeded.
    public var chipGenuine: Check {
        if chipAuth.check == .pass || activeAuth?.check == .pass { return .pass }
        if chipAuth.check == .fail || activeAuth?.check == .fail { return .fail }
        return .unknown
    }
}

public struct Row: Identifiable {
    public let id = UUID()
    public let label: String
    public let value: String
    public let check: Check?
    public init(_ label: String, _ value: String, _ check: Check? = nil) { self.label = label; self.value = value; self.check = check }
}

public struct Section: Identifiable {
    public let id = UUID()
    public let title: String
    public let rows: [Row]
    public let mono: Bool
    public init(_ title: String, _ rows: [Row], mono: Bool = false) { self.title = title; self.rows = rows; self.mono = mono }
}

/// One description of a read, shared by the result and details screens.
public enum ReportModel {
    public static func sections(_ r: ReadReport) -> [Section] {
        [security(r), person(r), document(r), additional(r), dsc(r), technical(r)].compactMap { $0 }
    }

    /// "Germany" for the CSCA that signed this document, or the MRZ issuing state.
    public static func signerCountry(_ r: ReadReport) -> String {
        Countries.name(r.passive.dsc?.issuerCountry ?? r.dg1?.issuingState ?? "?")
    }

    /// Signer sanity: vendor/test PKI, and whether its country is the issuing state.
    public struct SignerFlags {
        public let testPki: Bool
        public let organisation: String?
        public let countryMatches: Bool?
        public let signerCountry: String?
    }

    public static func signerFlags(_ r: ReadReport) -> SignerFlags {
        guard let dsc = r.passive.dsc else { return SignerFlags(testPki: false, organisation: nil, countryMatches: nil, signerCountry: nil) }
        let test = SignerCheck.looksLikeTestPki(dsc.subject + "," + dsc.issuer, notBefore: dsc.notBefore ?? .distantPast, notAfter: dsc.notAfter ?? .distantPast)
        let cc = dsc.issuerCountry
        return SignerFlags(testPki: test, organisation: dsc.issuerOrganisation,
                           countryMatches: r.dg1.flatMap { SignerCheck.countryMatches($0.issuingState, cc) }, signerCountry: cc)
    }

    /// Name as printed in the MRZ; may be truncated to fit the zone (ICAO 9303 part 3, 4.6).
    public static func mrzName(_ r: ReadReport) -> (String, String) {
        guard let m = r.dg1 else { return ("", "") }
        return (m.primaryIdentifier, m.secondaryIdentifier)
    }

    /// Full name from DG11 when the chip has it, otherwise the MRZ name.
    public static func name(_ r: ReadReport) -> (String, String) {
        if let n = r.dg11?.nameOfHolder, !n.isEmpty { return splitIcaoName(n) }
        return mrzName(r)
    }

    /// "SURNAME<<GIVEN<NAMES" -> ("SURNAME", "GIVEN NAMES").
    public static func splitIcaoName(_ field: String) -> (String, String) {
        var f = field
        while f.hasSuffix("<") { f.removeLast() }
        let parts = f.components(separatedBy: "<<")
        let clean = { (p: String) in p.replacingOccurrences(of: "<", with: " ").trimmingCharacters(in: .whitespaces) }
        return (clean(parts[0]), parts.count > 1 ? clean(parts.dropFirst().joined(separator: "<<")) : "")
    }

    public static func isTruncation(_ mrz: String, _ full: String) -> Bool {
        mrz != full && full.hasPrefix(mrz.trimmingCharacters(in: .whitespaces))
    }

    /// YYYYMMDD (DG11/DG12) -> DD.MM.YYYY; anything else is returned unchanged.
    public static func date8(_ s: String?) -> String? {
        guard let t = s?.trimmingCharacters(in: .whitespaces) else { return nil }
        guard t.count == 8, t.allSatisfy(\.isASCIIDigit) else { return t }
        let a = Array(t)
        return "\(String(a[6...7])).\(String(a[4...5])).\(String(a[0...3]))"
    }

    /// YYMMDD -> DD.MM.YYYY. Birth dates are never in the future; expiry dates are always 20xx.
    public static func date(_ yymmdd: String, birth: Bool = false, now: Date = Date()) -> String {
        guard yymmdd.count == 6, yymmdd.allSatisfy(\.isASCIIDigit) else { return yymmdd }
        let a = Array(yymmdd)
        let yy = Int(String(a[0...1]))!
        let thisYear = Calendar(identifier: .gregorian).component(.year, from: now)
        let year = birth && 2000 + yy > thisYear ? 1900 + yy : 2000 + yy
        return "\(String(a[4...5])).\(String(a[2...3])).\(year)"
    }

    public static func verdict(_ c: Check) -> String {
        switch c {
        case .pass: return "verified"
        case .fail: return "FAILED"
        case .unknown: return "could not be decided"
        case .skipped: return "not performed"
        }
    }

    static func mark(_ c: Check) -> String {
        switch c {
        case .pass: return "✓"
        case .fail: return "✗"
        case .unknown: return "?"
        case .skipped: return "–"
        }
    }

    /// First two characters, the rest hidden: enough to recognise the document, useless to a bystander.
    public static func mask(_ doc: String) -> String {
        String(doc.prefix(2)) + String(repeating: "•", count: max(doc.count - 2, 0))
    }

    private static func security(_ r: ReadReport) -> Section {
        let pa = r.passive
        var rows = [Row("Access control", r.accessProtocol)]
        if let n = r.paceNote { rows.append(Row("PACE note", n)) }
        rows.append(Row("Data group hashes match SOD",
                        "\(pa.digestAlgorithm); " + pa.perDataGroup.keys.sorted().map { "DG\($0) \(mark(pa.perDataGroup[$0]!))" }.joined(separator: ", "),
                        pa.dataGroupHashes))
        rows.append(Row("SOD signature (DSC)", pa.signatureAlgorithm ?? "—", pa.sodSignature))
        rows.append(Row("DSC → CSCA chain", pa.cscaSubject ?? "no matching CSCA", pa.cscaChain))
        rows.append(Row("Passive Authentication", verdict(pa.overall), pa.overall))
        let f = signerFlags(r)
        if pa.dsc != nil {
            rows.append(f.testPki
                ? Row("Signer certificate", "Test / development PKI\(f.organisation.map { " (\($0))" } ?? ""), not a state authority", .fail)
                : Row("Signer certificate", "No test or development markers", .pass))
        }
        if let ok = f.countryMatches {
            rows.append(Row("Signer country = issuing state", "\(r.dg1?.issuingState ?? "?") vs \(f.signerCountry ?? "?")", ok ? .pass : .fail))
        }
        rows.append(Row("Chip Authentication", r.chipAuth.detail, r.chipAuth.check))
        if let aa = r.activeAuth {
            rows.append(Row("Active Authentication", [aa.algorithm, aa.digest, aa.detail].compactMap { $0 }.joined(separator: ", "), aa.check))
        } else {
            rows.append(Row("Active Authentication", "no DG15", .skipped))
        }
        rows.append(Row("Chip is genuine (not a clone)", verdict(r.chipGenuine), r.chipGenuine))
        rows += pa.errors.map { Row("Note", $0) }
        return Section("Security checks", rows)
    }

    private static func person(_ r: ReadReport) -> Section? {
        guard let m = r.dg1 else { return nil }
        let (sur, given) = name(r)
        let (mrzSur, mrzGiven) = mrzName(r)
        let fromDg11 = r.dg11?.nameOfHolder?.isEmpty == false
        let mrzDiffers = fromDg11 && (mrzSur != sur || mrzGiven != given)
        let truncated = isTruncation(mrzSur, sur) || isTruncation(mrzGiven, given)
        var rows = [Row("Surname", sur), Row("Given names", given)]
        if mrzDiffers { rows.append(Row(truncated ? "Name in MRZ (shortened to fit)" : "Name in MRZ", "\(mrzSur), \(mrzGiven)")) }
        rows.append(Row("Sex", m.sex == "<" ? "unspecified" : m.sex))
        rows.append(Row("Date of birth", date(m.dateOfBirth, birth: true)))
        rows.append(Row("Nationality", "\(Countries.name(m.nationality)) (\(m.nationality))"))
        if let p = m.optionalData { rows.append(Row("Personal number", p)) }
        return Section(fromDg11 ? "Holder (DG1 / DG11)" : "Holder (DG1 / MRZ)", rows)
    }

    private static func document(_ r: ReadReport) -> Section? {
        guard let m = r.dg1 else { return nil }
        var rows = [
            Row("Type", "\(m.documentCode) (\(m.documentCode.hasPrefix("P") ? "passport" : "identity card"))"),
            Row("Number", m.documentNumber),
            Row("Issuing state", "\(Countries.name(m.issuingState)) (\(m.issuingState))"),
            Row("Date of expiry", date(m.dateOfExpiry)),
        ]
        if let a = r.dg12?.issuingAuthority { rows.append(Row("Issuing authority", a)) }
        if let d = r.dg12?.dateOfIssue { rows.append(Row("Date of issue", date8(d)!)) }
        if let v = r.passive.ldsVersion { rows.append(Row("LDS version", v)) }
        return Section("Document", rows)
    }

    private static func additional(_ r: ReadReport) -> Section? {
        guard let d = r.dg11 else { return nil }
        var rows: [Row] = []
        if let n = d.nameOfHolder {
            let (sur, given) = splitIcaoName(n)
            rows.append(Row("Full name", [sur, given].filter { !$0.isEmpty }.joined(separator: ", ")))
        }
        if let b = d.fullDateOfBirth { rows.append(Row("Full date of birth", date8(b)!)) }
        if !d.placeOfBirth.isEmpty { rows.append(Row("Place of birth", d.placeOfBirth.joined(separator: ", "))) }
        if !d.permanentAddress.isEmpty { rows.append(Row("Address", d.permanentAddress.joined(separator: ", "))) }
        if let p = d.personalNumber { rows.append(Row("Personal number (DG11)", p)) }
        return rows.isEmpty ? nil : Section("Additional details (DG11)", rows)
    }

    private static func dsc(_ r: ReadReport) -> Section? {
        guard let c = r.passive.dsc else { return nil }
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.timeZone = TimeZone(identifier: "UTC")
        var rows = [Row("Subject", c.subject), Row("Issuer", c.issuer), Row("Serial", c.serialHex)]
        if let aki = c.authorityKeyId { rows.append(Row("Authority key ID", aki.map { String(format: "%02X", $0) }.joined(separator: ":"))) }
        rows.append(Row("Validity", "\(c.notBefore.map(fmt.string) ?? "?") → \(c.notAfter.map(fmt.string) ?? "?")"))
        if let v = r.passive.dscValidNow { rows.append(Row("Valid today", v ? "yes" : "no (normal: signer keys are used only briefly)")) }
        rows.append(Row("Key", "\(c.publicKeyAlgorithm) · \(c.signatureAlgorithm)"))
        return Section("Document Signer certificate", rows, mono: true)
    }

    private static func technical(_ r: ReadReport) -> Section {
        var rows = r.tagInfo.map { Row($0.0, $0.1) }
        rows.append(Row("Files read", (["SOD \(r.sod.count) B"] + r.dataGroups.keys.sorted().map { "DG\($0) \(r.dataGroups[$0]!.count) B" }).joined(separator: ", ")))
        for dg in r.notRead.keys.sorted() { rows.append(Row("DG\(dg) not read", r.notRead[dg]!, .skipped)) }
        for (i, f) in r.faces.enumerated() { rows.append(Row("Face image \(i)", "\(f.mimeType), \(f.bytes.count) bytes")) }
        if let ca = r.cardAccess { rows.append(Row("EF.CardAccess", String(ca.hex.prefix(64)) + (ca.count > 32 ? "…" : ""))) }
        rows.append(Row("Read time", "\(r.millis) ms"))
        return Section("Technical", rows, mono: true)
    }
}
