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

    /**
     * The whole HD scene the 3D view builds (Phase 8): a zoom-15 area is 5 x 5 tiles with its
     * halo, 1280 x 1280 cells. It is built once per ~1 km walked, off the main thread, while a
     * status line says so; the budget keeps that under a few seconds on a phone.
     */
    @Test
    fun theHdSceneBuildsWithinBudget() {
        val src = com.ginsengo.steward.Fixtures.mosaic("scan_boone_z12.bin").grid
        val n = 5 * DemTileStore.TILE
        val z = FloatArray(n * n) { i -> src.z[((i / n) % src.h) * src.w + (i % n) % src.w] }
        val mo = DemTileStore.Mosaic(TerrainMath.Grid(n, n, z, 3.9), 15, 8829, 12917, 5, 5, DemTileStore.TILE, 25, 25)
        println("HD scene (1280x1280 cells, 768 interior, JVM):")
        lateinit var scene: Terrain3D.Scene
        val build = time("scores + hydrology + mesh", warmups = 1, runs = 2) { scene = Terrain3D.build(mo) }
        val bake = time("texture ${scene.textureSize}^2", warmups = 1, runs = 2) {
            Terrain3D.Scene(scene.mosaic, scene.ground, scene.mesh, scene.creekKm).texture(TerrainTextures.Mode.HABITAT)
        }
        println("  mesh ${scene.mesh.gridN}^2 vertices, ${scene.mesh.triangleCount} triangles")
        assertTrue("scene build $build ms", build < 5_000)
        assertTrue("texture bake $bake ms", bake < 3_000)
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
