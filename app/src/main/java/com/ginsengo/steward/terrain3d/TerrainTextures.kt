package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The colour draped over the 3D terrain, baked on the CPU at up to two texels per elevation
 * cell. Pure Kotlin, so every pixel of it is testable on the JVM.
 *
 * WHY A TEXTURE (Phase 8). The first 3D view coloured the mesh per VERTEX, from its own
 * suitability computation, every ~60 m, with a ramp whose low end was near-black and an
 * alpha that faded weak ground to the black clear colour. The user reported the heatmap did
 * not work in 3D. Here the habitat colour is [SuitabilityRasterizer.colourFor] applied to
 * [SuitabilityRasterizer.scoreGrid]: the exact function and ramp the 2D heatmap draws, at
 * the elevation data's own resolution, composited over a neutral relief so weak ground reads
 * as bare terrain instead of black.
 *
 * Layers, bottom to top: base (neutral relief + habitat, or hypsometric elevation), baked
 * hillshade (light from the north-west, the convention relief maps use), contour lines, and
 * channels traced by [Hydrology].
 */
object TerrainTextures {

    enum class Mode { HABITAT, ELEVATION }

    /** Everything the baker needs about the ground; produced once per 3D build. */
    class Ground(
        val mosaic: DemTileStore.Mosaic,
        /** Habitat score over the interior, [scoreSize] x [scoreSize] (scoreGrid's layout). */
        val scores: DoubleArray?,
        val scoreSize: Int,
        val lines: List<Hydrology.Line>,
        val exaggeration: Double,
    )

    const val MAX_SIZE = 2048
    const val MIN_SCORE = 0.35

    fun sizeFor(m: DemTileStore.Mosaic): Int {
        val interior = max(m.grid.w, m.grid.h) - 2 * m.haloPx
        return min(MAX_SIZE, 2 * interior)
    }

    /** A contour interval giving ~25-40 lines across the relief, from a list a map reader knows. */
    fun contourInterval(reliefM: Double): Double =
        doubleArrayOf(5.0, 10.0, 20.0, 25.0, 50.0, 100.0).firstOrNull { reliefM / it <= 40.0 } ?: 200.0

    /** ARGB pixels, row-major, north up, covering the mosaic interior. */
    fun bake(ground: Ground, mode: Mode, size: Int = sizeFor(ground.mosaic)): IntArray {
        val m = ground.mosaic
        val g = m.grid
        val halo = m.haloPx
        val iw = g.w - 2 * halo
        val ih = g.h - 2 * halo
        val out = IntArray(size * size)
        val elev = FloatArray(size * size)

        // Elevation at every texel centre, and the relief it spans.
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (ty in 0 until size) {
            val gy = halo + (ty + 0.5) / size * ih - 0.5
            for (tx in 0 until size) {
                val gx = halo + (tx + 0.5) / size * iw - 0.5
                val e = bilinear(g.z, g.w, g.h, gx, gy)
                elev[ty * size + tx] = e
                if (e < lo) lo = e; if (e > hi) hi = e
            }
        }
        val span = (hi - lo).coerceAtLeast(1f)
        val cellX = g.cellSizeM * iw / size          // metres per texel
        val cellY = g.cellSizeM * ih / size

        for (ty in 0 until size) {
            for (tx in 0 until size) {
                val i = ty * size + tx
                val e = elev[i]
                val t = ((e - lo) / span).toDouble()
                var rgb = when (mode) {
                    Mode.ELEVATION -> ramp(ELEVATION_RAMP, t)
                    Mode.HABITAT -> {
                        val base = ramp(NEUTRAL_RAMP, t)
                        val s = ground.scores
                        if (s == null) base else {
                            val score = bilinear(s, ground.scoreSize, ground.scoreSize,
                                (tx + 0.5) / size * ground.scoreSize - 0.5,
                                (ty + 0.5) / size * ground.scoreSize - 0.5).toDouble()
                            over(base, SuitabilityRasterizer.colourFor(score, MIN_SCORE))
                        }
                    }
                }
                // Hillshade from the texel gradient (central differences, clamped at edges).
                val xl = elev[ty * size + max(tx - 1, 0)]; val xr = elev[ty * size + min(tx + 1, size - 1)]
                val yu = elev[max(ty - 1, 0) * size + tx]; val yd = elev[min(ty + 1, size - 1) * size + tx]
                val dzdx = (xr - xl) / (2 * cellX) * ground.exaggeration
                val dzdy = (yd - yu) / (2 * cellY) * ground.exaggeration   // rows run south
                // Normal (east, north, up) = (-dz/de, -dz/dn, 1) with dz/dn = -dzdy.
                val nx = -dzdx; val ny = dzdy; val len = sqrt(nx * nx + ny * ny + 1.0)
                val hs = ((nx * LIGHT_E + ny * LIGHT_N + LIGHT_UP) / len).coerceAtLeast(0.0)
                val shade = (0.28 + 0.72 * hs / LIGHT_UP).coerceIn(0.22, 1.18)
                rgb = scale(rgb, shade)
                out[i] = rgb or (0xFF shl 24)
            }
        }

        drawContours(out, elev, size, contourInterval((hi - lo).toDouble()))
        drawChannels(out, ground, size)
        return out
    }

    /**
     * A texel is on a contour where its elevation band differs from its east or south
     * neighbour's; every fifth (index) contour is stronger, as on a topo sheet. Lines are
     * dark on light ground and light on dark ground: dark-only contours vanished in shaded
     * coves and at the low end of the elevation tint (caught by Terrain3DTest).
     */
    private fun drawContours(px: IntArray, elev: FloatArray, size: Int, interval: Double) {
        fun band(v: Float) = floor(v / interval).toInt()
        for (ty in 0 until size - 1) for (tx in 0 until size - 1) {
            val i = ty * size + tx
            val b = band(elev[i])
            val be = band(elev[i + 1]); val bs = band(elev[i + size])
            if (b == be && b == bs) continue
            val level = max(b, max(be, bs))
            val index = level % 5 == 0
            px[i] = if (luminance(px[i]) >= 80.0) mixTo(px[i], CONTOUR, if (index) 0.62 else 0.34)
            else mixTo(px[i], CONTOUR_LIGHT, if (index) 0.46 else 0.24)
        }
    }

    /** Channels as anti-aliased strokes, wider and more opaque for more area. */
    private fun drawChannels(px: IntArray, ground: Ground, size: Int) {
        val m = ground.mosaic
        val g = m.grid
        val halo = m.haloPx
        val iw = g.w - 2 * halo
        val ih = g.h - 2 * halo
        val metresPerTexel = g.cellSizeM * iw / size
        for (line in ground.lines) {
            val n = line.cells.size
            val xs = DoubleArray(n) { ((line.cells[it] % g.w - halo) + 0.5) / iw * size - 0.5 }
            val ys = DoubleArray(n) { ((line.cells[it] / g.w - halo) + 0.5) / ih * size - 0.5 }
            smooth(xs); smooth(ys)
            val style = WATER.getValue(line.kind)
            val half = max(0.6, style.widthM / metresPerTexel / 2)
            for (k in 1 until n) stroke(px, size, xs[k - 1], ys[k - 1], xs[k], ys[k], half, style.rgb, style.alpha)
        }
    }

    private fun stroke(px: IntArray, size: Int, x0: Double, y0: Double, x1: Double, y1: Double,
                       half: Double, rgb: Int, alpha: Double) {
        val pad = half + 1.0
        val minX = max(0, floor(min(x0, x1) - pad).toInt()); val maxX = min(size - 1, (max(x0, x1) + pad).toInt())
        val minY = max(0, floor(min(y0, y1) - pad).toInt()); val maxY = min(size - 1, (max(y0, y1) + pad).toInt())
        if (minX > maxX || minY > maxY) return
        val dx = x1 - x0; val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        for (y in minY..maxY) for (x in minX..maxX) {
            val t = if (len2 == 0.0) 0.0 else (((x - x0) * dx + (y - y0) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(x - (x0 + t * dx), y - (y0 + t * dy))
            val cover = (half + 0.5 - d).coerceIn(0.0, 1.0)
            if (cover <= 0.0) continue
            val i = y * size + x
            px[i] = mixTo(px[i], rgb, alpha * cover)
        }
    }

    /** Two passes of [0.25, 0.5, 0.25], ends fixed: D8 staircases become curves. */
    private fun smooth(v: DoubleArray) {
        if (v.size < 3) return
        repeat(2) {
            var prev = v[0]
            for (i in 1 until v.size - 1) {
                val cur = v[i]
                v[i] = 0.25 * prev + 0.5 * cur + 0.25 * v[i + 1]
                prev = cur
            }
        }
    }

    // ------------------------------------------------------------------ colour helpers

    class WaterStyle(val rgb: Int, val alpha: Double, val widthM: Double)

    val WATER = mapOf(
        Hydrology.Kind.DRAINAGE to WaterStyle(0x7CCBF5, 0.55, 2.5),
        Hydrology.Kind.CREEK to WaterStyle(0x3FB2F7, 0.92, 4.5),
        Hydrology.Kind.STREAM to WaterStyle(0x2A95F0, 1.0, 8.0),
    )

    const val CONTOUR = 0x0B1210
    const val CONTOUR_LIGHT = 0xD6E4DC

    internal fun luminance(c: Int): Double =
        0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255)

    /** Muted relief under the habitat colour: weak ground reads as bare terrain, not black. */
    val NEUTRAL_RAMP = intArrayOf(0x2A3531, 0x3A4641, 0x4C5852, 0x5F6A63, 0x747E76)

    /** Hypsometric tint, the stops the first 3D shader used, so "elevation" mode looks familiar. */
    val ELEVATION_RAMP = intArrayOf(0x06222E, 0x125A3A, 0x2E7D46, 0x8CA84E, 0xFFC857)

    // Light from the north-west, 45 degrees up, in (east, north, up).
    private const val LIGHT_E = -0.5
    private const val LIGHT_N = 0.5
    private const val LIGHT_UP = 0.70710678

    internal fun ramp(stops: IntArray, t: Double): Int {
        val segs = stops.size - 1
        val p = t.coerceIn(0.0, 1.0) * segs
        val i = p.toInt().coerceAtMost(segs - 1)
        val u = p - i
        val a = stops[i]; val b = stops[i + 1]
        fun ch(s: Int) = (((a shr s) and 255) + (((b shr s) and 255) - ((a shr s) and 255)) * u).roundToInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** [top] (ARGB, straight alpha) over opaque [base] (RGB). */
    internal fun over(base: Int, top: Int): Int {
        val a = ((top ushr 24) and 255) / 255.0
        if (a <= 0.0) return base and 0xFFFFFF
        return mixTo(base, top and 0xFFFFFF, a) and 0xFFFFFF
    }

    private fun mixTo(c: Int, to: Int, a: Double): Int {
        fun ch(s: Int) = ((((c shr s) and 255) * (1 - a)) + (((to shr s) and 255) * a)).roundToInt().coerceIn(0, 255)
        return (c and (0xFF shl 24)) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun scale(c: Int, k: Double): Int {
        fun ch(s: Int) = (((c shr s) and 255) * k).roundToInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun bilinear(a: FloatArray, w: Int, h: Int, x: Double, y: Double): Float {
        val x0 = x.toInt().coerceIn(0, w - 2); val y0 = y.toInt().coerceIn(0, h - 2)
        val tx = (x - x0).coerceIn(0.0, 1.0).toFloat(); val ty = (y - y0).coerceIn(0.0, 1.0).toFloat()
        val v00 = a[y0 * w + x0]; val v10 = a[y0 * w + x0 + 1]
        val v01 = a[(y0 + 1) * w + x0]; val v11 = a[(y0 + 1) * w + x0 + 1]
        return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty
    }

    private fun bilinear(a: DoubleArray, w: Int, h: Int, x: Double, y: Double): Float {
        val x0 = x.toInt().coerceIn(0, w - 2); val y0 = y.toInt().coerceIn(0, h - 2)
        val tx = (x - x0).coerceIn(0.0, 1.0); val ty = (y - y0).coerceIn(0.0, 1.0)
        val v00 = a[y0 * w + x0]; val v10 = a[y0 * w + x0 + 1]
        val v01 = a[(y0 + 1) * w + x0]; val v11 = a[(y0 + 1) * w + x0 + 1]
        return ((v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty).toFloat()
    }
}
