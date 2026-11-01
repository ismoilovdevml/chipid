package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.ui.ReportModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportModelTest {
    @Test
    fun splitsIcaoNameField() {
        assertEquals("ERIKSSON" to "ANNA MARIA", ReportModel.splitIcaoName("ERIKSSON<<ANNA<MARIA"))
        assertEquals("ERIKSSON" to "ANNA MARIA", ReportModel.splitIcaoName("ERIKSSON<<ANNA<MARIA<<<<"))
        assertEquals("ERIKSSON" to "", ReportModel.splitIcaoName("ERIKSSON"))
    }

    @Test
    fun formatsEightDigitDates() {
        // DG11 full date of birth and DG12 date of issue are YYYYMMDD.
        assertEquals("12.08.1974", ReportModel.date8("19740812"))
        assertEquals("1974-08", ReportModel.date8("1974-08"))
        assertNull(ReportModel.date8(null))
    }

    @Test
    fun detectsTruncatedMrzName() {
        // MRZ name fields are cut to fit; the DG11 name is the full one.
        assertEquals(true, ReportModel.isTruncation("ANNA MARIA LO", "ANNA MARIA LOUISE"))
        assertEquals(false, ReportModel.isTruncation("ANNA MARIA", "ANNA MARIA"))
    }
}
