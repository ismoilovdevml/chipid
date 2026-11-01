import XCTest
@testable import ChipCore

/// Runs against the real BSI master list that the Android build fetches into its assets
/// (android/app/src/main/assets/masterlist.ml, not committed). Skipped when that file is absent.
final class MasterListTests: XCTestCase {
    static var bsiList: [UInt8]? {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("../../../../android/app/src/main/assets/masterlist.ml").standardized
        return (try? Data(contentsOf: url)).map { [UInt8]($0) }
    }

    func testBsiListSignerChainsToPinnedGermanCsca() throws {
        let bytes = try XCTUnwrap(Self.bsiList, "masterlist.ml not fetched")
        let ml = try MasterList.parse(bytes)
        XCTAssertTrue(ml.signatureValid)
        XCTAssertTrue(ml.signerTrusted)
        XCTAssertEqual(ml.certificates.count, 609)
        // 121 distinct C= spellings; al, gb, ro and si also appear in lower case.
        XCTAssertEqual(ml.countries().count, 117)
        XCTAssertEqual(ml.byCountry("UZ").count, 8)
        // Every pin is the SPKI hash of a German CSCA that is actually in the list.
        let germanPins = Set(ml.byCountry("DE").map(\.spkiSha256))
        XCTAssertTrue(MasterList.germanCscaPins.isSubset(of: germanPins))
    }

    func testListIsRejectedWithoutThePin() throws {
        let bytes = try XCTUnwrap(Self.bsiList, "masterlist.ml not fetched")
        let ml = try MasterList.parse(bytes, pinnedRoots: ["00"])
        XCTAssertTrue(ml.signatureValid)
        XCTAssertFalse(ml.signerTrusted)
    }

    func testTamperedListFailsSignature() throws {
        var bytes = try XCTUnwrap(Self.bsiList, "masterlist.ml not fetched")
        // Flip one byte deep inside the certificate list (inside the signed content).
        bytes[bytes.count / 2] ^= 0x01
        let ml = try? MasterList.parse(bytes)
        XCTAssertFalse(ml?.signatureValid ?? false)
        XCTAssertFalse(ml?.signerTrusted ?? false)
    }
}
