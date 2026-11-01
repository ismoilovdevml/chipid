package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.Bc
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/** Builds a throwaway CSCA -> DSC hierarchy for tests. */
object TestPki {
    private val now = System.currentTimeMillis()
    private const val DAY = 86_400_000L

    fun rsaKeys(bits: Int = 2048): KeyPair =
        KeyPairGenerator.getInstance("RSA", Bc.provider).apply { initialize(bits) }.generateKeyPair()

    fun ecKeys(curve: String = "secp256r1"): KeyPair =
        KeyPairGenerator.getInstance("EC", Bc.provider).apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

    fun cert(
        subject: String,
        issuer: String,
        subjectKey: PublicKey,
        issuerKey: PrivateKey,
        ca: Boolean,
        serial: Long = 1,
    ): X509Certificate {
        val sigAlg = if (issuerKey.algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA"
        val builder = JcaX509v3CertificateBuilder(
            X500Name(issuer), BigInteger.valueOf(serial),
            Date(now - DAY), Date(now + 365 * DAY), X500Name(subject), subjectKey,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(if (ca) KeyUsage.keyCertSign or KeyUsage.cRLSign else KeyUsage.digitalSignature),
        )
        val signer = JcaContentSignerBuilder(sigAlg).setProvider(Bc.provider).build(issuerKey)
        return JcaX509CertificateConverter().setProvider(Bc.provider).getCertificate(builder.build(signer))
    }

    class Hierarchy(val cscaKeys: KeyPair, val csca: X509Certificate, val dscKeys: KeyPair, val dsc: X509Certificate)

    fun hierarchy(country: String = "UZ"): Hierarchy {
        val cscaName = "C=$country, O=TEST, CN=CSCA-TEST"
        val cscaKeys = rsaKeys()
        val csca = cert(cscaName, cscaName, cscaKeys.public, cscaKeys.private, ca = true)
        val dscKeys = ecKeys()
        val dsc = cert("C=$country, O=TEST, CN=DS-TEST", cscaName, dscKeys.public, cscaKeys.private, ca = false, serial = 2)
        return Hierarchy(cscaKeys, csca, dscKeys, dsc)
    }
}
