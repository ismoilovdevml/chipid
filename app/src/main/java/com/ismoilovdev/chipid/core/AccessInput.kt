package com.ismoilovdev.chipid.core

import java.time.DateTimeException
import java.time.LocalDate

/** Validation and normalisation of the access key the user types (MRZ fields or CAN). */
object AccessInput {
    private val dotted = Regex("""^(\d{2})[./\-]?(\d{2})[./\-]?(\d{4})$""")
    private val yymmdd = Regex("""^(\d{2})(\d{2})(\d{2})$""")
    private val docNumber = Regex("""^[A-Z0-9]{5,20}$""")

    /** "15.03.1990" / "15031990" -> the date; null if incomplete or impossible. */
    fun toLocalDate(input: String): LocalDate? {
        val m = dotted.matchEntire(input.trim()) ?: return null
        val (d, mo, y) = m.destructured
        return try {
            LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
        } catch (_: DateTimeException) {
            null
        }
    }

    /** "15.03.1990", "15031990" or "900315" -> "900315"; null if the date does not exist. */
    fun toYymmdd(input: String): String? {
        val s = input.trim()
        dotted.matchEntire(s)?.let { m ->
            val (d, mo, y) = m.destructured
            if (!valid(y.toInt(), mo.toInt(), d.toInt())) return null
            return y.takeLast(2) + mo + d
        }
        yymmdd.matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            // Any century works for validation except Feb 29; 2000 is a leap year.
            if (!valid(2000 + y.toInt(), mo.toInt(), d.toInt())) return null
            return s
        }
        return null
    }

    private fun valid(y: Int, m: Int, d: Int) = try {
        LocalDate.of(y, m, d); true
    } catch (_: DateTimeException) {
        false
    }

    /** What a date field keeps while typing: digits only, at most DDMMYYYY. */
    fun dateDigits(input: String): String = input.filter(Char::isDigit).take(8)

    /** What the document-number field keeps while typing. */
    fun documentDigits(input: String): String = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(20)

    fun normalizeDocumentNumber(input: String): String? =
        input.uppercase().filterNot { it.isWhitespace() }.takeIf { docNumber.matches(it) }

    fun normalizeCan(input: String): String? =
        input.filterNot { it.isWhitespace() }.takeIf { it.length == 6 && it.all(Char::isDigit) }

    /** ICAO 9303 part 3 check digit (weights 7,3,1; A=10..Z=35; '<'=0). */
    fun checkDigit(s: String): Int = s.foldIndexed(0) { i, acc, c ->
        val v = when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'Z' -> c - 'A' + 10
            else -> 0
        }
        acc + v * intArrayOf(7, 3, 1)[i % 3]
    } % 10
}
