package com.ginsengo.steward.terrain

import com.ginsengo.steward.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HydrologyTest {

    private val cell = 10.0

    /** A V-shaped valley running north to south down the middle column, draining south. */
    private fun valley(n: Int = 200, sideSlope: Double = 0.3, fall: Double = 0.05): TerrainMath.Grid {
        val c = n / 2
        return TerrainMath.Grid(n, n, FloatArray(n * n) { i ->
            val x = i % n; val y = i / n
            (500 + abs(x - c) * cell * sideSlope + (n - y) * cell * fall).toFloat()
        }, cell)
    }

    /** A plane tilted south: water runs in parallel lines and never converges. */
    private fun plane(n: Int) = TerrainMath.Grid(n, n, FloatArray(n * n) { i -> (500 + (n - i / n) * cell * 0.1).toFloat() }, cell)

    @Test
    fun theChannelRunsDownTheValleyFloor() {
        val g = valley()
        val hy = Hydrology.of(g)
        val lines = hy.lines()
        assertTrue("a valley must produce a channel", lines.isNotEmpty())
        for (l in lines) for (c in l.cells) {
            assertTrue("channel cell at x=${c % g.w} is off the valley floor", abs(c % g.w - g.w / 2) <= 1)
        }
        // Everything drains out the south edge: the outlet carries (almost) the whole grid.
        val outlet = (g.h - 1) * g.w + g.w / 2
        assertTrue(hy.areaM2(outlet) > 0.95 * g.w * g.h * cell * cell)
        assertEquals(Hydrology.Kind.STREAM, hy.kindAt(outlet))
    }

    /** Negative control: the same size of ground, tilted but not converging, has no channel. */
    @Test
    fun aPlaneHasNoChannelsAValleyOfTheSameSizeDoes() {
        assertTrue(Hydrology.of(plane(140)).lines().isEmpty())
        assertTrue(Hydrology.of(valley(140)).lines().isNotEmpty())
    }

    /** A pit in the valley floor must not end the stream: the filled surface drains through it. */
    @Test
    fun aPitDoesNotBreakTheStream() {
        val base = valley()
        val pitted = TerrainMath.Grid(base.w, base.h, base.z.copyOf(), cell)
        val c = base.w / 2
        for (y in 95..100) for (x in c - 2..c + 2) pitted.z[y * base.w + x] -= 8f
        val outlet = (base.h - 1) * base.w + c
        val clean = Hydrology.of(base).areaM2(outlet)
        val withPit = Hydrology.of(pitted).areaM2(outlet)
        assertEquals("the pit swallowed the stream", clean, withPit, clean * 0.01)
    }

    @Test
    fun fillingNeverLowersAndLeavesEveryInteriorCellDraining() {
        val g = valley()
        for (y in 50..60) for (x in 20..30) g.z[y * g.w + x] -= 30f     // a closed basin on a slope
        val f = Hydrology.fill(g)
        for (i in f.indices) assertTrue(f[i] >= g.z[i])
        for (y in 1 until g.h - 1) for (x in 1 until g.w - 1) {
            val z = f[y * g.w + x]
            var lower = false
            for (k in 0 until 8) if (f[(y + Hydrology.DY[k]) * g.w + x + Hydrology.DX[k]] < z) lower = true
            assertTrue("cell $x,$y cannot drain", lower)
        }
    }

    @Test
    fun linesAreConnectedDownhillAndWidenDownstream() {
        val g = valley()
        val hy = Hydrology.of(g)
        for (l in hy.lines()) {
            for (k in 1 until l.cells.size) {
                val a = l.cells[k - 1]; val b = l.cells[k]
                assertEquals("each step follows the flow", hy.receiver(a), b)
                assertTrue(hy.areaM2(b) >= hy.areaM2(a))
            }
            // Every cell before the last is of the line's own class.
            for (k in 0 until l.cells.size - 1) assertEquals(l.kind, hy.kindAt(l.cells[k]))
        }
    }

    @Test
    fun nearestChannelFindsTheValleyFloorAndRespectsTheRadius() {
        val g = valley()
        val hy = Hydrology.of(g)
        val c = g.w / 2
        val i = hy.nearestChannel(c + 25, 150, Hydrology.Kind.DRAINAGE, 60)
        assertTrue(i >= 0)
        assertTrue("found x=${i % g.w}", abs(i % g.w - c) <= 1)
        assertEquals(-1, hy.nearestChannel(c + 25, 150, Hydrology.Kind.DRAINAGE, 20))
    }

    /** On real ground the traced channels sit in valleys: lower than their surroundings. */
    @Test
    fun onRealTerrainChannelsRunInValleys() {
        val m = Fixtures.mosaic("scan_boone_z12.bin")
        val g = m.grid
        val hy = Hydrology.of(g)
        val lines = hy.lines(Hydrology.Kind.CREEK)
        assertTrue("no creeks traced on real terrain", lines.size > 5)
        val sat = TerrainMath.SummedArea(g)
        val r = 5
        var onChannel = 0.0; var nC = 0
        for (l in lines) for (c in l.cells) {
            val x = c % g.w; val y = c / g.w
            if (x < r || y < r || x >= g.w - r || y >= g.h - r) continue
            onChannel += TerrainMath.tpiFast(g, sat, x, y, r); nC++
        }
        var all = 0.0; var nA = 0
        for (y in r until g.h - r step 4) for (x in r until g.w - r step 4) { all += TerrainMath.tpiFast(g, sat, x, y, r); nA++ }
        val meanChannel = onChannel / nC
        val meanAll = all / nA
        assertTrue("channel TPI $meanChannel vs overall $meanAll", meanChannel < meanAll - 5.0 && meanChannel < 0.0)
        assertTrue("km of creek: ${hy.lengthM(lines) / 1000}", hy.lengthM(lines) > 10_000)
    }

    @Test
    fun theHeapPopsInOrder() {
        val h = Hydrology.MinHeap(4)
        val keys = floatArrayOf(5f, 1f, 9f, 3f, 3f, 7f, 0f, 2f, 8f)
        keys.forEachIndexed { i, k -> h.push(k, i) }
        val out = ArrayList<Float>()
        while (h.size > 0) out += keys[h.popIndex()]
        assertEquals(keys.sorted(), out)
    }
}

/** Missing elevation tiles are edge-extended, never left as 0 m cliffs (Phase 8). */
class MissingTileFillTest {

    private val t = DemTileStore.TILE

    @Test
    fun missingTilesTakeTheNearestLoadedEdgeAndLoadedDataIsUntouched() {
        val nx = 3; val ny = 3; val w = nx * t
        // Loaded: only the middle column of tiles, each pixel = 1000 + its column.
        val loaded = BooleanArray(nx * ny) { it % nx == 1 }
        val z = FloatArray(w * ny * t)
        for (r in 0 until ny * t) for (c in t until 2 * t) z[r * w + c] = 1000f + c
        val before = z.copyOf()
        DemTileStore.fillMissing(z, nx, ny, loaded)
        assertTrue("a zero cliff survived", z.none { it == 0f })
        for (r in 0 until ny * t) {
            for (c in t until 2 * t) assertEquals(before[r * w + c], z[r * w + c])
            assertEquals("west fill = west edge", 1000f + t, z[r * w + 0])
            assertEquals("east fill = east edge", 1000f + 2 * t - 1, z[r * w + w - 1])
        }
    }

    @Test
    fun aWholeMissingRowOfTilesIsCopiedFromTheNearestRow() {
        val nx = 2; val ny = 3; val w = nx * t
        val loaded = BooleanArray(nx * ny) { it / nx == 1 }            // only the middle row
        val z = FloatArray(w * ny * t)
        for (r in t until 2 * t) for (c in 0 until w) z[r * w + c] = 500f + r
        DemTileStore.fillMissing(z, nx, ny, loaded)
        assertTrue(z.none { it == 0f })
        assertEquals(500f + t, z[0])                                    // top copies the middle's top row
        assertEquals(500f + 2 * t - 1, z[(3 * t - 1) * w])              // bottom copies its bottom row
    }
}
