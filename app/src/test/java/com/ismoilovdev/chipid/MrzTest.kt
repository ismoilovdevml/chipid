package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.Mrz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Specimens from ICAO Doc 9303 part 4 (TD3) and part 5 (TD1). */
class MrzTest {
    private val td3 = listOf(
        "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
        "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
    )
    private val td1 = listOf(
        "I<UTOD231458907<<<<<<<<<<<<<<<",
        "7408122F1204159UTO<<<<<<<<<<<6",
        "ERIKSSON<<ANNA<MARIA<<<<<<<<<<",
    )

    @Test
    fun parsesTd3Specimen() {
        val m = Mrz.find(td3)!!
        assertEquals(Mrz.Format.TD3, m.format)
        assertEquals("L898902C3", m.documentNumber)
        assertEquals("740812", m.dateOfBirth)
        assertEquals("120415", m.dateOfExpiry)
        assertEquals("UTO", m.issuingState)
        assertEquals("ERIKSSON", m.surname)
        assertEquals("ANNA MARIA", m.givenNames)
        assertEquals(true, m.compositeValid)
    }

    @Test
    fun parsesTd1Specimen() {
        val m = Mrz.find(td1)!!
        assertEquals(Mrz.Format.TD1, m.format)
        assertEquals("D23145890", m.documentNumber)
        assertEquals("740812", m.dateOfBirth)
        assertEquals("120415", m.dateOfExpiry)
        assertEquals("ERIKSSON", m.surname)
    }

    @Test
    fun repairsTypicalOcrConfusions() {
        // OCR noise: spaces, lower case, 'O' for '0' in dates, '«' for '<<', junk lines around.
        val noisy = listOf(
            "PASSPORT",
            "P<UTOERIKSSON«ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
            "l898902c36UTO74O8122F12O4159ZE184226B<<<<<1O",
            "some other text",
        )
        val m = Mrz.find(noisy)!!
        assertEquals("L898902C3", m.documentNumber)
        assertEquals("740812", m.dateOfBirth)
        assertEquals("120415", m.dateOfExpiry)
    }

    @Test
    fun repairsAmbiguousDocumentNumberUsingCheckDigit() {
        // Document number read as "L8989O2C3": the check digit resolves O -> 0.
        val m = Mrz.find(listOf(td3[0], "L8989O2C36UTO7408122F1204159ZE184226B<<<<<10"))!!
        assertEquals("L898902C3", m.documentNumber)
    }

    @Test
    fun rejectsLineWithWrongCheckDigit() {
        assertNull(Mrz.find(listOf(td3[0], "L898902C36UTO7408121F1204159ZE184226B<<<<<10")))
    }

    @Test
    fun ignoresUnrelatedText() {
        assertNull(Mrz.find(listOf("REPUBLIC OF UZBEKISTAN", "PASSPORT", "12.03.2020")))
    }
}
