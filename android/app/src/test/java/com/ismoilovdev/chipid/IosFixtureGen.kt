package com.ismoilovdev.chipid

import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.RSABlindedEngine
import org.bouncycastle.crypto.signers.ISO9796d2Signer
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG15File
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.security.Signature
import kotlin.random.Random

/**
 * Writes test vectors made with BouncyCastle and JMRTD for the iOS ChipCore tests, so the Swift
 * (OpenSSL) verifiers are checked against an independent implementation. Skipped unless
 * CHIPID_IOS_FIXTURES names the output file, e.g. ../ios/ChipCore/Tests/ChipCoreTests/Fixtures/bc.json (run from android/).
 */
class IosFixtureGen {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    @Test
    fun generate() {
        val out = System.getenv("CHIPID_IOS_FIXTURES")
        assumeTrue("CHIPID_IOS_FIXTURES not set", !out.isNullOrBlank())
        val v = linkedMapOf<String, String>()

        // Passive Authentication: RSA CSCA -> EC DSC -> SOD over DG1 and DG2.
        val dg1 = byteArrayOf(0x61, 0x03, 0x5F, 0x1F, 0x00)
        val dg2 = ByteArray(300) { it.toByte() }
        val h = TestPki.hierarchy()
        v["dg1"] = hex(dg1); v["dg2"] = hex(dg2)
        v["csca"] = hex(h.csca.encoded)
        v["sod"] = hex(SODFile("SHA-256", "SHA256withECDSA", mapOf(1 to sha256(dg1), 2 to sha256(dg2)), h.dscKeys.private, h.dsc, "BC").encoded)
        v["otherCsca"] = hex(TestPki.hierarchy("XX").csca.encoded)
        val name = "C=UT, O=TEST, CN=CSCA-TEST"
        val impostorKeys = TestPki.rsaKeys()
        v["impostorCsca"] = hex(TestPki.cert(name, name, impostorKeys.public, impostorKeys.private, ca = true).encoded)

        // Same, but the DSC names its issuer with the RDNs reversed.
        val cscaKeys = TestPki.rsaKeys()
        val csca2 = TestPki.cert(name, name, cscaKeys.public, cscaKeys.private, ca = true)
        val dscKeys2 = TestPki.ecKeys()
        val dsc2 = TestPki.cert("C=UT, O=TEST, CN=DS-TEST", "CN=CSCA-TEST, O=TEST, C=UT", dscKeys2.public, cscaKeys.private, ca = false, serial = 2)
        v["reversedCsca"] = hex(csca2.encoded)
        v["reversedSod"] = hex(SODFile("SHA-256", "SHA256withECDSA", mapOf(1 to sha256(dg1), 2 to sha256(dg2)), dscKeys2.private, dsc2, "BC").encoded)

        // Active Authentication, as a chip would answer an 8-byte challenge.
        val challenge = Random(7).nextBytes(8)
        v["challenge"] = hex(challenge)
        for ((label, implicit, sha256) in listOf(Triple("rsaSha1", true, false), Triple("rsaSha256", false, true))) {
            val keys = TestPki.rsaKeys(1024)
            val priv = PrivateKeyFactory.createKey(keys.private.encoded)
            fun signer() = ISO9796d2Signer(RSABlindedEngine(), if (sha256) SHA256Digest() else SHA1Digest(), implicit)
            val probe = signer().apply { init(true, priv) }
            val long = ByteArray(512) { 1 }
            probe.update(long, 0, long.size)
            probe.generateSignature()
            val m1 = Random(3).nextBytes(probe.recoveredMessage.size)
            val s = signer().apply { init(true, priv) }
            s.update(m1, 0, m1.size)
            s.update(challenge, 0, challenge.size)
            v["${label}Dg15"] = hex(DG15File(keys.public).encoded)
            v["${label}Sig"] = hex(s.generateSignature())
        }
        val ec = TestPki.ecKeys("brainpoolP256r1")
        v["ecDg15"] = hex(DG15File(ec.public).encoded)
        v["ecSig"] = hex(Signature.getInstance("SHA256withPLAIN-ECDSA", com.ismoilovdev.chipid.core.Bc.provider).run { initSign(ec.private); update(challenge); sign() })
        v["ecOtherDg15"] = hex(DG15File(TestPki.ecKeys("brainpoolP256r1").public).encoded)

        // Demo document: the ICAO 9303 part 4 specimen with a drawn placeholder face, signed by a
        // throwaway Utopia CSCA that no trust list contains.
        val mrz = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<" + "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        val demo = mapOf(
            1 to tlv(0x61, tlv(0x5F1F, mrz.toByteArray())),
            2 to tlv(0x75, tlv(0x7F61, tlv(0x02, byteArrayOf(1)) + tlv(0x7F60, tlv(0xA1, tlv(0x80, byteArrayOf(1, 1))) +
                tlv(0x5F2E, "FAC".toByteArray() + byteArrayOf(0) + "010".toByteArray() + byteArrayOf(0) + ByteArray(10) + face())))),
            11 to tlv(0x6B, tlv(0x5C, byteArrayOf(0x5F, 0x0E, 0x5F, 0x2B, 0x5F, 0x11, 0x5F, 0x42)) +
                tlv(0x5F0E, "ERIKSSON<<ANNA<MARIA<LOUISE".toByteArray()) + tlv(0x5F2B, "19740812".toByteArray()) +
                tlv(0x5F11, "ZENITH<UTOPIA".toByteArray()) + tlv(0x5F42, "1 MAIN STREET<ZENITH".toByteArray())),
            12 to tlv(0x6C, tlv(0x5C, byteArrayOf(0x5F, 0x19, 0x5F, 0x26)) +
                tlv(0x5F19, "MINISTRY OF INTERIOR".toByteArray()) + tlv(0x5F26, "20120415".toByteArray())),
        )
        val utoName = "C=UT, O=Utopia, CN=CSCA-UTOPIA"
        val utoKeys = TestPki.rsaKeys()
        val utoCsca = TestPki.cert(utoName, utoName, utoKeys.public, utoKeys.private, ca = true)
        val utoDscKeys = TestPki.ecKeys("brainpoolP256r1")
        val utoDsc = TestPki.cert("C=UT, O=Utopia, CN=DS-UTOPIA", utoName, utoDscKeys.public, utoKeys.private, ca = false, serial = 7)
        demo.forEach { (dg, bytes) -> v["demoDg$dg"] = hex(bytes) }
        v["demoSod"] = hex(SODFile("SHA-256", "SHA256withECDSA", demo.mapValues { sha256(it.value) }, utoDscKeys.private, utoDsc, "BC").encoded)
        v["demoCsca"] = hex(utoCsca.encoded)

        File(out!!).writeText(v.entries.joinToString(",\n", "{\n", "\n}\n") { "  \"${it.key}\": \"${it.value}\"" })
    }

    /** BER-TLV with a 1- or 2-byte tag and definite length. */
    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val t = if (tag > 0xFF) byteArrayOf((tag shr 8).toByte(), tag.toByte()) else byteArrayOf(tag.toByte())
        val n = value.size
        val l = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        return t + l + value
    }

    /** A grey head-and-shoulders silhouette, 180x240 JPEG. */
    private fun face(): ByteArray {
        val img = java.awt.image.BufferedImage(180, 240, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = java.awt.Color(0xDCE3EC); g.fillRect(0, 0, 180, 240)
        g.color = java.awt.Color(0x8A94A3); g.fillOval(55, 45, 70, 86); g.fillOval(20, 150, 140, 150)
        g.dispose()
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "jpg", it) }.toByteArray()
    }
}
