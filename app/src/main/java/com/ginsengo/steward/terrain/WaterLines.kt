package com.ginsengo.steward.terrain

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/**
 * Channel lines as map polylines: cut to the displayed interior (the halo's drainage areas
 * are truncated at the grid edge), smoothed so the D8 staircase reads as a creek, thinned,
 * and placed with exact Web Mercator cell centres.
 */
object WaterLines {

    /** One polyline, interleaved lng, lat. */
    class Polyline(val kind: Hydrology.Kind, val lngLat: DoubleArray) {
        val points: Int get() = lngLat.size / 2
    }

    fun of(m: DemTileStore.Mosaic, lines: List<Hydrology.Line>, keepEvery: Int = 2): List<Polyline> {
        val w = m.grid.w; val h = m.grid.h; val halo = m.haloPx
        fun inside(c: Int) = c % w in halo until w - halo && c / w in halo until h - halo
        val out = ArrayList<Polyline>()
        for (line in lines) {
            var start = -1
            for (k in 0..line.cells.size) {
                val ok = k < line.cells.size && inside(line.cells[k])
                if (ok && start < 0) start = k
                if (!ok && start >= 0) {
                    if (k - start >= 2) out += polyline(m, line, start, k, keepEvery)
                    start = -1
                }
            }
        }
        return out
    }

    private fun polyline(m: DemTileStore.Mosaic, line: Hydrology.Line, from: Int, to: Int, keepEvery: Int): Polyline {
        val w = m.grid.w
        val n = to - from
        val xs = DoubleArray(n) { (line.cells[from + it] % w).toDouble() }
        val ys = DoubleArray(n) { (line.cells[from + it] / w).toDouble() }
        smooth(xs); smooth(ys)
        val keep = (0 until n).filter { it % keepEvery == 0 || it == n - 1 }
        val out = DoubleArray(keep.size * 2)
        keep.forEachIndexed { j, i ->
            out[2 * j] = lngOfCell(m, xs[i])
            out[2 * j + 1] = latOfCell(m, ys[i])
        }
        return Polyline(line.kind, out)
    }

    /** Longitude of a (fractional) cell column's centre. */
    fun lngOfCell(m: DemTileStore.Mosaic, x: Double): Double =
        (m.tileX0 * DemTileStore.TILE + x + 0.5) / (DemTileStore.TILE.toDouble() * (1 shl m.zoom)) * 360.0 - 180.0

    /** Latitude of a (fractional) cell row's centre: rows are linear in Mercator y, not latitude. */
    fun latOfCell(m: DemTileStore.Mosaic, y: Double): Double {
        val wy = (m.tileY0 * DemTileStore.TILE + y + 0.5) / (DemTileStore.TILE.toDouble() * (1 shl m.zoom))
        return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * wy))))
    }

    /** Two passes of [0.25, 0.5, 0.25] with the ends fixed. */
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
}
