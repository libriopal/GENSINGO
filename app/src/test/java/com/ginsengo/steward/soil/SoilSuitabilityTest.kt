package com.ginsengo.steward.soil

import com.ginsengo.steward.soil.SoilSuitability.Landform
import com.ginsengo.steward.soil.SoilSuitability.SoilReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The soil scorer, tested against readings actually returned by the USDA Soil Data Access API for
 * real North Carolina points, not against invented rows.
 *
 * Every fixture below was retrieved from SSURGO during development:
 *
 *   Pisgah NF (35.29, -82.78) ...... Ashe, somewhat excessively drained, pH 4.8, OM 4.5
 *   Nantahala NF (35.12, -83.45) ... Evard, well drained, pH 5.3, OM 4.0
 *   Roan area (36.09, -82.12) ...... Tanasee, well drained, pH 4.6, OM 15
 *   downtown Boone (36.2168, -81.6746) ... Urban land, 90%, no horizon data at all
 *
 * That last one is in here deliberately. It was the first point queried during development and it
 * returned nothing, which looked like a broken pipeline and was actually a parking lot. A tool
 * that reads soil has to say "no record here" rather than score a car park.
 */
class SoilSuitabilityTest {

    // ------------------------------------------------------------------ landform vocabulary

    /**
     * The survey's vocabulary is closed, and these are the literal strings measured across 19
     * western NC survey areas. If a mapping silently fails the term falls to UNKNOWN and scores
     * mid-range, which would quietly neutralise the strongest evidence this file has.
     */
    @Test
    fun theMeasuredSurveyVocabularyAllMapsToSomethingSpecific() {
        val measured = mapOf(
            "coves" to Landform.COVE,
            "fans" to Landform.COLLUVIAL,
            "benches" to Landform.COLLUVIAL,
            "toes" to Landform.COLLUVIAL,
            "drainageways" to Landform.DRAINAGEWAY,
            "mountain slopes" to Landform.SLOPE,
            "hillslopes" to Landform.SLOPE,
            "hillsides" to Landform.SLOPE,
            "ridges" to Landform.RIDGE,
            "interfluves" to Landform.RIDGE,
            "flood plains" to Landform.BOTTOMLAND,
            "stream terraces" to Landform.BOTTOMLAND,
            "depressions" to Landform.WET,
            "bogs" to Landform.WET,
        )
        measured.forEach { (surveyName, expected) ->
            assertEquals(
                "'$surveyName' is a real value in the NC survey and must not fall through to UNKNOWN",
                expected, Landform.of(surveyName),
            )
        }
    }

    @Test
    fun landformMatchingIsCaseAndWhitespaceTolerant() {
        assertEquals(Landform.COVE, Landform.of("  Coves "))
        assertEquals(Landform.RIDGE, Landform.of("RIDGES"))
    }

    /** Negative control: an unmapped term must NOT quietly become a cove. */
    @Test
    fun anUnknownLandformIsUnknownAndNotFavourable() {
        assertEquals(Landform.UNKNOWN, Landform.of(null))
        assertEquals("a nonsense term must not resolve", Landform.UNKNOWN, Landform.of("xyzzy"))
        assertTrue(
            "unknown must score below a cove, or absence of evidence reads as good ground",
            Landform.UNKNOWN.score < Landform.COVE.score,
        )
    }

    /**
     * THE TRAP, and the reason this file has a keyword guard at all.
     *
     * The statewide landform vocabulary contains "mainland coves" and "barrier coves" - both
     * COASTAL - alongside the mountain "coves" that ginseng actually grows in. A matcher that
     * looked for the word "cove" would send a digger to a barrier island. Ginseng is a mountain
     * plant; the guard has to beat the noun.
     */
    @Test
    fun coastalCovesAreNotGinsengCoves() {
        assertEquals(Landform.COVE, Landform.of("coves"))
        listOf("mainland coves", "barrier coves", "barrier flats", "tidal marshes",
               "marine terraces", "dunes", "pocosins").forEach {
            assertTrue(
                "'$it' is coastal and must never score as a mountain cove, got ${Landform.of(it)}",
                Landform.of(it).score <= Landform.BOTTOMLAND.score,
            )
        }
    }

    /**
     * The vocabulary is not closed - "mountainsides" was returned from the field by an area
     * outside the first sample and fell through to UNKNOWN. Unseen terms must land somewhere
     * sensible, and the fallback must never promote anything TO a cove.
     */
    @Test
    fun unseenTermsFallBackSensiblyAndNeverToCove() {
        assertEquals(Landform.SLOPE, Landform.of("mountainsides"))
        assertEquals(Landform.SLOPE, Landform.of("steep mountain sideslopes"))
        assertEquals(Landform.RIDGE, Landform.of("narrow ridgetops"))
        assertEquals(Landform.WET, Landform.of("wooded swamps"))
        // The conservative half: nothing unseen may be promoted to COVE.
        listOf("mountainsides", "narrow ridgetops", "wooded swamps", "alluvial fans",
               "xyzzy").forEach {
            assertTrue(
                "the fallback must never invent a cove, but '$it' became one",
                Landform.of(it) != Landform.COVE,
            )
        }
    }

    @Test
    fun coveOutranksSlopeOutranksRidge() {
        assertTrue(Landform.COVE.score > Landform.COLLUVIAL.score)
        assertTrue(Landform.COLLUVIAL.score > Landform.SLOPE.score)
        assertTrue(Landform.SLOPE.score > Landform.RIDGE.score)
        assertTrue(Landform.RIDGE.score > Landform.WET.score)
    }

    // ------------------------------------------------------------------ the non-monotonic case

    /**
     * The finding that makes this worth shipping. Alarka and Sylva are mapped as COVES and are
     * POORLY DRAINED - both facts from the survey. A scorer that reads "cove" and stops would
     * send a digger to waterlogged bottomland.
     */
    @Test
    fun aPoorlyDrainedCoveScoresWellBelowAWellDrainedOne() {
        val saunook = SoilSuitability.evaluate(
            SoilReading("Saunook", "coves", "Well drained", 5.2, 6.0)
        )
        val alarka = SoilSuitability.evaluate(
            SoilReading("Alarka", "coves", "Poorly drained", 5.0, 8.0)
        )
        assertEquals(Landform.COVE, saunook.landform)
        assertEquals(Landform.COVE, alarka.landform)
        assertTrue(
            "a poorly drained cove must not score like a well drained one " +
                "(${alarka.score} vs ${saunook.score})",
            saunook.score - alarka.score > 0.25,
        )
        assertTrue(
            "the digger must be told WHY the cove is wrong",
            alarka.reasons.any { it.contains("POORLY DRAINED") },
        )
    }

    @Test
    fun drainageIsPenalisedAtBothEndsForDifferentReasons() {
        val well = SoilSuitability.drainageScore("Well drained")
        val wet = SoilSuitability.drainageScore("Poorly drained")
        val dry = SoilSuitability.drainageScore("Excessively drained")
        assertTrue(well > wet && well > dry)
        assertTrue("both extremes must be penalised, not just the wet end", dry < 0.5 && wet < 0.5)
    }

    // ------------------------------------------------------------------ real retrieved fixtures

    @Test
    fun realNantahalaGroundOutscoresRealPisgahRidgeGround() {
        // Evard, well drained (Nantahala) vs Ashe, somewhat excessively drained (Pisgah).
        val evard = SoilSuitability.evaluate(
            SoilReading("Evard", "mountain slopes", "Well drained", 5.3, 4.0)
        )
        val ashe = SoilSuitability.evaluate(
            SoilReading("Ashe", "ridges", "Somewhat excessively drained", 4.8, 4.5)
        )
        assertTrue(
            "well drained mountain slope must beat a somewhat excessively drained ridge " +
                "(${evard.score} vs ${ashe.score})",
            evard.score > ashe.score,
        )
    }

    /**
     * pH 4.6-5.3 is what real NC mountain forest actually measures. The cultivation literature
     * quotes 5.5-6.0 as a planting target, and scoring against that would mark genuinely good
     * ground as wrong. This pins the decision.
     */
    @Test
    fun realAppalachianForestPhIsNotPenalised() {
        listOf(4.6, 4.8, 5.0, 5.3).forEach {
            assertEquals(
                "pH $it is normal on real NC mountain forest and must score full marks",
                1.0, SoilSuitability.phScore(it), 1e-9,
            )
        }
        assertTrue("strongly acid ground is still penalised", SoilSuitability.phScore(3.8) < 0.3)
    }

    @Test
    fun deepDuffBeatsThinEroded() {
        assertTrue(
            SoilSuitability.organicMatterScore(15.0) > SoilSuitability.organicMatterScore(0.8)
        )
        assertEquals(1.0, SoilSuitability.organicMatterScore(15.0), 1e-9)
    }

    // ------------------------------------------------------------------ absence of evidence

    /**
     * Downtown Boone. The survey says Urban land, 90 percent, with no horizon data. The tool must
     * report that it has nothing rather than score it.
     */
    @Test
    fun aCarParkReportsNoEvidenceRatherThanAScore() {
        val v = SoilSuitability.evaluate(SoilReading("Urban land", null, null, null, null))
        assertFalse("no landform and no drainage means no evidence", v.hasEvidence)
        assertTrue(
            "the digger must be told the survey has nothing here",
            v.reasons.any { it.contains("no usable record") },
        )
        assertTrue("and it must not read as good ground", v.score < 0.6)
    }

    /**
     * Negative control for the whole scorer: if evaluate() ignored its inputs and returned a
     * constant, every test above could still pass. Prove the output actually moves.
     */
    @Test
    fun theScorerActuallyReadsItsInputs() {
        val best = SoilSuitability.evaluate(
            SoilReading("Saunook", "coves", "Well drained", 5.2, 8.0)
        ).score
        val worst = SoilSuitability.evaluate(
            SoilReading("Sylva", "bogs", "Very poorly drained", 3.5, 0.5)
        ).score
        assertTrue(
            "best and worst ground must differ substantially, got $best vs $worst",
            best - worst > 0.6,
        )
        assertTrue("best must be genuinely high", best > 0.85)
        assertTrue("worst must be genuinely low", worst < 0.25)
    }

    @Test
    fun weightsSumToOne() {
        val sum = SoilSuitability.W_LANDFORM + SoilSuitability.W_DRAINAGE +
                SoilSuitability.W_PH + SoilSuitability.W_ORGANIC
        assertEquals(1.0, sum, 1e-9)
    }
}
