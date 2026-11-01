package com.ismoilovdev.chipid.core

/**
 * Finds and validates a machine readable zone in OCR output (ICAO 9303 parts 3-5).
 * Every field that has a check digit must verify, so a misread line is rejected rather than
 * producing a wrong chip key. Position-aware repair fixes the usual OCR confusions first.
 */
object Mrz {
    enum class Format { TD1, TD3 }

    data class Result(
        val format: Format,
        val documentCode: String,
        val issuingState: String,
        val documentNumber: String,
        val dateOfBirth: String,
        val dateOfExpiry: String,
        val nationality: String,
        val sex: String,
        val surname: String?,
        val givenNames: String?,
        val compositeValid: Boolean,
    )

    private val toDigit = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'U' to '0', 'I' to '1', 'L' to '1', 'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6', 'T' to '7')
    private val toLetter = mapOf('0' to 'O', '1' to 'I', '2' to 'Z', '5' to 'S', '8' to 'B', '6' to 'G', '7' to 'T')

    fun find(lines: List<String>): Result? {
        val clean = lines.map(::normalize).filter { it.length >= 28 }
        for (i in clean.indices) {
            val a = clean[i]
            val b = clean.getOrNull(i + 1)
            val c = clean.getOrNull(i + 2)
            fit(a, 44)?.let { l1 -> b?.let { fit(it, 44) }?.let { l2 -> parseTd3(l1, l2)?.let { return it } } }
            fit(a, 30)?.let { l1 ->
                val l2 = b?.let { fit(it, 30) } ?: return@let
                parseTd1(l1, l2, c?.let { fit(it, 30) })?.let { return it }
            }
        }
        return null
    }

    /** Upper-case, OCR-B quirks ('«' is '<<', spaces vanish), anything else becomes filler. */
    fun normalize(s: String): String = s.uppercase()
        .replace("«", "<<").replace("‹", "<").replace(" ", "")
        .map { if (it in 'A'..'Z' || it in '0'..'9' || it == '<') it else '<' }
        .joinToString("")

    /** OCR often drops or adds one trailing filler; accept ±2 and fix up the tail. */
    private fun fit(s: String, n: Int): String? = when {
        s.length == n -> s
        s.length in (n - 2) until n -> s.padEnd(n, '<')
        s.length in (n + 1)..(n + 2) && s.substring(n).all { it == '<' } -> s.take(n)
        else -> null
    }

    private fun parseTd3(l1: String, l2raw: String): Result? {
        if (l1[0] !in "PV") return null
        val l2 = l2raw.toCharArray()
        digits(l2, 13..19); digits(l2, 21..27); digits(l2, listOf(9, 42, 43))
        letters(l2, 10..12)
        val s = String(l2)
        val dob = s.substring(13, 19)
        val exp = s.substring(21, 27)
        if (!check(dob, s[19]) || !check(exp, s[27])) return null
        val doc = repairDocNumber(s.substring(0, 9), s[9]) ?: return null
        val fixed = doc + s.substring(9)
        val composite = fixed.substring(0, 10) + fixed.substring(13, 20) + fixed.substring(21, 43)
        val (sur, given) = names(l1.substring(5))
        return Result(
            Format.TD3, l1.substring(0, 2).trim('<'), lettersOf(l1.substring(2, 5)), doc.trimEnd('<'), dob, exp,
            fixed.substring(10, 13), fixed.substring(20, 21), sur, given, check(composite, fixed[43]),
        )
    }

    private fun parseTd1(l1raw: String, l2raw: String, l3: String?): Result? {
        if (l1raw[0] !in "IAC") return null
        val l2 = l2raw.toCharArray()
        digits(l2, 0..6); digits(l2, 8..14); digits(l2, listOf(29))
        letters(l2, 15..17)
        val s2 = String(l2)
        val dob = s2.substring(0, 6)
        val exp = s2.substring(8, 14)
        if (!check(dob, s2[6]) || !check(exp, s2[14])) return null
        val l1 = l1raw.toCharArray().also { digits(it, listOf(14)) }.let(::String)
        val doc = repairDocNumber(l1.substring(5, 14), l1[14]) ?: return null
        val fixed1 = l1.substring(0, 5) + doc + l1.substring(14)
        val composite = fixed1.substring(5, 30) + s2.substring(0, 7) + s2.substring(8, 15) + s2.substring(18, 29)
        val (sur, given) = l3?.let { names(it) } ?: (null to null)
        return Result(
            Format.TD1, fixed1.substring(0, 2).trim('<'), lettersOf(fixed1.substring(2, 5)), doc.trimEnd('<'), dob, exp,
            s2.substring(15, 18), s2.substring(7, 8), sur, given, check(composite, s2[29]),
        )
    }

    /**
     * Document numbers mix letters and digits, so OCR swaps cannot be undone by position alone.
     * Try the read value, then flip ambiguous characters until the check digit agrees.
     */
    private fun repairDocNumber(raw: String, checkChar: Char): String? {
        if (check(raw, checkChar)) return raw
        val ambiguous = raw.indices.filter { raw[it] in toDigit || raw[it] in toLetter }
        if (ambiguous.size > 10) return null
        val candidates = mutableListOf<String>()
        for (mask in 1 until (1 shl ambiguous.size)) {
            val c = raw.toCharArray()
            ambiguous.forEachIndexed { bit, idx ->
                if (mask shr bit and 1 == 1) c[idx] = toDigit[c[idx]] ?: toLetter[c[idx]] ?: c[idx]
            }
            val s = String(c)
            if (check(s, checkChar)) candidates += s
        }
        // Prefer the repair that matches the common "letters then digits" shape (e.g. FA1234567).
        return candidates.minByOrNull(::shapePenalty)
    }

    /** Letters after the first digit; 0 for the usual "letters then digits" shape. */
    private fun shapePenalty(s: String): Int {
        val firstDigit = s.indexOfFirst { it.isDigit() }.takeIf { it >= 0 } ?: return 0
        return s.substring(firstDigit).count { it.isLetter() }
    }

    private fun check(field: String, c: Char): Boolean = c.isDigit() && AccessInput.checkDigit(field) == c - '0'

    private fun digits(a: CharArray, r: Iterable<Int>) = r.forEach { i -> if (i < a.size) a[i] = toDigit[a[i]] ?: a[i] }
    private fun letters(a: CharArray, r: IntRange) = r.forEach { i -> if (i < a.size) a[i] = toLetter[a[i]] ?: a[i] }
    private fun lettersOf(s: String) = s.map { toLetter[it] ?: it }.joinToString("")

    private fun names(field: String): Pair<String?, String?> {
        val parts = field.trimEnd('<').split("<<", limit = 2)
        val sur = parts.getOrNull(0)?.replace('<', ' ')?.trim()?.takeIf { it.isNotEmpty() }
        val given = parts.getOrNull(1)?.replace('<', ' ')?.trim()?.takeIf { it.isNotEmpty() }
        return sur to given
    }
}
