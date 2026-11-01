package com.ismoilovdev.chipid.reader

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the raw chip files for offline analysis on a computer (adb pull). Nothing is written
 * unless the user asks; [wipe] removes every export.
 */
object Exporter {
    fun root(context: Context) = File(context.getExternalFilesDir(null), "export")

    fun export(context: Context, r: ReadReport, reportText: String): File {
        val dir = File(root(context), SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())).apply { mkdirs() }
        r.cardAccess?.let { File(dir, "EF.CardAccess.bin").writeBytes(it) }
        File(dir, "EF.SOD.bin").writeBytes(r.sod)
        r.dataGroups.forEach { (n, b) -> File(dir, "DG$n.bin").writeBytes(b) }
        r.faces.forEachIndexed { i, f ->
            val ext = when {
                f.mimeType.contains("jp2") || f.mimeType.contains("jpeg2000") -> "jp2"
                f.mimeType.contains("jpeg") -> "jpg"
                f.mimeType.contains("wsq") -> "wsq"
                else -> "bin"
            }
            File(dir, "face$i.$ext").writeBytes(f.bytes)
        }
        File(dir, "report.txt").writeText(reportText)
        return dir
    }

    fun wipe(context: Context): Boolean = root(context).deleteRecursively()
}
