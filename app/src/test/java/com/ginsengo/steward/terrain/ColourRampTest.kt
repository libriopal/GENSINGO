package com.ginsengo.steward.terrain

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The heatmap legend has to be readable, and "readable" is a measurable property rather than a
 * matter of taste.
 *
 * Two things are pinned here, and neither is a contrast ratio. Contrast ratio compares a colour
 * to its BACKGROUND; a data ramp fails in a different way, by failing to separate its own
 * classes from EACH OTHER. So:
 *
 *  1. Perceived lightness must increase monotonically along the ramp. If it does not, the ramp
 *     has a bright spot somewhere in the middle and the digger reads that as the best ground.
 *     This is also what keeps the surface readable in greyscale, in direct sun, and to anyone
 *     for whom hue carries no information at all.
 *
 *  2. Adjacent legend bands must stay separated under dichromat simulation, because roughly 8%
 *     of male users see a projection of this ramp rather than the ramp.
 *
 * Simulation is Vienot, Brettel & Mollon (1999), applied in linear light. It is a harsh model -
 * a full dichromat, not the anomalous trichromacy most affected people actually have - which is
 * the right direction to be wrong in for a safety-adjacent display.
 */
class ColourRampTest {

    // ------------------------------------------------------------------ colour science

    private fun toLinear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun toSrgb(v: Double): Int {
        val c = v.coerceIn(0.0, 1.0)
        val s = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
        return (s * 255.0).toInt().coerceIn(0, 255)
    }

    private fun lab(rgb: Triple<Int, Int, Int>): Triple<Double, Double, Double> {
        val r = toLinear(rgb.first); val g = toLinear(rgb.second); val b = toLinear(rgb.third)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (841.0 / 108.0) * t + 4.0 / 29.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** CIEDE2000. The 1976 Euclidean distance badly misjudges saturated greens, which is most
     *  of this ramp, so the extra arithmetic is not optional here. */
    private fun deltaE(
        a: Triple<Double, Double, Double>,
        b: Triple<Double, Double, Double>,
    ): Double {
        val (l1, a1, b1) = a
        val (l2, a2, b2) = b
        val c1 = hypot(a1, b1); val c2 = hypot(a2, b2)
        val cb = (c1 + c2) / 2
        val gg = if (cb > 0) 0.5 * (1 - sqrt(cb.pow(7) / (cb.pow(7) + 25.0.pow(7)))) else 0.0
        val a1p = a1 * (1 + gg); val a2p = a2 * (1 + gg)
        val c1p = hypot(a1p, b1); val c2p = hypot(a2p, b2)
        val h1p = (Math.toDegrees(atan2(b1, a1p)) + 360) % 360
        val h2p = (Math.toDegrees(atan2(b2, a2p)) + 360) % 360
        val dlp = l2 - l1
        val dcp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p > h1p -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp) / 2)
        val lbar = (l1 + l2) / 2
        val cbp = (c1p + c2p) / 2
        val hbp = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(Math.toRadians(hbp - 30)) + 0.24 * cos(Math.toRadians(2 * hbp)) +
                0.32 * cos(Math.toRadians(3 * hbp + 6)) - 0.20 * cos(Math.toRadians(4 * hbp - 63))
        val dth = 30 * exp(-(((hbp - 275) / 25).pow(2)))
        val rc = if (cbp > 0) 2 * sqrt(cbp.pow(7) / (cbp.pow(7) + 25.0.pow(7))) else 0.0
        val sl = 1 + (0.015 * (lbar - 50).pow(2)) / sqrt(20 + (lbar - 50).pow(2))
        val sc = 1 + 0.045 * cbp
        val sh = 1 + 0.015 * cbp * t
        val rt = -sin(Math.toRadians(2 * dth)) * rc
        return sqrt(
            (dlp / sl).pow(2) + (dcp / sc).pow(2) + (dHp / sh).pow(2) +
                    rt * (dcp / sc) * (dHp / sh)
        )
    }

    private fun simulate(rgb: Triple<Int, Int, Int>, kind: String): Triple<Int, Int, Int> {
        if (kind == "normal") return rgb
        val r = toLinear(rgb.first); val g = toLinear(rgb.second); val b = toLinear(rgb.third)
        var l = 0.31399022 * r + 0.63951294 * g + 0.04649755 * b
        var m = 0.15537241 * r + 0.75789446 * g + 0.08670142 * b
        val s = 0.01775239 * r + 0.10944209 * g + 0.87256922 * b
        when (kind) {
            "protan" -> l = 2.02344 * m - 2.52581 * s
            "deutan" -> m = 0.494207 * l + 1.24827 * s
        }
        return Triple(
            toSrgb(5.47221206 * l - 4.64196010 * m + 0.16963708 * s),
            toSrgb(-1.12524190 * l + 2.29317094 * m - 0.16789520 * s),
            toSrgb(0.02980165 * l - 0.19318073 * m + 1.16364789 * s),
        )
    }

    // ------------------------------------------------------------------ ramps

    /** The shipped ramp, read through the real production function. */
    private fun shipped(t: Double): Triple<Int, Int, Int> {
        val argb = SuitabilityRasterizer.colourFor(t, 0.0)
        return Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }

    /** The ramp this replaced, kept as the negative control. */
    private fun previous(t: Double): Triple<Int, Int, Int> {
        fun l(a: Int, b: Int, u: Double) = (a + (b - a) * u).toInt()
        return if (t < 0.5) {
            val u = t / 0.5
            Triple(l(0x0E, 0x00, u), l(0x4A, 0xFF, u), l(0x5A, 0x88, u))
        } else {
            val u = (t - 0.5) / 0.5
            Triple(l(0x00, 0xFF, u), l(0xFF, 0xC8, u), l(0x88, 0x57, u))
        }
    }

    private val steps = (0..20).map { it / 20.0 }
    private val bands = listOf(0.1, 0.3, 0.5, 0.7, 0.9)

    private fun reversals(ramp: (Double) -> Triple<Int, Int, Int>, kind: String): Int {
        val ls = steps.map { lab(simulate(ramp(it), kind)).first }
        return (0 until ls.size - 1).count { ls[it + 1] < ls[it] - 0.5 }
    }

    private fun minAdjacent(ramp: (Double) -> Triple<Int, Int, Int>, kind: String): Double =
        bands.zipWithNext().minOf { (a, b) ->
            deltaE(lab(simulate(ramp(a), kind)), lab(simulate(ramp(b), kind)))
        }

    // ------------------------------------------------------------------ the assertions

    @Test
    fun lightnessIncreasesMonotonicallyForNormalAndDichromatVision() {
        for (kind in listOf("normal", "deutan", "protan")) {
            val rev = reversals(::shipped, kind)
            assertTrue(
                "ramp lightness reverses $rev time(s) under $kind vision: a ramp that gets " +
                "darker as the score rises tells the digger that worse ground is better",
                rev == 0,
            )
        }
    }

    @Test
    fun adjacentLegendBandsStaySeparatedUnderDichromacy() {
        for (kind in listOf("normal", "deutan", "protan")) {
            val d = minAdjacent(::shipped, kind)
            assertTrue(
                "adjacent legend bands are only ${"%.1f".format(d)} dE2000 apart under $kind " +
                "vision; below about 10 a categorical legend cannot be read",
                d >= 10.0,
            )
        }
    }

    @Test
    fun theRampSpansEnoughLightnessToBeReadableInGreyscale() {
        val ls = steps.map { lab(shipped(it)).first }
        val span = ls.max() - ls.min()
        assertTrue(
            "only ${"%.1f".format(span)} L* between the worst and best ground; the surface " +
            "has to survive being printed, screenshotted in grey, or read in direct sun",
            span >= 60.0,
        )
    }

    /**
     * Negative control. Every assertion above must FAIL on the ramp this replaced, or the
     * instrument is measuring nothing and would have passed the defect it was written to catch.
     */
    @Test
    fun thePreviousRampFailsTheseSameChecks() {
        val rev = reversals(::previous, "normal")
        assertTrue(
            "the previous ramp peaked in lightness at mid-score - if this now reports 0 " +
            "reversals, the measurement is broken, not the history",
            rev > 0,
        )
        val d = minAdjacent(::previous, "normal")
        assertTrue(
            "the previous ramp separated its middle bands by only ~6 dE2000; got " +
            "${"%.1f".format(d)}, which means this test cannot detect the defect it exists for",
            d < 10.0,
        )
    }

    /**
     * Below the display threshold the ramp must be fully transparent, or ground the model
     * explicitly declined to rank would still be painted as if it had been ranked.
     */
    @Test
    fun groundBelowTheThresholdIsNotPaintedAtAll() {
        val argb = SuitabilityRasterizer.colourFor(0.2, minScore = 0.4)
        assertTrue("sub-threshold ground must be fully transparent", (argb ushr 24) == 0)
    }

    @Test
    fun opacityRisesWithScoreSoTheBasemapStaysLegibleOnPoorGround() {
        val low = SuitabilityRasterizer.colourFor(0.0, 0.0) ushr 24
        val high = SuitabilityRasterizer.colourFor(1.0, 0.0) ushr 24
        assertTrue("alpha must rise with the score, got $low -> $high", high > low)
        assertTrue("poor ground must stay translucent, got alpha $low", low < 110)
    }
}
