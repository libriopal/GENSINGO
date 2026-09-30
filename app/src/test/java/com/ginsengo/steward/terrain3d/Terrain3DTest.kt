package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.Fixtures
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * The 3D view's colour and scene (Phase 8). The user reported the heatmap did not work in
 * 3D; these pin that the 3D surface IS the 2D heatmap, and that what it adds (relief,
 * contours, water) lands where the ground says it should.
 */
class Terrain3DTest {

    private fun mosaic(n: Int, cell: Double = 10.0, halo: Int = 0, z: (Int, Int) -> Double) =
        DemTileStore.Mosaic(
            TerrainMath.Grid(n, n, FloatArray(n * n) { i -> z(i % n, i / n).toFloat() }, cell),
            14, 4475, 6422, 1, 1, halo, 1, 1,
        )

    private fun lum(c: Int) = 0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255)

    /** On flat ground (no relief, no contour, no water) every texel is exactly the 2D colour. */
    @Test
    fun theHabitatColourIsTheTwoDimensionalHeatmapColour() {
        val n = 64
        val m = mosaic(n) { _, _ -> 700.0 }
        val scores = DoubleArray(n * n) { (it % n) / (n - 1.0) }          // 0 west .. 1 east
        val ground = TerrainTextures.Ground(m, scores, n, emptyList(), 1.5)
        val px = TerrainTextures.bake(ground, TerrainTextures.Mode.HABITAT, n)
        for (i in px.indices) {
            val want = TerrainTextures.over(TerrainTextures.NEUTRAL_RAMP[0],
                SuitabilityRasterizer.colourFor(scores[i], TerrainTextures.MIN_SCORE)) or (0xFF shl 24)
            assertEquals("texel $i", want, px[i])
        }
        // ...and that colour is visible: strong ground is far brighter than weak ground.
        assertTrue(lum(px[n / 2 * n + n - 1]) > lum(px[n / 2 * n]) + 60)
    }

    /** The scene scores the ground with the 2D map's own function, bit for bit. */
    @Test
    fun theSceneUsesTheHeatmapsScoringFunction() {
        val m = Fixtures.mosaic("scan_boone_z12.bin")
        val s = Terrain3D.build(m)
        val want = SuitabilityRasterizer.scoreGrid(m, 512, Terrain3D.TPI_RADIUS_M, GinsengSuitability.PRIOR_WEIGHTS)
        assertTrue(s.ground.scores!!.contentEquals(want))
        assertTrue("creeks traced: ${s.creekKm} km", s.creekKm > 5)
        val habitat = s.texture(TerrainTextures.Mode.HABITAT)
        val elevation = s.texture(TerrainTextures.Mode.ELEVATION)
        assertEquals(s.textureSize * s.textureSize, habitat.size)
        assertTrue(habitat.indices.count { habitat[it] != elevation[it] } > habitat.size / 2)
    }

    @Test
    fun contoursStandOutWhereTheElevationCrossesAnInterval() {
        val n = 200
        val m = mosaic(n) { x, _ -> 500.0 + x }                          // 1 m per cell, rising east
        val g = TerrainTextures.Ground(m, null, n, emptyList(), 1.0)
        val px = TerrainTextures.bake(g, TerrainTextures.Mode.ELEVATION, n)
        val interval = TerrainTextures.contourInterval(199.0)
        assertEquals(5.0, interval, 0.0)
        val row = n / 2
        var checked = 0
        for (x in 2 until n - 3) {
            val onLine = floor((500.0 + x) / interval) != floor((500.0 + x + 1) / interval)
            if (!onLine) continue
            // Dark on light ground, light on dark ground: either way it must stand out.
            val c = lum(px[row * n + x])
            assertTrue("contour at x=$x does not stand out",
                abs(c - lum(px[row * n + x - 1])) > 10 && abs(c - lum(px[row * n + x + 2])) > 10)
            checked++
        }
        assertTrue(checked > 30)
    }

    @Test
    fun creeksAreBlueAlongTheValleyFloorAndNowhereElse() {
        val n = 200; val c = n / 2
        val m = mosaic(n) { x, y -> 500.0 + abs(x - c) * 3.0 + (n - y) * 0.5 }
        val lines = Hydrology.of(m.grid).lines()
        val g = TerrainTextures.Ground(m, null, n, lines, 1.0)
        val px = TerrainTextures.bake(g, TerrainTextures.Mode.ELEVATION, n)
        fun blue(p: Int) = (p and 255) - ((p shr 16) and 255) > 60
        val lowRows = (n * 3 / 4 until n - 2)
        assertTrue("the valley floor is not blue", lowRows.count { blue(px[it * n + c]) } > lowRows.count() * 0.8)
        for (y in 0 until n) for (x in listOf(10, 40, n - 40, n - 10)) assertTrue("blue at $x,$y", !blue(px[y * n + x]))
    }

    /** Relief shading lights slopes facing the north-west light, not the ones facing away. */
    @Test
    fun hillshadeLightsTheSlopeFacingTheLight() {
        // Large enough that the ~25-40 contour lines across the relief are a small minority
        // of texels (on a 128-texel image they covered half of them and swamped the median).
        val n = 512; val c = n / 2
        // A ridge along north-south: the west half faces west, the east half faces east.
        val m = mosaic(n) { x, _ -> 800.0 - abs(x - c) * 4.0 }
        val px = TerrainTextures.bake(TerrainTextures.Ground(m, null, n, emptyList(), 1.0), TerrainTextures.Mode.ELEVATION, n)
        // Medians, because contour lines (light on dark ground) are a minority of texels and
        // would otherwise be measured as shading.
        val west = ArrayList<Double>(); val east = ArrayList<Double>()
        for (y in 10 until n - 10) {
            for (x in 10 until c - 10) west += lum(px[y * n + x])
            for (x in c + 10 until n - 10) east += lum(px[y * n + x])
        }
        val w = west.sorted()[west.size / 2]; val e = east.sorted()[east.size / 2]
        assertTrue("west-facing $w vs east-facing $e", w > e * 1.3)
    }

    @Test
    fun theAreaIsTheThreeByThreeTilesAroundTheUser() {
        val lat = 35.5605; val lng = -82.996
        for (z in Terrain3D.ZOOMS) {
            val b = Terrain3D.areaBounds(lat, lng, z)
            val tx = DemTileStore.lonToTileX(lng, z); val ty = DemTileStore.latToTileY(lat, z)
            assertEquals(tx - 1, DemTileStore.lonToTileX(b[1], z))
            assertEquals(tx + 1, DemTileStore.lonToTileX(b[3], z))
            assertEquals(ty - 1, DemTileStore.latToTileY(b[0], z))
            assertEquals(ty + 1, DemTileStore.latToTileY(b[2], z))
            assertTrue(lat in b[2]..b[0] && lng in b[1]..b[3])
        }
    }

    @Test
    fun theFitZoomFitsTheArea() {
        val z = Terrain3D.fitZoom(3000.0, 35.5, 1080)
        val cam = MapCamera(35.5, -83.0, z, 0.0, 0.0, 1080, 2400)
        assertEquals(3000.0, cam.metersPerPixel * 1080 * 0.92, 1.0)
    }
}
