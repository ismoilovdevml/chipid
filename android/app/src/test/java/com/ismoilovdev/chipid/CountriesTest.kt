package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.Countries
import org.junit.Assert.assertEquals
import org.junit.Test

class CountriesTest {
    @Test
    fun mapsMrzAndCertificateCodes() {
        assertEquals("France", Countries.name("FRA"))
        assertEquals("Japan", Countries.name("JP"))
        assertEquals("Germany", Countries.name("D<<"))
        assertEquals("United Kingdom", Countries.name("GBR"))
        assertEquals("XYZ", Countries.name("XYZ"))
    }
}
