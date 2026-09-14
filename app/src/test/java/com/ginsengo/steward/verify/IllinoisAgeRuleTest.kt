package com.ginsengo.steward.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Illinois is the outlier, and the outlier is where a global default does harm.
 *
 * Eighteen jurisdictions require 5 years and 3 prongs, and the three-prong rule exists precisely
 * because three prongs is generally not reached before the fifth year - so counting leaves is
 * reasonable evidence of legal age there. Illinois requires TEN years and four prongs, and no
 * number of prongs demonstrates ten years: a four-prong plant can be six years old.
 *
 * So in Illinois the root neck is not corroboration, it is the only evidence there is, and the
 * app must not reach a permissive verdict without it. Before this, stem scars were optional
 * everywhere and an Illinois plant could pass on leaf count alone.
 */
class IllinoisAgeRuleTest {

    private fun input(
        minAge: Int,
        minProngs: Int,
        prongs: ProngCount,
        scars: Int?,
    ) = VerificationInput(
        prongs = prongs,
        berries = BerryState.RED_BERRIES,
        stemScars = scars,
        stateMinProngs = minProngs,
        stateMinAgeYears = minAge,
        stateName = if (minAge == 10) "Illinois" else "West Virginia",
        seasonOpen = true,
    )

    @Test
    fun prongCountCannotDemonstrateATenYearMinimum() {
        assertTrue(
            "three prongs is accepted as evidence of five years",
            PlantVerification.prongsCanDemonstrateAge(5),
        )
        assertFalse(
            "no prong count demonstrates ten years",
            PlantVerification.prongsCanDemonstrateAge(10),
        )
    }

    @Test
    fun illinoisWithoutScarsIsNotAPermissiveVerdict() {
        val r = PlantVerification.evaluate(input(10, 4, ProngCount.FOUR_PLUS, scars = null))
        assertNotNull(r)
        assertEquals(
            "four prongs in Illinois must not read as legal without the root neck",
            Verdict.CHECK_STATE, r!!.verdict,
        )
        assertFalse(r.isHarvestable)
        assertTrue(
            "the app must say what to do next, not merely refuse",
            r.reasons.any { it.contains("scars", ignoreCase = true) },
        )
    }

    @Test
    fun illinoisWithNineScarsMeetsTheTenYearRule() {
        val r = PlantVerification.evaluate(input(10, 4, ProngCount.FOUR_PLUS, scars = 9))
        assertNotNull(r)
        assertEquals(Verdict.LEGAL, r!!.verdict)
    }

    @Test
    fun illinoisWithFourScarsIsStillTooYoung() {
        // 4 scars is five years - legal in eighteen states, four years short in Illinois.
        val r = PlantVerification.evaluate(input(10, 4, ProngCount.FOUR_PLUS, scars = 4))
        assertEquals(Verdict.TOO_YOUNG, r!!.verdict)
    }

    /**
     * Negative control. The five-year states must NOT have been made stricter by this change, or
     * the fix has broken the common case in order to protect the rare one.
     */
    @Test
    fun theFiveYearStatesStillReachAVerdictWithoutTheNeck() {
        val r = PlantVerification.evaluate(input(5, 3, ProngCount.THREE, scars = null))
        assertNotNull(r)
        assertEquals(
            "a three-prong plant in a five-year state must still resolve without scars",
            Verdict.LEGAL, r!!.verdict,
        )
    }

    /**
     * Scars give a floor, never an estimate: a dormant season adds no scar, and old scars can be
     * lost on a weathered neck. The error runs safe - the plant reads younger than it is - which
     * is exactly why the number must not be presented as "about".
     */
    @Test
    fun scarCountIsReportedAsAMinimumAge() {
        assertEquals(5, PlantVerification.minimumAgeFromScars(4))
        assertEquals(10, PlantVerification.minimumAgeFromScars(9))
        val r = PlantVerification.evaluate(input(5, 3, ProngCount.THREE, scars = 6))
        assertTrue(
            "the reading must be phrased as a floor, got: ${r!!.reasons}",
            r.reasons.any { it.contains("AT LEAST") },
        )
    }
}
