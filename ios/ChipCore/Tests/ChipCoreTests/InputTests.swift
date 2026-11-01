import XCTest
@testable import ChipCore

/// Specimens from ICAO Doc 9303 part 4 (TD3) and part 5 (TD1); same cases as the Android tests.
final class MrzTests: XCTestCase {
    let td3 = ["P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10"]
    let td1 = ["I<UTOD231458907<<<<<<<<<<<<<<<", "7408122F1204159UTO<<<<<<<<<<<6", "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"]

    func testParsesTd3Specimen() throws {
        let m = try XCTUnwrap(Mrz.find(td3))
        XCTAssertEqual(m.format, .td3)
        XCTAssertEqual(m.documentNumber, "L898902C3")
        XCTAssertEqual(m.dateOfBirth, "740812")
        XCTAssertEqual(m.dateOfExpiry, "120415")
        XCTAssertEqual(m.issuingState, "UTO")
        XCTAssertEqual(m.surname, "ERIKSSON")
        XCTAssertEqual(m.givenNames, "ANNA MARIA")
        XCTAssertTrue(m.compositeValid)
    }

    func testParsesTd1Specimen() throws {
        let m = try XCTUnwrap(Mrz.find(td1))
        XCTAssertEqual(m.format, .td1)
        XCTAssertEqual(m.documentNumber, "D23145890")
        XCTAssertEqual(m.dateOfBirth, "740812")
        XCTAssertEqual(m.dateOfExpiry, "120415")
        XCTAssertEqual(m.surname, "ERIKSSON")
    }

    func testRepairsTypicalOcrConfusions() throws {
        let noisy = ["PASSPORT", "P<UTOERIKSSON«ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "l898902c36UTO74O8122F12O4159ZE184226B<<<<<1O", "some other text"]
        let m = try XCTUnwrap(Mrz.find(noisy))
        XCTAssertEqual(m.documentNumber, "L898902C3")
        XCTAssertEqual(m.dateOfBirth, "740812")
        XCTAssertEqual(m.dateOfExpiry, "120415")
    }

    func testRepairsAmbiguousDocumentNumberUsingCheckDigit() throws {
        let m = try XCTUnwrap(Mrz.find([td3[0], "L8989O2C36UTO7408122F1204159ZE184226B<<<<<10"]))
        XCTAssertEqual(m.documentNumber, "L898902C3")
    }

    func testRejectsLineWithWrongCheckDigit() {
        XCTAssertNil(Mrz.find([td3[0], "L898902C36UTO7408121F1204159ZE184226B<<<<<10"]))
    }

    func testIgnoresUnrelatedText() {
        XCTAssertNil(Mrz.find(["REPUBLIC OF UTOPIA", "PASSPORT", "12.03.2020"]))
    }

    func testAccessKeyMrzInformation() {
        // ICAO 9303 part 11 worked example: L898902C<3 + 690806 + 940623 with check digits.
        let k = AccessKey.mrz(documentNumber: "L898902C", dateOfBirth: "690806", dateOfExpiry: "940623")
        XCTAssertEqual(k.mrzInformation, "L898902C<369080619406236")
    }
}

final class AccessInputTests: XCTestCase {
    func testParsesDottedDateToYymmdd() {
        XCTAssertEqual(AccessInput.toYymmdd("15.03.1990"), "900315")
        XCTAssertEqual(AccessInput.toYymmdd("01.01.2034"), "340101")
    }

    func testAcceptsBareEightDigitDate() {
        XCTAssertEqual(AccessInput.toYymmdd("15031990"), "900315")
        XCTAssertEqual(AccessInput.toYymmdd("15/03/1990"), "900315")
        XCTAssertEqual(AccessInput.toYymmdd("15-03-1990"), "900315")
        XCTAssertNil(AccessInput.toYymmdd("31021990"))
    }

    func testDateDigitsAreCappedAtEight() {
        XCTAssertEqual(AccessInput.dateDigits("15.03.19901"), "15031990")
        XCTAssertEqual(AccessInput.dateDigits("15a03"), "1503")
    }

    func testDocumentNumberInputIsFiltered() { XCTAssertEqual(AccessInput.documentDigits("fa-1 2"), "FA12") }

    func testAcceptsYymmddAsIs() { XCTAssertEqual(AccessInput.toYymmdd("900315"), "900315") }

    func testRejectsImpossibleDates() {
        XCTAssertNil(AccessInput.toYymmdd("31.02.1990"))
        XCTAssertNil(AccessInput.toYymmdd("1990-03-15"))
        XCTAssertNil(AccessInput.toYymmdd("901315"))
    }

    func testFutureDates() {
        XCTAssertTrue(AccessInput.isFuture(AccessInput.toDate("01.01.2999")!))
        XCTAssertFalse(AccessInput.isFuture(AccessInput.toDate("15.03.1990")!))
    }

    func testNormalizesDocumentNumber() {
        XCTAssertEqual(AccessInput.normalizeDocumentNumber(" aa 123 4567 "), "AA1234567")
        XCTAssertNil(AccessInput.normalizeDocumentNumber("AA-12"))
        XCTAssertNil(AccessInput.normalizeDocumentNumber(""))
    }

    func testValidatesCan() {
        XCTAssertEqual(AccessInput.normalizeCan(" 123 456 "), "123456")
        XCTAssertNil(AccessInput.normalizeCan("12345"))
        XCTAssertNil(AccessInput.normalizeCan("12345a"))
    }

    func testComputesIcaoCheckDigit() {
        XCTAssertEqual(AccessInput.checkDigit("L898902C3"), 6)
        XCTAssertEqual(AccessInput.checkDigit("740812"), 2)
    }
}

final class SignerAndCountryTests: XCTestCase {
    private func year(_ y: Int) -> Date { Calendar(identifier: .gregorian).date(from: DateComponents(year: y, month: 1, day: 1))! }

    func testRecognisesDevelopmentAndTestPkis() {
        XCTAssertTrue(SignerCheck.looksLikeTestPki("CN=CSCA,OU=development,O=vendor,C=DE", notBefore: year(2020), notAfter: year(2030)))
        XCTAssertTrue(SignerCheck.looksLikeTestPki("CN=Test CSCA,O=Example,C=UT", notBefore: year(2020), notAfter: year(2030)))
        XCTAssertTrue(SignerCheck.looksLikeTestPki("CN=SPECIMEN DS,C=UT", notBefore: year(2020), notAfter: year(2030)))
        XCTAssertTrue(SignerCheck.looksLikeTestPki("CN=DS,O=Vendor,C=UT", notBefore: year(2020), notAfter: year(2080)))
    }

    func testProductionNamesAreNotFlagged() {
        XCTAssertFalse(SignerCheck.looksLikeTestPki("CN=CSCA-UTOPIA,OU=GCP,O=GOV,C=UT", notBefore: year(2023), notAfter: year(2033)))
        XCTAssertFalse(SignerCheck.looksLikeTestPki("CN=Contestant Authority,O=GOV,C=UT", notBefore: year(2023), notAfter: year(2033)))
    }

    func testComparesSignerCountryWithIssuingState() {
        XCTAssertEqual(SignerCheck.countryMatches("DEU", "DE"), true)
        XCTAssertEqual(SignerCheck.countryMatches("D<<", "DE"), true)
        XCTAssertEqual(SignerCheck.countryMatches("FRA", "DE"), false)
        XCTAssertNil(SignerCheck.countryMatches("FRA", nil))
    }

    func testMapsMrzAndCertificateCodes() {
        XCTAssertEqual(Countries.name("FRA"), "France")
        XCTAssertEqual(Countries.name("JP"), "Japan")
        XCTAssertEqual(Countries.name("D<<"), "Germany")
        XCTAssertEqual(Countries.name("GBR"), "United Kingdom")
        XCTAssertEqual(Countries.name("UZB"), "Uzbekistan")
        XCTAssertEqual(Countries.name("XYZ"), "XYZ")
    }
}

final class ReportModelTests: XCTestCase {
    func testSplitsIcaoNameField() {
        XCTAssertTrue(ReportModel.splitIcaoName("ERIKSSON<<ANNA<MARIA") == ("ERIKSSON", "ANNA MARIA"))
        XCTAssertTrue(ReportModel.splitIcaoName("ERIKSSON<<ANNA<MARIA<<<<") == ("ERIKSSON", "ANNA MARIA"))
        XCTAssertTrue(ReportModel.splitIcaoName("ERIKSSON") == ("ERIKSSON", ""))
    }

    func testFormatsEightDigitDates() {
        XCTAssertEqual(ReportModel.date8("19740812"), "12.08.1974")
        XCTAssertEqual(ReportModel.date8("1974-08"), "1974-08")
        XCTAssertNil(ReportModel.date8(nil))
    }

    func testDetectsTruncatedMrzName() {
        XCTAssertTrue(ReportModel.isTruncation("ANNA MARIA LO", "ANNA MARIA LOUISE"))
        XCTAssertFalse(ReportModel.isTruncation("ANNA MARIA", "ANNA MARIA"))
    }

    func testMasksDocumentNumber() { XCTAssertEqual(ReportModel.mask("AA1234567"), "AA•••••••") }

    /// The demo document end to end: LDS parsing, Passive Authentication and the report sections.
    func testDemoDocumentReport() throws {
        let dgs = [1, 2, 11, 12].reduce(into: [Int: [UInt8]]()) { $0[$1] = BcFixtures.bytes("demoDg\($1)") }
        let sod = BcFixtures.bytes("demoSod")
        let trusted = PassiveAuth.verify(sod: sod, dataGroups: dgs, anchors: [BcFixtures.cert("demoCsca")])
        XCTAssertEqual(trusted.overall, .pass, "\(trusted.errors)")
        XCTAssertEqual(trusted.signatureAlgorithm, "ecdsa-with-SHA256")
        XCTAssertEqual(trusted.ldsVersion, nil)

        // Without its CSCA (the app's case) the signature still holds but the issuer is unknown.
        let pa = PassiveAuth.verify(sod: sod, dataGroups: dgs, anchors: [])
        XCTAssertEqual(pa.sodSignature, .pass)
        XCTAssertEqual(pa.overall, .unknown)

        let r = ReadReport(accessProtocol: "Demo", paceNote: nil, cardAccess: nil, sod: sod, dataGroups: dgs, notRead: [:],
                           passive: pa, chipAuth: ChipAuthOutcome(.skipped, "demo"), activeAuth: nil, tagInfo: [], millis: 0, log: [])
        let m = try XCTUnwrap(r.dg1)
        XCTAssertEqual(m.documentNumber, "L898902C3")
        XCTAssertEqual(m.primaryIdentifier, "ERIKSSON")
        XCTAssertEqual(m.secondaryIdentifier, "ANNA MARIA")
        XCTAssertEqual(m.nationality, "UTO")
        XCTAssertEqual(m.dateOfBirth, "740812")
        XCTAssertEqual(m.sex, "F")
        XCTAssertEqual(m.optionalData, "ZE184226B")
        XCTAssertEqual(r.faces.first?.mimeType, "image/jpeg")
        XCTAssertEqual(Array(r.faces.first!.bytes.suffix(2)), [0xFF, 0xD9]) // whole JPEG, up to EOI
        XCTAssertEqual(r.dg11?.nameOfHolder, "ERIKSSON<<ANNA<MARIA<LOUISE")
        XCTAssertEqual(r.dg11?.placeOfBirth, ["ZENITH", "UTOPIA"])
        XCTAssertEqual(r.dg12?.issuingAuthority, "MINISTRY OF INTERIOR")
        XCTAssertTrue(ReportModel.name(r) == ("ERIKSSON", "ANNA MARIA LOUISE"))
        XCTAssertEqual(ReportModel.signerCountry(r), "UT")
        XCTAssertNil(ReportModel.signerFlags(r).countryMatches) // UTO is not an ISO 3166 code

        let rows = ReportModel.sections(r).flatMap(\.rows)
        XCTAssertTrue(rows.contains { $0.label == "Name in MRZ (shortened to fit)" && $0.value == "ERIKSSON, ANNA MARIA" })
        XCTAssertTrue(rows.contains { $0.label == "Date of issue" && $0.value == "15.04.2012" })
        XCTAssertTrue(rows.contains { $0.label == "Passive Authentication" && $0.check == .unknown })
    }

    func testTd1Dg1WithLongDocumentNumber() throws {
        // TD1 where the number runs into the optional data: '<' in place of the check digit.
        let mrz = "I<UTOD23145890<7349<<<<<<<<<<<" + "7408122F1204159UTO<<<<<<<<<<<6" + "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"
        let m = try Lds.parseMrz(mrz)
        XCTAssertEqual(m.documentNumber, "D23145890734")
        XCTAssertEqual(m.primaryIdentifier, "ERIKSSON")
    }
}
