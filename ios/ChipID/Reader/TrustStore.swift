import ChipCore
import Foundation

/// CSCA trust anchors with their origin.
///
/// - The bundled BSI master list is used only if its signer chains to a pinned German CSCA;
///   a list that fails this check contributes no anchors (fail closed).
/// - Files the user puts in ChipID's "csca" folder (Files app → On My iPhone → ChipID) are honoured,
///   but a document that verifies only through one of them is flagged, never shown as plainly genuine.
struct TrustStore {
    let anchors: [Certificate]
    private let userAdded: Set<String>
    let sources: [String]
    let countries: Int
    let bundledTrusted: Bool

    func isUserAdded(_ cert: Certificate?) -> Bool {
        guard let cert else { return false }
        return userAdded.contains(cert.spkiSha256 + cert.serialHex)
    }

    var userAddedCount: Int { userAdded.count }

    static var userFolder: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("csca", isDirectory: true)
    }

    static func load() -> TrustStore {
        var bundled: [Certificate] = []
        var user: [Certificate] = []
        var sources: [String] = []
        var trusted = false

        if let url = Bundle.main.url(forResource: "masterlist", withExtension: "ml"),
           let data = try? Data(contentsOf: url), let ml = try? MasterList.parse([UInt8](data)) {
            trusted = ml.signerTrusted
            if ml.signerTrusted {
                bundled = ml.certificates
                sources.append("BSI master list: \(ml.certificates.count) CSCAs, signer chains to pinned German CSCA, sha256 \(ml.sha256.prefix(16))…")
            } else {
                sources.append("BSI master list REJECTED: signer does not chain to a pinned German CSCA (sha256 \(ml.sha256.prefix(16))…)")
            }
        } else {
            sources.append("No bundled master list in this build")
        }

        let fm = FileManager.default
        try? fm.createDirectory(at: userFolder, withIntermediateDirectories: true)
        let files = (try? fm.contentsOfDirectory(at: userFolder, includingPropertiesForKeys: nil)) ?? []
        for f in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) {
            guard let data = try? Data(contentsOf: f) else { continue }
            let bytes = [UInt8](data)
            var added: [Certificate] = []
            if f.pathExtension.lowercased() == "ml" {
                added = (try? MasterList.parse(bytes, pinnedRoots: []).certificates) ?? []
            } else if let c = try? Certificate(der: bytes) {
                added = [c]
            } else if let text = String(data: data, encoding: .utf8) {
                added = pemBlocks(text).compactMap { try? Certificate(der: $0) }
            }
            user += added
            sources.append(added.isEmpty ? "\(f.lastPathComponent): unreadable" : "\(f.lastPathComponent): \(added.count) user-added certificates")
        }

        var seen = Set<[UInt8]>()
        let unique = (bundled + user).filter { seen.insert($0.der).inserted }
        let bundledKeys = Set(bundled.map { $0.spkiSha256 + $0.serialHex })
        // A certificate that is also in the verified list keeps its verified status.
        let userOnly = Set(user.map { $0.spkiSha256 + $0.serialHex }).subtracting(bundledKeys)
        let countries = Set(unique.compactMap(\.subjectCountry)).count
        return TrustStore(anchors: unique, userAdded: userOnly, sources: sources, countries: countries, bundledTrusted: trusted)
    }

    private static func pemBlocks(_ text: String) -> [[UInt8]] {
        text.components(separatedBy: "-----BEGIN CERTIFICATE-----").dropFirst().compactMap { block in
            guard let body = block.components(separatedBy: "-----END CERTIFICATE-----").first else { return nil }
            return Data(base64Encoded: body.filter { !$0.isWhitespace }).map { [UInt8]($0) }
        }
    }
}
