package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.AccessInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessInputTest {
    @Test
    fun parsesDottedDateToYymmdd() {
        assertEquals("900315", AccessInput.toYymmdd("15.03.1990"))
        assertEquals("340101", AccessInput.toYymmdd("01.01.2034"))
    }

    @Test
    fun acceptsBareEightDigitDate() {
        // A numeric keyboard has no '.', so users type DDMMYYYY.
        assertEquals("900315", AccessInput.toYymmdd("15031990"))
        assertEquals("900315", AccessInput.toYymmdd("15/03/1990"))
        assertEquals("900315", AccessInput.toYymmdd("15-03-1990"))
        assertNull(AccessInput.toYymmdd("31021990"))
    }

    @Test
    fun dateDigitsAreCappedAtEight() {
        assertEquals("15031990", AccessInput.dateDigits("15.03.19901"))
        assertEquals("1503", AccessInput.dateDigits("15a03"))
    }

    @Test
    fun documentNumberInputIsFiltered() {
        assertEquals("FA12", AccessInput.documentDigits("fa-1 2"))
    }

    @Test
    fun acceptsYymmddAsIs() {
        assertEquals("900315", AccessInput.toYymmdd("900315"))
    }

    @Test
    fun rejectsImpossibleDates() {
        assertNull(AccessInput.toYymmdd("31.02.1990"))
        assertNull(AccessInput.toYymmdd("1990-03-15"))
        assertNull(AccessInput.toYymmdd("901315"))
    }

    @Test
    fun normalizesDocumentNumber() {
        assertEquals("AA1234567", AccessInput.normalizeDocumentNumber(" aa 123 4567 "))
        assertNull(AccessInput.normalizeDocumentNumber("AA-12"))
        assertNull(AccessInput.normalizeDocumentNumber(""))
    }

    @Test
    fun validatesCan() {
        assertEquals("123456", AccessInput.normalizeCan(" 123 456 "))
        assertNull(AccessInput.normalizeCan("12345"))
        assertNull(AccessInput.normalizeCan("12345a"))
    }

    @Test
    fun computesIcaoCheckDigit() {
        // ICAO 9303 part 3 worked example: document number L898902C3 -> check digit 6.
        assertEquals(6, AccessInput.checkDigit("L898902C3"))
        assertEquals(2, AccessInput.checkDigit("740812"))
    }
}
