package com.ginsengo.steward.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one feature whose correctness nobody will be checking at the moment it is needed.
 */
class EmergencyPositionTest {

    // Boone, North Carolina — the terrain fixture's own ground.
    private val lat = 36.2168
    private val lng = -81.6746

    @Test
    fun decimalDegreesKeepsSixPlacesAndTheSign() {
        assertEquals("36.216800, -81.674600", EmergencyPosition.decimalDegrees(lat, lng))
    }

    /**
     * 36.2168 deg = 36 deg + 0.2168*60 = 13.008 min.
     * 81.6746 deg = 81 deg + 0.6746*60 = 40.476 min, west, padded to three digits.
     */
    @Test
    fun degreesDecimalMinutesMatchesWhatDispatchExpects() {
        assertEquals(
            "N 36° 13.008'  W 081° 40.476'",
            EmergencyPosition.degreesDecimalMinutes(lat, lng),
        )
    }

    @Test
    fun southernAndEasternHemispheresAreNotMisreported() {
        val s = EmergencyPosition.degreesDecimalMinutes(-33.8688, 151.2093)
        assertTrue("expected S and E, got $s", s.startsWith("S 33") && s.contains("E 151"))
    }

    /**
     * The rounding boundary. 59.9999 minutes must roll into the next degree rather than print
     * as "60.000'", which is not a coordinate and would be read aloud as one.
     */
    @Test
    fun minutesNeverPrintAsSixty() {
        val text = EmergencyPosition.degreesDecimalMinutes(35.99999999, -82.99999999)
        assertFalse("minutes printed as 60: $text", text.contains("60.000'"))
        assertTrue("should have rolled to the next degree, got $text", text.contains("36°"))
        assertTrue(text.contains("083°"))
    }

    @Test
    fun longitudeIsAlwaysThreeDigits() {
        // "W 82" and "W 082" are read differently over a bad radio link.
        assertTrue(EmergencyPosition.degreesDecimalMinutes(35.0, -82.0).contains("W 082°"))
        assertTrue(EmergencyPosition.degreesDecimalMinutes(35.0, -9.5).contains("W 009°"))
    }

    // ------------------------------------------------------------------ freshness

    @Test
    fun aFreshFixIsNotStaleAndAnOldOneIs() {
        val now = 1_000_000_000L
        assertFalse(EmergencyPosition.isStale(now - 30_000, now))
        assertTrue(EmergencyPosition.isStale(now - 10 * 60_000, now))
    }

    @Test
    fun fixAgeReadsAsSomethingAPersonWouldSay() {
        val now = 1_000_000_000L
        assertEquals("just now", EmergencyPosition.fixAge(now - 2_000, now))
        assertEquals("45 sec old", EmergencyPosition.fixAge(now - 45_000, now))
        assertEquals("4 min old", EmergencyPosition.fixAge(now - 4 * 60_000, now))
        assertEquals("2 hr old", EmergencyPosition.fixAge(now - 2 * 3_600_000, now))
    }

    @Test
    fun aFutureTimestampDoesNotProduceNegativeAge() {
        val now = 1_000_000_000L
        assertEquals("just now", EmergencyPosition.fixAge(now + 5_000, now))
    }

    // ------------------------------------------------------------------ the message

    @Test
    fun theMessageLeadsWithPositionInBothFormats() {
        val now = 1_000_000_000L
        val m = EmergencyPosition.emergencyMessage(
            lat, lng, accuracyM = 8f, altitudeM = 1015.0,
            fixTimeMs = now - 20_000, nowMs = now,
        )
        assertTrue(m.startsWith("HELP."))
        assertTrue("decimal degrees missing", m.contains("36.216800, -81.674600"))
        assertTrue("deg/dec-min missing", m.contains("N 36° 13.008'"))
        assertTrue(m.contains("+/- 8 m"))
        assertTrue(m.contains("Elevation: 1015 m"))
        assertFalse("a fresh fix must not be flagged old", m.contains("WARNING"))
    }

    /** The case that matters: an old fix must say so inside the message that gets sent. */
    @Test
    fun aStaleFixCarriesItsWarningIntoTheSentMessage() {
        val now = 1_000_000_000L
        val m = EmergencyPosition.emergencyMessage(
            lat, lng, accuracyM = 12f, altitudeM = null,
            fixTimeMs = now - 25 * 60_000, nowMs = now,
        )
        assertTrue("a 25-minute-old fix must be flagged in the message", m.contains("WARNING"))
        assertTrue(m.contains("may have moved"))
    }

    @Test
    fun aPoorFixIsFlaggedToo() {
        val now = 1_000_000_000L
        val m = EmergencyPosition.emergencyMessage(
            lat, lng, accuracyM = 140f, altitudeM = null, fixTimeMs = now, nowMs = now,
        )
        assertTrue("a 140 m fix must be marked poor", m.contains("(POOR)"))
    }

    /**
     * Negative control. If the message were assembled from constants rather than from the
     * position it was given, every test above would pass on the wrong data.
     */
    @Test
    fun theMessageActuallyReflectsTheCoordinatesPassedIn() {
        val now = 1_000_000_000L
        fun msg(la: Double, ln: Double) = EmergencyPosition.emergencyMessage(
            la, ln, accuracyM = 5f, altitudeM = null, fixTimeMs = now, nowMs = now,
        )
        val a = msg(36.2168, -81.6746)
        val b = msg(35.5950, -82.5515)
        assertFalse("two different positions produced the same message", a == b)
        assertTrue(b.contains("35.595000, -82.551500"))
        assertFalse("the second message still carries the first position", b.contains("36.216800"))
    }

    @Test
    fun anOptionalNoteIsIncludedAndBlankNotesAreNot() {
        val now = 1_000_000_000L
        fun msg(note: String?) = EmergencyPosition.emergencyMessage(
            lat, lng, 5f, null, now, now, note,
        )
        assertTrue(msg("Leg broken, cannot walk out").contains("Leg broken, cannot walk out"))
        assertFalse(msg("   ").contains("\n\n\n\n"))
    }

    @Test
    fun theMapLinkIsWellFormed() {
        val now = 1_000_000_000L
        val m = EmergencyPosition.emergencyMessage(lat, lng, 5f, null, now, now)
        assertTrue(m.contains("https://www.google.com/maps?q=36.2168,-81.6746"))
    }
}
