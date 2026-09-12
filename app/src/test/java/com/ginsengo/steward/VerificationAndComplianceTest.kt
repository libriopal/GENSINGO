package com.ginsengo.steward

import com.ginsengo.steward.compliance.ComplianceEngine
import com.ginsengo.steward.compliance.SeasonStatus
import com.ginsengo.steward.data.reference.StateRegulation
import com.ginsengo.steward.geo.PointInPolygon
import com.ginsengo.steward.geo.Poly
import com.ginsengo.steward.geo.Ring
import com.ginsengo.steward.verify.BerryState
import com.ginsengo.steward.verify.PlantVerification
import com.ginsengo.steward.verify.ProngCount
import com.ginsengo.steward.verify.Verdict
import com.ginsengo.steward.verify.VerificationInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlantVerificationTest {

    private fun input(
        prongs: ProngCount?, berries: BerryState?, scars: Int? = null,
        minProngs: Int = 3, minAge: Int = 5, seasonOpen: Boolean? = true,
    ) = VerificationInput(prongs, berries, scars, minProngs, minAge, "Test State", seasonOpen)

    @Test
    fun incompleteInputGivesNoVerdict() {
        assertNull(PlantVerification.evaluate(input(null, BerryState.RED_BERRIES)))
        assertNull(PlantVerification.evaluate(input(ProngCount.THREE, null)))
    }

    @Test
    fun noBerriesAlwaysBlocksHarvest() {
        // Even a big, clearly mature plant: no seed this year means leave it.
        val r = PlantVerification.evaluate(input(ProngCount.FOUR_PLUS, BerryState.NO_BERRIES))!!
        assertEquals(Verdict.DO_NOT_HARVEST, r.verdict)
        assertFalse(r.isHarvestable)
    }

    @Test
    fun tooFewProngsIsTooYoung() {
        listOf(ProngCount.ONE, ProngCount.TWO).forEach {
            val r = PlantVerification.evaluate(input(it, BerryState.RED_BERRIES))!!
            assertEquals(Verdict.TOO_YOUNG, r.verdict)
        }
    }

    @Test
    fun illinoisRejectsWhatEveryOtherStateAllows() {
        // The single most consequential per-state difference in the dataset.
        val elsewhere = PlantVerification.evaluate(
            input(ProngCount.THREE, BerryState.RED_BERRIES, minProngs = 3, minAge = 5)
        )!!
        assertEquals(Verdict.LEGAL, elsewhere.verdict)

        val illinois = PlantVerification.evaluate(
            input(ProngCount.THREE, BerryState.RED_BERRIES, minProngs = 4, minAge = 10)
        )!!
        assertEquals(Verdict.TOO_YOUNG, illinois.verdict)
    }

    @Test
    fun stemScarsCanOverrideAnApparentlyMaturePlant() {
        // 3 prongs but only 2 scars => about 3 years old => under the 5-year minimum.
        val r = PlantVerification.evaluate(
            input(ProngCount.THREE, BerryState.RED_BERRIES, scars = 2)
        )!!
        assertEquals(Verdict.TOO_YOUNG, r.verdict)
    }

    @Test
    fun greenBerriesMeanWait() {
        val r = PlantVerification.evaluate(input(ProngCount.FOUR_PLUS, BerryState.GREEN_BERRIES))!!
        assertEquals(Verdict.CHECK_STATE, r.verdict)
        assertFalse(r.isHarvestable)
    }

    @Test
    fun closedSeasonBlocksAnOtherwiseLegalPlant() {
        val r = PlantVerification.evaluate(
            input(ProngCount.FOUR_PLUS, BerryState.RED_BERRIES, seasonOpen = false)
        )!!
        assertEquals(Verdict.CHECK_STATE, r.verdict)
    }

    @Test
    fun ageFromScars() {
        assertEquals(5, PlantVerification.ageFromScars(4))
        assertEquals(10, PlantVerification.ageFromScars(9))
    }

    @Test
    fun stewardshipRemindersShowEvenWhenHarvestIsRefused() {
        val r = PlantVerification.evaluate(input(ProngCount.ONE, BerryState.RED_BERRIES))!!
        assertTrue(r.stewardshipReminders.isNotEmpty())
    }
}

class SeasonTest {

    private fun state(
        start: String? = "Sept 1", end: String? = null, endVerified: Boolean = false,
    ) = StateRegulation(
        stateCode = "XX", stateName = "Test", seasonStart = start,
        seasonStartVerified = true, seasonEnd = end, seasonEndVerified = endVerified,
        agency = "Test Agency",
    )

    @Test
    fun parsesMonthDay() {
        assertEquals(java.time.MonthDay.of(9, 1), ComplianceEngine.parseMonthDay("Sept 1"))
        assertEquals(java.time.MonthDay.of(11, 30), ComplianceEngine.parseMonthDay("Nov 30"))
        assertEquals(java.time.MonthDay.of(12, 31), ComplianceEngine.parseMonthDay("Dec 31"))
        assertEquals(java.time.MonthDay.of(9, 1), ComplianceEngine.parseMonthDay("September"))
        assertNull(ComplianceEngine.parseMonthDay(null))
        assertNull(ComplianceEngine.parseMonthDay("not a month"))
    }

    @Test
    fun verifiedWindowOpensAndCloses() {
        val s = state(end = "Nov 30", endVerified = true)
        assertEquals(SeasonStatus.OPEN, ComplianceEngine.seasonOf(s, LocalDate.of(2026, 10, 1)).first)
        assertEquals(SeasonStatus.CLOSED, ComplianceEngine.seasonOf(s, LocalDate.of(2026, 8, 31)).first)
        assertEquals(SeasonStatus.CLOSED, ComplianceEngine.seasonOf(s, LocalDate.of(2026, 12, 1)).first)
    }

    /**
     * The honesty case. FWS publishes no closing date for most states, so an unverified end
     * must never render as a confident date - the message has to send the digger to the
     * state agency instead.
     *
     * This test used to assert OPEN here, and was wrong in a way worth leaving on the record:
     * it checked that the MESSAGE was honest while pinning the STATUS as confident permission.
     * Half the defect was caught and the other half was blessed by the same test. The status is
     * UNKNOWN - see UnknownSeasonIsNotPermissionTest for why a false OPEN and a false CLOSED
     * are not comparable errors.
     */
    @Test
    fun unverifiedEndDateNeverAssertsAClosingDate() {
        val s = state(end = null, endVerified = false)
        val (status, detail) = ComplianceEngine.seasonOf(s, LocalDate.of(2026, 10, 1))
        assertEquals(SeasonStatus.UNKNOWN, status)
        assertTrue("must point at the agency, got: $detail", detail.contains("Test Agency"))
        assertTrue(detail.contains("not", ignoreCase = true))
        assertEquals("confirm with Test Agency", s.seasonEndDisplay)
    }

    /**
     * An end date present in the data but NOT verified must still be withheld: the flag
     * governs, not the presence of a string.
     */
    @Test
    fun presentButUnverifiedEndDateIsStillWithheld() {
        val s = state(end = "Nov 30", endVerified = false)
        assertEquals("confirm with Test Agency", s.seasonEndDisplay)
        val (_, detail) = ComplianceEngine.seasonOf(s, LocalDate.of(2026, 12, 15))
        assertFalse("must not quote an unverified closing date", detail.contains("Nov 30"))
    }

    @Test
    fun noStateIsUnknownNotOpen() {
        assertEquals(SeasonStatus.UNKNOWN, ComplianceEngine.seasonOf(null, LocalDate.of(2026, 10, 1)).first)
    }
}

class PointInPolygonTest {

    // A unit square from (0,0) to (10,10), in GeoJSON (lng, lat) order.
    private val square = Poly(
        Ring(doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0, 0.0, 0.0))
    )

    @Test
    fun insideIsInside() {
        assertTrue(PointInPolygon.inPoly(square, 5.0, 5.0))
        assertTrue(PointInPolygon.inPoly(square, 0.1, 0.1))
    }

    /** Negative control: points outside must be rejected, including just outside. */
    @Test
    fun outsideIsOutside() {
        assertFalse(PointInPolygon.inPoly(square, 15.0, 5.0))
        assertFalse(PointInPolygon.inPoly(square, 5.0, -0.5))
        assertFalse(PointInPolygon.inPoly(square, -0.1, 5.0))
        assertFalse(PointInPolygon.inPoly(square, 10.5, 10.5))
    }

    /**
     * A point level with a horizontal edge. US state borders are full of these, and a
     * double-counted vertex here reports a digger as being in the wrong state.
     */
    @Test
    fun pointsOnHorizontalGridLinesAreNotDoubleCounted() {
        assertTrue(PointInPolygon.inPoly(square, 0.0 + 1e-9, 5.0))
        assertFalse(PointInPolygon.inPoly(square, 10.0 + 1e-9, 5.0))
    }

    @Test
    fun holesAreExcluded() {
        val withHole = Poly(
            outer = square.outer,
            holes = listOf(Ring(doubleArrayOf(4.0, 4.0, 6.0, 4.0, 6.0, 6.0, 4.0, 6.0, 4.0, 4.0))),
        )
        assertTrue(PointInPolygon.inPoly(withHole, 2.0, 2.0))
        assertFalse("a point in the hole is not in the polygon", PointInPolygon.inPoly(withHole, 5.0, 5.0))
    }
}
