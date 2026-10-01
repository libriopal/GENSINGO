package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One-map integration (docs/blueprints/one-map.md): after a gesture the 3D camera is put back
 * on the ground under the screen centre ([CameraMath.reanchor]) without the picture moving.
 *
 * THE WITNESS is [MapCamera.project], checked on the device against MapLibre's own projection:
 * every terrain point must land on the same screen pixel before and after, with its height
 * measured from the new ground reference. A re-anchor that only moved the target (no zoom
 * change) would jump the whole picture; that is the negative control (mutant O1).
 */
class ReanchorTest {

    private val W = 1080
    private val H = 2400
    private val LAT = 35.56
    private val LNG = -83.0

    /** Rolling hills on a 400 m step: the camera's plane starts far off the ground. */
    private val hills: (Double, Double) -> Double = { lat, lng ->
        val e = (lng - LNG) * cos(Math.toRadians(LAT)) * MapCamera.EARTH_CIRCUMFERENCE / 360.0
        val n = (lat - LAT) * MapCamera.EARTH_CIRCUMFERENCE / 360.0
        400.0 + 120.0 * sin(e / 700.0) * cos(n / 900.0) + 0.08 * n
    }
    private val range = 200.0..650.0

    private fun mc(c: ViewCamera) = MapCamera(c.lat, c.lng, c.zoom, c.bearing, c.pitch, W, H)

    @Test
    fun reanchoringMovesNothingOnScreen() {
        for (pitch in listOf(0.0, 35.0, 55.0, 70.0)) for (bearing in listOf(0.0, 40.0, 215.0)) {
            val before = ViewCamera(LAT + 0.004, LNG - 0.003, 15.2, bearing, pitch)
            val (after, dh) = requireNotNull(CameraMath.reanchor(before, W, H, hills, range)) { "no re-anchor at pitch $pitch" }
            val m0 = mc(before); val m1 = mc(after)
            var worst = 0.0
            // A spread of terrain points across the view.
            for (sx in listOf(120.0, 540.0, 960.0)) for (sy in listOf(900.0, 1300.0, 1700.0, 2200.0)) {
                val p = m0.unproject(sx, sy, 0.0) ?: continue
                val h = hills(p[0], p[1])
                val a = m0.project(p[0], p[1], h) ?: continue
                val b = requireNotNull(m1.project(p[0], p[1], h - dh))
                worst = maxOf(worst, hypot((a[0] - b[0]).toDouble(), (a[1] - b[1]).toDouble()))
            }
            assertTrue("pitch $pitch bearing $bearing: the picture moved %.3f px".format(worst), worst < 0.5)
        }
    }

    @Test
    fun theNewTargetIsOnTheGround() {
        val before = ViewCamera(LAT, LNG, 15.0, 120.0, 55.0)
        val (after, dh) = CameraMath.reanchor(before, W, H, hills, range)!!
        assertEquals("the new centre is not on the terrain", hills(after.lat, after.lng), dh, 0.5)
        assertEquals("bearing and pitch are the user's, untouched", 120.0, after.bearing, 0.0)
        assertEquals(55.0, after.pitch, 0.0)
        assertTrue("ground above the old plane brings the target nearer: zoom must rise", after.zoom > before.zoom)
    }

    @Test
    fun aCameraAlreadyOnTheGroundIsLeftAlone() {
        assertNull(CameraMath.reanchor(ViewCamera(LAT, LNG, 15.0, 0.0, 55.0), W, H, { _, _ -> 0.0 }))
    }

    @Test
    fun theProjectorIsProject() {
        val m = MapCamera(LAT, LNG, 15.3, 33.0, 60.0, W, H)
        val f = m.projector()
        for (k in 0 until 20) {
            val lat = LAT + (k - 10) * 0.0007; val lng = LNG + (k % 7 - 3) * 0.0009; val e = k * 13.0 - 60
            val a = m.project(lat, lng, e); val b = f(lat, lng, e)
            if (a == null) { assertNull(b); continue }
            assertEquals(a[0], b!![0], 0f); assertEquals(a[1], b[1], 0f)
        }
    }
}
