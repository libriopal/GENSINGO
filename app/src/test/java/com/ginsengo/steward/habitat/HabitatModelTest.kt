package com.ginsengo.steward.habitat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests over the REAL shipped graph, read from the same bytes the app loads.
 *
 * The interesting one is [negativeControl_inertFeaturesCannotMoveTheScore]. Read the
 * comment on it before changing anything here.
 */
class HabitatModelTest {

    private fun model(): LinearHabitatModel {
        val stream = javaClass.getResourceAsStream("/habitat_model.onnx")
        assertNotNull("habitat_model.onnx missing from test resources", stream)
        val init = OnnxGraphWeights.readInitializers(stream!!)
        val m = LinearHabitatModel.fromInitializers(init, "test")
        assertNotNull("could not build a model from the shipped graph", m)
        return m!!
    }

    private fun inputs(
        lat: Double = 37.5, lng: Double = -81.0, alt: Double = 600.0,
        canopy: Double = 0.5, slope: Double = 20.0, aspect: Double = 90.0,
        moisture: Double = 0.5,
    ) = listOf(
        FeatureInput(HabitatFeature.LATITUDE, lat, InputOrigin.MEASURED),
        FeatureInput(HabitatFeature.LONGITUDE, lng, InputOrigin.MEASURED),
        FeatureInput(HabitatFeature.ALTITUDE, alt, InputOrigin.MEASURED),
        FeatureInput(HabitatFeature.CANOPY_COVER, canopy, InputOrigin.FROM_YOUR_CHECKLIST),
        FeatureInput(HabitatFeature.SLOPE_ANGLE, slope, InputOrigin.MEASURED),
        FeatureInput(HabitatFeature.ASPECT, aspect, InputOrigin.MEASURED),
        FeatureInput(HabitatFeature.MOISTURE, moisture, InputOrigin.FROM_YOUR_CHECKLIST),
    )

    @Test
    fun parsesTheShippedWeights() {
        val m = model()
        // Decoded directly from the 235-byte graph's raw_data.
        assertEquals(0.0, m.weightOf(HabitatFeature.LATITUDE), 0.0)
        assertEquals(0.0, m.weightOf(HabitatFeature.LONGITUDE), 0.0)
        assertEquals(-0.0005, m.weightOf(HabitatFeature.ALTITUDE), 1e-7)
        assertEquals(1.2, m.weightOf(HabitatFeature.CANOPY_COVER), 1e-6)
        assertEquals(0.0, m.weightOf(HabitatFeature.SLOPE_ANGLE), 0.0)
        assertEquals(0.0, m.weightOf(HabitatFeature.ASPECT), 0.0)
        assertEquals(1.0, m.weightOf(HabitatFeature.MOISTURE), 1e-6)
    }

    /**
     * POSITIVE control. Establishes that this test can detect a score change at all -
     * without it, the negative control below would pass even if the model were a constant
     * and the whole test file would be decoration (EINCOL.md §4, "the vacuous control").
     */
    @Test
    fun positiveControl_activeFeaturesDoMoveTheScore() {
        val m = model()
        val low = m.analyse(inputs(canopy = 0.0)).score
        val high = m.analyse(inputs(canopy = 1.0)).score
        assertNotEquals("canopy cover must move the score", low, high)
        assertTrue("more canopy should raise suitability", high > low)

        val dry = m.analyse(inputs(moisture = 0.0)).score
        val wet = m.analyse(inputs(moisture = 1.0)).score
        assertTrue("more moisture should raise suitability", wet > dry)

        val lowGround = m.analyse(inputs(alt = 150.0)).score
        val highGround = m.analyse(inputs(alt = 1800.0)).score
        assertNotEquals("elevation must move the score", lowGround, highGround)
    }

    /**
     * NEGATIVE CONTROL - the finding this whole app had to be corrected around.
     *
     * PRD §8.1 specifies deriving slope and aspect from a bundled DEM by finite differences
     * and feeding them to the model, and PRD Workflow A is built around which way the hill
     * faces. The shipped graph weights both at exactly 0.0, so neither can change the
     * output by a single bit.
     *
     * This test asserts that inertness DIRECTLY: sweep slope across every angle a hillside
     * can have and aspect around the entire compass, and the score must not move at all.
     * It fails the moment someone swaps in a graph that does use them - which is precisely
     * when the UI copy claiming they are ignored would become a lie.
     *
     * It is paired with the positive control above so that "nothing moved" is evidence
     * about the model rather than evidence that the harness is inert.
     */
    @Test
    fun negativeControl_inertFeaturesCannotMoveTheScore() {
        val m = model()
        val baseline = m.analyse(inputs()).score

        for (slope in 0..60 step 5) {
            for (aspect in 0 until 360 step 15) {
                val s = m.analyse(inputs(slope = slope.toDouble(), aspect = aspect.toDouble())).score
                assertEquals(
                    "slope=$slope aspect=$aspect changed a score the graph weights at zero",
                    baseline, s, 0.0,
                )
            }
        }
        // Latitude and longitude are inert too: the model has no idea where it is.
        assertEquals(baseline, m.analyse(inputs(lat = 25.0, lng = -120.0)).score, 0.0)

        assertEquals(
            setOf(
                HabitatFeature.LATITUDE, HabitatFeature.LONGITUDE,
                HabitatFeature.SLOPE_ANGLE, HabitatFeature.ASPECT,
            ),
            m.analyse(inputs()).inertFeatures.map { it.feature }.toSet(),
        )
    }

    /**
     * The second half of the finding: of the movement that DOES happen, most is the
     * digger's own checklist answers coming back out. The UI states a percentage; this
     * pins it so the claim cannot silently drift.
     */
    @Test
    fun mostOfTheScoreComesFromTheUsersOwnAnswers() {
        val a = model().analyse(inputs())
        val share = a.shareFromYourAnswers
        assertTrue(
            "expected the checklist-derived share to dominate, got $share",
            share > 0.7,
        )
        assertTrue(share <= 1.0)
    }

    @Test
    fun scoreStaysInRange() {
        val m = model()
        val extremes = listOf(
            inputs(alt = -400.0, canopy = 1.0, moisture = 1.0),
            inputs(alt = 9000.0, canopy = 0.0, moisture = 0.0),
        )
        extremes.forEach {
            val s = m.analyse(it).score
            assertTrue("sigmoid must stay in (0,1), got $s", s > 0.0 && s < 1.0)
        }
    }

    @Test
    fun rejectsAGraphWithNoUsableWeights() {
        // A model built from nothing must refuse rather than invent a score.
        assertEquals(null, LinearHabitatModel.fromInitializers(emptyMap(), "empty"))
    }

    @Test
    fun contributionsSumToTheLogit() {
        val m = model()
        val a = m.analyse(inputs())
        val logit = a.contributions.sumOf { it.logitPush } + a.bias
        val expected = 1.0 / (1.0 + kotlin.math.exp(-logit))
        assertTrue(abs(expected - a.score) < 1e-12)
    }
}
