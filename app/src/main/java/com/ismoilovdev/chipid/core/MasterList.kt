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
    val signerSubject: String?,
    val sha256: String,
) {
    fun countries(): Set<String> = certificates.mapNotNull { country(it.subjectX500Principal) }.toSet()

    fun byCountry(cc: String): List<X509Certificate> =
        certificates.filter { country(it.subjectX500Principal) == cc }

    companion object {
        private val converter by lazy { JcaX509CertificateConverter().setProvider(Bc.provider) }

        fun parse(bytes: ByteArray): MasterList {
            val cms = CMSSignedData(bytes)
            val content = cms.signedContent.content as ByteArray
            val seq = ASN1Sequence.getInstance(content)
            val set = ASN1Set.getInstance(seq.getObjectAt(1))
            val certs = set.map { converter.getCertificate(X509CertificateHolder(it.toASN1Primitive().encoded)) }

            val signer = cms.signerInfos.signers.firstOrNull()
            val signerCert = signer?.let { s -> cms.certificates.getMatches(null).firstOrNull { s.sid.match(it) } }
            val valid = try {
                signer != null && signerCert != null &&
                    signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(Bc.provider).build(signerCert))
            } catch (_: Exception) {
                false
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return MasterList(certs, valid, signerCert?.subject?.toString(), hash)
        }

        fun country(p: X500Principal): String? =
            Regex("""(?:^|,)\s*C=([A-Z]{2})""").find(p.getName(X500Principal.RFC2253))?.groupValues?.get(1)
    }
}
