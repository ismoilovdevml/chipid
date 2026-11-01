import ChipCore
import Foundation
import NFCPassportReader

/// Runs one NFC read and turns the raw files into a verified report.
enum ChipReader {
    static var nfcAvailable: Bool { ChipSession.isAvailable }

    @MainActor
    static func read(key: AccessKey, anchors: [Certificate], progress: @escaping @MainActor (ChipProgress) -> Void) async throws -> ReadReport {
        let access: ChipAccess
        switch key {
        case .mrz: access = .mrz(key.mrzInformation!)
        case .can(let can): access = .can(can)
        }
        let session = ChipSession(
            access: access,
            listDataGroups: { try SecurityObject.parse($0).dataGroupHashes.keys.sorted() },
            progress: { p in Task { @MainActor in progress(p) } }
        )
        let files = try await session.read(prompt: "Hold the top of your iPhone against the document")
        return verify(files, anchors: anchors)
    }

    static func verify(_ f: ChipFiles, anchors: [Certificate]) -> ReadReport {
        let pa = PassiveAuth.verify(sod: f.sod, dataGroups: f.dataGroups, anchors: anchors)
        let aa: ActiveAuth.Result? = f.dataGroups[15].map { dg15 in
            if let challenge = f.aaChallenge, let response = f.aaResponse {
                return ActiveAuth.verify(dg15: dg15, challenge: challenge, response: response, signatureAlgorithmOid: f.aaSignatureOid)
            }
            return ActiveAuth.Result(check: .fail, algorithm: "AA", digest: nil, detail: f.aaError ?? "no response")
        }
        let ca = ChipAuthOutcome(f.chipAuthPassed.map { $0 ? .pass : .fail } ?? .skipped, f.chipAuthDetail)
        return ReadReport(accessProtocol: f.accessProtocol, paceNote: f.paceNote, cardAccess: f.cardAccess, sod: f.sod,
                          dataGroups: f.dataGroups, notRead: f.notRead, passive: pa, chipAuth: ca, activeAuth: aa,
                          tagInfo: f.tagInfo, millis: f.millis, log: f.log)
    }

    /// The ICAO 9303 specimen (ERIKSSON, Utopia) signed with throwaway test keys, for trying the
    /// result screens without a chip. Its CSCA is in no trust list, so it never shows as genuine.
    static func demo(anchors: [Certificate]) -> ReadReport? {
        guard let url = Bundle.main.url(forResource: "demo", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let hex = try? JSONSerialization.jsonObject(with: data) as? [String: String] else { return nil }
        var dgs: [Int: [UInt8]] = [:]
        for (k, v) in hex where k.hasPrefix("Dg") { dgs[Int(k.dropFirst(2))!] = hexBytes(v) }
        let sod = hexBytes(hex["Sod"] ?? "")
        let pa = PassiveAuth.verify(sod: sod, dataGroups: dgs, anchors: anchors)
        return ReadReport(accessProtocol: "Demo document (no chip)", paceNote: nil, cardAccess: nil, sod: sod, dataGroups: dgs,
                          notRead: [:], passive: pa, chipAuth: ChipAuthOutcome(.skipped, "Demo: no chip to authenticate"),
                          activeAuth: nil, tagInfo: [("Source", "Bundled ICAO specimen, test keys")], millis: 0, log: [])
    }
}
