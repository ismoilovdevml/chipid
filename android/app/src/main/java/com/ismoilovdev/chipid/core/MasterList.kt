package com.ismoilovdev.chipid.core

import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * ICAO CSCA master list (ICAO 9303 part 12): CMS SignedData whose eContent is
 * CscaMasterList ::= SEQUENCE { version INTEGER, certList SET OF Certificate }.
 */
class MasterList(
    val certificates: List<X509Certificate>,
    /** The CMS signature is intact and made by the embedded signer certificate. */
    val signatureValid: Boolean,
    /** The signer certificate is issued by one of the pinned roots, so the list is the publisher's. */
    val signerTrusted: Boolean,
    val signerSubject: String?,
    val sha256: String,
) {
    fun countries(): Set<String> = certificates.mapNotNull { country(it.subjectX500Principal) }.toSet()

    fun byCountry(cc: String): List<X509Certificate> =
        certificates.filter { country(it.subjectX500Principal) == cc }

    companion object {
        /**
         * SHA-256 of the SubjectPublicKeyInfo of every German CSCA key (C=DE, O=bund, OU=bsi,
         * CN=csca-germany) in DE_ML_2026-10-01-11-50-52.ml, downloaded over HTTPS from bsi.bund.de on
         * 2026-11-01. The BSI "CSCA Master List Signer" certificate must be issued by one of them.
         * A new German CSCA key needs an app update.
         */
        val GERMAN_CSCA_PINS = setOf(
            "cbd048969ae6171addb96cea36897f2fa381346b8ebc43c45bee24f0ad71ef42", // serial 101, 2013
            "20d1496eaa3c19f6af15aea9eda62ace5ad8ae6be66e637ba17c7190f779b2a3", // serial 103, 2016
            "04c58c0bee5e902c725954a490e451dcbafb281941ae8106cc746060d568a7ce", // 2019
            "aa9a2c80eb69bbb600255798e028b9a0177199b1851393329979be8e6ec9c3e2", // 2021
            "b7d7c66d3eb51e4fbd47c7e637cf31ee50447e4dff11e61e1b536f26373c5590", // 2024, signs the 2028 list signer
        )

        private val converter by lazy { JcaX509CertificateConverter().setProvider(Bc.provider) }

        fun parse(bytes: ByteArray, pinnedRoots: Set<String> = GERMAN_CSCA_PINS): MasterList {
            val cms = CMSSignedData(bytes)
            val content = cms.signedContent.content as ByteArray
            val seq = ASN1Sequence.getInstance(content)
            val set = ASN1Set.getInstance(seq.getObjectAt(1))
            val certs = set.map { converter.getCertificate(X509CertificateHolder(it.toASN1Primitive().encoded)) }

            val signer = cms.signerInfos.signers.firstOrNull()
            val embedded = cms.certificates.getMatches(null)
            val signerHolder = signer?.let { s -> embedded.firstOrNull { s.sid.match(it) } }
            val valid = try {
                signer != null && signerHolder != null &&
                    signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(Bc.provider).build(signerHolder))
            } catch (_: Exception) {
                false
            }

            // The issuer may sit in the CMS certificate set or in the list itself; only a pinned key counts.
            val trusted = valid && signerHolder != null && run {
                val signerCert = converter.getCertificate(signerHolder)
                val candidates = certs + embedded.map { converter.getCertificate(it) }
                candidates.any { root ->
                    spkiSha256(root) in pinnedRoots &&
                        root.subjectX500Principal == signerCert.issuerX500Principal &&
                        runCatching { signerCert.verify(root.publicKey, Bc.provider) }.isSuccess
                }
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return MasterList(certs, valid, trusted, signerHolder?.subject?.toString(), hash)
        }

        /** Hash of the certificate's SubjectPublicKeyInfo exactly as encoded in the certificate. */
        fun spkiSha256(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(X509CertificateHolder(cert.encoded).subjectPublicKeyInfo.encoded)
                .joinToString("") { "%02x".format(it) }

        fun country(p: X500Principal): String? =
            Regex("""(?:^|,)\s*C=([A-Z]{2})""").find(p.getName(X500Principal.RFC2253))?.groupValues?.get(1)
    }
}
