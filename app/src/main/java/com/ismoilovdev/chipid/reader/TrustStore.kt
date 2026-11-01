package com.ismoilovdev.chipid.reader

import android.content.Context
import com.ismoilovdev.chipid.core.Bc
import com.ismoilovdev.chipid.core.MasterList
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * CSCA trust anchors with their origin.
 *
 * - The bundled BSI master list is used only if its signer chains to a pinned German CSCA;
 *   a list that fails this check contributes no anchors (fail closed).
 * - Files in Android/data/com.ismoilovdev.chipid/files/csca/ are user-added. They are honoured, but
 *   a document that verifies only through one of them is flagged, never shown as plainly genuine.
 */
class TrustStore(
    val anchors: List<X509Certificate>,
    private val userAdded: Set<String>,
    val sources: List<String>,
    val countries: Int,
    val bundledTrusted: Boolean,
) {
    fun isUserAdded(cert: X509Certificate?): Boolean = cert != null && MasterList.spkiSha256(cert) + cert.serialNumber in userAdded

    val userAddedCount: Int get() = userAdded.size

    companion object {
        fun load(context: Context): TrustStore {
            Bc.install()
            val bundled = mutableListOf<X509Certificate>()
            val user = mutableListOf<X509Certificate>()
            val sources = mutableListOf<String>()

            val ml = MasterList.parse(context.assets.open("masterlist.ml").use { it.readBytes() })
            if (ml.signerTrusted) {
                bundled += ml.certificates
                sources += "BSI master list: ${ml.certificates.size} CSCAs, signer chains to pinned German CSCA, sha256 ${ml.sha256.take(16)}…"
            } else {
                sources += "BSI master list REJECTED: signer does not chain to a pinned German CSCA (sha256 ${ml.sha256.take(16)}…)"
            }

            val dir = File(context.getExternalFilesDir(null), "csca")
            val cf = CertificateFactory.getInstance("X.509", Bc.provider)
            dir.listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                runCatching {
                    val added = if (f.extension.equals("ml", true)) {
                        MasterList.parse(f.readBytes(), pinnedRoots = emptySet()).certificates
                    } else {
                        f.inputStream().use { cf.generateCertificates(it) }.filterIsInstance<X509Certificate>()
                    }
                    user += added
                    sources += "${f.name}: ${added.size} user-added certificates"
                }.onFailure { sources += "${f.name}: unreadable (${it.message})" }
            }

            val unique = (bundled + user).distinctBy { it.encoded.contentHashCode() }
            val bundledKeys = bundled.map { MasterList.spkiSha256(it) + it.serialNumber }.toSet()
            // A certificate that is also in the verified list keeps its verified status.
            val userOnly = user.map { MasterList.spkiSha256(it) + it.serialNumber }.filterNot { it in bundledKeys }.toSet()
            val countries = unique.mapNotNull { MasterList.country(it.subjectX500Principal) }.toSet().size
            return TrustStore(unique, userOnly, sources, countries, ml.signerTrusted)
        }
    }
}
