package com.ismoilovdev.chipid.reader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.ismoilovdev.chipid.core.ActiveAuth
import com.ismoilovdev.chipid.core.Bc
import com.ismoilovdev.chipid.core.Check
import com.ismoilovdev.chipid.core.PassiveAuth
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.BACKey
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG11File
import org.jmrtd.lds.icao.DG12File
import org.jmrtd.lds.icao.DG14File
import org.jmrtd.lds.icao.DG15File
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.MRZInfo
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate

sealed interface AccessKey {
    /** documentNumber, dateOfBirth and dateOfExpiry as on the MRZ (dates YYMMDD). */
    data class Mrz(val documentNumber: String, val dateOfBirth: String, val dateOfExpiry: String) : AccessKey
    data class Can(val can: String) : AccessKey
}

class Face(val mimeType: String, val bytes: ByteArray, val width: Int, val height: Int)

class ChipAuthOutcome(val check: Check, val detail: String)

class ReadReport(
    val accessProtocol: String,
    val paceNote: String?,
    val cardAccess: ByteArray?,
    val sod: ByteArray,
    val dataGroups: Map<Int, ByteArray>,
    val notRead: Map<Int, String>,
    val mrz: MRZInfo?,
    val faces: List<Face>,
    val dg11: DG11File?,
    val dg12: DG12File?,
    val passive: PassiveAuth.Result,
    val chipAuth: ChipAuthOutcome,
    val activeAuth: ActiveAuth.Result?,
    val tagInfo: List<Pair<String, String>>,
    val millis: Long,
    val log: List<String>,
) {
    /** Clone detection passes if either chip-genuineness protocol succeeded. */
    val chipGenuine: Check
        get() = when {
            chipAuth.check == Check.PASS || activeAuth?.check == Check.PASS -> Check.PASS
            chipAuth.check == Check.FAIL || activeAuth?.check == Check.FAIL -> Check.FAIL
            else -> Check.UNKNOWN
        }
}

class ChipReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The four user-visible stages of a read, plus progress through the whole read (0..1). */
enum class Stage { FOUND, CHANNEL, DATA, VERIFY }

data class ReadProgress(val stage: Stage, val fraction: Float, val detail: String)

/**
 * Reads an ICAO 9303 eMRTD (passport or ID card) over NFC. Order matters:
 * PACE (or BAC fallback) -> EF.SOD -> every data group SOD lists (except EAC-protected DG3/DG4)
 * -> Active Authentication -> Chip Authentication, then one re-read under the CA session keys,
 * which only succeeds if the chip owns the DG14 private key. Data is read before CA so a CA
 * failure cannot cost the user their data.
 */
object ChipReader {
    private val eacProtected = setOf(3, 4)

    fun read(tag: Tag, key: AccessKey, anchors: List<X509Certificate>, progress: (ReadProgress) -> Unit): ReadReport {
        Bc.install()
        val started = System.currentTimeMillis()
        val log = mutableListOf<String>()
        fun step(stage: Stage, f: Float, s: String) { log += s; progress(ReadProgress(stage, f, s)) }

        val iso = IsoDep.get(tag) ?: throw ChipReadException("This is not an ISO 14443-4 (IsoDep) chip")
        iso.timeout = 20_000
        val card = IsoDepCardService(iso)
        try {
            card.open()
            val svc = PassportService(
                card, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, false, false,
            )
            svc.open()

            // ---- Access control -----------------------------------------------------------
            step(Stage.FOUND, 0.04f, "Reading EF.CardAccess")
            val cardAccess = try {
                readFile(svc, PassportService.EF_CARD_ACCESS)
            } catch (e: Exception) {
                log += "No EF.CardAccess: ${e.message}"; null
            }
            val paceInfos = cardAccess?.let {
                runCatching { CardAccessFile(ByteArrayInputStream(it)).securityInfos.filterIsInstance<PACEInfo>() }.getOrNull()
            }.orEmpty()

            var protocol: String? = null
            var paceNote: String? = null
            for (info in paceInfos) {
                step(Stage.CHANNEL, 0.08f, "PACE: ${paceName(info)}")
                try {
                    val paceKey = when (key) {
                        is AccessKey.Mrz -> PACEKeySpec.createMRZKey(BACKey(key.documentNumber, key.dateOfBirth, key.dateOfExpiry))
                        is AccessKey.Can -> PACEKeySpec.createCANKey(key.can)
                    }
                    val params = info.parameterId?.let { PACEInfo.toParameterSpec(it) }
                    svc.doPACE(paceKey, info.objectIdentifier, params, info.parameterId)
                    protocol = "PACE · ${paceName(info)}"
                    break
                } catch (e: Exception) {
                    paceNote = "PACE failed (${paceName(info)}): ${rootMessage(e)}"
                    log += paceNote
                }
            }
            svc.sendSelectApplet(protocol != null)
            if (protocol == null) {
                if (key !is AccessKey.Mrz) {
                    throw ChipReadException(
                        if (paceInfos.isEmpty()) "This chip has no PACE, and a CAN only works with PACE. Use the MRZ instead."
                        else "PACE with this CAN was rejected. Check the 6-digit number.",
                    )
                }
                step(Stage.CHANNEL, 0.10f, "BAC")
                try {
                    svc.doBAC(BACKey(key.documentNumber, key.dateOfBirth, key.dateOfExpiry))
                } catch (e: CardServiceException) {
                    throw ChipReadException("The chip refused the key. Check the document number and both dates.", e)
                }
                protocol = "BAC (3DES)"
            }

            // ---- Files --------------------------------------------------------------------
            step(Stage.DATA, 0.16f, "EF.SOD")
            val sodBytes = readFile(svc, PassportService.EF_SOD) { }
            val listed = SODFile(ByteArrayInputStream(sodBytes)).dataGroupHashes.keys.sorted()
            log += "Data groups in SOD: ${listed.joinToString { "DG$it" }}"

            val dgs = linkedMapOf<Int, ByteArray>()
            val notRead = linkedMapOf<Int, String>()
            // Progress band 0.2..0.85 split by weight; the face image (DG2) dominates the transfer.
            val toRead = listed.filter { it !in eacProtected }
            val weights = toRead.map { if (it == 2) 8f else 1f }
            val total = weights.sum().coerceAtLeast(1f)
            var done = 0f
            for (dg in listed) {
                if (dg in eacProtected) {
                    notRead[dg] = "Needs EAC terminal authentication (state-issued certificates)"
                    continue
                }
                val w = weights[toRead.indexOf(dg)]
                val base = done
                step(Stage.DATA, 0.2f + 0.65f * base / total, "Reading DG$dg")
                try {
                    dgs[dg] = readFile(svc, (0x0100 + dg).toShort()) { f ->
                        progress(ReadProgress(Stage.DATA, 0.2f + 0.65f * (base + w * f) / total, "Reading DG$dg"))
                    }
                } catch (e: Exception) {
                    notRead[dg] = rootMessage(e)
                }
                done += w
            }

            // ---- Active Authentication (DG15) ---------------------------------------------
            val dg14 = dgs[14]?.let { runCatching { DG14File(ByteArrayInputStream(it)) }.getOrNull() }
            val aa = dgs[15]?.let { raw ->
                step(Stage.VERIFY, 0.87f, "Active Authentication")
                try {
                    val pub = DG15File(ByteArrayInputStream(raw)).publicKey
                    val challenge = ByteArray(8).also { SecureRandom().nextBytes(it) }
                    val res = svc.doAA(pub, null, null, challenge)
                    val oid = dg14?.activeAuthenticationInfos?.firstOrNull()?.signatureAlgorithmOID
                    ActiveAuth.verify(pub, challenge, res.response, oid)
                } catch (e: Exception) {
                    ActiveAuth.Result(Check.FAIL, "AA", null, rootMessage(e))
                }
            }

            // ---- Chip Authentication (DG14) -----------------------------------------------
            val ca = if (dg14 == null) {
                ChipAuthOutcome(Check.SKIPPED, "No DG14: chip does not offer Chip Authentication")
            } else {
                step(Stage.VERIFY, 0.92f, "Chip Authentication")
                chipAuthenticate(svc, dg14, dgs[1])
            }

            step(Stage.VERIFY, 0.97f, "Passive Authentication")
            val pa = PassiveAuth.verify(sodBytes, dgs, anchors)

            val dg1 = dgs[1]?.let { runCatching { DG1File(ByteArrayInputStream(it)).mrzInfo }.getOrNull() }
            val faces = dgs[2]?.let { runCatching { faces(it) }.getOrElse { e -> log += "DG2: ${e.message}"; emptyList() } }.orEmpty()
            val dg11 = dgs[11]?.let { runCatching { DG11File(ByteArrayInputStream(it)) }.getOrNull() }
            val dg12 = dgs[12]?.let { runCatching { DG12File(ByteArrayInputStream(it)) }.getOrNull() }

            return ReadReport(
                protocol, paceNote, cardAccess, sodBytes, dgs, notRead, dg1, faces, dg11, dg12, pa, ca, aa,
                tagInfo(tag, iso), System.currentTimeMillis() - started, log,
            )
        } catch (e: ChipReadException) {
            throw e
        } catch (e: Exception) {
            throw ChipReadException("The connection was lost (${rootMessage(e)}). Keep the document still against the phone and try again.", e)
        } finally {
            runCatching { card.close() }
        }
    }

    private fun chipAuthenticate(svc: PassportService, dg14: DG14File, dg1Before: ByteArray?): ChipAuthOutcome {
        val pubs = dg14.chipAuthenticationPublicKeyInfos
        if (pubs.isEmpty()) return ChipAuthOutcome(Check.SKIPPED, "DG14 has no Chip Authentication key")
        val infos = dg14.chipAuthenticationInfos
        var last = "unknown"
        for (pk in pubs) {
            val info = infos.firstOrNull { it.keyId == pk.keyId } ?: infos.firstOrNull()
            val oid = info?.objectIdentifier ?: when (pk.subjectPublicKey.algorithm) {
                "EC", "ECDH" -> SecurityInfo.ID_CA_ECDH_3DES_CBC_CBC
                else -> SecurityInfo.ID_CA_DH_3DES_CBC_CBC
            }
            try {
                svc.doEACCA(pk.keyId, oid, pk.objectIdentifier, pk.subjectPublicKey)
                // The new session keys depend on the chip's private key: a clone without it
                // cannot answer this read with a valid MAC.
                val again = readFile(svc, PassportService.EF_DG1) { }
                val same = dg1Before == null || again.contentEquals(dg1Before)
                return if (same) ChipAuthOutcome(Check.PASS, "${caName(oid)}; DG1 re-read under the new session keys")
                else ChipAuthOutcome(Check.FAIL, "DG1 differed after Chip Authentication")
            } catch (e: Exception) {
                last = rootMessage(e)
            }
        }
        return ChipAuthOutcome(Check.FAIL, "Chip Authentication failed: $last")
    }

    private fun faces(raw: ByteArray): List<Face> =
        DG2File(ByteArrayInputStream(raw)).faceInfos.flatMap { it.faceImageInfos }.map { fi ->
            val bytes = ByteArray(fi.imageLength).also { DataInputStream(fi.imageInputStream).readFully(it) }
            Face(fi.mimeType ?: "?", bytes, fi.width, fi.height)
        }

    private fun readFile(svc: PassportService, fid: Short, onProgress: (Float) -> Unit = {}): ByteArray {
        val s = svc.getInputStream(fid)
        val out = ByteArray(s.length)
        var off = 0
        while (off < out.size) {
            val n = s.read(out, off, minOf(1024, out.size - off))
            if (n < 0) throw java.io.EOFException("file ended at $off of ${out.size} bytes")
            off += n
            onProgress(off.toFloat() / out.size)
        }
        return out
    }

    private fun tagInfo(tag: Tag, iso: IsoDep): List<Pair<String, String>> = listOfNotNull(
        "Technologies" to tag.techList.joinToString { it.substringAfterLast('.') },
        "UID" to tag.id.hex() + if (tag.id.size == 4 && tag.id[0] == 0x08.toByte()) " (random, changes every session)" else "",
        iso.historicalBytes?.let { "Historical bytes (NFC-A)" to it.hex() },
        iso.hiLayerResponse?.let { "Hi-layer response (NFC-B)" to it.hex() },
        "Extended length APDU" to if (iso.isExtendedLengthApduSupported) "yes" else "no",
        "Max transceive" to "${iso.maxTransceiveLength} bytes",
    )

    fun paceName(info: PACEInfo): String {
        val oid = info.objectIdentifier
        val mapping = runCatching { PACEInfo.toMappingType(oid).name }.getOrDefault("?")
        val agreement = runCatching { PACEInfo.toKeyAgreementAlgorithm(oid) }.getOrDefault("?")
        val cipher = runCatching { PACEInfo.toCipherAlgorithm(oid) }.getOrDefault("?")
        val bits = runCatching { PACEInfo.toKeyLength(oid) }.getOrDefault(0)
        val params = info.parameterId?.let { runCatching { PACEInfo.toStandardizedParamIdString(it) }.getOrNull() }
        return listOfNotNull("$agreement-$mapping", "$cipher-$bits", params).joinToString(", ")
    }

    private fun caName(oid: String): String = runCatching {
        "CA ${org.jmrtd.lds.ChipAuthenticationInfo.toKeyAgreementAlgorithm(oid)}, " +
            "${org.jmrtd.lds.ChipAuthenticationInfo.toCipherAlgorithm(oid)}-${org.jmrtd.lds.ChipAuthenticationInfo.toKeyLength(oid)}"
    }.getOrDefault("CA $oid")

    private fun rootMessage(e: Throwable): String {
        var t = e
        while (t.cause != null && t.cause !== t) t = t.cause!!
        return t.message ?: t.javaClass.simpleName
    }
}

fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }
