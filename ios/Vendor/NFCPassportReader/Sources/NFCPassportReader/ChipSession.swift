// ChipID addition (not upstream): reads an eMRTD in the order the ChipID Android app uses and
// returns the raw files. All verification happens in ChipCore, outside this library.
//
// Order: EF.CardAccess -> PACE (MRZ or CAN; BAC fallback for MRZ) -> EF.SOD -> every data group SOD
// lists except EAC-protected DG3/DG4 -> Active Authentication -> Chip Authentication, then one DG1
// re-read under the CA session keys, which only succeeds if the chip owns the DG14 private key.
// Data is read before CA so a CA failure cannot cost the user their data.

import Foundation
#if !os(macOS)
import CoreNFC

public enum ChipAccess {
    /// MRZ information: number + check digit, birth date + check digit, expiry + check digit.
    case mrz(String)
    case can(String)
}

public enum ChipStage: Int, Sendable { case found, channel, data, verify }

public struct ChipProgress: Sendable {
    public let stage: ChipStage
    public let fraction: Float
    public let detail: String
}

public struct ChipFiles {
    public var accessProtocol = ""
    public var paceNote: String?
    public var cardAccess: [UInt8]?
    public var sod: [UInt8] = []
    public var dataGroups: [Int: [UInt8]] = [:]
    public var notRead: [Int: String] = [:]
    public var aaChallenge: [UInt8]?
    public var aaResponse: [UInt8]?
    public var aaError: String?
    /// Signature algorithm from DG14's ActiveAuthenticationInfo (EC chips).
    public var aaSignatureOid: String?
    /// nil: no DG14 / no CA key. Otherwise whether CA and the DG1 re-read succeeded.
    public var chipAuthPassed: Bool?
    public var chipAuthDetail = ""
    public var tagInfo: [(String, String)] = []
    public var millis = 0
    public var log: [String] = []
}

public struct ChipReadError: Error, CustomStringConvertible {
    public let description: String
    public let cancelled: Bool
    init(_ message: String, cancelled: Bool = false) { description = message; self.cancelled = cancelled }
}

/// One NFC reading session. Create a new instance per read.
public final class ChipSession: NSObject, NFCTagReaderSessionDelegate, @unchecked Sendable {
    private let access: ChipAccess
    private let listDataGroups: ([UInt8]) throws -> [Int]
    private let progress: (ChipProgress) -> Void
    private var session: NFCTagReaderSession?
    private var continuation: CheckedContinuation<ChipFiles, Error>?
    private var finished = false
    private var files = ChipFiles()
    private var currentLabel = ""

    /// `listDataGroups` returns the data group numbers EF.SOD covers (parsed by the caller).
    public init(access: ChipAccess, listDataGroups: @escaping ([UInt8]) throws -> [Int], progress: @escaping (ChipProgress) -> Void) {
        self.access = access
        self.listDataGroups = listDataGroups
        self.progress = progress
    }

    public static var isAvailable: Bool { NFCTagReaderSession.readingAvailable }

    public func read(prompt: String) async throws -> ChipFiles {
        guard NFCTagReaderSession.readingAvailable else { throw ChipReadError("This iPhone cannot read NFC chips.") }
        return try await withCheckedThrowingContinuation { c in
            continuation = c
            DispatchQueue.main.async {
                self.session = NFCTagReaderSession(pollingOption: [.iso14443], delegate: self, queue: nil)
                self.session?.alertMessage = prompt
                self.session?.begin()
            }
        }
    }

    private func finish(_ result: Result<ChipFiles, Error>, message: String? = nil) {
        guard !finished else { return }
        finished = true
        if case .failure = result { session?.invalidate(errorMessage: message ?? "Reading stopped") }
        else { session?.alertMessage = message ?? "Done"; session?.invalidate() }
        continuation?.resume(with: result)
        continuation = nil
    }

    // MARK: NFCTagReaderSessionDelegate

    public func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    public func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        guard !finished else { return }
        let code = (error as? NFCReaderError)?.code
        switch code {
        case .readerSessionInvalidationErrorUserCanceled:
            finish(.failure(ChipReadError("Cancelled.", cancelled: true)))
        case .readerSessionInvalidationErrorSessionTimeout:
            finish(.failure(ChipReadError("The reading window closed (iOS allows about a minute). Hold the document still and try again.")))
        default:
            finish(.failure(ChipReadError(error.localizedDescription)))
        }
    }

    public func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard tags.count == 1, case let .iso7816(chip) = tags[0] else {
            session.alertMessage = tags.count > 1 ? "More than one chip found. Use one document." : "This is not a passport or ID chip."
            session.restartPolling()
            return
        }
        Task {
            do {
                try await session.connect(to: tags[0])
                let result = try await run(chip)
                finish(.success(result), message: "Chip read")
            } catch let e as ChipReadError {
                finish(.failure(e), message: e.description)
            } catch {
                let lost = (error as? NFCReaderError).map {
                    $0.code == .readerTransceiveErrorTagConnectionLost || $0.code == .readerTransceiveErrorTagResponseError
                } ?? false
                let msg = lost
                    ? "The connection was lost. Keep the document still against the phone and try again."
                    : "The connection was lost (\(describe(error))). Keep the document still against the phone and try again."
                finish(.failure(ChipReadError(msg)), message: msg)
            }
        }
    }

    // MARK: Reading

    private func step(_ stage: ChipStage, _ f: Float, _ s: String) {
        files.log.append(s)
        currentLabel = s
        progress(ChipProgress(stage: stage, fraction: f, detail: s))
        session?.alertMessage = "\(s)… \(Int(f * 100))%"
    }

    private func run(_ chip: NFCISO7816Tag) async throws -> ChipFiles {
        let started = Date()
        let reader = TagReader(tag: chip)
        files.tagInfo = tagInfo(chip)
        step(.found, 0.04, "Reading EF.CardAccess")

        // ---- Access control ----------------------------------------------------------------
        var cardAccess: CardAccess?
        do {
            let raw = try await reader.readCardAccess()
            files.cardAccess = raw
            cardAccess = try CardAccess(raw)
        } catch {
            files.log.append("No EF.CardAccess: \(describe(error))")
        }
        try await authenticate(reader, cardAccess: cardAccess, first: true)

        // ---- Files ---------------------------------------------------------------------------
        step(.data, 0.16, "Reading EF.SOD")
        files.sod = try await reader.readDataGroup(dataGroup: .SOD)
        let listed = try listDataGroups(files.sod).sorted()
        files.log.append("Data groups in SOD: " + listed.map { "DG\($0)" }.joined(separator: ", "))

        let eacProtected: Set<Int> = [3, 4]
        let toRead = listed.filter { !eacProtected.contains($0) }
        // Progress band 0.2..0.85 split by weight; the face image (DG2) dominates the transfer.
        let weight = { (dg: Int) -> Float in dg == 2 ? 8 : 1 }
        let total = max(toRead.map(weight).reduce(0, +), 1)
        var done: Float = 0
        var channelBroken = false
        for dg in listed {
            if eacProtected.contains(dg) {
                files.notRead[dg] = "Needs EAC terminal authentication (state-issued certificates)"
                continue
            }
            if channelBroken { files.notRead[dg] = "Not read: the secure channel could not be re-opened"; continue }
            guard let id = DataGroupId.allCases.first(where: { $0.getName() == "DG\(dg)" }) else {
                files.notRead[dg] = "Unsupported data group"; continue
            }
            let base = done, w = weight(dg)
            step(.data, 0.2 + 0.65 * base / total, "Reading DG\(dg)")
            reader.progress = { [weak self] pct in
                guard let self else { return }
                let f = 0.2 + 0.65 * (base + w * Float(pct) / 100) / total
                self.progress(ChipProgress(stage: .data, fraction: f, detail: "Reading DG\(dg)"))
                self.session?.alertMessage = "Reading DG\(dg)… \(Int(f * 100))%"
            }
            do {
                files.dataGroups[dg] = try await readWithRetry(reader, id, cardAccess: cardAccess)
            } catch {
                files.notRead[dg] = describe(error)
                // A chip ends secure messaging after an error status; open a new channel to go on.
                do { try await authenticate(reader, cardAccess: cardAccess, first: false) } catch { channelBroken = true }
            }
            reader.progress = nil
            done += w
        }

        // ---- Active Authentication (DG15) ---------------------------------------------------
        let dg14 = files.dataGroups[14].flatMap { try? DataGroup14($0) }
        files.aaSignatureOid = dg14?.securityInfos.compactMap { $0 as? ActiveAuthenticationInfo }.first?.signatureAlgorithmOID
        if files.dataGroups[15] != nil, !channelBroken {
            step(.verify, 0.87, "Active Authentication")
            var challenge = [UInt8](repeating: 0, count: 8)
            _ = SecRandomCopyBytes(kSecRandomDefault, challenge.count, &challenge)
            files.aaChallenge = challenge
            do {
                files.aaResponse = try await reader.doInternalAuthentication(challenge: challenge, useExtendedMode: false).data
            } catch {
                files.aaError = describe(error)
                do { try await authenticate(reader, cardAccess: cardAccess, first: false) } catch { channelBroken = true }
            }
        }

        // ---- Chip Authentication (DG14) -----------------------------------------------------
        if let dg14, !channelBroken {
            let ca = ChipAuthenticationHandler(dg14: dg14, tagReader: reader)
            if ca.isChipAuthenticationSupported {
                step(.verify, 0.92, "Chip Authentication")
                do {
                    try await ca.doChipAuthentication()
                    // The new session keys depend on the chip's private key: a clone without it
                    // cannot answer this read with a valid MAC.
                    let again = try await reader.readDataGroup(dataGroup: .DG1)
                    let same = files.dataGroups[1] == nil || again == files.dataGroups[1]
                    files.chipAuthPassed = same
                    files.chipAuthDetail = same ? "\(caName(dg14)); DG1 re-read under the new session keys" : "DG1 differed after Chip Authentication"
                } catch {
                    files.chipAuthPassed = false
                    files.chipAuthDetail = "Chip Authentication failed: \(describe(error))"
                }
            } else {
                files.chipAuthDetail = "DG14 has no Chip Authentication key"
            }
        } else {
            files.chipAuthDetail = dg14 == nil ? "No DG14: chip does not offer Chip Authentication" : "Skipped: secure channel lost"
        }

        step(.verify, 0.97, "Checking signatures")
        files.millis = Int(Date().timeIntervalSince(started) * 1000)
        return files
    }

    private func readWithRetry(_ reader: TagReader, _ id: DataGroupId, cardAccess: CardAccess?) async throws -> [UInt8] {
        do {
            return try await reader.readDataGroup(dataGroup: id)
        } catch let NFCPassportReaderError.ResponseError(msg, _, _) where msg.hasPrefix("Wrong length") || msg.hasPrefix("End of file") {
            // The chip cannot serve 0xA0-byte reads; drop to a smaller block over a fresh channel.
            reader.overrideDataAmountToRead(newAmount: 0x60)
            try await authenticate(reader, cardAccess: cardAccess, first: false)
            return try await reader.readDataGroup(dataGroup: id)
        }
    }

    /// Opens secure messaging: PACE when EF.CardAccess offers it, else BAC (MRZ key only).
    private func authenticate(_ reader: TagReader, cardAccess: CardAccess?, first: Bool) async throws {
        reader.secureMessaging = nil
        if let cardAccess, cardAccess.paceInfo != nil, first || files.accessProtocol.hasPrefix("PACE") {
            let name = paceName(cardAccess)
            if first { step(.channel, 0.08, "PACE: \(name)") }
            do {
                if !first { _ = try await reader.send(cmd: NFCISO7816APDU(instructionClass: 0, instructionCode: 0xA4, p1Parameter: 0, p2Parameter: 0x0C, data: Data([0x3F, 0x00]), expectedResponseLength: -1)) }
                let pace = try PACEHandler(cardAccess: cardAccess, tagReader: reader)
                switch access {
                case .mrz(let k): try await pace.doPACE(mrzKey: k)
                case .can(let c): try await pace.doPACE(can: c)
                }
                _ = try await reader.selectPassportApplication()
                if first { files.accessProtocol = "PACE · \(name)" }
                return
            } catch {
                files.paceNote = "PACE failed (\(name)): \(describe(error))"
                files.log.append(files.paceNote!)
                reader.secureMessaging = nil
            }
        }
        guard case .mrz(let key) = access else {
            throw ChipReadError(cardAccess?.paceInfo == nil
                ? "This chip has no PACE, and a CAN only works with PACE. Use the MRZ instead."
                : "PACE with this CAN was rejected. Check the 6-digit number.")
        }
        if first { step(.channel, 0.10, "BAC") }
        _ = try? await reader.selectPassportApplication()
        do {
            try await BACHandler(tagReader: reader).performBACAndGetSessionKeys(mrzKey: key)
        } catch {
            throw ChipReadError("The chip refused the key. Check the document number and both dates.")
        }
        if first { files.accessProtocol = "BAC (3DES)" }
    }

    private func paceName(_ ca: CardAccess) -> String {
        guard let p = ca.paceInfo else { return "?" }
        let agreement = (try? p.getKeyAgreementAlgorithm()) ?? "?"
        let mapping = (try? p.getMappingType().description()) ?? "?"
        let cipher = (try? p.getCipherAlgorithm()) ?? "?"
        let bits = (try? p.getKeyLength()) ?? 0
        return "\(agreement)-\(mapping), \(cipher)-\(bits)"
    }

    private func caName(_ dg14: DataGroup14) -> String {
        let info = dg14.securityInfos.compactMap { $0 as? ChipAuthenticationInfo }.first
        return info.map { "CA \($0.getProtocolOIDString())" } ?? "CA"
    }

    private func tagInfo(_ chip: NFCISO7816Tag) -> [(String, String)] {
        let uid = [UInt8](chip.identifier)
        var out: [(String, String)] = [
            ("UID", binToHexRep(uid) + (uid.count == 4 && uid.first == 0x08 ? " (random, changes every session)" : "")),
            ("Selected AID", chip.initialSelectedAID),
        ]
        if let h = chip.historicalBytes { out.append(("Historical bytes (NFC-A)", binToHexRep([UInt8](h)))) }
        if let a = chip.applicationData { out.append(("Application data (NFC-B)", binToHexRep([UInt8](a)))) }
        return out
    }
}

func describe(_ error: Error) -> String {
    if let e = error as? NFCPassportReaderError { return e.value }
    if let e = error as? ChipReadError { return e.description }
    return error.localizedDescription
}
#endif
