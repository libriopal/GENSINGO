package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import com.ginsengo.steward.terrain.WaterLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One-map integration: the 3D square's edges (where the basemap snapshot is rendered, and the
 * rebuild test) and the elevation the view, the pan and the markers read.
 *
 * Witnesses: [DemTileStore.tileYToLat] / [DemTileStore.tileXToLon] for tile edges and
 * [WaterLines.lngOfCell] / [WaterLines.latOfCell] (the 2D map's placement of cell centres),
 * both older code than the functions tested.
 */
class SceneGeometryTest {

    private val z = 14
    private val tx0 = 4475
    private val ty0 = 6422

    /** 3 x 3 tiles with a one-tile halo: the displayed square is the middle tile. */
    private fun scene(): Terrain3D.Scene {
        val n = 3 * DemTileStore.TILE
        val g = TerrainMath.Grid(n, n, FloatArray(n * n) { i -> (500 + 0.5 * (i % n) + 0.25 * (i / n)).toFloat() }, 9.5)
        return Terrain3D.build(DemTileStore.Mosaic(g, z, tx0, ty0, 3, 3, DemTileStore.TILE, 9, 9))
    }

    @Test
    fun theSquaresEdgesAreExactMercatorTileEdges() {
        val s = scene()
        assertEquals(DemTileStore.tileYToLat(ty0 + 1, z), s.north, 1e-10)
        assertEquals(DemTileStore.tileYToLat(ty0 + 2, z), s.south, 1e-10)
        assertEquals(DemTileStore.tileXToLon(tx0 + 1, z), s.west, 1e-10)
        assertEquals(DemTileStore.tileXToLon(tx0 + 2, z), s.east, 1e-10)
    }

    @Test
    fun elevationIsBilinearBetweenCellCentresAndAbsentInTheHalo() {
        val s = scene()
        val m = s.mosaic
        // A plane is reproduced exactly by bilinear sampling, at any fractional position.
        for (x in listOf(256.0, 300.25, 400.5, 511.0)) for (y in listOf(256.0, 333.75, 450.5, 511.0)) {
            val e = s.elevationAt(WaterLines.latOfCell(m, y), WaterLines.lngOfCell(m, x))
            assertEquals("at cell ($x, $y)", 500 + 0.5 * x + 0.25 * y, e!!, 1e-3)
        }
        assertNull("the halo is not drawn", s.elevationAt(WaterLines.latOfCell(m, 100.0), WaterLines.lngOfCell(m, 400.0)))
        assertFalse(s.contains(WaterLines.latOfCell(m, 400.0), WaterLines.lngOfCell(m, 600.0)))
        assertTrue(s.contains(WaterLines.latOfCell(m, 400.0), WaterLines.lngOfCell(m, 400.0)))
    }

    /**
     * The 3D view's gesture-end and rebuild step: the camera, looking at a plane 200 m under
     * the ground, is put on the ground and the picture does not move. Witness: the drawn
     * height of each point, (elevation - ground) x exaggeration, through [MapCamera.project].
     * Moving the ground by the exaggerated height instead (mutant O5) moves the picture.
     */
    @Test
    fun settlingPutsTheCameraOnTheGroundWithoutMovingThePicture() {
        val s = scene()
        val m = s.mosaic
        val w = 1080; val h = 2400
        val cam = CameraState(WaterLines.latOfCell(m, 384.0), WaterLines.lngOfCell(m, 384.0), 14.6, 30.0, 55.0)
        val ground = s.elevationAt(cam.lat, cam.lng)!! - 200.0
        val (next, nextGround) = Terrain3D.settle(cam, w, h, s, ground)
        assertEquals("the new ground plane is the terrain under the new centre",
            s.elevationAt(next.lat, next.lng)!!, nextGround, 0.5)
        val before = MapCamera(cam.lat, cam.lng, cam.zoom, cam.bearing, cam.pitch, w, h)
        val after = MapCamera(next.lat, next.lng, next.zoom, next.bearing, next.pitch, w, h)
        var worst = 0.0
        for (x in listOf(270.0, 330.0, 384.0, 440.0, 500.0)) for (y in listOf(280.0, 384.0, 490.0)) {
            val lat = WaterLines.latOfCell(m, y); val lng = WaterLines.lngOfCell(m, x)
            val e = s.elevationAt(lat, lng)!!
            val a = before.project(lat, lng, (e - ground) * Terrain3D.EXAGGERATION) ?: continue
            val b = after.project(lat, lng, (e - nextGround) * Terrain3D.EXAGGERATION) ?: continue
            worst = maxOf(worst, kotlin.math.hypot((a[0] - b[0]).toDouble(), (a[1] - b[1]).toDouble()))
        }
        assertTrue("the picture moved %.3f px".format(worst), worst < 0.5)
        val (same, sameGround) = Terrain3D.settle(next, w, h, s, nextGround)
        assertEquals("settling twice changes nothing more", next.zoom, same.zoom, 1e-3)
        assertEquals(nextGround, sameGround, 0.5)
    }
}
