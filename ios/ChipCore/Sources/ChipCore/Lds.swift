import Foundation

/// Parsers for the LDS data groups ChipID shows (ICAO 9303 part 10). Each takes the whole file
/// as read from the chip, including its outer tag.
public enum Lds {
    /// DG1: the MRZ as stored on the chip.
    public struct Dg1: Equatable, Sendable {
        public let raw: String
        public let documentCode: String
        public let issuingState: String
        public let documentNumber: String
        public let primaryIdentifier: String
        public let secondaryIdentifier: String
        public let nationality: String
        public let dateOfBirth: String
        public let sex: String
        public let dateOfExpiry: String
        /// TD3 personal number, or TD1/TD2 optional data.
        public let optionalData: String?
    }

    public struct Dg11: Equatable, Sendable {
        public var nameOfHolder: String?
        public var otherNames: [String] = []
        public var personalNumber: String?
        public var fullDateOfBirth: String?
        public var placeOfBirth: [String] = []
        public var permanentAddress: [String] = []
        public var telephone: String?
        public var profession: String?
        public var title: String?
    }

    public struct Dg12: Equatable, Sendable {
        public var issuingAuthority: String?
        public var dateOfIssue: String?
        public var personalizationTime: String?
    }

    public struct Face: Sendable {
        /// "image/jpeg" or "image/jp2".
        public let mimeType: String
        public let bytes: [UInt8]
    }

    public static func dg1(_ file: [UInt8]) throws -> Dg1 {
        let outer = try Tlv.parse(file).0
        guard outer.tag == 0x61, let mrz = try outer.child(0x5F1F) else { throw CryptoError("not a DG1") }
        return try parseMrz(mrz.text)
    }

    static func parseMrz(_ s: String) throws -> Dg1 {
        let a = Array(s)
        func f(_ from: Int, _ to: Int) -> String { String(a[min(from, a.count)..<min(to, a.count)]) }
        func strip(_ v: String) -> String { v.trimmingCharacters(in: CharacterSet(charactersIn: "<")) }
        func name(_ v: String) -> (String, String) {
            let parts = strip(v).components(separatedBy: "<<")
            let clean = { (p: String) in p.replacingOccurrences(of: "<", with: " ").trimmingCharacters(in: .whitespaces) }
            return (clean(parts.first ?? ""), clean(parts.dropFirst().joined(separator: " ")))
        }
        switch a.count {
        case 90: // TD1: 3 x 30
            var doc = f(5, 14), opt = f(15, 30)
            // Long document numbers continue in the optional data; '<' replaces the check digit.
            if a[14] == "<" {
                let tail = strip(opt).split(separator: "<").first.map(String.init) ?? ""
                if tail.count > 1 { doc += tail.dropLast(); opt = String(opt.dropFirst(tail.count + 1)) }
            }
            let (p, g) = name(f(60, 90))
            return Dg1(raw: s, documentCode: strip(f(0, 2)), issuingState: f(2, 5), documentNumber: strip(doc),
                       primaryIdentifier: p, secondaryIdentifier: g, nationality: f(45, 48), dateOfBirth: f(30, 36),
                       sex: f(37, 38), dateOfExpiry: f(38, 44), optionalData: nonEmpty(strip(opt) + strip(f(48, 59))))
        case 72: // TD2: 2 x 36
            let (p, g) = name(f(5, 36))
            return Dg1(raw: s, documentCode: strip(f(0, 2)), issuingState: f(2, 5), documentNumber: strip(f(36, 45)),
                       primaryIdentifier: p, secondaryIdentifier: g, nationality: f(46, 49), dateOfBirth: f(49, 55),
                       sex: f(56, 57), dateOfExpiry: f(57, 63), optionalData: nonEmpty(strip(f(64, 71))))
        case 88: // TD3: 2 x 44
            let (p, g) = name(f(5, 44))
            return Dg1(raw: s, documentCode: strip(f(0, 2)), issuingState: f(2, 5), documentNumber: strip(f(44, 53)),
                       primaryIdentifier: p, secondaryIdentifier: g, nationality: f(54, 57), dateOfBirth: f(57, 63),
                       sex: f(64, 65), dateOfExpiry: f(65, 71), optionalData: nonEmpty(strip(f(72, 86))))
        default:
            throw CryptoError("DG1 MRZ has \(a.count) characters")
        }
    }

    public static func dg11(_ file: [UInt8]) throws -> Dg11 {
        let outer = try Tlv.parse(file).0
        guard outer.tag == 0x6B else { throw CryptoError("not a DG11") }
        var d = Dg11()
        for t in try outer.children() {
            switch t.tag {
            case 0x5F0E: d.nameOfHolder = nonEmpty(t.text)
            case 0xA0: d.otherNames = (try? t.children())?.filter { $0.tag == 0x5F0F }.map(\.text) ?? []
            case 0x5F10: d.personalNumber = nonEmpty(t.text)
            case 0x5F2B: d.fullDateOfBirth = date(t.value)
            case 0x5F11: d.placeOfBirth = list(t.text)
            case 0x5F42: d.permanentAddress = list(t.text)
            case 0x5F12: d.telephone = nonEmpty(t.text)
            case 0x5F13: d.profession = nonEmpty(t.text)
            case 0x5F14: d.title = nonEmpty(t.text)
            default: break
            }
        }
        return d
    }

    public static func dg12(_ file: [UInt8]) throws -> Dg12 {
        let outer = try Tlv.parse(file).0
        guard outer.tag == 0x6C else { throw CryptoError("not a DG12") }
        var d = Dg12()
        for t in try outer.children() {
            switch t.tag {
            case 0x5F19: d.issuingAuthority = nonEmpty(t.text)
            case 0x5F26: d.dateOfIssue = date(t.value)
            case 0x5F55: d.personalizationTime = date(t.value)
            default: break
            }
        }
        return d
    }

    /// Face images in DG2 (ICAO 9303 part 10, 4.7.2): biometric data blocks (5F2E or 7F2E) holding an
    /// ISO/IEC 19794-5 record. The image is located by its JPEG or JPEG 2000 signature and runs to the
    /// end of the block.
    public static func dg2Faces(_ file: [UInt8]) throws -> [Face] {
        var blocks: [[UInt8]] = []
        func walk(_ tlvs: [Tlv]) {
            for t in tlvs {
                if t.tag == 0x5F2E || t.tag == 0x7F2E { blocks.append(t.value) }
                else if t.constructed, let kids = try? t.children() { walk(kids) }
            }
        }
        walk([try Tlv.parse(file).0])
        return blocks.compactMap { b in
            let signatures: [([UInt8], String)] = [
                ([0xFF, 0xD8, 0xFF], "image/jpeg"),
                ([0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20], "image/jp2"),
                ([0xFF, 0x4F, 0xFF, 0x51], "image/jp2"),
            ]
            let hits = signatures.compactMap { sig, mime in find(sig, in: b).map { ($0, mime) } }
            guard let (start, mime) = hits.min(by: { $0.0 < $1.0 }) else { return nil }
            return Face(mimeType: mime, bytes: Array(b[start...]))
        }
    }

    private static func find(_ needle: [UInt8], in hay: [UInt8]) -> Int? {
        guard hay.count >= needle.count else { return nil }
        for i in 0...(hay.count - needle.count) where hay[i] == needle[0] && Array(hay[i..<i + needle.count]) == needle { return i }
        return nil
    }

    /// LDS dates are "YYYYMMDD" text, but some issuers store 4 BCD bytes.
    private static func date(_ v: [UInt8]) -> String? {
        if v.count == 4 { return v.map { String(format: "%02X", $0) }.joined() }
        return nonEmpty(String(decoding: v, as: UTF8.self))
    }

    private static func list(_ s: String) -> [String] {
        s.split(separator: "<").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    private static func nonEmpty(_ s: String) -> String? {
        let t = s.trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : t
    }
}
