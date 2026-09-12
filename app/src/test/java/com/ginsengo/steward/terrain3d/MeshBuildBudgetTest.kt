package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream

/**
 * Measures what a mesh build actually costs, and holds it to a budget.
 *
 * This exists because "rebuild while the camera moves" is only safe if a rebuild is cheap
 * enough to happen mid-gesture. Guessing at that is how you ship a map that freezes on every
 * pan, so the cost is measured on real terrain and the budget is enforced.
 *
 * The numbers here are wall-clock on a JVM, not on a phone, so they are a floor rather than
 * a prediction. A build that is already slow here has no chance on a handset.
 */
class MeshBuildBudgetTest {

    private fun fixtureGrid(cellSizeM: Double = 7.71): TerrainMath.Grid {
        val stream = javaClass.getResourceAsStream("/terrain_boone_z14.bin")
        assertNotNull("missing terrain fixture", stream)
        DataInputStream(stream!!.buffered()).use { d ->
            val magic = ByteArray(8); d.readFully(magic)
            val w = d.readInt(); val h = d.readInt()
            return TerrainMath.Grid(w, h, FloatArray(w * h) { d.readShort().toFloat() }, cellSizeM)
        }
    }

    private fun mosaic() = DemTileStore.Mosaic(
        grid = fixtureGrid(), zoom = 14,
        tileX0 = 4475, tileY0 = 6422, tilesX = 1, tilesY = 1,
        haloPx = 32, tilesLoaded = 1, tilesRequested = 1,
    )

    private fun camera() = MapCamera(36.2, -81.67, 14.0, 0.0, 50.0, 1080, 1920)

    private fun time(label: String, warmups: Int = 2, runs: Int = 3, body: () -> Unit): Long {
        repeat(warmups) { body() }
        var best = Long.MAX_VALUE
        repeat(runs) {
            val t0 = System.nanoTime()
            body()
            best = minOf(best, (System.nanoTime() - t0) / 1_000_000)
        }
        println("  $label: ${best} ms")
        return best
    }

    @Test
    fun meshBuildStaysWithinAnInteractiveBudget() {
        val mo = mosaic()
        val cam = camera()
        println("mesh build cost (256x256 DEM mosaic, JVM):")

        // Tight TPI radius, no suitability: the cheapest useful configuration.
        val plain = time("gridN=192, tpiRadius=15 cells, no suitability") {
            TerrainMesh.build(mo, cam, 192, tpiRadiusM = 120.0, withSuitability = false)
        }

        // With the forecast tint, which adds multiple-flow accumulation over the mosaic.
        val tinted = time("gridN=192, tpiRadius=15 cells, with suitability") {
            TerrainMesh.build(mo, cam, 192, tpiRadiusM = 120.0, withSuitability = true)
        }

        // The wide-radius case that a zoomed-out camera asks for.
        val wide = time("gridN=128, tpiRadius=1500 m, with suitability") {
            TerrainMesh.build(mo, cam, 128, tpiRadiusM = 1500.0, withSuitability = true)
        }

        // A rebuild happens off the main thread while the old mesh stays on screen, so it
        // does not have to hit a frame budget — but it must finish inside a gesture, or the
        // terrain visibly trails the map all the way through a pan.
        assertTrue("plain build too slow for mid-gesture rebuild: $plain ms", plain < 400)
        assertTrue("tinted build too slow for mid-gesture rebuild: $tinted ms", tinted < 600)
        assertTrue("wide-radius build too slow for mid-gesture rebuild: $wide ms", wide < 600)
    }

    /**
     * Isolates the topographic position index, because its cost scales with the SQUARE of
     * the radius in the naive form and the radius is chosen from the camera zoom. If this
     * is the hot spot, a wide-radius build at low zoom is the worst case in the whole app.
     */
    @Test
    fun topographicPositionIndexCostDoesNotExplodeWithRadius() {
        val g = fixtureGrid()
        println("TPI cost over ${g.w}x${g.h}, sampled on a 192x192 lattice:")
        val sat = TerrainMath.SummedArea(g)
        val timings = listOf(5, 15, 40, 60).map { r ->
            r to time("radius=$r cells") {
                var acc = 0.0
                for (j in 0 until 192) for (i in 0 until 192) {
                    val x = 1 + i * (g.w - 3) / 191
                    val y = 1 + j * (g.h - 3) / 191
                    acc += TerrainMath.tpiFast(g, sat, x, y, r)
                }
                check(acc.isFinite())
            }
        }
        val smallest = timings.first().second
        val largest = timings.last().second
        println("  radius 5 -> 60 cost ratio: ${if (smallest == 0L) "n/a" else "${largest / maxOf(smallest, 1L)}x"}")
        // The summed-area path is O(radius) — one rectangle query per row of the disk —
        // not O(1). A square window WOULD have been O(1), and it was measured and rejected:
        // it moved some cells' final ginseng score by 0.125, more than half a suitability
        // band. Exactness was worth the linear term. Naive was O(radius^2): 8 ms at r=5 and
        // 1142 ms at r=60, a 142x spread, against ~10x now.
        assertTrue(
            "widest TPI radius must stay inside the build budget, got $largest ms",
            largest < 100,
        )
        assertTrue(
            "TPI cost must be sub-quadratic in radius (r=5 ${smallest} ms, r=60 ${largest} ms)",
            largest <= maxOf(smallest, 1L) * 20,
        )
    }
}
