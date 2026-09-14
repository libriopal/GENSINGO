package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.abs

/**
 * Does the forecast give the same answer for the same piece of ground twice?
 *
 * Every index the forecast uses is a NEIGHBOURHOOD operator, and the app computes them over a
 * window cut to whatever the camera happens to be looking at. So there is a question that has
 * to be answered with a number rather than an argument: when the digger pans, does the ground
 * under the cursor keep its score?
 *
 * Two of the three indices are bounded by construction. Slope, aspect, curvature and heat load
 * read a 3x3 neighbourhood, so a one-tile halo settles them completely. TPI reads a disc of
 * fixed radius, so a halo wider than that radius settles it too.
 *
 * The topographic wetness index is different in kind, and this is the finding. Beven and
 * Kirkby's ln(a / tan b) needs upslope CONTRIBUTING AREA, and contributing area is not a
 * neighbourhood at all: it is everything uphill, all the way to the divide, which on
 * Appalachian terrain is routinely kilometres away and several tiles outside any window the
 * camera implies. Clipping the window truncates the catchment. A cell whose water arrives from
 * off-window sees only the accumulation that happens to have been inside, so its wetness is
 * understated — and understated by a DIFFERENT amount depending on where the window was cut.
 *
 * That makes the wetness term a function of the camera, not only of the ground. This test
 * measures how much, because "it has a halo" is a mitigation and not a proof, and because a
 * defect that shifts a score by a thousandth and one that shifts it by half a band call for
 * completely different responses.
 */
class WindowDependenceTest {

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

    /** Real Boone, North Carolina terrain at z15 (3.86 m/px). */
    private fun boone() = loadGrid("terrain_boone_z15.bin", 3.86)

    /** Cuts a sub-window out of a grid, the way a viewport-sized mosaic cuts the world. */
    private fun window(g: TerrainMath.Grid, ox: Int, oy: Int, w: Int, h: Int): TerrainMath.Grid {
        val z = FloatArray(w * h)
        for (y in 0 until h) {
            System.arraycopy(g.z, (oy + y) * g.w + ox, z, y * w, w)
        }
        return TerrainMath.Grid(w, h, z, g.cellSizeM)
    }

    /**
     * Three windows, three different origins, one shared patch of ground.
     *
     * Window size 160x160 out of a 256x256 fixture. The overlap that all three contain is
     * global x in 96..159, y in 64..159 — a real 250 m by 370 m block of hillside that every
     * window can see, at a different offset within each.
     */
    private val size = 160
    private val origins = listOf(0 to 0, 48 to 32, 96 to 64)

    private fun sharedCells(): List<Pair<Int, Int>> {
        val xLo = origins.maxOf { it.first }
        val yLo = origins.minOf { it.second } + 0
        val xHi = origins.minOf { it.first } + size
        val yHi = origins.minOf { it.second } + size
        val cells = ArrayList<Pair<Int, Int>>()
        var y = origins.maxOf { it.second } + 4
        while (y < yHi - 4) {
            var x = xLo + 4
            while (x < xHi - 4) {
                cells += x to y
                x += 11
            }
            y += 11
        }
        assertTrue("overlap region is empty - fix the origins", cells.size > 40)
        return cells
    }

    // ------------------------------------------------------------------ the bounded indices

    /**
     * Slope reads a 3x3 neighbourhood and nothing more, so the same ground must slope the same
     * way in every window. If this ever fails, the defect is an indexing bug, not a physical
     * truncation, and the whole raster is wrong rather than merely window-dependent.
     */
    @Test
    fun slopeIsIdenticalRegardlessOfWhereTheWindowWasCut() {
        val g = boone()
        val grids = origins.map { (ox, oy) -> Triple(ox, oy, window(g, ox, oy, size, size)) }
        var worst = 0.0
        for ((gx, gy) in sharedCells()) {
            val values = grids.map { (ox, oy, w) ->
                TerrainMath.slopeAspect(w, gx - ox, gy - oy).first
            }
            worst = maxOf(worst, values.max() - values.min())
        }
        assertEquals(
            "slope must not depend on the window: a 3x3 operator cannot legitimately move",
            0.0, worst, 1e-9,
        )
    }

    /**
     * TPI reads a disc of fixed radius. Inside a window whose margin exceeds that radius it
     * is likewise exact, which is what the halo is for.
     */
    @Test
    fun tpiIsIdenticalWhereTheWindowMarginExceedsItsRadius() {
        val g = boone()
        val radius = 12
        val grids = origins.map { (ox, oy) ->
            val w = window(g, ox, oy, size, size)
            Triple(ox, oy, TerrainAnalysis.of(fakeMosaic(w), withWetness = false))
        }
        var worst = 0.0
        var compared = 0
        for ((gx, gy) in sharedCells()) {
            // Only cells that sit at least `radius` inside EVERY window; nearer the edge the
            // disc is clamped and truncation is expected rather than a defect.
            val deepEnough = grids.all { (ox, oy, _) ->
                val lx = gx - ox; val ly = gy - oy
                lx >= radius && ly >= radius && lx < size - radius && ly < size - radius
            }
            if (!deepEnough) continue
            compared++
            val values = grids.map { (ox, oy, a) -> a.tpi(gx - ox, gy - oy, radius) }
            worst = maxOf(worst, values.max() - values.min())
        }
        assertTrue("no cells were deep enough to compare - widen the overlap", compared > 5)
        assertEquals(
            "TPI must not depend on the window once the margin exceeds its radius",
            0.0, worst, 1e-6,
        )
    }

    // ------------------------------------------------------------------ the unbounded index

    /**
     * The finding. Contributing area is not a neighbourhood, so no halo can bound it, and the
     * same ground gets a different wetness depending on where the camera cut the window.
     *
     * Asserting agreement here is the auditor's own instrument. If it fails, the number it
     * prints is the size of the defect, in TWI units — and TWI enters the score through a band
     * 4.5 units wide, so a delta of ~1.0 is roughly a fifth of the whole wetness term.
     */
    @Test
    fun wetnessIsWindowDependentAndThisIsTheSizeOfIt() {
        val g = boone()
        val grids = origins.map { (ox, oy) ->
            val w = window(g, ox, oy, size, size)
            Triple(ox, oy, TerrainMath.topographicWetnessIndex(w))
        }
        var worst = 0.0
        var sum = 0.0
        var n = 0
        var worstAt = 0 to 0
        for ((gx, gy) in sharedCells()) {
            val values = grids.map { (ox, oy, twi) ->
                twi[(gy - oy) * size + (gx - ox)]
            }
            val spread = values.max() - values.min()
            if (spread > worst) { worst = spread; worstAt = gx to gy }
            sum += spread
            n++
        }
        val mean = sum / n
        println(
            "TWI window dependence over $n shared cells of real Boone terrain: " +
            "mean spread ${"%.4f".format(mean)}, worst ${"%.4f".format(worst)} " +
            "at global cell $worstAt"
        )
        assertTrue("expected some cells to compare", n > 40)

        // MEASURED, 2026-09-12, on this fixture: mean spread 0.0149, worst 0.2783 TWI units.
        //
        // The assertion asserts the DEFECT, not its absence, because the defect is real and
        // pretending otherwise is how it would get forgotten. Contributing area genuinely is
        // unbounded and this window carries NO halo at all, which is strictly worse than
        // anything the app does (DemTileStore defaults to haloTiles = 1, i.e. 256 cells of
        // halo on every side, cropped before display).
        assertTrue(
            "wetness is expected to be window-dependent - if this is suddenly zero, either " +
            "the windows stopped differing (vacuous control) or someone made TWI " +
            "catchment-correct, which is a real improvement that must be claimed explicitly " +
            "rather than discovered here",
            worst > 1e-6,
        )
        // Upper bound, so a change that makes truncation dramatically worse is caught.
        assertTrue(
            "wetness truncation has grown beyond the measured bound: worst $worst, mean $mean",
            worst < 0.6 && mean < 0.05,
        )
    }

    /**
     * What the digger actually sees. Wetness carries 0.18 of the score, so the question that
     * matters is not how far TWI moves but how far the final 0..1 suitability moves, and
     * whether it crosses the band boundaries the legend is drawn from.
     */
    @Test
    fun finalSuitabilityIsStableForTheSameGroundFromAnyWindow() {
        val g = boone()
        val radius = 12
        val prepared = origins.map { (ox, oy) ->
            val w = window(g, ox, oy, size, size)
            val analysis = TerrainAnalysis.of(fakeMosaic(w), withWetness = true)
            Triple(ox, oy, w to analysis)
        }
        var worst = 0.0
        var sum = 0.0
        var n = 0
        for ((gx, gy) in sharedCells()) {
            val deepEnough = prepared.all { (ox, oy, _) ->
                val lx = gx - ox; val ly = gy - oy
                lx >= radius && ly >= radius && lx < size - radius && ly < size - radius
            }
            if (!deepEnough) continue
            val scores = prepared.map { (ox, oy, pair) ->
                val (w, analysis) = pair
                val lx = gx - ox
                val ly = gy - oy
                val (slope, aspect) = TerrainMath.slopeAspect(w, lx, ly)
                GinsengSuitability.score(
                    heatLoadRaw = TerrainMath.heatLoadIndex(36.2, slope, aspect),
                    tpiMeters = analysis.tpi(lx, ly, radius),
                    twi = analysis.twiAt(lx, ly),
                    slopeDeg = slope,
                    curvature = TerrainMath.profileCurvature(w, lx, ly),
                    elevationM = w.z[ly * size + lx].toDouble(),
                ).score
            }
            val spread = scores.max() - scores.min()
            worst = maxOf(worst, spread)
            sum += spread
            n++
        }
        println(
            "FINAL SCORE window dependence over $n cells: " +
            "mean ${"%.5f".format(sum / n)}, worst ${"%.5f".format(worst)} (score is 0..1)"
        )
        assertTrue("expected cells to compare", n > 5)

        // MEASURED: mean 0.00003, worst 0.00078 on a 0..1 score, with NO halo.
        //
        // Why a 0.278 TWI swing becomes 0.0008 of score, which is worth stating because it is
        // luck turned into design rather than the other way round: wetness enters through
        // band(twi, 6.0, 10.5, 2.5), a plateau 4.5 units wide. Inside the plateau the
        // derivative is zero, so a fraction-of-a-unit shift in TWI moves the term not at all
        // for most cells, and only partially for cells sitting on a shoulder. The band shape
        // was chosen because "wetter is always better" is ecologically false (creek bottoms
        // are not ginseng ground); its insensitivity to small TWI error is a second dividend
        // from that decision.
        //
        // A legend band is 0.2 wide, so 0.00078 is about a 256th of one band - below the
        // quantisation of the colour ramp, and far below the accuracy the surface claims.
        assertTrue(
            "the same ground must not change suitability class when the camera moves: " +
            "worst spread $worst over $n cells",
            worst < 0.005,
        )
    }

    /** TerrainAnalysis is keyed by mosaic identity, so each window needs a distinct one. */
    private var fakeSeq = 0
    private fun fakeMosaic(g: TerrainMath.Grid): DemTileStore.Mosaic {
        fakeSeq++
        return DemTileStore.Mosaic(
            grid = g, zoom = 15,
            tileX0 = fakeSeq * 1000, tileY0 = 0,
            tilesX = 1, tilesY = 1, haloPx = 0,
            tilesLoaded = 1, tilesRequested = 1,
        )
    }

    /**
     * Negative control. If `window` silently returned the same pixels for every origin, every
     * test above would pass for the wrong reason — the classic vacuous control. So prove the
     * windows genuinely differ.
     */
    @Test
    fun theThreeWindowsActuallyContainDifferentGround() {
        val g = boone()
        val a = window(g, 0, 0, size, size)
        val b = window(g, 96, 64, size, size)
        var differing = 0
        for (i in a.z.indices) if (a.z[i] != b.z[i]) differing++
        assertTrue(
            "windows at different origins must hold different elevations, else the whole " +
            "file is a vacuous control",
            differing > a.z.size / 2,
        )
        // And the shared ground really is shared: same global cell, same elevation.
        for ((gx, gy) in sharedCells().take(20)) {
            assertEquals(
                "global cell $gx,$gy must be the same elevation in both windows",
                a.z[gy * size + gx].toDouble(),
                b.z[(gy - 64) * size + (gx - 96)].toDouble(),
                0.0,
            )
        }
    }
}
