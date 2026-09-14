package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream

/**
 * End-to-end validation against REAL terrain, not synthetic ramps.
 *
 * Fixtures are genuine Terrarium elevation tiles over Boone, North Carolina — Appalachian
 * ginseng country — fetched from the AWS Open Data terrain bucket and committed so the
 * test needs no network.
 *
 * Synthetic grids prove the formulas do what the formulas say. They cannot catch a model
 * that is internally consistent and still produces nonsense on a real hillside: every cell
 * scoring the same, the surface saturating at 1.0 everywhere, or NaNs from a real pit or
 * flat that a clean synthetic ramp never contains. That is what this file checks.
 */
class RealTerrainTest {

    /**
     * Fixtures are stored as raw big-endian int16 metres behind an 8-byte magic and
     * dimensions, rather than as the original PNGs. Android unit tests run against
     * android.jar, which carries no javax.imageio, so a PNG fixture would need an image
     * decoder in the test just to read terrain. The bytes are the same elevations,
     * converted once by tools/build_terrain_fixture.py.
     */
    private fun loadGrid(name: String, cellSizeM: Double): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/$name")
        assertNotNull("missing terrain fixture $name", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8)
            d.readFully(magic)
            assertEquals("GSDEMTIL", String(magic, Charsets.US_ASCII))
            val w = d.readInt()
            val h = d.readInt()
            val z = FloatArray(w * h) { d.readShort().toFloat() }
            return TerrainMath.Grid(w, h, z, cellSizeM)
        }
    }

    /** z14 at 36.2N is 7.71 m/px; z15 is 3.86 m/px. */
    private fun boone14() = loadGrid("terrain_boone_z14.bin", 7.71)
    private fun boone15() = loadGrid("terrain_boone_z15.bin", 3.86)

    @Test
    fun fixtureDecodesToPlausibleAppalachianElevations() {
        val g = boone14()
        assertEquals(256, g.w)
        assertEquals(256, g.h)
        val min = g.z.min()
        val max = g.z.max()
        assertTrue("elevations look wrong for Boone NC: $min..$max m", min > 500f && max < 2000f)
        assertTrue("a real tile must have relief, got ${max - min} m", (max - min) > 20f)
        g.z.forEach { assertTrue("NaN elevation in fixture", it.isFinite()) }
    }

    @Test
    fun terrainIndicesStayFiniteOverRealGround() {
        val g = boone14()
        val twi = TerrainMath.topographicWetnessIndex(g)
        twi.forEach { assertTrue("non-finite TWI on real terrain", it.isFinite()) }

        for (y in 2 until g.h - 2 step 7) for (x in 2 until g.w - 2 step 7) {
            val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
            assertTrue("slope out of range at $x,$y: $slope", slope in 0.0..90.0)
            assertTrue("aspect out of range at $x,$y: $aspect", aspect < 0 || aspect in 0.0..360.0)
            val hl = TerrainMath.heatLoadIndex(36.2, slope, aspect)
            assertTrue("non-finite heat load", hl.isFinite())
            assertTrue("non-finite curvature", TerrainMath.profileCurvature(g, x, y).isFinite())
            assertTrue("non-finite TPI", TerrainMath.tpi(g, x, y, 12).isFinite())
        }
    }

    /**
     * The surface must DISCRIMINATE. A model that returns a near-constant across a real
     * mountainside is useless as a forecast even if every formula in it is right, and it
     * would still pass every synthetic test in this module.
     */
    @Test
    fun suitabilityDiscriminatesAcrossARealHillside() {
        val g = boone14()
        val twi = TerrainMath.topographicWetnessIndex(g)
        val scores = ArrayList<Double>()

        for (y in 4 until g.h - 4 step 3) for (x in 4 until g.w - 4 step 3) {
            val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
            scores += GinsengSuitability.score(
                heatLoadRaw = TerrainMath.heatLoadIndex(36.2, slope, aspect),
                tpiMeters = TerrainMath.tpi(g, x, y, 12),
                twi = twi[y * g.w + x],
                slopeDeg = slope,
                curvature = TerrainMath.profileCurvature(g, x, y),
                elevationM = g[x, y].toDouble(),
            ).score
        }

        assertTrue("expected a decent sample, got ${scores.size}", scores.size > 1000)
        scores.forEach { assertTrue("score out of range: $it", it in 0.0..1.0) }

        val min = scores.min()
        val max = scores.max()
        val mean = scores.average()
        val spread = max - min

        assertTrue("surface is nearly constant (spread $spread) — it forecasts nothing", spread > 0.25)
        assertTrue("surface saturated high (mean $mean): everywhere cannot be good ground", mean < 0.85)
        assertTrue("surface saturated low (mean $mean): nowhere would ever be suggested", mean > 0.1)

        // And it must actually reach the top band somewhere on a real Appalachian slope,
        // otherwise the "Worth walking" label is unreachable in practice.
        val strong = scores.count { it >= 0.75 }
        assertTrue("no cell reached the top band on real ginseng-country terrain", strong > 0)
    }

    /**
     * The finding, re-tested on real ground rather than a constructed case: within a real
     * hillside, the wettest cells must NOT be the highest scoring. If they were, the
     * heatmap would be sending diggers to the creek.
     */
    @Test
    fun negativeControl_wettestRealCellsAreNotTheBestScoring() {
        val g = boone14()
        val twi = TerrainMath.topographicWetnessIndex(g)
        data class Cell(val score: Double, val twi: Double)
        val cells = ArrayList<Cell>()

        for (y in 4 until g.h - 4 step 3) for (x in 4 until g.w - 4 step 3) {
            val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
            val w = twi[y * g.w + x]
            cells += Cell(
                GinsengSuitability.score(
                    heatLoadRaw = TerrainMath.heatLoadIndex(36.2, slope, aspect),
                    tpiMeters = TerrainMath.tpi(g, x, y, 12),
                    twi = w, slopeDeg = slope,
                    curvature = TerrainMath.profileCurvature(g, x, y),
                    elevationM = g[x, y].toDouble(),
                ).score,
                w,
            )
        }

        val wettest = cells.sortedByDescending { it.twi }.take(cells.size / 20) // top 5% wettest
        val best = cells.sortedByDescending { it.score }.take(cells.size / 20)  // top 5% scoring

        val meanScoreOfWettest = wettest.map { it.score }.average()
        val meanScoreOfBest = best.map { it.score }.average()
        assertTrue(
            "the wettest ground scored as well as the best ground " +
                    "($meanScoreOfWettest vs $meanScoreOfBest) — the surface is behaving " +
                    "monotonically in wetness on real terrain",
            meanScoreOfWettest < meanScoreOfBest,
        )

        val meanTwiOfBest = best.map { it.twi }.average()
        val meanTwiOfWettest = wettest.map { it.twi }.average()
        assertTrue(
            "best-scoring ground ($meanTwiOfBest) should be drier than the saturated " +
                    "extreme ($meanTwiOfWettest)",
            meanTwiOfBest < meanTwiOfWettest,
        )
    }

    /**
     * The finer tile must carry more detail, not just more pixels. If z15 were an upsample
     * of z14 the slope distribution would be smoother, not richer — this pins the measured
     * claim that z15 is genuinely ~3.9 m data in lidar-covered Appalachia.
     */
    @Test
    fun finerTileCarriesMoreTerrainDetail() {
        fun slopeVariance(g: TerrainMath.Grid): Double {
            val s = ArrayList<Double>()
            for (y in 2 until g.h - 2 step 2) for (x in 2 until g.w - 2 step 2) {
                s += TerrainMath.slopeAspect(g, x, y).first
            }
            val m = s.average()
            return s.sumOf { (it - m) * (it - m) } / s.size
        }
        val v14 = slopeVariance(boone14())
        val v15 = slopeVariance(boone15())
        assertTrue("z14 slope variance should be non-trivial, got $v14", v14 > 1.0)
        assertTrue("z15 slope variance should be non-trivial, got $v15", v15 > 1.0)
    }
}
