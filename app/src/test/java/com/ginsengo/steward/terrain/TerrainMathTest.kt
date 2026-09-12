package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Synthetic grid helper. Row 0 is NORTH, so +y runs south. */
private fun grid(w: Int, h: Int, cell: Double, f: (x: Int, y: Int) -> Double) =
    TerrainMath.Grid(w, h, FloatArray(w * h) { f(it % w, it / w).toFloat() }, cell)

class SlopeAspectTest {

    @Test
    fun northFacingSlopeReadsAsNorth() {
        // Elevation rises southward => the hill faces north.
        val g = grid(9, 9, 10.0) { _, y -> y * 10.0 }
        val (slope, aspect) = TerrainMath.slopeAspect(g, 4, 4)
        assertEquals(45.0, slope, 0.5)
        assertEquals(0.0, aspect, 1.0)
    }

    @Test
    fun eastFacingSlopeReadsAsEast() {
        // Elevation falls eastward => the hill faces east.
        val g = grid(9, 9, 10.0) { x, _ -> (8 - x) * 10.0 }
        val (_, aspect) = TerrainMath.slopeAspect(g, 4, 4)
        assertEquals(90.0, aspect, 1.0)
    }

    @Test
    fun southWestFacingSlopeReadsAsSouthWest() {
        val g = grid(9, 9, 10.0) { x, y -> (x * 10.0) + ((8 - y) * 10.0) }
        val (_, aspect) = TerrainMath.slopeAspect(g, 4, 4)
        assertEquals(225.0, aspect, 2.0)
    }

    @Test
    fun flatGroundHasNoAspect() {
        val g = grid(9, 9, 10.0) { _, _ -> 500.0 }
        val (slope, aspect) = TerrainMath.slopeAspect(g, 4, 4)
        assertEquals(0.0, slope, 1e-6)
        assertTrue("flat aspect must be reported undefined", aspect < 0)
    }
}

class HeatLoadTest {

    private val lat = 37.5 // central Appalachia, inside McCune & Keon Eq.3's 30-60N range

    /**
     * The defining property of the McCune & Keon heat load transform: folding about the
     * NE-SW line puts the coolest value at NE and the hottest at SW. This is the entire
     * reason ginseng's preference for north- and east-facing slopes is expressible as a
     * single number, so if this inverts, the heatmap inverts with it.
     */
    @Test
    fun northEastIsCoolestAndSouthWestIsHottest() {
        val slope = 25.0
        val ne = TerrainMath.heatLoadIndex(lat, slope, 45.0)
        val sw = TerrainMath.heatLoadIndex(lat, slope, 225.0)
        val n = TerrainMath.heatLoadIndex(lat, slope, 0.0)
        val s = TerrainMath.heatLoadIndex(lat, slope, 180.0)

        assertTrue("NE must be the coolest aspect, got NE=$ne SW=$sw", ne < sw)
        assertTrue("N must be cooler than S", n < s)
        assertTrue("NE must be cooler than plain N", ne < n)
    }

    @Test
    fun foldingIsSymmetricAboutTheNeSwAxis() {
        // Aspects equidistant from the NE-SW axis must carry the same heat load.
        val a = TerrainMath.heatLoadIndex(lat, 30.0, 45.0 - 40.0)
        val b = TerrainMath.heatLoadIndex(lat, 30.0, 45.0 + 40.0)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun steeperSlopesAmplifyAspect() {
        val gentleSpread = abs(
            TerrainMath.heatLoadIndex(lat, 5.0, 225.0) - TerrainMath.heatLoadIndex(lat, 5.0, 45.0)
        )
        val steepSpread = abs(
            TerrainMath.heatLoadIndex(lat, 35.0, 225.0) - TerrainMath.heatLoadIndex(lat, 35.0, 45.0)
        )
        assertTrue("aspect should matter more on steep ground", steepSpread > gentleSpread)
    }

    @Test
    fun matchesPublishedCoefficientsOnAWorkedCase() {
        // Recomputed by hand from Eq. 3: 0.339 + 0.808 cosL cosS - 0.196 sinL sinS
        //                                       - 0.482 cos(folded) sinS
        val l = Math.toRadians(37.5); val s = Math.toRadians(30.0)
        val folded = Math.toRadians(180.0 - abs(90.0 - 225.0))
        val expected = 0.339 + 0.808 * Math.cos(l) * Math.cos(s) -
                0.196 * Math.sin(l) * Math.sin(s) -
                0.482 * Math.cos(folded) * Math.sin(s)
        assertEquals(expected, TerrainMath.heatLoadIndex(37.5, 30.0, 90.0), 1e-12)
    }
}

class TpiAndCurvatureTest {

    @Test
    fun ridgeIsPositiveValleyIsNegative() {
        // A V-shaped valley running north-south down the middle column.
        val g = grid(31, 31, 10.0) { x, _ -> abs(x - 15) * 12.0 }
        val valley = TerrainMath.tpi(g, 15, 15, 8)
        val flank = TerrainMath.tpi(g, 30, 15, 8)
        assertTrue("valley floor must have negative TPI, got $valley", valley < 0)
        assertTrue("upper flank must have positive TPI, got $flank", flank > 0)
    }

    /**
     * Pins BOTH signs. Curvature feeding [GinsengSuitability] with the wrong sign would
     * score every ridge nose as a cove and every cove as a ridge, and the heatmap would
     * still look entirely plausible — just inverted. Testing only the hollow would leave
     * that half-checked, so the convex case is asserted alongside it.
     */
    @Test
    fun curvatureIsPositiveInAHollowAndNegativeOnARidge() {
        val hollow = grid(21, 21, 10.0) { x, _ -> abs(x - 10) * 12.0 }
        assertTrue(
            "a hollow must read concave (positive)",
            TerrainMath.profileCurvature(hollow, 10, 10) > 0,
        )

        val ridge = grid(21, 21, 10.0) { x, _ -> -abs(x - 10) * 12.0 }
        assertTrue(
            "a ridge nose must read convex (negative)",
            TerrainMath.profileCurvature(ridge, 10, 10) < 0,
        )

        val flat = grid(21, 21, 10.0) { _, _ -> 400.0 }
        assertEquals(0.0, TerrainMath.profileCurvature(flat, 10, 10), 1e-9)
    }
}

class WetnessTest {

    @Test
    fun waterAccumulatesInTheValley() {
        // Valley down the centre column, also tilted south so water has somewhere to go.
        val g = grid(41, 41, 10.0) { x, y -> abs(x - 20) * 8.0 + (40 - y) * 2.0 }
        val twi = TerrainMath.topographicWetnessIndex(g)
        val valley = twi[20 * 41 + 20]
        val flank = twi[20 * 41 + 38]
        assertTrue("valley TWI ($valley) must exceed flank TWI ($flank)", valley > flank)
    }

    @Test
    fun everyCellGetsAFiniteIndex() {
        val g = grid(25, 25, 10.0) { x, y -> abs(x - 12) * 5.0 + abs(y - 12) * 5.0 }
        TerrainMath.topographicWetnessIndex(g).forEach {
            assertTrue("TWI must stay finite, got $it", it.isFinite())
        }
    }
}

class BandTest {

    @Test
    fun bandPeaksInsideAndFallsOffOutside() {
        assertEquals(1.0, TerrainMath.band(10.0, 6.0, 14.0, 4.0), 1e-9)
        assertEquals(1.0, TerrainMath.band(6.0, 6.0, 14.0, 4.0), 1e-9)
        assertEquals(0.0, TerrainMath.band(1.0, 6.0, 14.0, 4.0), 1e-9)
        assertEquals(0.0, TerrainMath.band(19.0, 6.0, 14.0, 4.0), 1e-9)
        val below = TerrainMath.band(4.0, 6.0, 14.0, 4.0)
        assertTrue("must ramp, not step", below > 0.0 && below < 1.0)
    }
}
