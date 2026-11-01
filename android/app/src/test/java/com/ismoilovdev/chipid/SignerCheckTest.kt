package com.ismoilovdev.chipid

import com.ismoilovdev.chipid.core.SignerCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class SignerCheckTest {
    private fun year(y: Int): Date = Calendar.getInstance().apply { clear(); set(y, 0, 1) }.time

    @Test
    fun recognisesDevelopmentAndTestPkis() {
        assertTrue(SignerCheck.looksLikeTestPki("CN=CSCA,OU=development,O=vendor,C=DE", year(2020), year(2030)))
        assertTrue(SignerCheck.looksLikeTestPki("CN=Test CSCA,O=Example,C=UT", year(2020), year(2030)))
        assertTrue(SignerCheck.looksLikeTestPki("CN=SPECIMEN DS,C=UT", year(2020), year(2030)))
        // 60-year validity is not something a production document signer gets.
        assertTrue(SignerCheck.looksLikeTestPki("CN=DS,O=Vendor,C=UT", year(2020), year(2080)))
    }

    @Test
    fun productionNamesAreNotFlagged() {
        assertFalse(SignerCheck.looksLikeTestPki("CN=CSCA-UTOPIA,OU=GCP,O=GOV,C=UT", year(2023), year(2033)))
        // "Testing" inside a longer word must not trigger.
        assertFalse(SignerCheck.looksLikeTestPki("CN=Contestant Authority,O=GOV,C=UT", year(2023), year(2033)))
    }

    @Test
    fun comparesSignerCountryWithIssuingState() {
        assertEquals(true, SignerCheck.countryMatches("DEU", "DE"))
        assertEquals(true, SignerCheck.countryMatches("D<<", "DE"))
        assertEquals(false, SignerCheck.countryMatches("FRA", "DE"))
        assertNull(SignerCheck.countryMatches("FRA", null))
    }
}
