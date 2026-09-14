package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import kotlin.math.abs

/**
 * Do the six weights mean anything?
 *
 * The forecast combines six terrain indices in a weighted sum with coefficients assigned by
 * reading the literature rather than fitted to occurrence data: heat load 0.28, slope position
 * 0.24, wetness 0.18, slope angle 0.14, curvature 0.10, elevation 0.06. Two distinct objections
 * apply and both are measurable rather than arguable:
 *
 *  1. COLLINEARITY. Every index is a derivative of one elevation surface. Heat load is an
 *     analytic function of slope and aspect. Wetness contains tan(slope). Position and
 *     elevation are both neighbourhood functions of the same heights. If the six columns are
 *     strongly correlated then the stated coefficients are not the model's actual sensitivities,
 *     slope is doing more work than 0.14 suggests, and quoting 0.28 against 0.24 claims a
 *     discriminative precision the elicitation cannot support.
 *
 *  2. DECORATIVENESS. If perturbing a weight by half its value barely moves the ranking of
 *     ground, then the weight is not really a parameter and the honest thing is to say so. If it
 *     moves the ranking a great deal, the app is shipping a number nobody can defend, because
 *     these weights have never been validated against a single known ginseng location.
 *
 * Either answer is more useful than silence, which is why this measures rather than asserts. The
 * assertions pin only what was actually observed, so a later change to the weights or the bands
 * that alters the model's character shows up as a failure instead of a shrug.
 */
class WeightSensitivityTest {

    private fun loadGrid(name: String, cellSizeM: Double): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/$name")
        assertNotNull("missing terrain fixture $name", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8)
            d.readFully(magic)
            assertEquals("GSDEMTIL", String(magic, Charsets.US_ASCII))
            val w = d.readInt(); val h = d.readInt()
            val z = FloatArray(w * h) { d.readShort().toFloat() }
            return TerrainMath.Grid(w, h, z, cellSizeM)
        }
    }

    private val radius = 16

    /** Per-cell factor values over real terrain, in the order of Factor.values(). */
    private data class Sample(val factors: DoubleArray, val score: Double)

    private fun sampleTerrain(): List<Sample> {
        val g = loadGrid("terrain_boone_z15.bin", 3.86)
        val sat = TerrainMath.SummedArea(g)
        val twi = TerrainMath.topographicWetnessIndex(g)
        val out = ArrayList<Sample>()
        var y = radius + 2
        while (y < g.h - radius - 2) {
            var x = radius + 2
            while (x < g.w - radius - 2) {
                val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
                val b = GinsengSuitability.score(
                    heatLoadRaw = TerrainMath.heatLoadIndex(36.2, slope, aspect),
                    tpiMeters = TerrainMath.tpiFast(g, sat, x, y, radius),
                    twi = twi[y * g.w + x],
                    slopeDeg = slope,
                    curvature = TerrainMath.profileCurvature(g, x, y),
                    elevationM = g.z[y * g.w + x].toDouble(),
                )
                val order = GinsengSuitability.Factor.entries
                out += Sample(
                    DoubleArray(order.size) { b.factors[order[it]] ?: 0.0 },
                    b.score,
                )
                x += 3
            }
            y += 3
        }
        assertTrue("need a real sample, got ${out.size}", out.size > 2000)
        return out
    }

    // ------------------------------------------------------------------ statistics

    private fun ranks(v: DoubleArray): DoubleArray {
        val idx = v.indices.sortedBy { v[it] }
        val r = DoubleArray(v.size)
        var i = 0
        while (i < idx.size) {
            var j = i
            while (j + 1 < idx.size && v[idx[j + 1]] == v[idx[i]]) j++
            val mean = (i + j) / 2.0 + 1.0
            for (k in i..j) r[idx[k]] = mean
            i = j + 1
        }
        return r
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val n = a.size
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in 0 until n) {
            val x = a[i] - ma; val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        return if (da == 0.0 || db == 0.0) 0.0 else num / kotlin.math.sqrt(da * db)
    }

    private fun spearman(a: DoubleArray, b: DoubleArray) = pearson(ranks(a), ranks(b))

    // ------------------------------------------------------------------ 1. collinearity

    @Test
    fun theSixFactorsAreMeasurablyCollinearSoTheWeightsAreNotSensitivities() {
        val samples = sampleTerrain()
        val order = GinsengSuitability.Factor.entries
        val cols = Array(order.size) { f -> DoubleArray(samples.size) { samples[it].factors[f] } }

        println("Pairwise Spearman between the six factor surfaces, real Boone terrain, " +
                "${samples.size} cells:")
        print("                  ")
        order.forEach { print("%9s".format(it.name.take(8))) }
        println()
        var worstPair = "" ; var worstAbs = 0.0
        for (i in order.indices) {
            print("%-18s".format(order[i].name.take(17)))
            for (j in order.indices) {
                val r = if (i == j) 1.0 else spearman(cols[i], cols[j])
                print("%9.3f".format(r))
                if (i != j && abs(r) > worstAbs) {
                    worstAbs = abs(r); worstPair = "${order[i].name} vs ${order[j].name}"
                }
            }
            println()
        }
        println("Strongest off-diagonal dependence: $worstPair at ${"%.3f".format(worstAbs)}")

        // MEASURED. The claim being pinned is that the columns are NOT independent, because
        // that is what makes the published coefficients uninterpretable as sensitivities.
        assertTrue(
            "the six factors are expected to be collinear - they are all derivatives of one " +
            "elevation surface. Strongest was $worstPair at $worstAbs; if this ever drops " +
            "near zero the factors have genuinely been made orthogonal, which is a real " +
            "change and must be claimed rather than discovered here",
            worstAbs > 0.25,
        )
    }

    /**
     * What actually drives the ranking, as opposed to what the weight table says. A factor whose
     * surface correlates with the final score far above or below its nominal weight is not
     * contributing what the UI implies when it names a dominant factor.
     */
    @Test
    fun effectiveInfluenceIsReportedAgainstNominalWeight() {
        val samples = sampleTerrain()
        val order = GinsengSuitability.Factor.entries
        val score = DoubleArray(samples.size) { samples[it].score }

        println("Factor influence on the final ranking vs its nominal weight:")
        println("  factor              weight   spearman(factor, score)   variance of factor")
        for ((i, f) in order.withIndex()) {
            val col = DoubleArray(samples.size) { samples[it].factors[i] }
            val mean = col.average()
            val varc = col.sumOf { (it - mean) * (it - mean) } / col.size
            println("  %-18s  %5.2f   %+.3f                    %.4f"
                .format(f.name.take(17), f.weight, spearman(col, score), varc))
        }
        // MEASURED on real Boone terrain, and this is the finding: the weight table does not
        // describe what drives the ranking.
        //
        //   factor          weight   rho(factor, score)
        //   HEAT_LOAD        0.28      +0.137   (variance 0.0071)
        //   SLOPE_POSITION   0.24      +0.657   (variance 0.0696)
        //   WETNESS          0.18      +0.545   (variance 0.1788)
        //   SLOPE_ANGLE      0.14      -0.026   (variance 0.1942)
        //   CURVATURE        0.10      +0.647   (variance 0.1205)
        //   ELEVATION        0.06      +0.000   (variance 0.0000, see below)
        //
        // The heaviest-weighted factor is nearly the least influential, and curvature at 0.10
        // does as much work as slope position at 0.24. Most of that is correct physics rather
        // than a coding error - heat load's aspect term carries sin(slope), so gentle ground has
        // no aspect signal to find - but it means quoting 0.28 against 0.24 asserts a
        // discriminative precision the model does not have.
        //
        // ELEVATION is a special case worth not over-reading. It is flat here because Boone sits
        // entirely inside the 250-1200 m plateau, which is the band doing its job, not failing
        // it. Its purpose is to trim ground outside the Appalachian range across 19 states, and
        // a single hillside cannot exercise that. elevationBandStillDiscriminatesOutOfRange
        // covers it instead.
        val elevation = GinsengSuitability.Factor.ELEVATION
        for ((i, f) in order.withIndex()) {
            if (f == elevation) continue
            val col = DoubleArray(samples.size) { samples[it].factors[i] }
            val mean = col.average()
            val varc = col.sumOf { (it - mean) * (it - mean) } / col.size
            assertTrue(
                "${f.name} is constant over real terrain (variance $varc), so its weight " +
                "${f.weight} buys nothing and the UI must not name it as a driver",
                varc > 1e-6,
            )
        }
    }

    /**
     * The elevation term contributes nothing within one Appalachian hillside, which is correct.
     * It has to still discriminate at the edges of its range, or the 0.06 weight is genuinely
     * decorative rather than merely dormant on this fixture.
     */
    @Test
    fun elevationBandStillDiscriminatesOutOfRange() {
        fun at(m: Double) = GinsengSuitability.score(
            heatLoadRaw = 0.7, tpiMeters = -4.0, twi = 8.0,
            slopeDeg = 12.0, curvature = 0.0, elevationM = m,
        ).factors[GinsengSuitability.Factor.ELEVATION]!!

        assertTrue("700 m Appalachian ground must score full marks, got ${at(700.0)}", at(700.0) > 0.98)
        assertTrue("40 m coastal plain must be penalised, got ${at(40.0)}", at(40.0) < 0.15)
        assertTrue("1900 m spruce-fir must be penalised, got ${at(1900.0)}", at(1900.0) < 0.15)
        assertTrue("the band must be ordered on the low side", at(40.0) < at(300.0))
    }

    // ------------------------------------------------------------------ 2. decorativeness

    /**
     * Perturb each weight by +/-50%, renormalise so the set still sums to 1, and measure how far
     * the RANKING of ground moves. Ranking is the right target: the digger uses this surface to
     * choose where to walk, so what matters is whether the best hillside is still the best
     * hillside, not whether its number changed in the third decimal.
     */
    @Test
    fun perturbingEachWeightByHalfShowsWhetherTheWeightsAreLoadBearing() {
        val samples = sampleTerrain()
        val order = GinsengSuitability.Factor.entries
        val nominal = order.map { it.weight }.toDoubleArray()

        fun scoreWith(w: DoubleArray) = DoubleArray(samples.size) { s ->
            var acc = 0.0
            for (i in order.indices) acc += w[i] * samples[s].factors[i]
            acc
        }

        val base = scoreWith(nominal)
        println("Weight sensitivity, +/-50% on each weight, renormalised, " +
                "${samples.size} real cells:")
        println("  factor              rho(-50%)  rho(+50%)   worst rank displacement")

        var leastStable = 1.0
        var leastStableName = ""
        for ((i, f) in order.withIndex()) {
            val rhos = ArrayList<Double>()
            for (mult in listOf(0.5, 1.5)) {
                val w = nominal.copyOf()
                w[i] = nominal[i] * mult
                val sum = w.sum()
                for (j in w.indices) w[j] = w[j] / sum
                val rho = spearman(base, scoreWith(w))
                rhos += rho
                if (rho < leastStable) { leastStable = rho; leastStableName = f.name }
            }
            println("  %-18s  %+.4f    %+.4f".format(f.name.take(17), rhos[0], rhos[1]))
        }
        println("Least stable weight: $leastStableName at rho ${"%.4f".format(leastStable)}")

        // MEASURED: worst case is WETNESS at -50%, rho 0.875. Every other weight stays above
        // 0.93 and elevation is exactly 1.000 on this fixture.
        //
        // So the weights are load-bearing but not brittle: halving the most influential one
        // leaves the ranking of ground 87% rank-correlated with the original. That is the
        // honest answer to "are these numbers decoration" - they are not, and neither are they
        // precise. A model whose output survives a 50% error in its largest coefficient at
        // rho 0.88 is a model that identifies broadly promising hillsides, not one that ranks
        // candidate sites.
        assertTrue(
            "changing one weight by half reorders the ranking more than measured (rho " +
            "$leastStable for $leastStableName); for coefficients never fitted to occurrence " +
            "data this is the number that decides how the surface may be described",
            leastStable > 0.85,
        )
        assertTrue(
            "no weight changed the ranking at all, which would mean the six-factor breakdown " +
            "the UI shows is decoration",
            leastStable < 0.99999,
        )
    }

    /**
     * The comparison that matters most for honesty: is the ranking more sensitive to the WEIGHTS
     * (which are guesses) or to the BANDS (which encode published ecology)?
     *
     * This test was written expecting the bands to dominate, because that would have made the
     * unfitted weights the less alarming half of the model. MEASURED, the opposite is true:
     * halving the wetness weight gives rho 0.8754, while moving the wetness optimum by a full
     * 2 TWI units - a defensible disagreement between two extension publications - gives 0.8885.
     * The guessed coefficients move the map slightly MORE than a real ecological disagreement
     * does.
     *
     * The conclusion is corrected rather than the assertion relaxed, because that is the entire
     * point of measuring. It does not make the model worthless; it does mean the weights cannot
     * be described as a minor detail on top of published ecology, and the surface must be
     * labelled as an unvalidated terrain heuristic rather than a forecast.
     */
    @Test
    fun theUnfittedWeightsMoveTheRankingAtLeastAsMuchAsTheEcologicalBandsDo() {
        val samples = sampleTerrain()
        val order = GinsengSuitability.Factor.entries
        val nominal = order.map { it.weight }.toDoubleArray()
        fun scoreWith(w: DoubleArray) = DoubleArray(samples.size) { s ->
            var acc = 0.0
            for (i in order.indices) acc += w[i] * samples[s].factors[i]
            acc
        }
        val base = scoreWith(nominal)

        // Worst case over all single-weight +/-50% perturbations.
        var worstWeightRho = 1.0
        for (i in order.indices) for (mult in listOf(0.5, 1.5)) {
            val w = nominal.copyOf(); w[i] = nominal[i] * mult
            val sum = w.sum(); for (j in w.indices) w[j] = w[j] / sum
            worstWeightRho = minOf(worstWeightRho, spearman(base, scoreWith(w)))
        }

        // Now move a BAND instead: shift the wetness optimum by 2 TWI units, which is a
        // defensible disagreement between two extension publications rather than a wild change.
        val g = loadGrid("terrain_boone_z15.bin", 3.86)
        val sat = TerrainMath.SummedArea(g)
        val twiArr = TerrainMath.topographicWetnessIndex(g)
        val shifted = ArrayList<Double>()
        var y = radius + 2
        while (y < g.h - radius - 2) {
            var x = radius + 2
            while (x < g.w - radius - 2) {
                val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
                val heat = 1.0 - TerrainMath.normaliseHeatLoad(
                    TerrainMath.heatLoadIndex(36.2, slope, aspect)
                )
                val pos = TerrainMath.band(TerrainMath.tpiFast(g, sat, x, y, radius), -8.0, -1.0, 6.0)
                // the perturbation: optimum wetness band moved from 6.0-10.5 to 8.0-12.5
                val wet = TerrainMath.band(twiArr[y * g.w + x], 8.0, 12.5, 2.5)
                val steep = TerrainMath.band(slope, 6.0, 20.0, 7.0)
                val cove = TerrainMath.smoothStep(
                    (TerrainMath.profileCurvature(g, x, y) + 0.02) / 0.04
                )
                val elev = TerrainMath.band(g.z[y * g.w + x].toDouble(), 250.0, 1200.0, 250.0)
                shifted += GinsengSuitability.W_HEAT_LOAD * heat +
                        GinsengSuitability.W_SLOPE_POSITION * pos +
                        GinsengSuitability.W_WETNESS * wet +
                        GinsengSuitability.W_SLOPE_ANGLE * steep +
                        GinsengSuitability.W_CURVATURE * cove +
                        GinsengSuitability.W_ELEVATION * elev
                x += 3
            }
            y += 3
        }
        val bandRho = spearman(base, shifted.toDoubleArray())

        println("Worst rho from a +/-50% WEIGHT change:        ${"%.4f".format(worstWeightRho)}")
        println("rho from shifting the wetness BAND by 2 TWI:  ${"%.4f".format(bandRho)}")
        assertEquals("sample sizes must match", samples.size, shifted.size)
        assertTrue(
            "the guessed weights are expected to move the ranking at least as much as a real " +
            "ecological disagreement about the wetness optimum (weight $worstWeightRho vs " +
            "band $bandRho). If the bands ever dominate instead, the model's character has " +
            "changed and the report's description of it must change too",
            worstWeightRho <= bandRho + 1e-9,
        )
        // Both perturbations must actually perturb something, or this compares two no-ops.
        assertTrue("the band shift did nothing: rho $bandRho", bandRho < 0.999)
        assertTrue("no weight change did anything: rho $worstWeightRho", worstWeightRho < 0.999)
    }
}
