package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * The 2D map's contour lines (one-map blueprint): the maplibre-contour port's lines, placed on
 * the map. The witness is the grid's own elevation, sampled at each vertex through an inverse
 * Web Mercator written here, independently of the code under test: on a plane every vertex
 * must sit at its line's height. Dropping the halo offset (mutant O3) moves every line by the
 * halo's width, hundreds of metres of height on this plane.
 */
class ContourLinesTest {

    private val z = 14
    private val tx0 = 4475
    private val ty0 = 6422
    private val n = 3 * DemTileStore.TILE
    private val halo = DemTileStore.TILE

    private fun plane(x: Double, y: Double) = 500 + 0.5 * x + 0.25 * y

    private fun mosaic() = DemTileStore.Mosaic(
        TerrainMath.Grid(n, n, FloatArray(n * n) { i -> plane((i % n).toDouble(), (i / n).toDouble()).toFloat() }, 9.5),
        z, tx0, ty0, 3, 3, halo, 9, 9,
    )

    /** Cell coordinates (sample (x, y) at the cell's centre) of a position. */
    private fun cellOf(lat: Double, lng: Double): DoubleArray {
        val world = DemTileStore.TILE.toDouble() * (1 shl z)
        val x = (lng + 180.0) / 360.0 * world - tx0 * DemTileStore.TILE - 0.5
        val r = Math.toRadians(lat)
        val y = (1.0 - ln(tan(PI / 4 + r / 2)) / PI) / 2.0 * world - ty0 * DemTileStore.TILE - 0.5
        return doubleArrayOf(x, y)
    }

    @Test
    fun everyVertexSitsAtItsLinesHeightInsideTheDisplayedSquare() {
        val m = mosaic()
        val relief = ContourLines.interiorReliefM(m)
        assertEquals(0.75 * 255, relief, 1e-3)
        val lines = ContourLines.of(m, 5.0)
        assertTrue("a sloping plane must have contours", lines.size > 20)
        for (l in lines) for (k in 0 until l.points) {
            val c = cellOf(l.lngLat[2 * k + 1], l.lngLat[2 * k])
            assertEquals("vertex of the ${l.levelM} m line", l.levelM.toDouble(), plane(c[0], c[1]), 0.05)
            assertTrue("vertex outside the displayed square: ${c[0]}, ${c[1]}",
                c[0] in halo - 1e-6..n - halo - 1 + 1e-6 && c[1] in halo - 1e-6..n - halo - 1 + 1e-6)
        }
    }

    @Test
    fun everyFifthLevelIsAnIndexContour() {
        val lines = ContourLines.of(mosaic(), 5.0)
        assertTrue(lines.any { it.index } && lines.any { !it.index })
        for (l in lines) assertEquals("level ${l.levelM}", l.levelM % 25 == 0, l.index)
    }
}
