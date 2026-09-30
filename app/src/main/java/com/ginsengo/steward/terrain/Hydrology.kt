package com.ginsengo.steward.terrain

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Where water runs, traced from elevation alone, so it works offline wherever the heatmap does.
 *
 * Three standard steps:
 *
 *  1. **Depression filling**, Priority-Flood+ε (Barnes, Lehman & Mulla 2014, Computers &
 *     Geosciences 62:117-127). Elevation tiles have pits (sensor noise, and road fills that
 *     dam a culverted creek); without filling, every pit ends a stream. Cells are raised just
 *     enough (one float step above the cell that reached them) to drain, never lowered.
 *  2. **D8 routing** (O'Callaghan & Mark 1984): each cell drains to its steepest downhill
 *     neighbour. D8, not the multiple-flow routing the wetness index uses: MFD is the better
 *     model of soil moisture on a hillslope, but a channel is one line, and D8 is what draws it.
 *  3. **Contributing area**, accumulated downstream in topological order (no sort, O(n)).
 *
 * A channel is drawn where the area draining through a cell passes a threshold ([Kind]).
 * These are DISPLAY classes by contributing area, not surveyed hydrography: whether water is
 * flowing in a given week depends on season, rock and soil, which elevation cannot see.
 * Area is counted only within the loaded tiles, so a river entering from outside them is
 * drawn with less area than it really has.
 */
class Hydrology private constructor(
    val w: Int,
    val h: Int,
    val cellSizeM: Double,
    /** D8 direction index 0..7 into [DX]/[DY], or -1 where water leaves the grid. */
    private val dir: ByteArray,
    /** Contributing area in cells, the cell itself included. */
    private val area: FloatArray,
) {
    enum class Kind(val minAreaM2: Double, val label: String) {
        /** Hollows and draws: shape the coves ginseng grows in, often dry. */
        DRAINAGE(20_000.0, "small drainage"),
        CREEK(200_000.0, "creek"),
        STREAM(2_000_000.0, "stream"),
        ;

        companion object {
            fun of(areaM2: Double): Kind? = entries.lastOrNull { areaM2 >= it.minAreaM2 }
        }
    }

    /** One traced channel segment, upstream to downstream, of one [kind] throughout. */
    class Line(val cells: IntArray, val kind: Kind)

    private val cellArea = cellSizeM * cellSizeM

    fun areaM2(i: Int): Double = area[i] * cellArea

    fun kindAt(i: Int): Kind? = Kind.of(areaM2(i))

    /** Index of the cell [i] drains to, or -1 where water leaves the grid. */
    fun receiver(i: Int): Int {
        val d = dir[i].toInt()
        if (d < 0) return -1
        return (i / w + DY[d]) * w + (i % w + DX[d])
    }

    /**
     * Channel segments of at least [minKind]. A segment runs from a channel head, a junction
     * or a change of [Kind] down to the next junction or change, so each is drawn with one
     * width; junction cells end their inflows and start the outflow, so the network connects.
     */
    fun lines(minKind: Kind = Kind.DRAINAGE): List<Line> {
        val minCells = (minKind.minAreaM2 / cellArea).toFloat()
        val n = w * h
        fun isChannel(i: Int) = area[i] >= minCells
        val upstream = ByteArray(n)
        for (i in 0 until n) {
            if (!isChannel(i)) continue
            val r = receiver(i)
            if (r >= 0 && isChannel(r) && upstream[r] < 3) upstream[r]++
        }
        val out = ArrayList<Line>()
        val buf = IntArray(n.coerceAtMost(1 shl 16))
        for (s in 0 until n) {
            if (!isChannel(s) || upstream[s].toInt() == 1) continue
            // s is a head (no channel inflow) or a junction (two or more): segments start here.
            var start = s
            while (true) {
                var len = 0
                var c = start
                val kind = kindAt(c) ?: minKind
                var next = -1
                buf[len++] = c
                while (true) {
                    val r = receiver(c)
                    if (r < 0 || !isChannel(r)) break
                    buf[len++] = r
                    if (upstream[r].toInt() != 1) break          // junction: its own segment starts there
                    val k = kindAt(r) ?: minKind
                    if (k != kind || len == buf.size) { next = r; break }   // wider from here, or a very long run: continue in a new segment
                    c = r
                }
                // Labelled by the cells it runs through; the last one may already be the wider class.
                if (len >= 2) out += Line(buf.copyOf(len), kind)
                if (next < 0) break
                start = next
            }
        }
        return out
    }

    /**
     * The nearest cell of at least [minKind] to (x, y) within [maxRadiusCells], by straight
     * distance, or -1. Used for "nearest creek" in a suggestion.
     */
    fun nearestChannel(x: Int, y: Int, minKind: Kind, maxRadiusCells: Int): Int {
        val minCells = (minKind.minAreaM2 / cellArea).toFloat()
        return nearest(w, h, x, y, maxRadiusCells) { area[it] >= minCells }
    }

    /** Channel class per cell (0 none, else Kind.ordinal + 1): a compact map to keep around. */
    fun kindMap(): ByteArray = ByteArray(w * h) { i -> ((kindAt(i)?.ordinal ?: -1) + 1).toByte() }

    /** Total length of channel of at least [minKind], in metres. */
    fun lengthM(lines: List<Line>): Double {
        var total = 0.0
        for (l in lines) for (k in 1 until l.cells.size) {
            val a = l.cells[k - 1]; val b = l.cells[k]
            total += hypot((a % w - b % w).toDouble(), (a / w - b / w).toDouble()) * cellSizeM
        }
        return total
    }

    companion object {
        val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        private val DIST = DoubleArray(8) { if (it % 2 == 0) 1.0 else sqrt(2.0) }

        /**
         * The nearest cell to (x, y) within [maxRadiusCells] for which [hit] is true, by
         * straight distance, or -1. Square rings outward: a ring at Chebyshev radius r is at
         * least r away, so once a hit is closer than that nothing further out can beat it.
         */
        fun nearest(w: Int, h: Int, x: Int, y: Int, maxRadiusCells: Int, hit: (Int) -> Boolean): Int {
            var best = -1
            var bestD2 = Long.MAX_VALUE
            val maxD2 = maxRadiusCells.toLong() * maxRadiusCells
            fun visit(dx: Int, dy: Int) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) return
                val i = ny * w + nx
                if (!hit(i)) return
                val d2 = dx.toLong() * dx + dy.toLong() * dy
                if (d2 < bestD2 && d2 <= maxD2) { bestD2 = d2; best = i }
            }
            for (r in 0..maxRadiusCells) {
                if (best >= 0 && r.toLong() * r > bestD2) break
                if (r == 0) { visit(0, 0); continue }
                for (d in -r..r) { visit(d, -r); visit(d, r) }
                for (d in -r + 1 until r) { visit(-r, d); visit(r, d) }
            }
            return best
        }

        fun of(g: TerrainMath.Grid): Hydrology {
            val w = g.w; val h = g.h; val n = w * h
            val filled = fill(g)

            // D8 on the filled surface. After Priority-Flood+ε every interior cell has a
            // strictly lower neighbour (the one that reached it), so only edge cells can drain
            // off the grid.
            val dir = ByteArray(n)
            for (i in 0 until n) {
                val x = i % w; val y = i / w
                val z0 = filled[i]
                var best = -1; var bestDrop = 0.0
                for (k in 0 until 8) {
                    val nx = x + DX[k]; val ny = y + DY[k]
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val drop = (z0 - filled[ny * w + nx]) / DIST[k]
                    if (drop > bestDrop) { bestDrop = drop; best = k }
                }
                dir[i] = best.toByte()
            }

            // Contributing area in topological order (Kahn): a cell is final once everything
            // draining into it has been added.
            val indeg = IntArray(n)
            fun recv(i: Int): Int {
                val d = dir[i].toInt()
                return if (d < 0) -1 else (i / w + DY[d]) * w + (i % w + DX[d])
            }
            for (i in 0 until n) { val r = recv(i); if (r >= 0) indeg[r]++ }
            val queue = IntArray(n)
            var head = 0; var tail = 0
            for (i in 0 until n) if (indeg[i] == 0) queue[tail++] = i
            val area = FloatArray(n) { 1f }
            while (head < tail) {
                val i = queue[head++]
                val r = recv(i)
                if (r < 0) continue
                area[r] += area[i]
                if (--indeg[r] == 0) queue[tail++] = r
            }
            return Hydrology(w, h, g.cellSizeM, dir, area)
        }

        /**
         * Priority-Flood+ε: flood inward from the edges in order of elevation; a cell reached
         * from a higher one is raised to one float step above it, so every cell drains.
         */
        internal fun fill(g: TerrainMath.Grid): FloatArray {
            val w = g.w; val h = g.h; val n = w * h
            val filled = g.z.copyOf()
            val closed = BooleanArray(n)
            val heap = MinHeap(maxOf(16, 2 * (w + h)))
            for (x in 0 until w) for (y in intArrayOf(0, h - 1)) {
                val i = y * w + x
                if (!closed[i]) { closed[i] = true; heap.push(filled[i], i) }
            }
            for (y in 1 until h - 1) for (x in intArrayOf(0, w - 1)) {
                val i = y * w + x
                if (!closed[i]) { closed[i] = true; heap.push(filled[i], i) }
            }
            while (heap.size > 0) {
                val c = heap.popIndex()
                val zc = filled[c]
                val x = c % w; val y = c / w
                for (k in 0 until 8) {
                    val nx = x + DX[k]; val ny = y + DY[k]
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val i = ny * w + nx
                    if (closed[i]) continue
                    closed[i] = true
                    if (filled[i] <= zc) filled[i] = Math.nextUp(zc)
                    heap.push(filled[i], i)
                }
            }
            return filled
        }
    }

    /** Binary min-heap of (float key, int index): primitive, because ART does not unbox. */
    internal class MinHeap(capacity: Int) {
        private var keys = FloatArray(capacity)
        private var vals = IntArray(capacity)
        var size = 0
            private set

        fun push(key: Float, value: Int) {
            if (size == keys.size) {
                keys = keys.copyOf(size * 2); vals = vals.copyOf(size * 2)
            }
            var i = size++
            while (i > 0) {
                val p = (i - 1) / 2
                if (keys[p] <= key) break
                keys[i] = keys[p]; vals[i] = vals[p]; i = p
            }
            keys[i] = key; vals[i] = value
        }

        fun popIndex(): Int {
            val top = vals[0]
            size--
            if (size > 0) {
                val k = keys[size]; val v = vals[size]
                var i = 0
                while (true) {
                    val l = 2 * i + 1
                    if (l >= size) break
                    val r = l + 1
                    val c = if (r < size && keys[r] < keys[l]) r else l
                    if (keys[c] >= k) break
                    keys[i] = keys[c]; vals[i] = vals[c]; i = c
                }
                keys[i] = k; vals[i] = v
            }
            return top
        }
    }
}
