package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.field.TravelCells
import com.ginsengo.steward.geo.Projection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The travel memory over one 3D square, as a small mask the GPU samples (J31, J20): north up, Web
 * Mercator, edge to edge over the same square the colour texture covers, so a texel here is the
 * same ground as the texel under it there.
 *
 * Kept apart from the colour texture on purpose: a new cell flips a few bytes here and uploads a
 * 512 × 512 two-byte texture, instead of re-baking the 16 MB colour texture every few seconds
 * while the owner walks.
 */
class TravelMask(
    north: Double, west: Double, south: Double, east: Double,
    /** The square's width in metres: how many texels the J20 buffer spans. */
    widthM: Double,
    val size: Int = SIZE,
) {
    /** 255 where the owner has been, 0 elsewhere; row 0 is the north edge. */
    val visited = ByteArray(size * size)

    private val x0 = Projection.x(west)
    private val x1 = Projection.x(east)
    private val y0 = Projection.y(north)
    private val y1 = Projection.y(south)
    private val bounds = doubleArrayOf(north, west, south, east)

    /** Texels per side of the J20 buffer (15 m), rounded; 0 where one texel is already wider. */
    val bufferTexels: Int = (UNWALKED_BUFFER_M / (widthM / size)).roundToInt()

    /** Marks one cell; true when a texel changed. Cells outside the square change nothing. */
    fun mark(cell: Long): Boolean {
        if (!TravelCells.inBox(cell, bounds[0], bounds[1], bounds[2], bounds[3])) return false
        val c0 = col(TravelCells.west(cell)); val c1 = col(TravelCells.east(cell))
        val r0 = row(TravelCells.north(cell)); val r1 = row(TravelCells.south(cell))
        // Every texel the cell overlaps, at least one: at the coarsest level a cell is under a texel.
        val a = floor(c0).toInt().coerceIn(0, size - 1); val b = (ceil(c1).toInt() - 1).coerceIn(a, size - 1)
        val t = floor(r0).toInt().coerceIn(0, size - 1); val u = (ceil(r1).toInt() - 1).coerceIn(t, size - 1)
        var changed = false
        for (r in t..u) for (c in a..b) {
            val i = r * size + c
            if (visited[i] == 0.toByte()) { visited[i] = ON; changed = true }
        }
        return changed
    }

    fun markAll(cells: LongArray): Boolean {
        var changed = false
        for (c in cells) changed = mark(c) or changed
        return changed
    }

    private fun col(lng: Double) = (Projection.x(lng) - x0) / (x1 - x0) * size
    private fun row(lat: Double) = (Projection.y(lat) - y0) / (y1 - y0) * size

    /** The visited texels grown by a disk of [radius] texels: the 15 m J20 buffer. */
    fun dilated(radius: Int = bufferTexels): ByteArray {
        if (radius <= 0) return visited.copyOf()
        val out = ByteArray(size * size)
        val r2 = radius * radius
        for (y in 0 until size) for (x in 0 until size) {
            if (visited[y * size + x] == 0.toByte()) continue
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy < 0 || yy >= size) continue
                for (dx in -radius..radius) {
                    val xx = x + dx
                    if (xx < 0 || xx >= size || dx * dx + dy * dy > r2) continue
                    out[yy * size + xx] = ON
                }
            }
        }
        return out
    }

    /** Interleaved two-byte texels for the GPU: R = where you've been, G = the J20 buffer. */
    fun rg(buffer: ByteArray?): ByteArray {
        val g = buffer ?: visited
        val out = ByteArray(size * size * 2)
        for (i in 0 until size * size) { out[2 * i] = visited[i]; out[2 * i + 1] = g[i] }
        return out
    }

    companion object {
        const val SIZE = 512
        /** J20: ground within this of where you've been counts as walked. */
        const val UNWALKED_BUFFER_M = 15.0
        private const val ON = 0xFF.toByte()

        /** J20: strong ground the owner has not walked, texel by texel. */
        fun unwalked(strong: BooleanArray, buffer: ByteArray): BooleanArray =
            BooleanArray(strong.size) { strong[it] && buffer[it] == 0.toByte() }
    }
}
