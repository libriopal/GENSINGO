package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On a coarse square a thin water line is left out instead of painted many times its width (M.1:
 * on the 48 km square the 2.5 m drains, drawn at least 1.2 texels wide, covered the ground).
 */
class WaterLevelOfDetailTest {

    private val n = 200

    private fun ground(cellM: Double): TerrainTextures.Ground {
        val m = DemTileStore.Mosaic(
            TerrainMath.Grid(n, n, FloatArray(n * n) { 600f }, cellM),
            14, 4475, 6422, 1, 1, 0, 1, 1,
        )
        // One line of each kind, north to south, at its own column.
        fun column(x: Int, kind: Hydrology.Kind) = Hydrology.Line(IntArray(180) { (it + 10) * n + x }, kind)
        val lines = listOf(column(50, Hydrology.Kind.DRAINAGE), column(100, Hydrology.Kind.CREEK), column(150, Hydrology.Kind.STREAM))
        val grey = IntArray(n * n) { 0xFF7A7468.toInt() }
        return TerrainTextures.Ground(m, null, n, lines, 1.0, basemap = grey)
    }

    // The grey reads -18 (blue minus red); a drain at its 0.55 alpha over it reads +58.
    private fun blue(p: Int) = (p and 255) - ((p shr 16) and 255) > 30

    private fun blueIn(px: IntArray, x: Int) = (20 until 180).count { y -> (x - 2..x + 2).any { blue(px[y * n + it]) } }

    private fun bake(cellM: Double) =
        TerrainTextures.bake(ground(cellM), TerrainTextures.Mode.MAP, n, TerrainTextures.Layers(habitat = false, contours = false, hillshade = false, water = true))

    @Test
    fun atFineTexelsEveryKindIsDrawn() {
        val px = bake(2.0)     // 2 m texels: the 3 km square's scale
        for (x in listOf(50, 100, 150)) assertEquals("column $x", 160, blueIn(px, x))
    }

    @Test
    fun atCoarseTexelsOnlyLinesAQuarterTexelWideAreDrawn() {
        val px = bake(20.0)    // 20 m texels: a drain (2.5 m) and a creek (4.5 m) are under a quarter texel; a stream (8 m) is not
        assertEquals("drain", 0, blueIn(px, 50))
        assertEquals("creek", 0, blueIn(px, 100))
        assertEquals("stream", 160, blueIn(px, 150))
        assertTrue(TerrainTextures.WATER.getValue(Hydrology.Kind.STREAM).widthM / 20.0 >= TerrainTextures.MIN_WATER_TEXELS)
    }
}
