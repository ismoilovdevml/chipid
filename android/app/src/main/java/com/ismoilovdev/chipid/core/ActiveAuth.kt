package com.ismoilovdev.chipid.core

import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA224Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA384Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.engines.RSAEngine
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.signers.ISO9796d2Signer
import java.math.BigInteger
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

/**
 * Active Authentication (ICAO 9303 part 11, 6.1): the chip signs a fresh terminal challenge with
 * the private key matching DG15. A valid signature proves the chip holds a key that cannot be
 * copied, i.e. the chip is not a clone.
 *
 * RSA chips sign with ISO/IEC 9796-2 scheme 1 (partial message recovery): the chip's own nonce
 * M1 is recovered from the signature and the challenge is the non-recoverable part M2.
 * EC chips sign the challenge with plain (r||s) ECDSA, hash taken from DG14.
 */
object ActiveAuth {
    class Result(val check: Check, val algorithm: String, val digest: String?, val detail: String?)

    fun verify(publicKey: PublicKey, challenge: ByteArray, response: ByteArray, signatureAlgorithmOid: String?): Result =
        when (publicKey) {
            is RSAPublicKey -> verifyRsa(publicKey, challenge, response)
            is ECPublicKey -> verifyEc(publicKey, challenge, response, signatureAlgorithmOid)
            else -> Result(Check.UNKNOWN, publicKey.algorithm, null, "unsupported key type")
        }

    private fun verifyRsa(key: RSAPublicKey, challenge: ByteArray, response: ByteArray): Result {
        val trailer = try {
            recoverTrailer(key, response)
        } catch (e: Exception) {
            return Result(Check.FAIL, "RSA ISO9796-2", null, e.message)
        }
        val (digest, name) = digestForTrailer(trailer)
            ?: return Result(Check.FAIL, "RSA ISO9796-2", null, "unknown trailer ${"%04X".format(trailer)}")
        val implicit = trailer and 0xFF == 0xBC
        return try {
            val signer = ISO9796d2Signer(RSAEngine(), digest, implicit)
            signer.init(false, RSAKeyParameters(false, key.modulus, key.publicExponent))
            signer.updateWithRecoveredMessage(response)
            signer.update(challenge, 0, challenge.size)
            val ok = signer.verifySignature(response)
            Result(if (ok) Check.PASS else Check.FAIL, "RSA ISO9796-2", name, if (ok) null else "hash mismatch")
        } catch (e: Exception) {
            Result(Check.FAIL, "RSA ISO9796-2", name, e.message)
        }
    }

    /** Raw RSA public operation; returns the 1- or 2-byte ISO 9796-2 trailer. */
    private fun recoverTrailer(key: RSAPublicKey, response: ByteArray): Int {
        val n = key.modulus
        var x = BigInteger(1, response).modPow(key.publicExponent, n)
        if (x.toInt() and 0x0F != 0x0C) x = n.subtract(x)
        val b = x.toByteArray()
        val last = b[b.size - 1].toInt() and 0xFF
        return if (last == 0xBC) 0xBC else ((b[b.size - 2].toInt() and 0xFF) shl 8) or last
    }

    private fun digestForTrailer(t: Int): Pair<Digest, String>? = when (t) {
        0xBC, 0x33CC -> SHA1Digest() to "SHA-1"
        0x34CC -> SHA256Digest() to "SHA-256"
        0x35CC -> SHA512Digest() to "SHA-512"
        0x36CC -> SHA384Digest() to "SHA-384"
        0x38CC -> SHA224Digest() to "SHA-224"
        else -> null
    }

    private fun verifyEc(key: ECPublicKey, challenge: ByteArray, response: ByteArray, oid: String?): Result {
        val hash = ecHash(oid)
        return try {
            val sig = Signature.getInstance("${hash.replace("-", "")}withPLAIN-ECDSA", Bc.provider)
            sig.initVerify(key)
            sig.update(challenge)
            val ok = sig.verify(response)
            Result(if (ok) Check.PASS else Check.FAIL, "ECDSA (plain)", hash, if (ok) null else "signature invalid")
        } catch (e: Exception) {
            Result(Check.FAIL, "ECDSA (plain)", hash, e.message)
        }
    }

    /** Maps X9.62 and BSI TR-03111 ecdsa-plain OIDs to a hash; ICAO requires DG14 to name it. */
    private fun ecHash(oid: String?): String = when (oid) {
        "1.2.840.10045.4.1", "0.4.0.127.0.7.1.1.4.1.1" -> "SHA-1"
        "1.2.840.10045.4.3.1", "0.4.0.127.0.7.1.1.4.1.2" -> "SHA-224"
        "1.2.840.10045.4.3.2", "0.4.0.127.0.7.1.1.4.1.3" -> "SHA-256"
        "1.2.840.10045.4.3.3", "0.4.0.127.0.7.1.1.4.1.4" -> "SHA-384"
        "1.2.840.10045.4.3.4", "0.4.0.127.0.7.1.1.4.1.5" -> "SHA-512"
        else -> "SHA-256"
    }
}
