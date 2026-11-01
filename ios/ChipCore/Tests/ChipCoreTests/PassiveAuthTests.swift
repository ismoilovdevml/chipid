import XCTest
@testable import ChipCore

final class PassiveAuthTests: XCTestCase {
    let dg1 = BcFixtures.bytes("dg1")
    let dg2 = BcFixtures.bytes("dg2")
    let sod = BcFixtures.bytes("sod")

    func testFullChainVerifies() {
        let csca = BcFixtures.cert("csca")
        let r = PassiveAuth.verify(sod: sod, dataGroups: [1: dg1, 2: dg2], anchors: [csca])
        XCTAssertEqual(r.dataGroupHashes, .pass)
        XCTAssertEqual(r.sodSignature, .pass)
        XCTAssertEqual(r.cscaChain, .pass)
        XCTAssertEqual(r.overall, .pass)
        XCTAssertEqual(r.digestAlgorithm, "SHA-256")
        XCTAssertTrue(r.cscaSubject!.contains("CSCA-TEST"))
        XCTAssertEqual(r.csca?.der, csca.der)
        XCTAssertEqual(r.dsc?.subjectCountry, "UT")
        XCTAssertEqual(r.dscValidNow, true)
        XCTAssertTrue(r.errors.isEmpty, "\(r.errors)")
    }

    func testTamperedDataGroupFails() {
        var bad = dg2
        bad[10] = 99
        let r = PassiveAuth.verify(sod: sod, dataGroups: [1: dg1, 2: bad], anchors: [BcFixtures.cert("csca")])
        XCTAssertEqual(r.dataGroupHashes, .fail)
        XCTAssertEqual(r.perDataGroup[2], .fail)
        XCTAssertEqual(r.perDataGroup[1], .pass)
        XCTAssertEqual(r.overall, .fail)
    }

    func testTamperedSodSignatureFails() {
        var bad = sod
        // Flip a byte inside the signed LDSSecurityObject (a DG hash), keeping the DER valid.
        let target = BcFixtures.all["sod"]!.range(of: Digest.sha256(dg2).map { String(format: "%02x", $0) }.joined())!
        let index = BcFixtures.all["sod"]!.distance(from: BcFixtures.all["sod"]!.startIndex, to: target.lowerBound) / 2
        bad[index] ^= 0x01
        let r = PassiveAuth.verify(sod: bad, dataGroups: [1: dg1], anchors: [BcFixtures.cert("csca")])
        XCTAssertEqual(r.sodSignature, .fail)
        XCTAssertEqual(r.overall, .fail)
    }

    func testUnknownCscaIsNotAFailureButIsReported() {
        let r = PassiveAuth.verify(sod: sod, dataGroups: [1: dg1, 2: dg2], anchors: [BcFixtures.cert("otherCsca")])
        XCTAssertEqual(r.sodSignature, .pass)
        XCTAssertEqual(r.cscaChain, .unknown)
        XCTAssertEqual(r.overall, .unknown)
    }

    func testCscaWithSameNameButWrongKeyFails() {
        let r = PassiveAuth.verify(sod: sod, dataGroups: [1: dg1, 2: dg2], anchors: [BcFixtures.cert("impostorCsca")])
        XCTAssertEqual(r.cscaChain, .fail)
        XCTAssertEqual(r.overall, .fail)
    }

    func testIssuerNameInDifferentRdnOrderStillChains() {
        let r = PassiveAuth.verify(sod: BcFixtures.bytes("reversedSod"), dataGroups: [1: dg1, 2: dg2], anchors: [BcFixtures.cert("reversedCsca")])
        XCTAssertEqual(r.cscaChain, .pass)
        XCTAssertEqual(r.overall, .pass)
    }

    func testDataGroupNotReadIsSkipped() {
        let r = PassiveAuth.verify(sod: sod, dataGroups: [1: dg1], anchors: [BcFixtures.cert("csca")])
        XCTAssertEqual(r.dataGroupHashes, .pass)
        XCTAssertEqual(r.perDataGroup[2], .skipped)
    }

    func testGarbageSodFailsCleanly() {
        let r = PassiveAuth.verify(sod: [0x77, 0x02, 0x30, 0x00], dataGroups: [1: dg1], anchors: [])
        XCTAssertEqual(r.overall, .fail)
        XCTAssertFalse(r.errors.isEmpty)
    }
}

final class ActiveAuthTests: XCTestCase {
    let challenge = BcFixtures.bytes("challenge")

    func testRsaIso9796WithImplicitSha1Verifies() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("rsaSha1Dg15"), challenge: challenge, response: BcFixtures.bytes("rsaSha1Sig"), signatureAlgorithmOid: nil)
        XCTAssertEqual(r.check, .pass, r.detail ?? "")
        XCTAssertEqual(r.digest, "SHA-1")
    }

    func testRsaIso9796WithExplicitSha256Verifies() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("rsaSha256Dg15"), challenge: challenge, response: BcFixtures.bytes("rsaSha256Sig"), signatureAlgorithmOid: nil)
        XCTAssertEqual(r.check, .pass, r.detail ?? "")
        XCTAssertEqual(r.digest, "SHA-256")
    }

    func testRsaWithWrongChallengeFails() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("rsaSha1Dg15"), challenge: challenge.reversed(), response: BcFixtures.bytes("rsaSha1Sig"), signatureAlgorithmOid: nil)
        XCTAssertEqual(r.check, .fail)
    }

    func testRsaWithOtherKeyFails() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("rsaSha256Dg15"), challenge: challenge, response: BcFixtures.bytes("rsaSha1Sig"), signatureAlgorithmOid: nil)
        XCTAssertEqual(r.check, .fail)
    }

    func testBrainpoolEcdsaPlainVerifies() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("ecDg15"), challenge: challenge, response: BcFixtures.bytes("ecSig"), signatureAlgorithmOid: "0.4.0.127.0.7.1.1.4.1.3")
        XCTAssertEqual(r.check, .pass, r.detail ?? "")
        XCTAssertEqual(r.algorithm, "ECDSA (plain)")
    }

    func testEcdsaWithOtherKeyFails() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("ecOtherDg15"), challenge: challenge, response: BcFixtures.bytes("ecSig"), signatureAlgorithmOid: "1.2.840.10045.4.3.2")
        XCTAssertEqual(r.check, .fail)
    }

    func testEcdsaWithWrongChallengeFails() {
        let r = ActiveAuth.verify(dg15: BcFixtures.bytes("ecDg15"), challenge: [UInt8](repeating: 0, count: 8), response: BcFixtures.bytes("ecSig"), signatureAlgorithmOid: nil)
        XCTAssertEqual(r.check, .fail)
    }
}
