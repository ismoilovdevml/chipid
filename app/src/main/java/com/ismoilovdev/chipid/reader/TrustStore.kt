package com.ismoilovdev.chipid.reader

import android.content.Context
import com.ismoilovdev.chipid.core.Bc
import com.ismoilovdev.chipid.core.MasterList
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * CSCA trust anchors: the bundled BSI master list plus anything the user drops into
 * Android/data/com.ismoilovdev.chipid/files/csca/ (.ml master lists, .der/.cer/.crt/.pem certs).
 */
class TrustStore(val anchors: List<X509Certificate>, val sources: List<String>, val countries: Int) {
    companion object {
        fun load(context: Context): TrustStore {
            Bc.install()
            val certs = mutableListOf<X509Certificate>()
            val sources = mutableListOf<String>()

            context.assets.open("masterlist.ml").use { it.readBytes() }.let { bytes ->
                val ml = MasterList.parse(bytes)
                certs += ml.certificates
                sources += "BSI master list: ${ml.certificates.size} CSCAs, signature ${if (ml.signatureValid) "valid" else "NOT VERIFIED"}, sha256 ${ml.sha256.take(16)}…"
            }

            val dir = File(context.getExternalFilesDir(null), "csca").apply { mkdirs() }
            val cf = CertificateFactory.getInstance("X.509", Bc.provider)
            dir.listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                runCatching {
                    if (f.extension.equals("ml", true)) {
                        val ml = MasterList.parse(f.readBytes())
                        certs += ml.certificates
                        sources += "${f.name}: ${ml.certificates.size} CSCAs"
                    } else {
                        val added = f.inputStream().use { cf.generateCertificates(it) }.filterIsInstance<X509Certificate>()
                        certs += added
                        sources += "${f.name}: ${added.size} certificates"
                    }
                }.onFailure { sources += "${f.name}: unreadable (${it.message})" }
            }
            val unique = certs.distinctBy { it.encoded.contentHashCode() }
            val countries = unique.mapNotNull { MasterList.country(it.subjectX500Principal) }.toSet().size
            return TrustStore(unique, sources, countries)
        }
    }
}
