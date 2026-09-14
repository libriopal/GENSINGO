package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

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
     * The constant-time TPI must actually track the circular reference it replaces.
     *
     * It is a different window (square, not circular), so exact equality is not the claim —
     * the claim is that swapping it in does not change which ground reads as valley and
     * which reads as ridge. Checked by correlation of sign and by bounded disagreement,
     * because a fast index that ranks terrain differently is not an optimisation, it is a
     * different model with the old model's name.
     */
    @Test
    fun fastTpiAgreesWithTheCircularReferenceOnRealTerrain() {
        // Measured on the real Boone NC fixture, not a synthetic ramp. A synthetic surface
        // with a hard-edged bump is the worst possible case for a window-shape change and
        // says nothing useful about how the two indices rank an actual hillside.
        val stream = javaClass.getResourceAsStream("/terrain_boone_z14.bin")
        assertNotNull("missing terrain fixture", stream)
        val g = java.io.DataInputStream(stream!!.buffered()).use { d ->
            d.readFully(ByteArray(8))
            val w = d.readInt(); val h = d.readInt()
            TerrainMath.Grid(w, h, FloatArray(w * h) { d.readShort().toFloat() }, 7.71)
        }
        val sat = TerrainMath.SummedArea(g)

        for (r in listOf(3, 8, 20, 40)) {
            var agree = 0
            var agreeMeaningful = 0
            var meaningful = 0
            var total = 0
            var sumAbs = 0.0
            var worst = 0.0
            var worstScoreDelta = 0.0
            val refs = ArrayList<Double>()
            val fasts = ArrayList<Double>()
            for (y in r + 1 until g.h - r - 1 step 5) for (x in r + 1 until g.w - r - 1 step 5) {
                val ref = TerrainMath.tpi(g, x, y, r)
                val fast = TerrainMath.tpiFast(g, sat, x, y, r)
                total++
                if ((ref >= 0) == (fast >= 0)) agree++
                if (abs(ref) > 1.0) {
                    meaningful++
                    if ((ref >= 0) == (fast >= 0)) agreeMeaningful++
                }
                sumAbs += abs(ref - fast)
                worst = maxOf(worst, abs(ref - fast))
                refs += ref; fasts += fast

                // The claim that actually matters downstream: does swapping the index
                // change the ginseng score this cell gets? Everything else is diagnostics.
                val (slopeDeg, aspectDeg) = TerrainMath.slopeAspect(g, x, y)
                val hl = TerrainMath.heatLoadIndex(36.2, slopeDeg, aspectDeg)
                val curv = TerrainMath.profileCurvature(g, x, y)
                val e = g[x, y].toDouble()
                val sRef = GinsengSuitability.score(hl, ref, 8.0, slopeDeg, curv, e).score
                val sFast = GinsengSuitability.score(hl, fast, 8.0, slopeDeg, curv, e).score
                worstScoreDelta = maxOf(worstScoreDelta, abs(sRef - sFast))
            }
            // Sign agreement is only measured where the sign is a real claim. Within a
            // metre of zero the cell is neither valley nor ridge, and a 0.2 m difference
            // flips it for no meaningful reason — counting those would measure noise.
            val rate = if (meaningful == 0) 1.0 else agreeMeaningful.toDouble() / meaningful
            val meanAbs = sumAbs / total

            // Pearson correlation: does the fast index RANK terrain the same way?
            val mr = refs.average(); val mf = fasts.average()
            var num = 0.0; var dr = 0.0; var df = 0.0
            for (i in refs.indices) {
                val a = refs[i] - mr; val b = fasts[i] - mf
                num += a * b; dr += a * a; df += b * b
            }
            val corr = num / sqrt(dr * df)

            println("TPI fast-vs-reference r=$r: sign(|tpi|>1m) ${"%.3f".format(rate)} " +
                    "corr ${"%.4f".format(corr)} meanAbs ${"%.2f".format(meanAbs)} m " +
                    "worst ${"%.1f".format(worst)} m " +
                    "worstScoreDelta ${"%.4f".format(worstScoreDelta)}")

            assertTrue(
                "radius $r: sign agreement ${"%.3f".format(rate)} — the fast index disagrees " +
                        "about valley vs ridge too often",
                rate > 0.95,
            )
            assertTrue(
                "radius $r: correlation ${"%.4f".format(corr)} — the fast index ranks terrain " +
                        "differently, which makes it a different model rather than a faster one",
                corr > 0.9999,
            )
            assertTrue(
                "radius $r: mean disagreement ${"%.4f".format(meanAbs)} m — the fast path " +
                        "should reproduce the circular mask exactly, not approximate it",
                meanAbs < 0.01,
            )
            // The end-to-end claim. A change in the index only matters if it changes the
            // score a digger is shown.
            assertTrue(
                "radius $r: swapping the index moves a suitability score by " +
                        "${"%.4f".format(worstScoreDelta)} — too much to call it the same model",
                worstScoreDelta < 0.001,
            )
        }
    }

    /** The summed-area mean must equal a brute-force CIRCULAR mean exactly. */
    @Test
    fun summedAreaMeanMatchesBruteForce() {
        val g = grid(37, 29, 10.0) { x, y -> (x * 7 + y * 13) % 97 * 1.0 }
        val sat = TerrainMath.SummedArea(g)
        for (r in listOf(1, 4, 9)) {
            for (y in listOf(0, 5, 14, 28)) for (x in listOf(0, 3, 18, 36)) {
                var sum = 0.0; var n = 0
                for (dy in -r..r) for (dx in -r..r) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until g.w || yy !in 0 until g.h) continue
                    if (dx == 0 && dy == 0) continue
                    if (dx * dx + dy * dy > r * r) continue
                    sum += g[xx, yy]; n++
                }
                val expected = if (n == 0) g[x, y].toDouble() else sum / n
                assertEquals(
                    "circular mean at $x,$y r=$r",
                    expected, sat.neighbourhoodMean(x, y, r), 1e-9,
                )
            }
        }
    }

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
