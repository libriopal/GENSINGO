package com.ginsengo.steward.field

import com.ginsengo.steward.prospect.Prospects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** N.1: walking guidance to a chosen point. */
class GuidanceTest {

    @Test
    fun theClockReadsRelativeToWhereThePhoneFaces() {
        assertEquals(12, Guidance.clock(90.0, 90.0))
        assertEquals(3, Guidance.clock(180.0, 90.0))
        assertEquals(9, Guidance.clock(0.0, 90.0))
        assertEquals(6, Guidance.clock(270.0, 90.0))
        assertEquals(12, Guidance.clock(5.0, 355.0))      // across north, not through south
        assertEquals(1, Guidance.clock(25.0, 355.0))
    }

    @Test
    fun arrivalIsWithinTwelveMetresOrTheFixesOwnAccuracy() {
        assertTrue(Guidance.describe(Guidance.Leg(11.0, 0.0), null, 5f).startsWith("Arrived"))
        assertTrue(Guidance.describe(Guidance.Leg(25.0, 0.0), null, 30f).startsWith("Arrived"))
        assertEquals("40 m N", Guidance.describe(Guidance.Leg(40.0, 0.0), null, 5f))
        assertEquals("40 m N · at 3 o'clock", Guidance.describe(Guidance.Leg(40.0, 0.0), 270f, 5f))
    }

    @Test
    fun theHeadingTipIsWhereItShouldBe() {
        val (la, lo) = Guidance.destination(35.56, -82.99, 45.0, 60.0)
        assertEquals(60.0, Prospects.distanceMetres(35.56, -82.99, la, lo), 0.5)
        assertEquals(45.0, Prospects.bearingTrue(35.56, -82.99, la, lo), 0.5)
    }
}
