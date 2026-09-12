package com.ginsengo.steward.terrain

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Terrain indices computed from an elevation grid.
 *
 * Every function here is pure and unit-testable on a plain JVM. That matters: this file is
 * the only part of the heatmap stack that can be verified without a device, so all of the
 * science lives here and none of it lives in the renderer.
 *
 * Sources are named per function. Where a published equation exists it is used verbatim
 * rather than approximated.
 */
object TerrainMath {

    /** A square elevation grid in metres, row-major, row 0 = NORTH edge. */
    class Grid(val w: Int, val h: Int, val z: FloatArray, val cellSizeM: Double) {
        operator fun get(x: Int, y: Int): Float =
            z[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]

        fun inBounds(x: Int, y: Int) = x in 0 until w && y in 0 until h
    }

    // ---------------------------------------------------------------- slope & aspect

    /**
     * Horn (1981) 3x3 slope/aspect — the method ArcGIS and GDAL use. Preferred over simple
     * central differences because it weights the eight neighbours, which suppresses the
     * single-pixel noise that lidar-derived DEMs carry into slope.
     *
     * Returns slope in DEGREES and aspect in DEGREES clockwise from north (downslope
     * direction), or aspect = -1 where the surface is flat and aspect is undefined.
     */
    fun slopeAspect(g: Grid, x: Int, y: Int): Pair<Double, Double> {
        // row 0 is north, so +y is south: dz/dy is negated to keep "north" positive
        val a = g[x - 1, y - 1]; val b = g[x, y - 1]; val c = g[x + 1, y - 1]
        val d = g[x - 1, y];                          val f = g[x + 1, y]
        val gg = g[x - 1, y + 1]; val hh = g[x, y + 1]; val i = g[x + 1, y + 1]

        val cs = g.cellSizeM
        val dzdx = ((c + 2f * f + i) - (a + 2f * d + gg)) / (8.0 * cs)
        val dzdy = ((gg + 2f * hh + i) - (a + 2f * b + c)) / (8.0 * cs)

        val rise = sqrt(dzdx * dzdx + dzdy * dzdy)
        val slopeDeg = Math.toDegrees(atan(rise))
        if (rise < 1e-9) return slopeDeg to -1.0

        // aspect: direction of steepest DESCENT, clockwise from north
        var aspect = Math.toDegrees(atan2(dzdy, -dzdx))
        aspect = (90.0 - aspect) % 360.0
        if (aspect < 0) aspect += 360.0
        return slopeDeg to aspect
    }

    // ---------------------------------------------------------------- heat load

    /**
     * McCune & Keon (2002) heat load index, Equation 3.
     * *Equations for potential annual direct incident radiation and heat load*,
     * Journal of Vegetation Science 13:603-606.
     *
     * Eq. 3 coefficients, read from Table 2 of the paper: 0.339, 0.808, -0.196, -0.482.
     * Adjusted R^2 = 0.983. Valid for latitude 30-60 degrees N and slope 0-60 degrees —
     * which covers every one of the 19 approved ginseng states (30.2N to 49.4N).
     *
     * Aspect is FOLDED ABOUT THE NE-SW LINE (not north-south): `180 - |aspect - 225|`.
     * That is what makes the index a *heat load* rather than a raw radiation figure —
     * it puts the minimum at north-east and the maximum at south-west, matching the
     * observation that NE slopes stay coolest because they catch morning rather than
     * afternoon sun. North-east being the coolest aspect is exactly why ginseng favours it.
     *
     * Returns the raw index in the paper's units (roughly 0.2 to 1.2 in this latitude band);
     * use [normaliseHeatLoad] for a 0..1 value.
     */
    fun heatLoadIndex(latitudeDeg: Double, slopeDeg: Double, aspectDeg: Double): Double {
        val l = Math.toRadians(latitudeDeg.coerceIn(0.0, 60.0))
        val s = Math.toRadians(slopeDeg.coerceIn(0.0, 60.0))
        // Flat ground has no aspect; treat it as the neutral mid-load case.
        val aspect = if (aspectDeg < 0) 225.0 - 90.0 else aspectDeg
        val folded = Math.toRadians(180.0 - abs(aspect - 225.0))
        return 0.339 +
                0.808 * cos(l) * cos(s) -
                0.196 * sin(l) * sin(s) -
                0.482 * cos(folded) * sin(s)
    }

    /** Maps the raw McCune-Keon value onto 0 (coolest) .. 1 (hottest). */
    fun normaliseHeatLoad(raw: Double): Double = ((raw - 0.2) / 1.0).coerceIn(0.0, 1.0)

    // ---------------------------------------------------------------- position on slope

    /**
     * Topographic Position Index (Weiss 2001): elevation minus the mean elevation of a
     * circular neighbourhood of [radiusCells]. Negative = valley / lower slope / cove,
     * positive = ridge / upper slope.
     *
     * This is the index that operationalises "near the bottom of slopes" from the
     * Virginia Cooperative Extension site-selection guidance, and it is scale-dependent
     * by construction — which is why the caller passes a radius chosen from the zoom.
     */
    fun tpi(g: Grid, x: Int, y: Int, radiusCells: Int): Double {
        if (radiusCells < 1) return 0.0
        var sum = 0.0
        var n = 0
        val r2 = radiusCells * radiusCells
        for (dy in -radiusCells..radiusCells) {
            for (dx in -radiusCells..radiusCells) {
                if (dx == 0 && dy == 0) continue
                if (dx * dx + dy * dy > r2) continue
                sum += g[x + dx, y + dy]
                n++
            }
        }
        if (n == 0) return 0.0
        return g[x, y] - (sum / n)
    }

    // ---------------------------------------------------------------- curvature

    /**
     * Mean curvature (Zevenbergen & Thorne 1987, simplified).
     *
     * SIGN CONVENTION: POSITIVE = concave = hollows, coves and benches — the sites the
     * extension literature repeatedly names as productive ginseng ground. Negative =
     * convex = ridge noses and spurs.
     *
     * The sign is stated this loudly because an earlier revision negated the result while
     * documenting the opposite convention, and [GinsengSuitability] consumed it as
     * "positive = cove". Every hollow in the country would have scored as a ridge. A unit
     * test over a synthetic V-valley caught it; nothing else would have, because the
     * heatmap would still have looked plausible — just inverted.
     */
    fun profileCurvature(g: Grid, x: Int, y: Int): Double {
        val cs = g.cellSizeM
        val zc = g[x, y]
        val d2x = (g[x - 1, y] - 2f * zc + g[x + 1, y]) / (cs * cs)
        val d2y = (g[x, y - 1] - 2f * zc + g[x, y + 1]) / (cs * cs)
        return (d2x + d2y) / 2.0
    }

    // ---------------------------------------------------------------- wetness

    /**
     * Multiple-flow-direction accumulation (Freeman 1991), then
     * TWI = ln(a / tan beta) (Beven & Kirkby 1979).
     *
     * MFD is used rather than the far simpler D8 on the strength of Kopecky & Cizkova
     * (2010, Applied Vegetation Science 13:450-459), who compared 11 routing algorithms
     * against Ellenberg soil-moisture indicator values across 521 forest plots and found
     * that correlation with actual moisture DOUBLED with multiple-flow routing; D8 was
     * among the worst. For a habitat surface whose whole purpose is to find moist ground,
     * using D8 would have thrown away half the signal for a simpler loop.
     *
     * Returns a TWI grid the same size as [g].
     */
    fun topographicWetnessIndex(g: Grid, exponent: Double = 1.1): DoubleArray {
        val n = g.w * g.h
        val acc = DoubleArray(n) { 1.0 }   // each cell contributes its own area (in cells)

        // Process cells from highest to lowest so upslope is always resolved first.
        val order = (0 until n).sortedByDescending { g.z[it] }

        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val dist = DoubleArray(8) { if (it % 2 == 0) 1.0 else sqrt(2.0) }
        // contour width each neighbour drains across, per Freeman's formulation
        val weightScale = DoubleArray(8) { if (it % 2 == 0) 0.5 else 0.354 }

        val w = g.w; val h = g.h
        val grad = DoubleArray(8)
        for (idx in order) {
            val x = idx % w; val y = idx / w
            val z0 = g.z[idx]
            var total = 0.0
            for (k in 0 until 8) {
                val nx = x + dx[k]; val ny = y + dy[k]
                if (!g.inBounds(nx, ny)) { grad[k] = 0.0; continue }
                val drop = (z0 - g.z[ny * w + nx]) / (dist[k] * g.cellSizeM)
                grad[k] = if (drop > 0) Math.pow(drop, exponent) * weightScale[k] else 0.0
                total += grad[k]
            }
            if (total <= 0.0) continue   // pit or flat: water stays put
            val a = acc[idx]
            for (k in 0 until 8) {
                if (grad[k] <= 0.0) continue
                val nx = x + dx[k]; val ny = y + dy[k]
                acc[ny * w + nx] += a * (grad[k] / total)
            }
        }

        val twi = DoubleArray(n)
        for (i in 0 until n) {
            val x = i % w; val y = i / w
            val (slopeDeg, _) = slopeAspect(g, x, y)
            // A floor on tan(beta) keeps flat cells from producing an infinite index.
            val tanB = tan(Math.toRadians(slopeDeg)).coerceAtLeast(0.001)
            val aPerWidth = (acc[i] * g.cellSizeM * g.cellSizeM) / g.cellSizeM
            twi[i] = ln(aPerWidth / tanB)
        }
        return twi
    }

    // ---------------------------------------------------------------- shaping helpers

    /**
     * A smooth optimum band: 1.0 across [lo]..[hi], falling to 0 over [soft] beyond each edge.
     *
     * This exists because the two variables that most obviously look monotonic are not.
     * "Lower is wetter is better" and "flatter is moister is better" both fail at the
     * bottom: the extension guidance states plainly that ginseng "will not grow in
     * waterlogged soil ... leaf-filled depressions ... water flows", and that flat sites
     * with poor drainage or a flooding history will not support it. A monotonic surface
     * would paint creek bottoms and seeps as the best ground on the map, which is the one
     * place a digger should not be sent.
     */
    fun band(v: Double, lo: Double, hi: Double, soft: Double): Double {
        if (soft <= 0.0) return if (v in lo..hi) 1.0 else 0.0
        return when {
            v < lo -> smoothStep((v - (lo - soft)) / soft)
            v > hi -> smoothStep((((hi + soft) - v)) / soft)
            else -> 1.0
        }.coerceIn(0.0, 1.0)
    }

    /** Hermite smoothstep on 0..1, used so bands have no hard visual edges. */
    fun smoothStep(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    /** Logistic falloff, 1.0 at [centre], approaching 0 as v rises. */
    fun decreasing(v: Double, centre: Double, scale: Double): Double =
        1.0 / (1.0 + exp((v - centre) / scale.coerceAtLeast(1e-6)))
}
