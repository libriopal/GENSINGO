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

    /** Highest minus lowest real elevation over the interior, metres (no-data cells do not count). */
    fun interiorReliefM(m: DemTileStore.Mosaic): Double {
        val g = m.grid; val halo = m.haloPx
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (y in halo until g.h - halo) for (x in halo until g.w - halo) {
            if (!m.hasData(x, y)) continue
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
            for (xy in lines) for (run in realRuns(m, xy)) {
                val n = run.size
                if (n < 2) continue
                val keep = (0 until n).filter { it % keepEvery == 0 || it == n - 1 }
                val ll = DoubleArray(keep.size * 2)
                keep.forEachIndexed { k, r ->
                    val i = run[r]
                    // Isoline coordinates are interior samples; the mosaic's cells start at the halo.
                    ll[2 * k] = WaterLines.lngOfCell(m, xy[2 * i] + halo.toDouble())
                    ll[2 * k + 1] = WaterLines.latOfCell(m, xy[2 * i + 1] + halo.toDouble())
                }
                out += Line(level, index, ll)
            }
        }
        return out
    }

    /**
     * The runs of [xy]'s vertices (indices) that lie between cells of real elevation (B4): a
     * contour crossing the store's flat stand-in, or stacked along its edge, is not drawn. The
     * whole line when the mosaic has no gap.
     */
    private fun realRuns(m: DemTileStore.Mosaic, xy: FloatArray): List<IntArray> {
        val n = xy.size / 2
        if (m.noData == null) return listOf(IntArray(n) { it })
        val runs = ArrayList<IntArray>()
        var cur = ArrayList<Int>()
        for (i in 0 until n) {
            val x0 = kotlin.math.floor(xy[2 * i].toDouble()).toInt() + m.haloPx
            val y0 = kotlin.math.floor(xy[2 * i + 1].toDouble()).toInt() + m.haloPx
            if (m.hasData(x0, y0) && m.hasData(x0 + 1, y0) && m.hasData(x0, y0 + 1) && m.hasData(x0 + 1, y0 + 1)) cur += i
            else { if (cur.size >= 2) runs += cur.toIntArray(); cur = ArrayList() }
        }
        if (cur.size >= 2) runs += cur.toIntArray()
        return runs
    }
}
