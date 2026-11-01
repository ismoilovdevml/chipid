import Foundation

/// Sanity checks on the Document Signer that go beyond the signature: a document whose signer is a
/// vendor test PKI, or belongs to another country than the issuing state, cannot be attributed to
/// the issuing state even when every hash and signature is intact.
public enum SignerCheck {
    private static let testWords = try! NSRegularExpression(
        pattern: "(?<![a-z])(test|testing|development|dev|specimen|sample|demo|dummy)(?![a-z])", options: [.caseInsensitive])

    /// Production DSCs are valid ~10 years (ICAO 9303-12); decades-long validity marks a test PKI.
    static let maxPlausibleYears = 30.0

    public static func looksLikeTestPki(_ dn: String, notBefore: Date, notAfter: Date) -> Bool {
        let years = notAfter.timeIntervalSince(notBefore) / (365.25 * 24 * 3600)
        let range = NSRange(dn.startIndex..., in: dn)
        return testWords.firstMatch(in: dn, range: range) != nil || years > maxPlausibleYears
    }

    /// MRZ issuing state (alpha-3, or ICAO special codes) vs the signer certificate's C= (alpha-2).
    public static func countryMatches(_ issuingState: String, _ signerCountry: String?) -> Bool? {
        guard let signerCountry, let a2 = Countries.alpha2(issuingState) else { return nil }
        return a2.caseInsensitiveCompare(signerCountry) == .orderedSame
    }
}
