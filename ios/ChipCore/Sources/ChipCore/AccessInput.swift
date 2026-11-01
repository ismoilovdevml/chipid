import Foundation

/// The key that unlocks the chip.
public enum AccessKey: Equatable, Sendable {
    /// documentNumber, dateOfBirth and dateOfExpiry as on the MRZ (dates YYMMDD).
    case mrz(documentNumber: String, dateOfBirth: String, dateOfExpiry: String)
    case can(String)

    /// The BAC/PACE "MRZ information": number, birth date and expiry, each followed by its check digit.
    /// The document number is padded with '<' to at least 9 characters (ICAO 9303 part 11, 4.3).
    public var mrzInformation: String? {
        guard case let .mrz(doc, dob, exp) = self else { return nil }
        let padded = doc.count < 9 ? doc + String(repeating: "<", count: 9 - doc.count) : doc
        return padded + String(AccessInput.checkDigit(padded))
            + dob + String(AccessInput.checkDigit(dob))
            + exp + String(AccessInput.checkDigit(exp))
    }
}

/// Validation and normalisation of the access key the user types (MRZ fields or CAN).
public enum AccessInput {
    /// "15.03.1990" / "15031990" -> (1990, 3, 15); nil if incomplete or impossible.
    public static func toDate(_ input: String) -> DateComponents? {
        guard let (d, m, y) = dotted(input.trimmingCharacters(in: .whitespaces)), valid(y, m, d) else { return nil }
        return DateComponents(year: y, month: m, day: d)
    }

    /// True when the dotted/plain DDMMYYYY date lies after today.
    public static func isFuture(_ c: DateComponents, now: Date = Date()) -> Bool {
        let today = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: now)
        return (c.year!, c.month!, c.day!) > (today.year!, today.month!, today.day!)
    }

    /// "15.03.1990", "15031990" or "900315" -> "900315"; nil if the date does not exist.
    public static func toYymmdd(_ input: String) -> String? {
        let s = input.trimmingCharacters(in: .whitespaces)
        if let (d, m, y) = dotted(s) {
            guard valid(y, m, d) else { return nil }
            return String(format: "%02d%02d%02d", y % 100, m, d)
        }
        if s.count == 6, s.allSatisfy(\.isASCIIDigit) {
            let n = s.map { Int(String($0))! }
            // Any century works for validation except Feb 29; 2000 is a leap year.
            guard valid(2000 + n[0] * 10 + n[1], n[2] * 10 + n[3], n[4] * 10 + n[5]) else { return nil }
            return s
        }
        return nil
    }

    private static func dotted(_ s: String) -> (Int, Int, Int)? {
        let parts = s.split(whereSeparator: { ".-/".contains($0) }).map(String.init)
        let digits: String
        if parts.count == 3 {
            guard parts[0].count == 2, parts[1].count == 2, parts[2].count == 4 else { return nil }
            digits = parts.joined()
        } else if parts.count == 1 {
            digits = s
        } else {
            return nil
        }
        guard digits.count == 8, digits.allSatisfy(\.isASCIIDigit) else { return nil }
        let d = Int(digits.prefix(2))!, m = Int(digits.dropFirst(2).prefix(2))!, y = Int(digits.suffix(4))!
        return (d, m, y)
    }

    private static func valid(_ y: Int, _ m: Int, _ d: Int) -> Bool {
        let cal = Calendar(identifier: .gregorian)
        let c = DateComponents(year: y, month: m, day: d)
        guard (1...12).contains(m), d >= 1, let date = cal.date(from: c) else { return false }
        let back = cal.dateComponents([.year, .month, .day], from: date)
        return back.year == y && back.month == m && back.day == d
    }

    /// What a date field keeps while typing: digits only, at most DDMMYYYY.
    public static func dateDigits(_ input: String) -> String { String(input.filter(\.isASCIIDigit).prefix(8)) }

    /// What the document-number field keeps while typing.
    public static func documentDigits(_ input: String) -> String {
        String(input.uppercased().filter { $0.isASCIIUpper || $0.isASCIIDigit }.prefix(20))
    }

    public static func normalizeDocumentNumber(_ input: String) -> String? {
        let s = input.uppercased().filter { !$0.isWhitespace }
        guard (5...20).contains(s.count), s.allSatisfy({ $0.isASCIIUpper || $0.isASCIIDigit }) else { return nil }
        return s
    }

    public static func normalizeCan(_ input: String) -> String? {
        let s = input.filter { !$0.isWhitespace }
        return s.count == 6 && s.allSatisfy(\.isASCIIDigit) ? s : nil
    }

    /// ICAO 9303 part 3 check digit (weights 7,3,1; A=10..Z=35; '<'=0).
    public static func checkDigit(_ s: String) -> Int {
        let weights = [7, 3, 1]
        var sum = 0
        for (i, c) in s.enumerated() {
            let v: Int
            if c.isASCIIDigit { v = Int(c.asciiValue! - 48) }
            else if c.isASCIIUpper { v = Int(c.asciiValue! - 65) + 10 }
            else { v = 0 }
            sum += v * weights[i % 3]
        }
        return sum % 10
    }
}

extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
    var isASCIIUpper: Bool { isASCII && ("A"..."Z").contains(self) }
}
