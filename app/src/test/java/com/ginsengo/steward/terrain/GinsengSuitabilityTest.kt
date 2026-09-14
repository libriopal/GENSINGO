package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The habitat forecast's behavioural contract.
 *
 * The important test here is [negativeControl_wetnessIsNotMonotonic]. Read its comment
 * before changing any band in [GinsengSuitability].
 */
class GinsengSuitabilityTest {

    private val lat = 37.5

    /** A site described the way the extension literature describes good ginseng ground. */
    private fun textbookSite(
        aspect: Double = 45.0,      // north-east
        slope: Double = 14.0,       // inside the productive band
        tpi: Double = -4.0,         // lower third of the slope
        twi: Double = 8.0,          // moist but drained
        curvature: Double = 0.03,   // a cove
        elevation: Double = 700.0,
    ) = GinsengSuitability.score(
        heatLoadRaw = TerrainMath.heatLoadIndex(lat, slope, aspect),
        tpiMeters = tpi, twi = twi, slopeDeg = slope,
        curvature = curvature, elevationM = elevation,
    )

    @Test
    fun textbookSiteScoresWellAndBakedRidgeDoesNot() {
        val good = textbookSite().score
        val bad = GinsengSuitability.score(
            heatLoadRaw = TerrainMath.heatLoadIndex(lat, 34.0, 225.0), // steep, SW-facing
            tpiMeters = 9.0,          // ridge top
            twi = 3.5,                // dry
            slopeDeg = 34.0,
            curvature = -0.04,        // convex nose
            elevationM = 700.0,
        ).score
        assertTrue("textbook cove ($good) must beat a baked ridge ($bad)", good > bad)
        assertTrue("textbook cove should score strongly, got $good", good > 0.7)
        assertTrue("baked ridge should score poorly, got $bad", bad < 0.35)
    }

    @Test
    fun aspectAloneFlipsTheVerdict() {
        val ne = textbookSite(aspect = 45.0).score
        val sw = textbookSite(aspect = 225.0).score
        assertTrue("NE ($ne) must beat SW ($sw) with all else identical", ne > sw)
    }

    /**
     * NEGATIVE CONTROL — the finding this heatmap exists to avoid.
     *
     * The obvious ginseng heatmap is monotonic: lower on the slope is wetter is better.
     * Built that way, the brightest pixels on the map land in the creek bottoms, because
     * that is where wetness and slope position both max out.
     *
     * That is precisely where ginseng does not grow. Virginia Cooperative Extension,
     * *Growing American Ginseng in Forestlands*: ginseng "will not grow in waterlogged
     * soil, compacted areas (such as old roadbeds), leaf-filled depressions, rocky
     * outcrops, water flows, or heavy clay soils", and "flat sites with poor drainage or a
     * history of flooding will not support ginseng growth."
     *
     * So this asserts the surface TURNS OVER: a saturated, flat valley floor must score
     * BELOW a moist mid-lower slope, even though it is wetter and lower. A monotonic model
     * cannot pass this test — which is what makes it a control rather than a restatement.
     */
    @Test
    fun negativeControl_wetnessIsNotMonotonic() {
        val moistLowerSlope = textbookSite(twi = 8.0, tpi = -4.0, slope = 12.0).score
        val saturatedBottom = GinsengSuitability.score(
            heatLoadRaw = TerrainMath.heatLoadIndex(lat, 1.0, 45.0),
            tpiMeters = -22.0,   // valley floor
            twi = 15.0,          // saturated
            slopeDeg = 1.0,      // flat, drains nowhere
            curvature = 0.05,
            elevationM = 700.0,
        ).score

        assertTrue(
            "a saturated valley floor ($saturatedBottom) must NOT outscore a moist " +
                    "lower slope ($moistLowerSlope) — a monotonic model would invert this",
            moistLowerSlope > saturatedBottom,
        )

        // And demonstrate the same turnover across a wetness sweep with everything else held.
        val sweep = listOf(2.0, 5.0, 8.0, 11.0, 14.0, 17.0).map { w ->
            w to textbookSite(twi = w).score
        }
        val peak = sweep.maxBy { it.second }
        assertTrue(
            "wetness response must peak in the middle, peaked at TWI=${peak.first}",
            peak.first in 5.0..11.0,
        )
        assertTrue("must fall off at the dry end", sweep.first().second < peak.second)
        assertTrue("must fall off at the saturated end", sweep.last().second < peak.second)
    }

    /** Slope steepness turns over for the same reason: erosion above, poor drainage below. */
    @Test
    fun negativeControl_slopeIsNotMonotonic() {
        val sweep = listOf(0.5, 4.0, 12.0, 20.0, 32.0, 45.0).map { s ->
            s to textbookSite(slope = s).score
        }
        val peak = sweep.maxBy { it.second }
        assertTrue("slope response must peak mid-range, peaked at $peak", peak.first in 4.0..20.0)
        assertTrue("near-flat must not win", sweep.first().second < peak.second)
        assertTrue("very steep must not win", sweep.last().second < peak.second)
    }

    /**
     * POSITIVE control. Without this, the two negative controls above would still pass on a
     * model that returned a constant, and the whole file would be decoration.
     */
    @Test
    fun positiveControl_theModelActuallyRespondsToItsInputs() {
        val base = textbookSite().score
        assertTrue(base > 0.0 && base < 1.0)
        val factors = listOf(
            textbookSite(aspect = 225.0).score,
            textbookSite(tpi = 12.0).score,
            textbookSite(twi = 2.0).score,
            textbookSite(slope = 44.0).score,
            textbookSite(curvature = -0.05).score,
            textbookSite(elevation = 2400.0).score,
        )
        factors.forEachIndexed { i, v ->
            assertTrue("factor $i must move the score away from $base (got $v)", v != base)
        }
    }

    @Test
    fun weightsSumToOneAndScoreStaysBounded() {
        val total = GinsengSuitability.Factor.entries.sumOf { it.weight }
        assertEquals(1.0, total, 1e-9)
        val extremes = listOf(
            textbookSite(aspect = 45.0, slope = 14.0, tpi = -4.0, twi = 8.0, curvature = 1.0),
            textbookSite(aspect = 225.0, slope = 60.0, tpi = 50.0, twi = 25.0, curvature = -1.0),
        )
        extremes.forEach {
            assertTrue("score out of range: ${it.score}", it.score in 0.0..1.0)
        }
    }

    @Test
    fun breakdownExplainsTheScore() {
        val b = textbookSite()
        val recomputed = b.factors.entries.sumOf { it.value * it.key.weight }
        assertEquals(b.score, recomputed, 1e-9)
        assertEquals(GinsengSuitability.Factor.entries.size, b.factors.size)
    }

    @Test
    fun labelsTrackTheScore() {
        assertEquals("Worth walking", GinsengSuitability.label(0.8))
        assertEquals("Possible", GinsengSuitability.label(0.6))
        assertEquals("Marginal", GinsengSuitability.label(0.4))
        assertEquals("Unlikely", GinsengSuitability.label(0.1))
    }
}

class AdaptiveScopeTest {

    @Test
    fun demZoomRisesWithCameraZoomButIsCappedAtWhatTheBucketServes() {
        assertEquals(15, DemTileStore.demZoomFor(17.0))
        assertEquals(15, DemTileStore.demZoomFor(15.0))
        assertEquals(12, DemTileStore.demZoomFor(12.4))
        assertEquals(8, DemTileStore.demZoomFor(3.0))
    }

    /**
     * The slope-position radius is specified in METRES and must shrink as the camera zooms
     * in. If it were fixed in cells, "position on the slope" would silently mean a
     * different physical distance at every zoom level and the heatmap would change meaning
     * under the user's fingers.
     */
    @Test
    fun tpiRadiusIsInMetresAndTightensAsYouZoomIn() {
        val far = DemTileStore.tpiRadiusMetresFor(9.0)
        val mid = DemTileStore.tpiRadiusMetresFor(12.0)
        val near = DemTileStore.tpiRadiusMetresFor(16.0)
        assertTrue("$far > $mid > $near", far > mid && mid > near)
        assertTrue("field-scale radius should be on the order of a hillside", near <= 200.0)
    }

    @Test
    fun tileMathRoundTrips() {
        val z = 14
        val lat = 36.2; val lon = -81.67
        val x = DemTileStore.lonToTileX(lon, z)
        val y = DemTileStore.latToTileY(lat, z)
        assertTrue(DemTileStore.tileXToLon(x, z) <= lon)
        assertTrue(DemTileStore.tileXToLon(x + 1, z) > lon)
        assertTrue(DemTileStore.tileYToLat(y, z) >= lat)
        assertTrue(DemTileStore.tileYToLat(y + 1, z) < lat)
    }

    @Test
    fun colourRampIsTransparentBelowThresholdAndOpaqueAbove() {
        assertEquals(0, SuitabilityRasterizer.colourFor(0.2, 0.35))
        val strong = SuitabilityRasterizer.colourFor(1.0, 0.35)
        val weak = SuitabilityRasterizer.colourFor(0.4, 0.35)
        val alphaStrong = (strong ushr 24) and 0xFF
        val alphaWeak = (weak ushr 24) and 0xFF
        assertTrue("stronger ground must render more opaque", alphaStrong > alphaWeak)
    }
}
