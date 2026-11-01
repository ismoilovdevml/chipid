package com.ismoilovdev.chipid.core

import java.util.Locale

/** ISO 3166 names for MRZ (alpha-3) and certificate (alpha-2) country codes. */
object Countries {
    private val alpha3to2: Map<String, String> by lazy {
        Locale.getISOCountries().associateBy { runCatching { Locale("", it).isO3Country }.getOrDefault("") }
    }

    /** MRZ codes that are not plain ISO 3166 alpha-3 (ICAO 9303 part 3, 5). */
    private val special = mapOf("D" to "DE", "GBD" to "GB", "GBN" to "GB", "GBO" to "GB", "GBP" to "GB", "GBS" to "GB", "RKS" to "XK")

    fun alpha2(code: String): String? {
        val c = code.trim('<').uppercase()
        return special[c] ?: if (c.length == 2) c else alpha3to2[c]
    }

    fun name(code: String): String {
        val c = code.trim('<').uppercase()
        if (c == "UTO") return "Utopia (ICAO specimen)"
        val a2 = alpha2(c) ?: return c
        return Locale("", a2).getDisplayCountry(Locale.ENGLISH).takeIf { it.isNotBlank() && it != a2 } ?: c
    }
}
