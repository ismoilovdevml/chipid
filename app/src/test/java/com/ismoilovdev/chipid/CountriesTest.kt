package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.Countries
import org.junit.Assert.assertEquals
import org.junit.Test

class CountriesTest {
    @Test
    fun mapsMrzAndCertificateCodes() {
        assertEquals("Uzbekistan", Countries.name("UZB"))
        assertEquals("Uzbekistan", Countries.name("UZ"))
        assertEquals("Germany", Countries.name("D<<"))
        assertEquals("United Kingdom", Countries.name("GBR"))
        assertEquals("XYZ", Countries.name("XYZ"))
    }
}
