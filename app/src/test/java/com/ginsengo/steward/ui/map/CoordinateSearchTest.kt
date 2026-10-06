package com.ginsengo.steward.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P.1: coordinates typed into the search bar, parsed on the phone. */
class CoordinateSearchTest {

    private fun at(q: String) = PlaceSearch.parseCoordinates(q)!!.let { it.lat to it.lng }

    @Test
    fun decimalDegreesInTheUsualForms() {
        assertEquals(35.5605 to -82.996, at("35.5605, -82.996"))
        assertEquals(35.5605 to -82.996, at("35.5605 -82.996"))
        assertEquals(35.5605 to -82.996, at("35.5605 N, 82.996 W"))
        assertEquals(-12.5 to 45.25, at("12.5 S 45.25 E"))
    }

    @Test
    fun degreesMinutesSeconds() {
        val (la, lo) = at("35°33'38\"N 82°59'46\"W")
        assertEquals(35.0 + 33 / 60.0 + 38 / 3600.0, la, 1e-9)
        assertEquals(-(82.0 + 59 / 60.0 + 46 / 3600.0), lo, 1e-9)
    }

    @Test
    fun wordsAndImpossibleNumbersAreNotCoordinates() {
        assertNull(PlaceSearch.parseCoordinates("Waynesville NC"))
        assertNull(PlaceSearch.parseCoordinates("123 Main St"))
        assertNull(PlaceSearch.parseCoordinates("95.0, 10.0"))
    }
}
