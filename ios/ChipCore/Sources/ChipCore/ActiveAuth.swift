import Foundation
import OpenSSL

/// Active Authentication (ICAO 9303 part 11, 6.1): the chip signs a fresh terminal challenge with
/// the private key matching DG15. A valid signature proves the chip holds a key that cannot be
/// copied, i.e. the chip is not a clone.
///
/// RSA chips sign with ISO/IEC 9796-2 scheme 1 (partial message recovery): the chip's own nonce
/// M1 is recovered from the signature and the challenge is the non-recoverable part M2.
/// EC chips sign the challenge with plain (r||s) ECDSA, hash taken from DG14.
public enum ActiveAuth {
    public struct Result {
        public let check: Check
        public let algorithm: String
        public let digest: String?
        public let detail: String?

        public init(check: Check, algorithm: String, digest: String?, detail: String?) {
            self.check = check; self.algorithm = algorithm; self.digest = digest; self.detail = detail
        }
    }

    /// `dg15`: the EF.DG15 file (tag 0x6F around a SubjectPublicKeyInfo).
    public static func verify(dg15: [UInt8], challenge: [UInt8], response: [UInt8], signatureAlgorithmOid: String?) -> Result {
        let key: OpaquePointer
        do {
            key = try publicKey(dg15: dg15)
        } catch {
            return Result(check: .fail, algorithm: "AA", digest: nil, detail: "\(error)")
        }
        defer { EVP_PKEY_free(key) }
        switch EVP_PKEY_get_base_id(key) {
        case EVP_PKEY_RSA: return verifyRsa(key, challenge, response)
        case EVP_PKEY_EC: return verifyEc(key, challenge, response, signatureAlgorithmOid)
        default: return Result(check: .unknown, algorithm: "type \(EVP_PKEY_get_base_id(key))", digest: nil, detail: "unsupported key type")
        }
    }

    static func publicKey(dg15: [UInt8]) throws -> OpaquePointer {
        let outer = try Tlv.parse(dg15).0
        let spki = outer.tag == 0x6F ? outer.value : dg15
        var p: UnsafePointer<UInt8>? = nil
        let key: OpaquePointer? = spki.withUnsafeBufferPointer { buf in
            p = buf.baseAddress
            return d2i_PUBKEY(nil, &p, buf.count)
        }
        guard let key else { throw CryptoError("DG15 holds no readable public key") }
        return key
    }

    private static func verifyRsa(_ key: OpaquePointer, _ challenge: [UInt8], _ response: [UInt8]) -> Result {
        let alg = "RSA ISO9796-2"
        guard var block = rawRsa(key, response) else {
            return Result(check: .fail, algorithm: alg, digest: nil, detail: CryptoError("RSA public operation failed").description)
        }
        // ISO 9796-2: the representative ends in nibble 0xC; otherwise the signer returned n - x.
        if block.last.map({ $0 & 0x0F }) != 0x0C {
            guard let flipped = modulusMinus(key, block) else {
                return Result(check: .fail, algorithm: alg, digest: nil, detail: "bad representative")
            }
            block = flipped
        }
        // The representative is one byte shorter than the modulus (top bit below the modulus).
        while block.first == 0 { block.removeFirst() }
        guard block.count > 2, block[0] & 0xC0 == 0x40 else {
            return Result(check: .fail, algorithm: alg, digest: nil, detail: "bad ISO 9796-2 header")
        }
        let trailer: Int, trailerLen: Int
        if block.last == 0xBC {
            trailer = 0xBC; trailerLen = 1
        } else {
            trailer = (Int(block[block.count - 2]) << 8) | Int(block[block.count - 1]); trailerLen = 2
        }
        guard let (md, name) = digestForTrailer(trailer) else {
            return Result(check: .fail, algorithm: alg, digest: nil, detail: String(format: "unknown trailer %04X", trailer))
        }
        let hashLen = (try? Digest.hash(md, []))?.count ?? 0
        // The recovered message starts after the first byte whose low nibble is 0xA (header + padding).
        guard let start = block.firstIndex(where: { $0 & 0x0F == 0x0A }).map({ $0 + 1 }),
              block.count - trailerLen - hashLen > start else {
            return Result(check: .fail, algorithm: alg, digest: name, detail: "no recoverable message")
        }
        let m1 = Array(block[start..<(block.count - trailerLen - hashLen)])
        let d = Array(block[(block.count - trailerLen - hashLen)..<(block.count - trailerLen)])
        let ok = (try? Digest.hash(md, m1 + challenge)) == d
        return Result(check: ok ? .pass : .fail, algorithm: alg, digest: name, detail: ok ? nil : "hash mismatch")
    }

    /// s^e mod n, left-padded to the modulus length.
    private static func rawRsa(_ key: OpaquePointer, _ sig: [UInt8]) -> [UInt8]? {
        guard let ctx = EVP_PKEY_CTX_new(key, nil) else { return nil }
        defer { EVP_PKEY_CTX_free(ctx) }
        guard EVP_PKEY_verify_recover_init(ctx) == 1,
              EVP_PKEY_CTX_set_rsa_padding(ctx, RSA_NO_PADDING) == 1 else { return nil }
        var out = [UInt8](repeating: 0, count: Int(EVP_PKEY_get_size(key)))
        var len = out.count
        guard EVP_PKEY_verify_recover(ctx, &out, &len, sig, sig.count) == 1 else { return nil }
        return Array(repeating: 0, count: out.count - len) + out.prefix(len)
    }

    private static func modulusMinus(_ key: OpaquePointer, _ x: [UInt8]) -> [UInt8]? {
        var n: OpaquePointer? = nil
        guard EVP_PKEY_get_bn_param(key, OSSL_PKEY_PARAM_RSA_N, &n) == 1, let n else { return nil }
        defer { BN_free(n) }
        guard let bx = BN_bin2bn(x, Int32(x.count), nil), let r = BN_new() else { return nil }
        defer { BN_free(bx); BN_free(r) }
        guard BN_sub(r, n, bx) == 1 else { return nil }
        var out = [UInt8](repeating: 0, count: x.count)
        guard BN_bn2binpad(r, &out, Int32(out.count)) == Int32(out.count) else { return nil }
        return out
    }

    private static func digestForTrailer(_ t: Int) -> (String, String)? {
        switch t {
        case 0xBC, 0x33CC: return ("SHA1", "SHA-1")
        case 0x34CC: return ("SHA256", "SHA-256")
        case 0x35CC: return ("SHA512", "SHA-512")
        case 0x36CC: return ("SHA384", "SHA-384")
        case 0x38CC: return ("SHA224", "SHA-224")
        default: return nil
        }
    }

    private static func verifyEc(_ key: OpaquePointer, _ challenge: [UInt8], _ response: [UInt8], _ oid: String?) -> Result {
        let alg = "ECDSA (plain)"
        let hash = ecHash(oid)
        guard response.count % 2 == 0, !response.isEmpty else {
            return Result(check: .fail, algorithm: alg, digest: hash, detail: "odd signature length")
        }
        // Plain r||s (BSI TR-03111) -> DER ECDSA-Sig-Value for OpenSSL.
        let half = response.count / 2
        guard let sig = ECDSA_SIG_new() else { return Result(check: .fail, algorithm: alg, digest: hash, detail: "alloc") }
        defer { ECDSA_SIG_free(sig) }
        let r = BN_bin2bn(Array(response[..<half]), Int32(half), nil)
        let s = BN_bin2bn(Array(response[half...]), Int32(half), nil)
        ECDSA_SIG_set0(sig, r, s)
        var der: UnsafeMutablePointer<UInt8>? = nil
        let derLen = i2d_ECDSA_SIG(sig, &der)
        guard derLen > 0, let der else { return Result(check: .fail, algorithm: alg, digest: hash, detail: "encode") }
        defer { CRYPTO_free(der, nil, 0) }

        guard let ctx = EVP_MD_CTX_new() else { return Result(check: .fail, algorithm: alg, digest: hash, detail: "alloc") }
        defer { EVP_MD_CTX_free(ctx) }
        let md = EVP_get_digestbyname(Digest.openSslName(hash) ?? "SHA256")
        ERR_clear_error()
        guard EVP_DigestVerifyInit(ctx, nil, md, nil, key) == 1 else {
            return Result(check: .fail, algorithm: alg, digest: hash, detail: CryptoError("verify init").description)
        }
        let ok = EVP_DigestVerify(ctx, der, Int(derLen), challenge, challenge.count) == 1
        ERR_clear_error()
        return Result(check: ok ? .pass : .fail, algorithm: alg, digest: hash, detail: ok ? nil : "signature invalid")
    }

    /// Maps X9.62 and BSI TR-03111 ecdsa-plain OIDs to a hash; ICAO requires DG14 to name it.
    static func ecHash(_ oid: String?) -> String {
        switch oid {
        case "1.2.840.10045.4.1", "0.4.0.127.0.7.1.1.4.1.1": return "SHA-1"
        case "1.2.840.10045.4.3.1", "0.4.0.127.0.7.1.1.4.1.2": return "SHA-224"
        case "1.2.840.10045.4.3.2", "0.4.0.127.0.7.1.1.4.1.3": return "SHA-256"
        case "1.2.840.10045.4.3.3", "0.4.0.127.0.7.1.1.4.1.4": return "SHA-384"
        case "1.2.840.10045.4.3.4", "0.4.0.127.0.7.1.1.4.1.5": return "SHA-512"
        default: return "SHA-256"
        }
    }
}
