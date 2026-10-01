package com.ginsengo.steward.terrain

import kotlin.math.roundToInt

/**
 * Contour lines for the 2D map, as (lng, lat) polylines: [isolines] (the maplibre-contour port)
 * run over the mosaic's interior, the part the map shows; the halo around it is only there for
 * the terrain analysis. The 3D texture draws contours with the same interval rule
 * (TerrainTextures.contourInterval), so a contour read on one view is the same height on the other.
 */
object ContourLines {

    /** One line: its height, whether it is an index (every fifth) contour, and lng, lat pairs. */
    class Line(val levelM: Int, val index: Boolean, val lngLat: DoubleArray) {
        val points: Int get() = lngLat.size / 2
    }

    /** Highest minus lowest elevation over the interior, metres. */
    fun interiorReliefM(m: DemTileStore.Mosaic): Double {
        val g = m.grid; val halo = m.haloPx
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (y in halo until g.h - halo) for (x in halo until g.w - halo) {
            val v = g.z[y * g.w + x]
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        return if (hi < lo) 0.0 else (hi - lo).toDouble()
    }

    /**
     * Lines every [intervalM] over the interior. Every [keepEvery]-th vertex is kept (and each
     * line's last): marching squares puts a vertex in every cell a line crosses, about one per
     * screen pixel at the zoom the elevation tiles are chosen for, more than the map needs.
     */
    fun of(m: DemTileStore.Mosaic, intervalM: Double, keepEvery: Int = 2): List<Line> {
        val g = m.grid; val halo = m.haloPx
        val iw = g.w - 2 * halo; val ih = g.h - 2 * halo
        if (iw < 2 || ih < 2 || !(intervalM > 0)) return emptyList()
        val z = FloatArray(iw * ih)
        for (y in 0 until ih) System.arraycopy(g.z, (y + halo) * g.w + halo, z, y * iw, iw)
        val out = ArrayList<Line>()
        for ((level, lines) in isolines(z, iw, ih, intervalM)) {
            val index = (level / intervalM).roundToInt() % 5 == 0
            for (xy in lines) {
                val n = xy.size / 2
                if (n < 2) continue
                val keep = (0 until n).filter { it % keepEvery == 0 || it == n - 1 }
                val ll = DoubleArray(keep.size * 2)
                keep.forEachIndexed { k, i ->
                    // Isoline coordinates are interior samples; the mosaic's cells start at the halo.
                    ll[2 * k] = WaterLines.lngOfCell(m, xy[2 * i] + halo.toDouble())
                    ll[2 * k + 1] = WaterLines.latOfCell(m, xy[2 * i + 1] + halo.toDouble())
                }
                out += Line(level, index, ll)
            }
        }
        return out
    }
}
