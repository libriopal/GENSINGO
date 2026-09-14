package com.ginsengo.steward.terrain

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a DEM mosaic into an ARGB suitability raster for the map.
 *
 * The compositing, terrain draping and final filtering are done on the GPU by MapLibre once
 * this bitmap is handed to an ImageSource. What happens here is the per-cell terrain
 * analysis that no map style can express: multiple-flow accumulation and a multi-scale
 * topographic position index are neighbourhood operators over the whole mosaic, and there
 * is no style expression or built-in layer that computes them.
 */
object SuitabilityRasterizer {

    /** One rasterised surface plus the numbers needed to place and explain it. */
    data class Raster(
        val bitmap: Bitmap,
        val north: Double, val west: Double, val south: Double, val east: Double,
        val demZoom: Int,
        val metresPerDemCell: Double,
        val tpiRadiusCells: Int,
        val superSample: Int,
        val tilesLoaded: Int, val tilesRequested: Int,
    ) {
        val coverage: Double
            get() = if (tilesRequested == 0) 0.0 else tilesLoaded.toDouble() / tilesRequested
    }

    /**
     * @param mosaic       elevation, halo included
     * @param outSize      output raster edge length in pixels
     * @param tpiRadiusM   neighbourhood radius for the position index, in METRES
     * @param minScore     scores below this render fully transparent, so the map stays
     *                     readable instead of being tinted edge to edge
     */
    suspend fun rasterise(
        mosaic: DemTileStore.Mosaic,
        outSize: Int,
        tpiRadiusM: Double,
        minScore: Double = 0.35,
    ): Raster = withContext(Dispatchers.Default) {
        val g = mosaic.grid
        val halo = mosaic.haloPx

        // Interior region (halo cropped) is what actually gets displayed.
        val ix0 = halo
        val iy0 = halo
        val iw = g.w - 2 * halo
        val ih = g.h - 2 * halo

        val tpiRadiusCells = (tpiRadiusM / g.cellSizeM).roundToInt().coerceIn(1, 60)

        // Wetness and the summed-area table are whole-mosaic operators and neither depends
        // on the camera, so they are cached against the mosaic rather than recomputed for
        // every viewport the user pans through.
        val analysis = TerrainAnalysis.of(mosaic, withWetness = true)

        // ---- ADAPTIVE ANTIALIASING -------------------------------------------------
        // The ratio between one output pixel and one DEM cell decides which artefact is
        // about to appear, and they need opposite fixes:
        //
        //   many DEM cells per output pixel  -> point-sampling ALIASES. Ridge lines beat
        //       against the sample grid and the heatmap shimmers as the camera moves.
        //       Fix: supersample and average, so each output pixel integrates the cells it
        //       actually covers.
        //   many output pixels per DEM cell  -> point-sampling looks BLOCKY, and blockiness
        //       here reads as false precision: a chunky square implies the model knows the
        //       terrain to that edge. Fix: bilinear interpolation between cell centres.
        //
        // So the supersample factor is derived from the ratio rather than fixed, and the
        // sub-cell case falls through to interpolation.
        val cellsPerOutPx = iw.toDouble() / outSize
        val superSample = when {
            cellsPerOutPx >= 4.0 -> 4
            cellsPerOutPx >= 2.0 -> 3
            cellsPerOutPx >= 1.0 -> 2
            else -> 1
        }

        val px = IntArray(outSize * outSize)

        for (oy in 0 until outSize) {
            for (ox in 0 until outSize) {
                var acc = 0.0
                var n = 0
                for (sy in 0 until superSample) {
                    for (sx in 0 until superSample) {
                        val fx = (ox + (sx + 0.5) / superSample) / outSize
                        val fy = (oy + (sy + 0.5) / superSample) / outSize
                        val gx = ix0 + fx * iw
                        val gy = iy0 + fy * ih
                        acc += scoreAt(g, analysis, gx, gy, mosaic, tpiRadiusCells, superSample == 1)
                        n++
                    }
                }
                val s = if (n == 0) 0.0 else acc / n
                px[oy * outSize + ox] = colourFor(s, minScore)
            }
        }

        val bmp = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, outSize, 0, 0, outSize, outSize)

        Raster(
            bitmap = bmp,
            north = latOfRow(mosaic, iy0.toDouble()),
            west = lonOfCol(mosaic, ix0.toDouble()),
            south = latOfRow(mosaic, (iy0 + ih).toDouble()),
            east = lonOfCol(mosaic, (ix0 + iw).toDouble()),
            demZoom = mosaic.zoom,
            metresPerDemCell = g.cellSizeM,
            tpiRadiusCells = tpiRadiusCells,
            superSample = superSample,
            tilesLoaded = mosaic.tilesLoaded,
            tilesRequested = mosaic.tilesRequested,
        )
    }

    private fun scoreAt(
        g: TerrainMath.Grid,
        analysis: TerrainAnalysis,
        gx: Double, gy: Double,
        mosaic: DemTileStore.Mosaic,
        tpiRadiusCells: Int,
        interpolate: Boolean,
    ): Double {
        val xi = gx.toInt().coerceIn(1, g.w - 2)
        val yi = gy.toInt().coerceIn(1, g.h - 2)

        val (slopeDeg, aspectDeg) = TerrainMath.slopeAspect(g, xi, yi)
        val lat = mosaic.latAtRow(yi)
        val hl = TerrainMath.heatLoadIndex(lat, slopeDeg, aspectDeg)
        val tpi = analysis.tpi(xi, yi, tpiRadiusCells)
        val curv = TerrainMath.profileCurvature(g, xi, yi)

        val wet = analysis.twiAt(xi, yi)
        val elev = if (interpolate) {
            bilinearF(g.z, g.w, g.h, gx, gy)
        } else {
            g[xi, yi].toDouble()
        }

        return GinsengSuitability.score(
            heatLoadRaw = hl,
            tpiMeters = tpi,
            twi = wet,
            slopeDeg = slopeDeg,
            curvature = curv,
            elevationM = elev,
        ).score
    }

    private fun bilinear(a: DoubleArray, w: Int, h: Int, x: Double, y: Double): Double {
        val x0 = x.toInt().coerceIn(0, w - 2); val y0 = y.toInt().coerceIn(0, h - 2)
        val tx = (x - x0).coerceIn(0.0, 1.0); val ty = (y - y0).coerceIn(0.0, 1.0)
        val v00 = a[y0 * w + x0]; val v10 = a[y0 * w + x0 + 1]
        val v01 = a[(y0 + 1) * w + x0]; val v11 = a[(y0 + 1) * w + x0 + 1]
        return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty
    }

    private fun bilinearF(a: FloatArray, w: Int, h: Int, x: Double, y: Double): Double {
        val x0 = x.toInt().coerceIn(0, w - 2); val y0 = y.toInt().coerceIn(0, h - 2)
        val tx = (x - x0).coerceIn(0.0, 1.0); val ty = (y - y0).coerceIn(0.0, 1.0)
        val v00 = a[y0 * w + x0].toDouble(); val v10 = a[y0 * w + x0 + 1].toDouble()
        val v01 = a[(y0 + 1) * w + x0].toDouble(); val v11 = a[(y0 + 1) * w + x0 + 1].toDouble()
        return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty
    }

    private fun latOfRow(m: DemTileStore.Mosaic, row: Double): Double {
        val f = row / m.grid.h
        return m.northLat + (m.southLat - m.northLat) * f
    }

    private fun lonOfCol(m: DemTileStore.Mosaic, col: Double): Double {
        val f = col / m.grid.w
        return m.westLon + (m.eastLon - m.westLon) * f
    }

    /**
     * Sequential colour ramp: deep forest -> emerald -> pale chartreuse, monotonically
     * increasing in perceived lightness.
     *
     * The previous ramp ran deep teal -> luminous green (#00FF88, the brand accent) -> field
     * amber, and it was measured wrong in a way no screenshot would have shown. Its lightness
     * PEAKED in the middle: L* climbed to 88.6 at score 0.5 and then fell back to 83.6 at 1.0.
     * A ramp whose brightest point is the middle tells the digger that mediocre ground is the
     * best ground. Worse, the green-to-amber half separated its bands by a dE2000 of only 6.1,
     * which is below the threshold for reading a categorical legend - and, counter-intuitively,
     * that half was WORSE for normal colour vision (6.1) than for a deuteranope (10.7), so
     * "check it for colour blindness" would have found the defect while mis-attributing it.
     *
     * This ramp is monotonic in L* under normal, deuteranope and protanope simulation, with a
     * minimum adjacent-band separation of 12.6 dE2000 in all three. It gives up the brand
     * accent at the top end, which is the correct trade: #00FF88 is an identity colour and this
     * raster is data. The accent still owns the UI chrome.
     *
     * Measured and pinned by ColourRampTest; generated and cross-checked by
     * tools/check_palette.py. Alpha still ramps with the score, so the strongest ground is the
     * brightest AND most opaque and the basemap stays legible everywhere else.
     */
    private val RAMP = intArrayOf(
        0x041E1A, // 0.00  deep forest, nearly the base colour
        0x0B4D3A, // 0.25  shaded cove
        0x148F5B, // 0.50  emerald
        0x7FCE7A, // 0.75  new growth
        0xEAF6C8, // 1.00  pale chartreuse
    )

    fun colourFor(score: Double, minScore: Double): Int {
        if (score < minScore) return 0
        val t = ((score - minScore) / (1.0 - minScore)).coerceIn(0.0, 1.0)

        val segments = RAMP.size - 1
        val pos = t * segments
        val i = pos.toInt().coerceAtMost(segments - 1)
        val u = pos - i
        val c0 = RAMP[i]
        val c1 = RAMP[i + 1]
        val r = lerp((c0 shr 16) and 0xFF, (c1 shr 16) and 0xFF, u)
        val g = lerp((c0 shr 8) and 0xFF, (c1 shr 8) and 0xFF, u)
        val b = lerp(c0 and 0xFF, c1 and 0xFF, u)

        val a = (70 + 150 * t).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(a: Int, b: Int, t: Double): Int =
        (a + (b - a) * t).roundToInt().coerceIn(0, 255)
}
