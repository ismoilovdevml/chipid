import Foundation

/// Finds and validates a machine readable zone in OCR output (ICAO 9303 parts 3-5).
/// Every field that has a check digit must verify, so a misread line is rejected rather than
/// producing a wrong chip key. Position-aware repair fixes the usual OCR confusions first.
public enum Mrz {
    public enum Format: String, Sendable { case td1 = "TD1", td3 = "TD3" }

    public struct Result: Equatable, Sendable {
        public let format: Format
        public let documentCode: String
        public let issuingState: String
        public let documentNumber: String
        public let dateOfBirth: String
        public let dateOfExpiry: String
        public let nationality: String
        public let sex: String
        public let surname: String?
        public let givenNames: String?
        public let compositeValid: Bool

        public var accessKey: AccessKey { .mrz(documentNumber: documentNumber, dateOfBirth: dateOfBirth, dateOfExpiry: dateOfExpiry) }
    }

    private static let toDigit: [Character: Character] = ["O": "0", "Q": "0", "D": "0", "U": "0", "I": "1", "L": "1", "Z": "2", "S": "5", "B": "8", "G": "6", "T": "7"]
    private static let toLetter: [Character: Character] = ["0": "O", "1": "I", "2": "Z", "5": "S", "8": "B", "6": "G", "7": "T"]

    public static func find(_ lines: [String]) -> Result? {
        let clean = lines.map(normalize).filter { $0.count >= 28 }
        for i in clean.indices {
            let a = clean[i]
            let b = i + 1 < clean.count ? clean[i + 1] : nil
            let c = i + 2 < clean.count ? clean[i + 2] : nil
            if let l1 = fit(a, 44), let b, let l2 = fit(b, 44), let r = parseTd3(l1, l2) { return r }
            if let l1 = fit(a, 30), let b, let l2 = fit(b, 30), let r = parseTd1(l1, l2, c.flatMap { fit($0, 30) }) { return r }
        }
        return nil
    }

    /// Upper-case, OCR-B quirks ('«' is '<<', spaces vanish), anything else becomes filler.
    public static func normalize(_ s: String) -> String {
        let t = s.uppercased().replacingOccurrences(of: "«", with: "<<").replacingOccurrences(of: "‹", with: "<")
            .replacingOccurrences(of: " ", with: "")
        return String(t.map { ($0.isASCIIUpper || $0.isASCIIDigit || $0 == "<") ? $0 : "<" })
    }

    /// OCR often drops or adds one trailing filler; accept ±2 and fix up the tail.
    private static func fit(_ s: String, _ n: Int) -> String? {
        if s.count == n { return s }
        if (n - 2..<n).contains(s.count) { return s + String(repeating: "<", count: n - s.count) }
        if (n + 1...n + 2).contains(s.count), s.dropFirst(n).allSatisfy({ $0 == "<" }) { return String(s.prefix(n)) }
        return nil
    }

    private static func parseTd3(_ l1: String, _ l2raw: String) -> Result? {
        let a1 = Array(l1)
        guard "PV".contains(a1[0]) else { return nil }
        var l2 = Array(l2raw)
        digits(&l2, Array(13...19)); digits(&l2, Array(21...27)); digits(&l2, [9, 42, 43])
        letters(&l2, Array(10...12))
        let s = String(l2)
        let dob = sub(s, 13, 19), exp = sub(s, 21, 27)
        guard check(dob, l2[19]), check(exp, l2[27]) else { return nil }
        guard let doc = repairDocNumber(sub(s, 0, 9), l2[9]) else { return nil }
        let fixed = doc + sub(s, 9, 44)
        let composite = sub(fixed, 0, 10) + sub(fixed, 13, 20) + sub(fixed, 21, 43)
        let (sur, given) = names(sub(l1, 5, 44))
        return Result(
            format: .td3, documentCode: sub(l1, 0, 2).trimmingCharacters(in: CharacterSet(charactersIn: "<")),
            issuingState: lettersOf(sub(l1, 2, 5)), documentNumber: trimFiller(doc), dateOfBirth: dob, dateOfExpiry: exp,
            nationality: sub(fixed, 10, 13), sex: sub(fixed, 20, 21), surname: sur, givenNames: given,
            compositeValid: check(composite, Array(fixed)[43]))
    }

    private static func parseTd1(_ l1raw: String, _ l2raw: String, _ l3: String?) -> Result? {
        guard "IAC".contains(Array(l1raw)[0]) else { return nil }
        var l2 = Array(l2raw)
        digits(&l2, Array(0...6)); digits(&l2, Array(8...14)); digits(&l2, [29])
        letters(&l2, Array(15...17))
        let s2 = String(l2)
        let dob = sub(s2, 0, 6), exp = sub(s2, 8, 14)
        guard check(dob, l2[6]), check(exp, l2[14]) else { return nil }
        var a1 = Array(l1raw)
        digits(&a1, [14])
        let l1 = String(a1)
        guard let doc = repairDocNumber(sub(l1, 5, 14), a1[14]) else { return nil }
        let fixed1 = sub(l1, 0, 5) + doc + sub(l1, 14, 30)
        let composite = sub(fixed1, 5, 30) + sub(s2, 0, 7) + sub(s2, 8, 15) + sub(s2, 18, 29)
        let (sur, given) = l3.map(names) ?? (nil, nil)
        return Result(
            format: .td1, documentCode: sub(fixed1, 0, 2).trimmingCharacters(in: CharacterSet(charactersIn: "<")),
            issuingState: lettersOf(sub(fixed1, 2, 5)), documentNumber: trimFiller(doc), dateOfBirth: dob, dateOfExpiry: exp,
            nationality: sub(s2, 15, 18), sex: sub(s2, 7, 8), surname: sur, givenNames: given,
            compositeValid: check(composite, l2[29]))
    }

    /// Document numbers mix letters and digits, so OCR swaps cannot be undone by position alone.
    /// Try the read value, then flip ambiguous characters until the check digit agrees.
    private static func repairDocNumber(_ raw: String, _ checkChar: Character) -> String? {
        if check(raw, checkChar) { return raw }
        let chars = Array(raw)
        let ambiguous = chars.indices.filter { toDigit[chars[$0]] != nil || toLetter[chars[$0]] != nil }
        if ambiguous.count > 10 { return nil }
        var candidates: [String] = []
        for mask in 1..<(1 << ambiguous.count) {
            var c = chars
            for (bit, idx) in ambiguous.enumerated() where (mask >> bit) & 1 == 1 {
                c[idx] = toDigit[c[idx]] ?? toLetter[c[idx]] ?? c[idx]
            }
            let s = String(c)
            if check(s, checkChar) { candidates.append(s) }
        }
        // Prefer the repair that matches the common "letters then digits" shape (e.g. FA1234567).
        return candidates.min { shapePenalty($0) < shapePenalty($1) }
    }

    /// Letters after the first digit; 0 for the usual "letters then digits" shape.
    private static func shapePenalty(_ s: String) -> Int {
        guard let first = s.firstIndex(where: \.isASCIIDigit) else { return 0 }
        return s[first...].filter(\.isLetter).count
    }

    private static func check(_ field: String, _ c: Character) -> Bool {
        c.isASCIIDigit && AccessInput.checkDigit(field) == Int(c.asciiValue! - 48)
    }

    private static func digits(_ a: inout [Character], _ r: [Int]) {
        for i in r where i < a.count { a[i] = toDigit[a[i]] ?? a[i] }
    }

    private static func letters(_ a: inout [Character], _ r: [Int]) {
        for i in r where i < a.count { a[i] = toLetter[a[i]] ?? a[i] }
    }

    private static func lettersOf(_ s: String) -> String { String(s.map { toLetter[$0] ?? $0 }) }

    private static func trimFiller(_ s: String) -> String {
        var t = s
        while t.hasSuffix("<") { t.removeLast() }
        return t
    }

    static func names(_ field: String) -> (String?, String?) {
        let parts = trimFiller(field).components(separatedBy: "<<")
        func clean(_ p: String?) -> String? {
            guard let p else { return nil }
            let v = p.replacingOccurrences(of: "<", with: " ").trimmingCharacters(in: .whitespaces)
            return v.isEmpty ? nil : v
        }
        return (clean(parts.first), clean(parts.count > 1 ? parts.dropFirst().joined(separator: "<<") : nil))
    }

    static func sub(_ s: String, _ from: Int, _ to: Int) -> String {
        let a = Array(s)
        return String(a[min(from, a.count)..<min(to, a.count)])
    }
}
