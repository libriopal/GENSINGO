/*
Kotlin port of maplibre-contour's src/isolines.ts (marching-squares contour lines).

Source:  https://github.com/onthegomap/maplibre-contour/blob/a3c4e3c683f34999af2bf4c7349c6e4cda345fcb/src/isolines.ts
         commit a3c4e3c683f34999af2bf4c7349c6e4cda345fcb, which is both `main` and tag v0.1.1
         (package.json "version": "0.1.1"); fetched 2026-10-01; file SHA-256
         0d0cc133ca38f90779313332b5cd76a662b4e44fa8c0764f9679d7328a0e2ed2.
Licence: BSD-3-Clause (maplibre-contour), and ISC (d3-contour, from which isolines.ts is adapted).
         Both notices follow and are also in THIRD_PARTY_NOTICES.md at the repository root.

---------------------------------------------------------------------------------------------
BSD 3-Clause License

Copyright (c) 2023, Michael Barry and maplibre-contour contributors

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

---------------------------------------------------------------------------------------------
Adapted from d3-contour https://github.com/d3/d3-contour

Copyright 2012-2023 Mike Bostock

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice
and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
---------------------------------------------------------------------------------------------

What is the same as the original: the CASES table, index(), ratio(), the four-edge interpolate(),
the single pass over the grid building every level at once (the min/max carried from the right
column to the left), the strict `corner > threshold` test, skipping cells with a NaN corner, and
the Fragment bookkeeping (fragmentByStart / fragmentByEnd per level: close a ring, join two
fragments, extend the end, extend the start, or start a new fragment), then flushing the open
fragments level by level.

Deviations, and why:
 1. Input. The original reads a HeightTile through tile.get(x, y); this reads a row-major
    FloatArray, z[y * w + x], the layout of the app's other grids (TerrainMath.Grid), through a
    local get(x, y) that returns NaN outside the grid as a HeightTile does past its data.
 2. buffer = 0 (the original defaults to 1, reading one pixel into the neighbouring tiles that
    maplibre-contour stitches around each tile). A bare grid has no neighbours, so every cell the
    buffer adds would have a NaN corner and be skipped; 0 gives the same lines without the reads.
 3. Output coordinates are grid-cell coordinates, as the contract asks: sample (x, y) is the
    point (x, y), so vertices lie in [0, w - 1] x [0, h - 1]. The original scales by
    multiplier = extent / (width - 1) and Math.round()s every coordinate to integer vector-tile
    units; here the multiplier is 1 and nothing is rounded (rounding to whole cells would throw
    away the interpolation; IsolinesTest 3 checks it to 0.05 m).
 4. Levels are threshold = k * interval for whole k, instead of accumulating threshold +=
    interval, so the same level computed in different cells is the same double and stitches
    (accumulation drifts for intervals such as 0.1).
 5. A non-positive or NaN interval returns no lines. The original returns {} only for 0 (and
    NaN); a negative interval would loop forever there.
 6. The result is keyed by the level in whole metres (threshold rounded to Int, as the contract's
    signature asks) and sorted by level, as a JS object with integer keys iterates. Intervals
    below 1 m can round two levels to one key; their lines then share that key's list.
 7. Fragment keeps its points in a float buffer with room at both ends: the original's prepend
    is Array.splice(0, 0, x, y), O(n) per call. Same order of points, same results.
*/
package com.ginsengo.steward.terrain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private class Fragment(var start: Int, var end: Int) {
    // points: flat [x0, y0, x1, y1, ...] in buf[head until tail] (deviation 7).
    private var buf = FloatArray(16)
    private var head = 8
    private var tail = 8

    fun append(x: Double, y: Double) {
        if (tail + 2 > buf.size) grow(0, 2)
        buf[tail++] = x.toFloat()
        buf[tail++] = y.toFloat()
    }

    fun prepend(x: Double, y: Double) {
        if (head < 2) grow(2, 0)
        buf[--head] = y.toFloat()
        buf[--head] = x.toFloat()
    }

    fun lineString(): FloatArray = toArray()

    fun isEmpty(): Boolean = tail - head < 2

    fun appendFragment(other: Fragment) {
        val n = other.tail - other.head
        if (tail + n > buf.size) grow(0, n)
        System.arraycopy(other.buf, other.head, buf, tail, n)
        tail += n
        this.end = other.end
    }

    fun toArray(): FloatArray = buf.copyOfRange(head, tail)

    /** Re-centre in a larger buffer with at least [front] free before and [back] free after. */
    private fun grow(front: Int, back: Int) {
        val n = tail - head
        val bigger = FloatArray(2 * (n + front + back) + 16)
        val newHead = (bigger.size - n) / 2
        System.arraycopy(buf, head, bigger, newHead, n)
        buf = bigger
        head = newHead
        tail = newHead + n
    }
}

// Edge midpoints of a cell in half-cell units: [0, 1] left, [2, 1] right, [1, 0] top, [1, 2] bottom.
// CASES[(tl ? 8 : 0) | (tr ? 4 : 0) | (br ? 2 : 0) | (bl ? 1 : 0)] lists each segment as [start, end].
private val CASES: Array<Array<Array<IntArray>>> = arrayOf(
    arrayOf(),
    arrayOf(arrayOf(intArrayOf(1, 2), intArrayOf(0, 1))),
    arrayOf(arrayOf(intArrayOf(2, 1), intArrayOf(1, 2))),
    arrayOf(arrayOf(intArrayOf(2, 1), intArrayOf(0, 1))),
    arrayOf(arrayOf(intArrayOf(1, 0), intArrayOf(2, 1))),
    arrayOf(
        arrayOf(intArrayOf(1, 2), intArrayOf(0, 1)),
        arrayOf(intArrayOf(1, 0), intArrayOf(2, 1)),
    ),
    arrayOf(arrayOf(intArrayOf(1, 0), intArrayOf(1, 2))),
    arrayOf(arrayOf(intArrayOf(1, 0), intArrayOf(0, 1))),
    arrayOf(arrayOf(intArrayOf(0, 1), intArrayOf(1, 0))),
    arrayOf(arrayOf(intArrayOf(1, 2), intArrayOf(1, 0))),
    arrayOf(
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 0)),
        arrayOf(intArrayOf(2, 1), intArrayOf(1, 2)),
    ),
    arrayOf(arrayOf(intArrayOf(2, 1), intArrayOf(1, 0))),
    arrayOf(arrayOf(intArrayOf(0, 1), intArrayOf(2, 1))),
    arrayOf(arrayOf(intArrayOf(1, 2), intArrayOf(2, 1))),
    arrayOf(arrayOf(intArrayOf(0, 1), intArrayOf(1, 2))),
    arrayOf(),
)

private fun index(width: Int, x: Int, y: Int, point: IntArray): Int {
    val px = x * 2 + point[0]
    val py = y * 2 + point[1]
    return px + py * (width + 1) * 2
}

private fun ratio(a: Double, b: Double, c: Double): Double = (b - a) / (c - a)

/** See deviation 2 in the header. */
private const val BUFFER = 0

/**
 * Generates contour lines from a height grid.
 *
 * @param z heights in metres, row-major (`z[y * w + x]`); NaN marks no data (cells touching it
 *   are skipped)
 * @param w grid width (columns)
 * @param h grid height (rows)
 * @param intervalM vertical distance between contours, metres
 * @return lines keyed by elevation level (metres), ascending; each line is a flat
 *   `[x0, y0, x1, y1, ...]` in grid-cell coordinates (sample (x, y) is the point (x, y)). A
 *   closed ring repeats its first point at the end.
 */
fun isolines(z: FloatArray, w: Int, h: Int, intervalM: Double): Map<Int, List<FloatArray>> {
    require(w >= 0 && h >= 0 && z.size == w * h) { "grid is ${z.size} values, not $w x $h" }
    if (!(intervalM > 0)) {
        return emptyMap()
    }
    val interval = intervalM
    fun get(x: Int, y: Int): Double =
        if (x in 0 until w && y in 0 until h) z[y * w + x].toDouble() else Double.NaN

    var tld = 0.0; var trd = 0.0; var bld = 0.0; var brd = 0.0
    var r = 0; var c = 0
    val segments = HashMap<Double, MutableList<FloatArray>>()
    val fragmentByStartByLevel = LinkedHashMap<Double, LinkedHashMap<Int, Fragment>>()
    val fragmentByEndByLevel = HashMap<Double, HashMap<Int, Fragment>>()

    fun interpolate(point: IntArray, threshold: Double, accept: (Double, Double) -> Unit) {
        if (point[0] == 0) {
            // left
            accept((c - 1).toDouble(), r - ratio(bld, threshold, tld))
        } else if (point[0] == 2) {
            // right
            accept(c.toDouble(), r - ratio(brd, threshold, trd))
        } else if (point[1] == 0) {
            // top
            accept(c - ratio(trd, threshold, tld), (r - 1).toDouble())
        } else {
            // bottom
            accept(c - ratio(brd, threshold, bld), r.toDouble())
        }
    }

    // Most marching-squares implementations (d3-contour, gdal-contour) make one pass through the matrix per threshold.
    // This implementation makes a single pass through the matrix, building up all of the contour lines at the
    // same time to improve performance.
    for (row in 1 - BUFFER until h + BUFFER) {
        r = row
        trd = get(0, r - 1)
        brd = get(0, r)
        var minR = minOf(trd, brd)
        var maxR = maxOf(trd, brd)
        for (col in 1 - BUFFER until w + BUFFER) {
            c = col
            tld = trd
            bld = brd
            trd = get(c, r - 1)
            brd = get(c, r)
            val minL = minR
            val maxL = maxR
            minR = minOf(trd, brd)
            maxR = maxOf(trd, brd)
            if (tld.isNaN() || trd.isNaN() || brd.isNaN() || bld.isNaN()) {
                continue
            }
            val min = minOf(minL, minR)
            val max = maxOf(maxL, maxR)
            // The original's start/end thresholds, as whole multiples of the interval (deviation 4).
            val kStart = ceil(min / interval).toInt()
            val kEnd = floor(max / interval).toInt()
            for (k in kStart..kEnd) {
                val threshold = k * interval
                val tl = tld > threshold
                val tr = trd > threshold
                val bl = bld > threshold
                val br = brd > threshold
                for (segment in CASES[(if (tl) 8 else 0) or (if (tr) 4 else 0) or (if (br) 2 else 0) or (if (bl) 1 else 0)]) {
                    val fragmentByStart = fragmentByStartByLevel.getOrPut(threshold) { LinkedHashMap() }
                    val fragmentByEnd = fragmentByEndByLevel.getOrPut(threshold) { HashMap() }
                    val start = segment[0]
                    val end = segment[1]
                    val startIndex = index(w, c, r, start)
                    val endIndex = index(w, c, r, end)
                    var f: Fragment?
                    val g: Fragment?

                    f = fragmentByEnd[startIndex]
                    if (f != null) {
                        fragmentByEnd.remove(startIndex)
                        g = fragmentByStart[endIndex]
                        if (g != null) {
                            fragmentByStart.remove(endIndex)
                            if (f === g) {
                                // closing a ring
                                interpolate(end, threshold, f::append)
                                if (!f.isEmpty()) {
                                    segments.getOrPut(threshold) { ArrayList() }.add(f.lineString())
                                }
                            } else {
                                // connecting 2 segments
                                f.appendFragment(g)
                                f.end = g.end
                                fragmentByEnd[f.end] = f
                            }
                        } else {
                            // adding to the end of f
                            interpolate(end, threshold, f::append)
                            f.end = endIndex
                            fragmentByEnd[f.end] = f
                        }
                    } else {
                        f = fragmentByStart[endIndex]
                        if (f != null) {
                            fragmentByStart.remove(endIndex)
                            // extending the start of f
                            interpolate(start, threshold, f::prepend)
                            f.start = startIndex
                            fragmentByStart[f.start] = f
                        } else {
                            // starting a new fragment
                            val newFrag = Fragment(startIndex, endIndex)
                            interpolate(start, threshold, newFrag::append)
                            interpolate(end, threshold, newFrag::append)
                            fragmentByStart[startIndex] = newFrag
                            fragmentByEnd[endIndex] = newFrag
                        }
                    }
                }
            }
        }
    }

    for ((level, fragmentByStart) in fragmentByStartByLevel) {
        var list: MutableList<FloatArray>? = null
        for (value in fragmentByStart.values) {
            if (!value.isEmpty()) {
                if (list == null) {
                    list = segments.getOrPut(level) { ArrayList() }
                }
                list.add(value.lineString())
            }
        }
    }

    // Deviation 6: whole-metre keys, ascending.
    val byLevel = sortedMapOf<Int, MutableList<FloatArray>>()
    for (level in segments.keys.sorted()) {
        byLevel.getOrPut(level.roundToInt()) { ArrayList() }.addAll(segments.getValue(level))
    }
    return byLevel
}
