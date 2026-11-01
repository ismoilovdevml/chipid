import Foundation

/// ICAO CSCA master list (ICAO 9303 part 12): CMS SignedData whose eContent is
/// CscaMasterList ::= SEQUENCE { version INTEGER, certList SET OF Certificate }.
public struct MasterList {
    public let certificates: [Certificate]
    /// The CMS signature is intact and made by the embedded signer certificate.
    public let signatureValid: Bool
    /// The signer certificate is issued by one of the pinned roots, so the list is the publisher's.
    public let signerTrusted: Bool
    public let signerSubject: String?
    public let sha256: String

    public func countries() -> Set<String> { Set(certificates.compactMap(\.subjectCountry)) }

    public func byCountry(_ cc: String) -> [Certificate] { certificates.filter { $0.subjectCountry == cc } }

    /// SHA-256 of the SubjectPublicKeyInfo of every German CSCA key (C=DE, O=bund, OU=bsi,
    /// CN=csca-germany) in DE_ML_2026-10-01-11-50-52.ml, downloaded over HTTPS from bsi.bund.de on
    /// 2026-11-01. The BSI "CSCA Master List Signer" certificate must be issued by one of them.
    /// A new German CSCA key needs an app update. Same pins as the Android app.
    public static let germanCscaPins: Set<String> = [
        "cbd048969ae6171addb96cea36897f2fa381346b8ebc43c45bee24f0ad71ef42", // serial 101, 2013
        "20d1496eaa3c19f6af15aea9eda62ace5ad8ae6be66e637ba17c7190f779b2a3", // serial 103, 2016
        "04c58c0bee5e902c725954a490e451dcbafb281941ae8106cc746060d568a7ce", // 2019
        "aa9a2c80eb69bbb600255798e028b9a0177199b1851393329979be8e6ec9c3e2", // 2021
        "b7d7c66d3eb51e4fbd47c7e637cf31ee50447e4dff11e61e1b536f26373c5590", // 2024, signs the 2028 list signer
    ]

    public static func parse(_ bytes: [UInt8], pinnedRoots: Set<String> = germanCscaPins) throws -> MasterList {
        let cms = try SignedData.parse(bytes)
        let seq = try Tlv.parse(cms.content).0.children()
        guard seq.count >= 2, seq[1].tag == 0x31 else { throw CryptoError("not a CscaMasterList") }
        let certs = try seq[1].children().map { try Certificate(der: $0.encoded) }

        // The issuer may sit in the CMS certificate set or in the list itself; only a pinned key counts.
        var trusted = false
        if cms.signatureValid, let signer = cms.signer {
            trusted = (certs + cms.certificates).contains { root in
                pinnedRoots.contains(root.spkiSha256) && signer.isIssued(byName: root) && signer.isSigned(by: root)
            }
        }
        let hash = Digest.sha256(bytes).map { String(format: "%02x", $0) }.joined()
        return MasterList(certificates: certs, signatureValid: cms.signatureValid, signerTrusted: trusted,
                          signerSubject: cms.signer?.subject, sha256: hash)
    }
}
