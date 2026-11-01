import Foundation

/// EF.SOD content: LDSSecurityObject (ICAO 9303 part 10, 4.6.2).
public struct SecurityObject {
    public let digestAlgorithm: String
    public let dataGroupHashes: [Int: [UInt8]]
    public let ldsVersion: String?
    let signed: SignedData

    /// EF.SOD is [APPLICATION 23] (tag 0x77) wrapping a CMS ContentInfo.
    public static func parse(_ sod: [UInt8]) throws -> SecurityObject {
        let outer = try Tlv.parse(sod).0
        guard outer.tag == 0x77 else { throw CryptoError("not an EF.SOD (tag \(String(format: "%02X", outer.tag)))") }
        let signed = try SignedData.parse(outer.value)
        let fields = try Tlv.parse(signed.content).0.children()
        guard fields.count >= 3 else { throw CryptoError("short LDSSecurityObject") }
        guard let alg = try fields[1].children().first else { throw CryptoError("no hash algorithm") }
        var hashes: [Int: [UInt8]] = [:]
        for dgh in try fields[2].children() {
            let parts = try dgh.children()
            guard parts.count == 2 else { continue }
            hashes[parts[0].intValue] = parts[1].value
        }
        var lds: String?
        if fields.count > 3, let info = try? fields[3].children(), let v = info.first {
            let raw = v.text
            // "0108" -> "1.8"
            lds = raw.count == 4 ? "\(Int(raw.prefix(2)) ?? 0).\(Int(raw.suffix(2)) ?? 0)" : raw
        }
        return SecurityObject(digestAlgorithm: alg.oid, dataGroupHashes: hashes, ldsVersion: lds, signed: signed)
    }
}

/// Passive Authentication (ICAO 9303 part 11, 5.1):
///  1. every data group read from the chip hashes to the value stored in EF.SOD,
///  2. EF.SOD's CMS signature verifies with the Document Signer certificate (DSC) inside it,
///  3. the DSC is signed by a trusted Country Signing CA (CSCA).
/// Proves the data was written by the issuing state and not altered. It does not prove the chip
/// itself is genuine; that is Chip/Active Authentication's job.
public enum PassiveAuth {
    public struct Result {
        public let digestAlgorithm: String
        public let perDataGroup: [Int: Check]
        public let dataGroupHashes: Check
        public let sodSignature: Check
        public let signatureAlgorithm: String?
        public let cscaChain: Check
        public let dsc: Certificate?
        /// The trust anchor the DSC chained to, so callers can tell where that trust came from.
        public let csca: Certificate?
        public let cscaSubject: String?
        public let dscValidNow: Bool?
        public let ldsVersion: String?
        public let errors: [String]

        public var overall: Check { combine(dataGroupHashes, sodSignature, cscaChain) }
    }

    public static func verify(sod sodBytes: [UInt8], dataGroups: [Int: [UInt8]], anchors: [Certificate], now: Date = Date()) -> Result {
        var errors: [String] = []
        let sod: SecurityObject
        do {
            sod = try SecurityObject.parse(sodBytes)
        } catch {
            return Result(digestAlgorithm: "?", perDataGroup: [:], dataGroupHashes: .fail, sodSignature: .fail,
                          signatureAlgorithm: nil, cscaChain: .fail, dsc: nil, csca: nil, cscaSubject: nil,
                          dscValidNow: nil, ldsVersion: nil, errors: ["EF.SOD: \(error)"])
        }

        var perDg: [Int: Check] = [:]
        for dg in sod.dataGroupHashes.keys.sorted() {
            guard let raw = dataGroups[dg] else { perDg[dg] = .skipped; continue }
            let actual = try? Digest.hash(sod.digestAlgorithm, raw)
            if actual == sod.dataGroupHashes[dg] {
                perDg[dg] = .pass
            } else {
                perDg[dg] = .fail
                errors.append(actual == nil ? "DG\(dg): unsupported digest \(sod.digestAlgorithm)" : "DG\(dg) hash mismatch")
            }
        }
        let extra = Set(dataGroups.keys).subtracting(sod.dataGroupHashes.keys)
        if !extra.isEmpty { errors.append("Read but not covered by SOD: " + extra.sorted().map { "DG\($0)" }.joined(separator: ", ")) }
        let hashes: Check = perDg.values.contains(.fail) || !extra.isEmpty ? .fail : perDg.values.contains(.pass) ? .pass : .unknown

        let dsc = sod.signed.signer
        let sodSig: Check
        if dsc == nil {
            errors.append("SOD signature: DSC not embedded in SOD")
            sodSig = .fail
        } else if sod.signed.signatureValid {
            sodSig = .pass
        } else {
            errors.append("SOD signature invalid" + (sod.signed.verifyError.map { ": \($0)" } ?? ""))
            sodSig = .fail
        }

        var cscaSubject: String?
        var matched: Certificate?
        let chain: Check
        if let cert = dsc {
            // Narrow by name or key identifier; only the signature check below decides. Some issuers
            // encode the DSC issuer with RDNs in a different order than their CSCA subject.
            let candidates = anchors.filter { cert.isIssued(byName: $0) || $0.sameRdns(issuerOf: cert) || keyIdMatches(cert, $0) }
                .sorted { keyIdMatches(cert, $0) && !keyIdMatches(cert, $1) }
            if candidates.isEmpty {
                errors.append("No CSCA for issuer \(cert.issuer)")
                chain = .unknown
            } else if let ok = candidates.first(where: { cert.isSigned(by: $0) }) {
                matched = ok
                cscaSubject = "\(ok.subject) (serial \(ok.serialHex))"
                chain = .pass
            } else {
                errors.append("DSC signature does not verify with any matching CSCA")
                chain = .fail
            }
        } else {
            chain = .fail
        }

        return Result(digestAlgorithm: Digest.displayName(sod.digestAlgorithm), perDataGroup: perDg, dataGroupHashes: hashes,
                      sodSignature: sodSig, signatureAlgorithm: sod.signed.signatureAlgorithm, cscaChain: chain, dsc: dsc,
                      csca: matched, cscaSubject: cscaSubject, dscValidNow: dsc?.isValid(at: now), ldsVersion: sod.ldsVersion,
                      errors: errors)
    }

    static func keyIdMatches(_ dsc: Certificate, _ csca: Certificate) -> Bool {
        guard let aki = dsc.authorityKeyId, let ski = csca.subjectKeyId else { return false }
        return aki == ski
    }
}
