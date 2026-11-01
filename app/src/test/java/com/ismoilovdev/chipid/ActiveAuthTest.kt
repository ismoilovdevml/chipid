package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.ActiveAuth
import com.ismoilovdev.chipid.core.Bc
import com.ismoilovdev.chipid.core.Check
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.RSABlindedEngine
import org.bouncycastle.crypto.signers.ISO9796d2Signer
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.Signature
import kotlin.random.Random

class ActiveAuthTest {
    private val challenge = Random(7).nextBytes(8)

    /**
     * Emulates a chip: signs M1 || challenge with ISO 9796-2 scheme 1, where M1 is exactly as long
     * as the recoverable capacity, so only the challenge is non-recoverable (ICAO 9303 part 11).
     */
    private fun chipSignRsa(implicit: Boolean, sha256: Boolean): Pair<java.security.KeyPair, ByteArray> {
        val keys = TestPki.rsaKeys(1024)
        val priv = PrivateKeyFactory.createKey(keys.private.encoded)
        fun signer() = ISO9796d2Signer(RSABlindedEngine(), if (sha256) SHA256Digest() else SHA1Digest(), implicit)

        // Find the recoverable capacity by signing an oversized message once.
        val probe = signer().apply { init(true, priv) }
        val long = ByteArray(512) { 1 }
        probe.update(long, 0, long.size)
        probe.generateSignature()
        val capacity = probe.recoveredMessage.size

        val m1 = Random(3).nextBytes(capacity)
        val s = signer().apply { init(true, priv) }
        s.update(m1, 0, m1.size)
        s.update(challenge, 0, challenge.size)
        return keys to s.generateSignature()
    }

    @Test
    fun rsaIso9796WithImplicitSha1Verifies() {
        val (keys, sig) = chipSignRsa(implicit = true, sha256 = false)
        val r = ActiveAuth.verify(keys.public, challenge, sig, signatureAlgorithmOid = null)
        assertEquals(Check.PASS, r.check)
        assertEquals("SHA-1", r.digest)
    }

    @Test
    fun rsaIso9796WithExplicitSha256Verifies() {
        val (keys, sig) = chipSignRsa(implicit = false, sha256 = true)
        val r = ActiveAuth.verify(keys.public, challenge, sig, signatureAlgorithmOid = null)
        assertEquals(Check.PASS, r.check)
        assertEquals("SHA-256", r.digest)
    }

    @Test
    fun rsaWithWrongChallengeFails() {
        val (keys, sig) = chipSignRsa(implicit = true, sha256 = false)
        val r = ActiveAuth.verify(keys.public, challenge.reversedArray(), sig, signatureAlgorithmOid = null)
        assertEquals(Check.FAIL, r.check)
    }

    @Test
    fun ecdsaPlainVerifies() {
        val keys = TestPki.ecKeys()
        val sig = Signature.getInstance("SHA256withPLAIN-ECDSA", Bc.provider).run {
            initSign(keys.private); update(challenge); sign()
        }
        val r = ActiveAuth.verify(keys.public, challenge, sig, signatureAlgorithmOid = "1.2.840.10045.4.3.2")
        assertEquals(Check.PASS, r.check)
    }

    @Test
    fun ecdsaWithOtherKeyFails() {
        val keys = TestPki.ecKeys()
        val other = TestPki.ecKeys()
        val sig = Signature.getInstance("SHA256withPLAIN-ECDSA", Bc.provider).run {
            initSign(other.private); update(challenge); sign()
        }
        val r = ActiveAuth.verify(keys.public, challenge, sig, signatureAlgorithmOid = "1.2.840.10045.4.3.2")
        assertEquals(Check.FAIL, r.check)
    }
}
