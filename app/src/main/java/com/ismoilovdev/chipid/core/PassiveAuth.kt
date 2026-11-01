package com.ismoilovdev.chipid.core

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.jmrtd.lds.SODFile
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Passive Authentication (ICAO 9303 part 11, 5.1):
 *  1. every data group read from the chip hashes to the value stored in EF.SOD,
 *  2. EF.SOD's CMS signature verifies with the Document Signer certificate (DSC) inside it,
 *  3. the DSC is signed by a trusted Country Signing CA (CSCA).
 * Proves the data was written by the issuing state and not altered. It does not prove the chip
 * itself is genuine; that is Chip/Active Authentication's job.
 */
object PassiveAuth {
    class Result(
        val digestAlgorithm: String,
        val perDataGroup: Map<Int, Check>,
        val dataGroupHashes: Check,
        val sodSignature: Check,
        val signatureAlgorithm: String?,
        val cscaChain: Check,
        val dsc: X509Certificate?,
        val cscaSubject: String?,
        val dscValidNow: Boolean?,
        val ldsVersion: String?,
        val errors: List<String>,
    ) {
        val overall: Check get() = combine(dataGroupHashes, sodSignature, cscaChain)
    }

    private val converter by lazy { JcaX509CertificateConverter().setProvider(Bc.provider) }

    fun verify(sodBytes: ByteArray, dataGroups: Map<Int, ByteArray>, anchors: List<X509Certificate>): Result {
        val errors = mutableListOf<String>()
        val sod = SODFile(ByteArrayInputStream(sodBytes))
        val digestAlg = sod.digestAlgorithm
        val expected = sod.dataGroupHashes

        val perDg = expected.keys.sorted().associateWith { dg ->
            val raw = dataGroups[dg] ?: return@associateWith Check.SKIPPED
            val actual = MessageDigest.getInstance(digestAlg, Bc.provider).digest(raw)
            if (actual.contentEquals(expected[dg])) Check.PASS else Check.FAIL.also { errors += "DG$dg hash mismatch" }
        }
        val extra = dataGroups.keys - expected.keys
        if (extra.isNotEmpty()) errors += "Read but not covered by SOD: ${extra.sorted().joinToString { "DG$it" }}"
        val hashes = when {
            perDg.values.any { it == Check.FAIL } || extra.isNotEmpty() -> Check.FAIL
            perDg.values.any { it == Check.PASS } -> Check.PASS
            else -> Check.UNKNOWN
        }

        var dsc: X509Certificate? = null
        var sigAlg: String? = null
        val sodSig = try {
            val cms = CMSSignedData(unwrapSod(sodBytes))
            val signer = cms.signerInfos.signers.single()
            val holder = cms.certificates.getMatches(null).firstOrNull { signer.sid.match(it) }
                ?: error("DSC not embedded in SOD")
            dsc = converter.getCertificate(holder)
            sigAlg = sod.digestEncryptionAlgorithm
            if (signer.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(Bc.provider).build(holder))) Check.PASS
            else Check.FAIL.also { errors += "SOD signature invalid" }
        } catch (e: Exception) {
            errors += "SOD signature: ${e.message}"
            Check.FAIL
        }

        var cscaSubject: String? = null
        val chain = dsc?.let { cert ->
            val candidates = anchors.filter { it.subjectX500Principal == cert.issuerX500Principal }
                .sortedByDescending { keyIdMatches(cert, it) }
            if (candidates.isEmpty()) {
                errors += "No CSCA for issuer ${cert.issuerX500Principal.name}"
                return@let Check.UNKNOWN
            }
            val ok = candidates.firstOrNull { csca ->
                try {
                    cert.verify(csca.publicKey, Bc.provider); true
                } catch (_: Exception) {
                    false
                }
            }
            if (ok != null) {
                cscaSubject = "${ok.subjectX500Principal.name} (serial ${ok.serialNumber.toString(16)})"
                Check.PASS
            } else {
                errors += "DSC signature does not verify with any matching CSCA"
                Check.FAIL
            }
        } ?: Check.FAIL

        val validNow = dsc?.let {
            val now = Date()
            !now.before(it.notBefore) && !now.after(it.notAfter)
        }
        return Result(digestAlg, perDg, hashes, sodSig, sigAlg, chain, dsc, cscaSubject, validNow, sod.ldsVersion, errors)
    }

    /** EF.SOD is [APPLICATION 23] (tag 0x77) wrapping a CMS ContentInfo. */
    fun unwrapSod(bytes: ByteArray): ByteArray {
        require(bytes[0] == 0x77.toByte()) { "not an EF.SOD (tag ${"%02X".format(bytes[0])})" }
        var i = 1
        val first = bytes[i++].toInt() and 0xFF
        val len = if (first < 0x80) first else {
            var l = 0
            repeat(first and 0x7F) { l = (l shl 8) or (bytes[i++].toInt() and 0xFF) }
            l
        }
        return bytes.copyOfRange(i, i + len)
    }

    private fun keyIdMatches(dsc: X509Certificate, csca: X509Certificate): Boolean {
        val aki = dsc.getExtensionValue(Extension.authorityKeyIdentifier.id)?.let {
            AuthorityKeyIdentifier.getInstance(ASN1OctetString.getInstance(it).octets).keyIdentifierOctets
        } ?: return false
        val ski = csca.getExtensionValue(Extension.subjectKeyIdentifier.id)?.let {
            SubjectKeyIdentifier.getInstance(ASN1OctetString.getInstance(it).octets).keyIdentifier
        } ?: return false
        return aki.contentEquals(ski)
    }
}
