package com.ismoilovdev.chipid.core

import java.util.Date

/**
 * Sanity checks on the Document Signer that go beyond the signature: a document whose signer is a
 * vendor test PKI, or belongs to another country than the issuing state, cannot be attributed to
 * the issuing state even when every hash and signature is intact.
 */
object SignerCheck {
    private val testWords = Regex("""(?i)(?<![a-z])(test|testing|development|dev|specimen|sample|demo|dummy)(?![a-z])""")

    /** Production DSCs are valid ~10 years (ICAO 9303-12); decades-long validity marks a test PKI. */
    private const val MAX_PLAUSIBLE_YEARS = 30

    fun looksLikeTestPki(dn: String, notBefore: Date, notAfter: Date): Boolean {
        val years = (notAfter.time - notBefore.time) / (365.25 * 24 * 3600 * 1000)
        return testWords.containsMatchIn(dn) || years > MAX_PLAUSIBLE_YEARS
    }

    /** MRZ issuing state (alpha-3, or ICAO special codes) vs the signer certificate's C= (alpha-2). */
    fun countryMatches(issuingState: String, signerCountry: String?): Boolean? {
        if (signerCountry == null) return null
        val a2 = Countries.alpha2(issuingState) ?: return null
        return a2.equals(signerCountry, ignoreCase = true)
    }
}
