import Foundation

/// Minimal BER/DER TLV reader for the few ICAO structures parsed by hand (LDS files, SOD content,
/// master list content). Keeps the exact encoded bytes of every element so hashes and pins are
/// taken over what the issuer signed, never over a re-encoding.
public struct Tlv {
    /// Full tag bytes as one integer (e.g. 0x30, 0x5F1F, 0x7F61).
    public let tag: UInt
    /// Tag, length and value exactly as encoded.
    public let encoded: [UInt8]
    public let value: [UInt8]

    public var constructed: Bool { (firstTagByte & 0x20) != 0 }

    private var firstTagByte: UInt8 {
        var t = tag
        while t > 0xFF { t >>= 8 }
        return UInt8(t)
    }

    /// Children of a constructed element.
    public func children() throws -> [Tlv] { try Tlv.parseAll(value) }

    public func child(_ tag: UInt) throws -> Tlv? { try children().first { $0.tag == tag } }

    public enum DerError: Error, CustomStringConvertible {
        case truncated(Int), badLength(Int), indefinite

        public var description: String {
            switch self {
            case .truncated(let at): return "DER truncated at byte \(at)"
            case .badLength(let at): return "DER length invalid at byte \(at)"
            case .indefinite: return "indefinite DER length is not supported"
            }
        }
    }

    /// Parses one element starting at `offset`; returns it and the offset after it.
    public static func parse(_ b: [UInt8], at offset: Int = 0) throws -> (Tlv, Int) {
        var i = offset
        guard i < b.count else { throw DerError.truncated(i) }
        var tag = UInt(b[i]); i += 1
        if tag & 0x1F == 0x1F {
            repeat {
                guard i < b.count else { throw DerError.truncated(i) }
                tag = (tag << 8) | UInt(b[i]); i += 1
            } while b[i - 1] & 0x80 != 0
        }
        guard i < b.count else { throw DerError.truncated(i) }
        let first = Int(b[i]); i += 1
        var len = 0
        if first < 0x80 {
            len = first
        } else if first == 0x80 {
            throw DerError.indefinite
        } else {
            let n = first & 0x7F
            guard n <= 4, i + n <= b.count else { throw DerError.badLength(i) }
            for _ in 0..<n { len = (len << 8) | Int(b[i]); i += 1 }
        }
        guard len >= 0, i + len <= b.count else { throw DerError.truncated(i) }
        let tlv = Tlv(tag: tag, encoded: Array(b[offset..<(i + len)]), value: Array(b[i..<(i + len)]))
        return (tlv, i + len)
    }

    public static func parseAll(_ b: [UInt8]) throws -> [Tlv] {
        var out: [Tlv] = []
        var i = 0
        while i < b.count {
            let (t, next) = try parse(b, at: i)
            out.append(t)
            i = next
        }
        return out
    }

    /// Non-negative INTEGER value (small numbers only).
    public var intValue: Int { value.reduce(0) { ($0 << 8) | Int($1) } }

    /// OBJECT IDENTIFIER in dotted form.
    public var oid: String {
        guard let first = value.first else { return "" }
        var parts = [first < 80 ? Int(first) / 40 : 2, first < 80 ? Int(first) % 40 : Int(first) - 80]
        var acc = 0
        for byte in value.dropFirst() {
            acc = (acc << 7) | Int(byte & 0x7F)
            if byte & 0x80 == 0 { parts.append(acc); acc = 0 }
        }
        return parts.map(String.init).joined(separator: ".")
    }

    /// Value as text (PrintableString, UTF8String, or the raw bytes of an LDS text field).
    public var text: String { String(decoding: value, as: UTF8.self) }
}

extension Array where Element == UInt8 {
    public var hex: String { map { String(format: "%02X", $0) }.joined() }
}

public func hexBytes(_ s: String) -> [UInt8] {
    let c = Array(s.filter { !$0.isWhitespace && $0 != ":" }.utf8)
    return stride(from: 0, to: c.count - 1, by: 2).map { UInt8(String(decoding: c[$0...$0 + 1], as: UTF8.self), radix: 16)! }
}
