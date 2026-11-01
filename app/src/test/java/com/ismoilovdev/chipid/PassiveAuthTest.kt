package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.Check
import com.ismoilovdev.chipid.core.PassiveAuth
import org.jmrtd.lds.SODFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PassiveAuthTest {
    private val dg1 = byteArrayOf(0x61, 0x03, 0x5F, 0x1F, 0x00)
    private val dg2 = ByteArray(300) { it.toByte() }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private fun sod(h: TestPki.Hierarchy, hashes: Map<Int, ByteArray>): ByteArray =
        SODFile("SHA-256", "SHA256withECDSA", hashes, h.dscKeys.private, h.dsc, "BC").encoded

    private fun goodSod(h: TestPki.Hierarchy) = sod(h, mapOf(1 to sha256(dg1), 2 to sha256(dg2)))

    @Test
    fun fullChainVerifies() {
        val h = TestPki.hierarchy()
        val r = PassiveAuth.verify(goodSod(h), mapOf(1 to dg1, 2 to dg2), listOf(h.csca))

        assertEquals(Check.PASS, r.dataGroupHashes)
        assertEquals(Check.PASS, r.sodSignature)
        assertEquals(Check.PASS, r.cscaChain)
        assertEquals(Check.PASS, r.overall)
        assertEquals("SHA-256", r.digestAlgorithm)
        assertTrue(r.cscaSubject!!.contains("CSCA-TEST"))
        assertEquals(h.csca, r.csca)
    }

    @Test
    fun tamperedDataGroupFails() {
        val h = TestPki.hierarchy()
        val bad = dg2.copyOf().also { it[10] = 99 }
        val r = PassiveAuth.verify(goodSod(h), mapOf(1 to dg1, 2 to bad), listOf(h.csca))

        assertEquals(Check.FAIL, r.dataGroupHashes)
        assertEquals(Check.FAIL, r.perDataGroup[2])
        assertEquals(Check.PASS, r.perDataGroup[1])
        assertEquals(Check.FAIL, r.overall)
    }

    @Test
    fun unknownCscaIsNotAFailureButIsReported() {
        val h = TestPki.hierarchy()
        val other = TestPki.hierarchy("XX")
        val r = PassiveAuth.verify(goodSod(h), mapOf(1 to dg1, 2 to dg2), listOf(other.csca))

        assertEquals(Check.PASS, r.sodSignature)
        assertEquals(Check.UNKNOWN, r.cscaChain)
        assertEquals(Check.UNKNOWN, r.overall)
    }

    @Test
    fun cscaWithSameNameButWrongKeyFails() {
        val h = TestPki.hierarchy()
        val impostorKeys = TestPki.rsaKeys()
        val name = "C=UT, O=TEST, CN=CSCA-TEST" // same DN as the real CSCA, different key
        val impostor = TestPki.cert(name, name, impostorKeys.public, impostorKeys.private, ca = true)
        val r = PassiveAuth.verify(goodSod(h), mapOf(1 to dg1, 2 to dg2), listOf(impostor))

        assertEquals(Check.FAIL, r.cscaChain)
        assertEquals(Check.FAIL, r.overall)
    }

    @Test
    fun dataGroupNotReadIsSkipped() {
        val h = TestPki.hierarchy()
        val r = PassiveAuth.verify(goodSod(h), mapOf(1 to dg1), listOf(h.csca))

        assertEquals(Check.PASS, r.dataGroupHashes)
        assertEquals(Check.SKIPPED, r.perDataGroup[2])
    }
}
