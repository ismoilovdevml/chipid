import Foundation
import OpenSSL

/// An X.509 certificate backed by OpenSSL, keeping its original DER encoding.
public final class Certificate: @unchecked Sendable {
    let x509: OpaquePointer
    public let der: [UInt8]

    public init(der: [UInt8]) throws {
        var p: UnsafePointer<UInt8>? = nil
        let made: OpaquePointer? = der.withUnsafeBufferPointer { buf in
            p = buf.baseAddress
            return d2i_X509(nil, &p, buf.count)
        }
        guard let made else { throw CryptoError("not an X.509 certificate") }
        x509 = made
        self.der = der
    }

    /// Takes ownership of an X509 reference (the caller must have up-ref'd it).
    init(owning x: OpaquePointer) throws {
        x509 = x
        var out: UnsafeMutablePointer<UInt8>? = nil
        let n = i2d_X509(x, &out)
        guard n > 0, let out else { throw CryptoError("cannot encode certificate") }
        der = Array(UnsafeBufferPointer(start: out, count: Int(n)))
        CRYPTO_free(out, nil, 0)
    }

    deinit { X509_free(x509) }

    public var subject: String { Certificate.rfc2253(X509_get_subject_name(x509)) }
    public var issuer: String { Certificate.rfc2253(X509_get_issuer_name(x509)) }

    /// Subject and issuer compared as encoded names (OpenSSL canonical comparison).
    public func isIssued(byName other: Certificate) -> Bool {
        X509_NAME_cmp(X509_get_issuer_name(x509), X509_get_subject_name(other.x509)) == 0
    }

    public var serialHex: String {
        guard let bn = ASN1_INTEGER_to_BN(X509_get0_serialNumber(x509), nil) else { return "?" }
        defer { BN_free(bn) }
        guard let s = BN_bn2hex(bn) else { return "?" }
        defer { CRYPTO_free(s, nil, 0) }
        return String(cString: s).lowercased()
    }

    public var notBefore: Date? { Certificate.date(X509_get0_notBefore(x509)) }
    public var notAfter: Date? { Certificate.date(X509_get0_notAfter(x509)) }

    public func isValid(at date: Date) -> Bool? {
        guard let nb = notBefore, let na = notAfter else { return nil }
        return date >= nb && date <= na
    }

    public var authorityKeyId: [UInt8]? {
        guard let s = X509_get0_authority_key_id(x509) else { return nil }
        return Certificate.bytes(s)
    }

    public var subjectKeyId: [UInt8]? {
        guard let s = X509_get0_subject_key_id(x509) else { return nil }
        return Certificate.bytes(s)
    }

    /// "C" attribute of a distinguished name, e.g. "UZ".
    public var subjectCountry: String? { Certificate.country(of: subject) }
    public var issuerCountry: String? { Certificate.country(of: issuer) }

    public var issuerOrganisation: String? { Certificate.attribute("O", of: issuer) }

    public var publicKeyAlgorithm: String {
        guard let k = X509_get0_pubkey(x509) else { return "?" }
        switch EVP_PKEY_get_base_id(k) {
        case EVP_PKEY_RSA: return "RSA \(EVP_PKEY_get_bits(k))"
        case EVP_PKEY_RSA_PSS: return "RSA-PSS \(EVP_PKEY_get_bits(k))"
        case EVP_PKEY_EC: return "EC \(EVP_PKEY_get_bits(k))"
        default: return "type \(EVP_PKEY_get_base_id(k))"
        }
    }

    public var signatureAlgorithm: String {
        Oid.name(nid: X509_get_signature_nid(x509))
    }

    /// True if this certificate's signature verifies with `issuer`'s public key.
    public func isSigned(by issuer: Certificate) -> Bool {
        guard let key = X509_get0_pubkey(issuer.x509) else { return false }
        ERR_clear_error()
        defer { ERR_clear_error() }
        return X509_verify(x509, key) == 1
    }

    /// SHA-256 of the SubjectPublicKeyInfo exactly as encoded in the certificate.
    public var spkiSha256: String {
        guard let spki = try? Certificate.spki(der) else { return "" }
        return Digest.sha256(spki).map { String(format: "%02x", $0) }.joined()
    }

    /// tbsCertificate -> subjectPublicKeyInfo, read from the certificate DER without re-encoding.
    static func spki(_ der: [UInt8]) throws -> [UInt8] {
        let cert = try Tlv.parse(der).0
        guard let tbs = try cert.children().first else { throw CryptoError("empty certificate") }
        var fields = try tbs.children()
        if fields.first?.tag == 0xA0 { fields.removeFirst() } // [0] version
        // serialNumber, signature, issuer, validity, subject, subjectPublicKeyInfo
        guard fields.count > 5 else { throw CryptoError("short tbsCertificate") }
        return fields[5].encoded
    }

    /// Same set of RDN attribute values regardless of order and case.
    public func sameRdns(issuerOf dsc: Certificate) -> Bool {
        Certificate.rdnSet(X509_get_subject_name(x509)) == Certificate.rdnSet(X509_get_issuer_name(dsc.x509))
    }

    private static func rdnSet(_ name: OpaquePointer?) -> Set<String> {
        guard let name else { return [] }
        var out = Set<String>()
        for i in 0..<X509_NAME_entry_count(name) {
            guard let e = X509_NAME_get_entry(name, i),
                  let obj = X509_NAME_ENTRY_get_object(e),
                  let data = X509_NAME_ENTRY_get_data(e) else { continue }
            var utf8: UnsafeMutablePointer<UInt8>? = nil
            let n = ASN1_STRING_to_UTF8(&utf8, UnsafePointer(data))
            guard n >= 0, let utf8 else { continue }
            let value = String(decoding: UnsafeBufferPointer(start: utf8, count: Int(n)), as: UTF8.self)
            CRYPTO_free(utf8, nil, 0)
            out.insert("\(OBJ_obj2nid(obj))=\(value.trimmingCharacters(in: .whitespaces).lowercased())")
        }
        return out
    }

    static func rfc2253(_ name: OpaquePointer?) -> String {
        guard let name, let bio = BIO_new(BIO_s_mem()) else { return "" }
        defer { BIO_free(bio) }
        // RFC 2253 order and escaping, but UTF-8 kept readable (no \\XX escapes for non-ASCII).
        let strFlags = ASN1_STRFLGS_ESC_2253 | ASN1_STRFLGS_ESC_CTRL | ASN1_STRFLGS_UTF8_CONVERT | ASN1_STRFLGS_DUMP_UNKNOWN | ASN1_STRFLGS_DUMP_DER
        let flags = UInt(strFlags) | UInt(XN_FLAG_SEP_COMMA_PLUS) | UInt(XN_FLAG_DN_REV) | UInt(XN_FLAG_FN_SN) | UInt(XN_FLAG_DUMP_UNKNOWN_FIELDS)
        X509_NAME_print_ex(bio, name, 0, flags)
        return Bio.string(bio)
    }

    static func country(of dn: String) -> String? {
        guard let c = attribute("C", of: dn), c.count == 2 else { return nil }
        return c.uppercased()
    }

    static func attribute(_ key: String, of dn: String) -> String? {
        for part in dn.split(separator: ",") {
            let kv = part.split(separator: "=", maxSplits: 1)
            if kv.count == 2, kv[0].trimmingCharacters(in: .whitespaces) == key {
                return kv[1].trimmingCharacters(in: .whitespaces)
            }
        }
        return nil
    }

    private static func date(_ t: UnsafePointer<ASN1_TIME>?) -> Date? {
        guard let t else { return nil }
        var tm = tm()
        guard ASN1_TIME_to_tm(t, &tm) == 1 else { return nil }
        var c = DateComponents()
        c.year = Int(tm.tm_year) + 1900; c.month = Int(tm.tm_mon) + 1; c.day = Int(tm.tm_mday)
        c.hour = Int(tm.tm_hour); c.minute = Int(tm.tm_min); c.second = Int(tm.tm_sec)
        c.timeZone = TimeZone(identifier: "UTC")
        return Calendar(identifier: .gregorian).date(from: c)
    }

    private static func bytes(_ s: UnsafePointer<ASN1_STRING>) -> [UInt8] {
        let n = Int(ASN1_STRING_length(s))
        guard n > 0, let d = ASN1_STRING_get0_data(s) else { return [] }
        return Array(UnsafeBufferPointer(start: d, count: n))
    }
}

public struct CryptoError: Error, CustomStringConvertible {
    public let description: String
    init(_ message: String) {
        // Append the OpenSSL error queue, which usually names the real cause.
        var details: [String] = []
        while true {
            let e = ERR_get_error()
            if e == 0 { break }
            var buf = [CChar](repeating: 0, count: 256)
            ERR_error_string_n(e, &buf, buf.count)
            details.append(String(cString: buf))
        }
        description = details.isEmpty ? message : "\(message) (\(details.joined(separator: "; ")))"
    }
}

enum Bio {
    static func string(_ bio: OpaquePointer) -> String {
        var ptr: UnsafeMutableRawPointer? = nil
        let n = BIO_ctrl(bio, BIO_CTRL_INFO, 0, &ptr)
        guard n > 0, let ptr else { return "" }
        return String(decoding: UnsafeRawBufferPointer(start: ptr, count: n), as: UTF8.self)
    }

    static func bytes(_ bio: OpaquePointer) -> [UInt8] {
        var ptr: UnsafeMutableRawPointer? = nil
        let n = BIO_ctrl(bio, BIO_CTRL_INFO, 0, &ptr)
        guard n > 0, let ptr else { return [] }
        return Array(UnsafeRawBufferPointer(start: ptr, count: n))
    }

    /// A read-only memory BIO over `bytes`; the buffer must outlive the BIO.
    static func withMem<T>(_ bytes: [UInt8], _ body: (OpaquePointer) throws -> T) throws -> T {
        try bytes.withUnsafeBytes { raw in
            guard let bio = BIO_new_mem_buf(raw.baseAddress, Int32(raw.count)) else { throw CryptoError("BIO_new_mem_buf") }
            defer { BIO_free(bio) }
            return try body(bio)
        }
    }
}

enum Oid {
    static func name(nid: Int32) -> String {
        guard nid != NID_undef, let ln = OBJ_nid2ln(nid) else { return "unknown" }
        return String(cString: ln)
    }

    static func name(_ obj: OpaquePointer?) -> String {
        guard let obj else { return "unknown" }
        let nid = OBJ_obj2nid(obj)
        if nid != NID_undef { return name(nid: nid) }
        var buf = [CChar](repeating: 0, count: 128)
        OBJ_obj2txt(&buf, Int32(buf.count), obj, 1)
        return String(cString: buf)
    }
}

/// Message digests by ICAO/ASN.1 name or OID.
public enum Digest {
    /// Maps the OIDs and names seen in SODs to an OpenSSL digest name.
    static func openSslName(_ s: String) -> String? {
        switch s.uppercased().replacingOccurrences(of: "-", with: "") {
        case "1.3.14.3.2.26", "SHA1": return "SHA1"
        case "2.16.840.1.101.3.4.2.4", "SHA224": return "SHA224"
        case "2.16.840.1.101.3.4.2.1", "SHA256": return "SHA256"
        case "2.16.840.1.101.3.4.2.2", "SHA384": return "SHA384"
        case "2.16.840.1.101.3.4.2.3", "SHA512": return "SHA512"
        default: return nil
        }
    }

    /// "SHA-256" style display name for an OID or name.
    public static func displayName(_ s: String) -> String {
        guard let n = openSslName(s) else { return s }
        return "SHA-" + n.dropFirst(3)
    }

    public static func hash(_ algorithm: String, _ data: [UInt8]) throws -> [UInt8] {
        guard let name = openSslName(algorithm), let md = EVP_get_digestbyname(name) else {
            throw CryptoError("unsupported digest \(algorithm)")
        }
        var out = [UInt8](repeating: 0, count: Int(EVP_MAX_MD_SIZE))
        var len: UInt32 = 0
        guard EVP_Digest(data, data.count, &out, &len, md, nil) == 1 else { throw CryptoError("digest failed") }
        return Array(out.prefix(Int(len)))
    }

    public static func sha256(_ data: [UInt8]) -> [UInt8] { (try? hash("SHA256", data)) ?? [] }
}
