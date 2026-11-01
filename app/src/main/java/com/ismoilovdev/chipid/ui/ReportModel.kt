package com.ismoilovdev.chipid.ui

import com.ismoilovdev.chipid.core.Check
import com.ismoilovdev.chipid.core.Countries
import com.ismoilovdev.chipid.core.MasterList
import com.ismoilovdev.chipid.reader.ReadReport
import com.ismoilovdev.chipid.reader.hex
import java.text.SimpleDateFormat
import java.util.Locale

class Row(val label: String, val value: String, val check: Check? = null)
class Section(val title: String, val rows: List<Row>, val mono: Boolean = false)

/** One description of a read, shared by the details screen and the exported report.txt. */
object ReportModel {
    fun sections(r: ReadReport): List<Section> = listOfNotNull(
        security(r), person(r), document(r), additional(r), dsc(r), technical(r),
    )

    /** "Germany" for the CSCA that signed this document, or the MRZ issuing state. */
    fun signerCountry(r: ReadReport): String {
        val cc = r.passive.dsc?.issuerX500Principal?.let { MasterList.country(it) }
        return Countries.name(cc ?: r.mrz?.issuingState ?: "?")
    }

    /** Name as printed in the MRZ; may be truncated to fit the zone (ICAO 9303 part 3, 4.6). */
    fun mrzName(r: ReadReport): Pair<String, String> {
        val m = r.mrz ?: return "" to ""
        return m.primaryIdentifier.replace('<', ' ').trim() to m.secondaryIdentifier.replace('<', ' ').trim()
    }

    /** Full name from DG11 when the chip has it, otherwise the MRZ name. */
    fun name(r: ReadReport): Pair<String, String> =
        r.dg11?.nameOfHolder?.takeIf { it.isNotBlank() }?.let(::splitIcaoName) ?: mrzName(r)

    /** "SURNAME<<GIVEN<NAMES" -> ("SURNAME", "GIVEN NAMES"). */
    fun splitIcaoName(field: String): Pair<String, String> {
        val parts = field.trimEnd('<').split("<<", limit = 2)
        return parts[0].replace('<', ' ').trim() to parts.getOrElse(1) { "" }.replace('<', ' ').trim()
    }

    fun isTruncation(mrz: String, full: String): Boolean = mrz != full && full.startsWith(mrz.trimEnd())

    /** YYYYMMDD (DG11/DG12) -> DD.MM.YYYY; anything else is returned unchanged. */
    fun date8(s: String?): String? = s?.trim()?.let {
        if (it.length == 8 && it.all(Char::isDigit)) "${it.substring(6)}.${it.substring(4, 6)}.${it.substring(0, 4)}" else it
    }

    private fun security(r: ReadReport): Section {
        val pa = r.passive
        return Section(
            "Security checks",
            buildList {
                add(Row("Access control", r.accessProtocol))
                r.paceNote?.let { add(Row("PACE note", it)) }
                add(Row("Data group hashes match SOD", "${pa.digestAlgorithm}; " + pa.perDataGroup.entries.joinToString { "DG${it.key} ${mark(it.value)}" }, pa.dataGroupHashes))
                add(Row("SOD signature (DSC)", pa.signatureAlgorithm ?: "—", pa.sodSignature))
                add(Row("DSC → CSCA chain", pa.cscaSubject ?: "no matching CSCA", pa.cscaChain))
                add(Row("Passive Authentication", verdict(pa.overall), pa.overall))
                add(Row("Chip Authentication", r.chipAuth.detail, r.chipAuth.check))
                add(
                    r.activeAuth?.let {
                        Row("Active Authentication", listOfNotNull(it.algorithm, it.digest, it.detail).joinToString(", "), it.check)
                    } ?: Row("Active Authentication", "no DG15", Check.SKIPPED),
                )
                add(Row("Chip is genuine (not a clone)", verdict(r.chipGenuine), r.chipGenuine))
                pa.errors.forEach { add(Row("Note", it)) }
            },
        )
    }

    private fun person(r: ReadReport): Section? {
        val m = r.mrz ?: return null
        val personal = m.personalNumber?.trim('<')?.takeIf { it.isNotBlank() }
            ?: m.optionalData1?.trim('<')?.takeIf { it.isNotBlank() }
        val (sur, given) = name(r)
        val (mrzSur, mrzGiven) = mrzName(r)
        val fromDg11 = r.dg11?.nameOfHolder?.isNotBlank() == true
        val mrzDiffers = fromDg11 && (mrzSur != sur || mrzGiven != given)
        val truncated = isTruncation(mrzSur, sur) || isTruncation(mrzGiven, given)
        return Section(
            if (fromDg11) "Holder (DG1 / DG11)" else "Holder (DG1 / MRZ)",
            listOfNotNull(
                Row("Surname", sur),
                Row("Given names", given),
                if (mrzDiffers) Row(
                    if (truncated) "Name in MRZ (shortened to fit)" else "Name in MRZ",
                    "$mrzSur, $mrzGiven",
                ) else null,
                Row("Sex", m.gender.toString()),
                Row("Date of birth", date(m.dateOfBirth, birth = true)),
                Row("Nationality", "${Countries.name(m.nationality)} (${m.nationality})"),
                personal?.let { Row("Personal number", it) },
            ),
        )
    }

    private fun document(r: ReadReport): Section? {
        val m = r.mrz ?: return null
        return Section(
            "Document",
            listOfNotNull(
                Row("Type", "${m.documentCode} (${if (m.documentCode.startsWith("P")) "passport" else "identity card"})"),
                Row("Number", m.documentNumber),
                Row("Issuing state", "${Countries.name(m.issuingState)} (${m.issuingState})"),
                Row("Date of expiry", date(m.dateOfExpiry)),
                r.dg12?.issuingAuthority?.let { Row("Issuing authority", it) },
                r.dg12?.dateOfIssue?.let { Row("Date of issue", date8(it)!!) },
                r.passive.ldsVersion?.let { Row("LDS version", it) },
            ),
        )
    }

    private fun additional(r: ReadReport): Section? {
        val d = r.dg11 ?: return null
        val rows = listOfNotNull(
            d.nameOfHolder?.let { splitIcaoName(it) }?.let { (sur, given) -> Row("Full name", listOf(sur, given).filter(String::isNotEmpty).joinToString(", ")) },
            d.fullDateOfBirth?.let { Row("Full date of birth", date8(it)!!) },
            d.placeOfBirth?.takeIf { it.isNotEmpty() }?.let { Row("Place of birth", it.joinToString(", ")) },
            d.permanentAddress?.takeIf { it.isNotEmpty() }?.let { Row("Address", it.joinToString(", ")) },
            d.personalNumber?.let { Row("Personal number (DG11)", it) },
        )
        return if (rows.isEmpty()) null else Section("Additional details (DG11)", rows)
    }

    private fun dsc(r: ReadReport): Section? {
        val c = r.passive.dsc ?: return null
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return Section(
            "Document Signer certificate",
            listOfNotNull(
                Row("Subject", c.subjectX500Principal.name),
                Row("Issuer", c.issuerX500Principal.name),
                Row("Serial", c.serialNumber.toString(16)),
                Row("Validity", "${fmt.format(c.notBefore)} → ${fmt.format(c.notAfter)}"),
                r.passive.dscValidNow?.let { Row("Valid today", if (it) "yes" else "no (normal: signer keys are used only briefly)") },
                Row("Key", "${c.publicKey.algorithm} · ${c.sigAlgName}"),
            ),
            mono = true,
        )
    }

    private fun technical(r: ReadReport): Section = Section(
        "Technical",
        buildList {
            addAll(r.tagInfo.map { Row(it.first, it.second) })
            add(Row("Files read", (listOf("SOD ${r.sod.size} B") + r.dataGroups.map { "DG${it.key} ${it.value.size} B" }).joinToString(", ")))
            r.notRead.forEach { (dg, why) -> add(Row("DG$dg not read", why, Check.SKIPPED)) }
            r.faces.forEachIndexed { i, f -> add(Row("Face image $i", "${f.mimeType}, ${f.width}×${f.height}, ${f.bytes.size} bytes")) }
            r.cardAccess?.let { add(Row("EF.CardAccess", it.hex().take(64) + if (it.size > 32) "…" else "")) }
            add(Row("Read time", "${r.millis} ms"))
        },
        mono = true,
    )

    fun text(r: ReadReport): String = buildString {
        sections(r).forEach { s ->
            appendLine("== ${s.title} ==")
            s.rows.forEach { appendLine("${it.check?.let { c -> "[${c.name}] " } ?: ""}${it.label}: ${it.value}") }
            appendLine()
        }
        appendLine("== Log ==")
        r.log.forEach { appendLine(it) }
    }

    fun verdict(c: Check) = when (c) {
        Check.PASS -> "verified"
        Check.FAIL -> "FAILED"
        Check.UNKNOWN -> "could not be decided"
        Check.SKIPPED -> "not performed"
    }

    private fun mark(c: Check) = when (c) {
        Check.PASS -> "✓"
        Check.FAIL -> "✗"
        Check.UNKNOWN -> "?"
        Check.SKIPPED -> "–"
    }

    /** YYMMDD -> DD.MM.YYYY. Birth dates are never in the future; expiry dates are always 20xx. */
    fun date(yymmdd: String, birth: Boolean = false): String {
        if (yymmdd.length != 6 || !yymmdd.all(Char::isDigit)) return yymmdd
        val yy = yymmdd.substring(0, 2).toInt()
        val year = if (birth && 2000 + yy > java.time.LocalDate.now().year) 1900 + yy else 2000 + yy
        return "${yymmdd.substring(4)}.${yymmdd.substring(2, 4)}.$year"
    }
}
