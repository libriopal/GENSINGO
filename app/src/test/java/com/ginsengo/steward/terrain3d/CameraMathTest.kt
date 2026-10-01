package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Acceptance tests for WP-B (docs/blueprints/one-map.md): the shared camera and 3D panning.
 *
 * THE WITNESS is [MapCamera.project], which existed before this work package and is itself
 * checked against MapLibre's own projection on the device ([AlignmentCheck]). Every "where is
 * the ground under the finger" question is answered here by solving `project(p, h) = finger`
 * with Newton's method, never by calling [MapCamera.unproject] or [CameraMath]: a reference
 * built from the code under test agrees with it precisely when it is wrong.
 */
class CameraMathTest {

    private val W = 1080
    private val H = 2400
    private val LAT = 35.56
    private val LNG = -83.0

    /** One-finger drags (fromX, fromY, toX, toY), starting where a thumb starts a pan. */
    private val DRAGS = listOf(
        doubleArrayOf(850.0, 1500.0, 350.0, 1300.0),
        doubleArrayOf(300.0, 1700.0, 800.0, 1500.0),
        doubleArrayOf(540.0, 1200.0, 540.0, 1700.0),
        doubleArrayOf(900.0, 1000.0, 500.0, 1300.0),
        doubleArrayOf(200.0, 1300.0, 600.0, 1900.0),
    )

    private val FLAT: (Double, Double) -> Double = { _, _ -> 0.0 }

    // ------------------------------------------------------------------ witness helpers

    private fun mapCam(c: ViewCamera) = MapCamera(c.lat, c.lng, c.zoom, c.bearing, c.pitch, W, H)

    private fun latOf(c: MapCamera, wy: Double) = MapCamera.latFromMercatorY(wy / c.worldSize)
    private fun lngOf(c: MapCamera, wx: Double) = MapCamera.lngFromMercatorX(wx / c.worldSize)

    /** The witness, addressed in world pixels so Newton's steps are well scaled. */
    private fun proj(c: MapCamera, wx: Double, wy: Double, h: Double): FloatArray? =
        c.project(latOf(c, wy), lngOf(c, wx), h)

    /**
     * The point at height [h] that camera [c] shows at screen ([sx], [sy]), in world pixels:
     * Newton's method on [MapCamera.project] with a numerical Jacobian. Null if it does not
     * converge (the point is behind the camera, or the ray never reaches that height).
     */
    private fun pointOnRay(
        c: MapCamera, sx: Double, sy: Double, h: Double,
        startX: Double = c.centerX, startY: Double = c.centerY,
    ): DoubleArray? {
        var x = startX
        var y = startY
        repeat(40) {
            val p = proj(c, x, y, h) ?: return null
            val rx = p[0] - sx
            val ry = p[1] - sy
            if (abs(rx) < 1e-3 && abs(ry) < 1e-3) return doubleArrayOf(x, y)
            val px = proj(c, x + 1.0, y, h) ?: return null
            val py = proj(c, x, y + 1.0, h) ?: return null
            val a = (px[0] - p[0]).toDouble(); val b = (py[0] - p[0]).toDouble()
            val cc = (px[1] - p[1]).toDouble(); val d = (py[1] - p[1]).toDouble()
            val det = a * d - b * cc
            if (abs(det) < 1e-12) return null
            x -= (d * rx - b * ry) / det
            y -= (-cc * rx + a * ry) / det
        }
        return null
    }

    /**
     * The TERRAIN point under the finger, found independently of the code under test: walk
     * down the view ray through ([sx], [sy]) in 2 m height steps (each ray point solved by
     * [pointOnRay]), stop at the first step where the ray has gone below the terrain, then
     * bisect that bracket. The first crossing is the visible surface. Returns (lat, lng, h).
     */
    private fun terrainUnder(
        c: MapCamera, sx: Double, sy: Double, heightAt: (Double, Double) -> Double,
    ): DoubleArray {
        fun gap(h: Double, start: DoubleArray?): Pair<DoubleArray, Double> {
            val w = pointOnRay(c, sx, sy, h, start?.get(0) ?: c.centerX, start?.get(1) ?: c.centerY)
                ?: pointOnRay(c, sx, sy, h)
                ?: throw AssertionError("witness: no ray point at h=$h for ($sx, $sy)")
            return w to heightAt(latOf(c, w[1]), lngOf(c, w[0])) - h
        }
        var hAbove = SEARCH_TOP_M
        val (w0, g0) = gap(hAbove, null)
        assertTrue("witness: the ray must start above the terrain", g0 < 0)
        var wAbove = w0
        var h = hAbove
        while (h > SEARCH_BOTTOM_M) {
            h -= SEARCH_STEP_M
            val (w, gh) = gap(h, wAbove)
            if (gh >= 0) {
                // Bracket [h, hAbove]: below the terrain at h, above it at hAbove.
                var lo = h; var wLo = w
                var hi = hAbove; var wHi = wAbove
                repeat(45) {
                    val m = 0.5 * (lo + hi)
                    val (wm, gm) = gap(m, wHi)
                    if (gm >= 0) { lo = m; wLo = wm } else { hi = m; wHi = wm }
                }
                return doubleArrayOf(latOf(c, wLo[1]), lngOf(c, wLo[0]), lo)
            }
            hAbove = h; wAbove = w
        }
        fail("witness: the ray through ($sx, $sy) never met the terrain")
        throw IllegalStateException()
    }

    /** Pixels between where [newCam] shows the 3D point and where the finger now is. */
    private fun miss(newCam: ViewCamera, p: DoubleArray, toX: Double, toY: Double): Double {
        val q = mapCam(newCam).project(p[0], p[1], p[2])
            ?: return Double.POSITIVE_INFINITY
        return hypot(q[0] - toX, q[1] - toY)
    }

    private fun assertFlatPanKeepsGround(pitch: Double, tolPx: Double) {
        for (bearing in listOf(0.0, 90.0, 200.0)) for (zoom in listOf(13.0, 15.0, 17.0)) {
            val cam = ViewCamera(LAT, LNG, zoom, bearing, pitch)
            val old = mapCam(cam)
            for (d in DRAGS) {
                val (fx, fy, tx, ty) = d.toList()
                val w = pointOnRay(old, fx, fy, 0.0)
                assertNotNull("witness: ground under ($fx, $fy) at b=$bearing z=$zoom", w)
                val ground = doubleArrayOf(latOf(old, w!![1]), lngOf(old, w[0]), 0.0)
                val moved = CameraMath.pan(cam, fx, fy, tx, ty, W, H, FLAT)
                val m = miss(moved, ground, tx, ty)
                assertTrue(
                    "pitch $pitch bearing $bearing zoom $zoom drag ($fx,$fy)->($tx,$ty): " +
                        "ground lands %.3f px from the finger".format(m),
                    m <= tolPx,
                )
                assertEquals("zoom kept", cam.zoom, moved.zoom, 0.0)
                assertEquals("bearing kept", cam.bearing, moved.bearing, 0.0)
                assertEquals("pitch kept", cam.pitch, moved.pitch, 0.0)
            }
        }
    }

    // ------------------------------------------------------------------ 1. flat, pitch 0

    @Test
    fun flatPanAtPitch0KeepsTheGroundUnderTheFinger() = assertFlatPanKeepsGround(pitch = 0.0, tolPx = 1.0)

    // ------------------------------------------------------------------ 2. flat, pitch 55

    @Test
    fun flatPanAtPitch55KeepsTheGroundUnderTheFinger() = assertFlatPanKeepsGround(pitch = 55.0, tolPx = 2.0)

    // ------------------------------------------------------------------ 3. sloped terrain

    /**
     * Terrain rising 0.3 m per metre east (in the same convention as project's elevationM, as
     * the 3D view passes `(e - ground) * exaggeration`). The view at zoom 15 is ~2.1 km wide,
     * so it holds ~600 m of relief. Two anchorings: [offsetM] 0 puts the camera's target plane
     * on the ground under the centre (as the 3D view starts); 300 is 0 m at the west edge
     * rising to ~600 m at the east edge.
     */
    private fun ramp(offsetM: Double): (Double, Double) -> Double = { _, lng ->
        offsetM + SLOPE * (lng - LNG) * cos(Math.toRadians(LAT)) * MapCamera.EARTH_CIRCUMFERENCE / 360.0
    }

    @Test
    fun slopedPanKeepsTheTerrainPointUnderTheFinger() {
        for (offset in listOf(0.0, 300.0)) for (bearing in listOf(0.0, 90.0, 200.0)) {
            val heightAt = ramp(offset)
            val cam = ViewCamera(LAT, LNG, 15.0, bearing, 55.0)
            val old = mapCam(cam)
            val terrainAware = mutableListOf<Double>()
            val cameraOnly = mutableListOf<Double>()
            for (d in DRAGS) {
                val (fx, fy, tx, ty) = d.toList()
                val p = terrainUnder(old, fx, fy, heightAt)

                val m = miss(CameraMath.pan(cam, fx, fy, tx, ty, W, H, heightAt), p, tx, ty)
                terrainAware += m
                assertTrue(
                    "offset $offset bearing $bearing drag ($fx,$fy)->($tx,$ty), terrain at " +
                        "%.1f m: lands %.3f px from the finger".format(p[2], m),
                    m <= 3.0,
                )
                // The auditor's runner-up: the same pan from the camera alone (heights ignored).
                cameraOnly += miss(CameraMath.pan(cam, fx, fy, tx, ty, W, H, FLAT), p, tx, ty)
            }
            val mean = cameraOnly.average()
            println(
                "sloped offset=$offset bearing=$bearing terrain-aware max miss %.3f px; ".format(terrainAware.max()) +
                    "camera-only miss px: " + cameraOnly.joinToString { "%.1f".format(it) } +
                    " (mean %.1f, max %.1f)".format(mean, cameraOnly.max())
            )
            assertTrue(
                "offset $offset bearing $bearing: camera-only pan must miss by > 10 px on " +
                    "average, measured %.1f".format(mean),
                mean > 10.0,
            )
        }
    }

    // ------------------------------------------------------------------ 4. unproject

    @Test
    fun unprojectInvertsProject() {
        for (zoom in listOf(13.0, 15.0, 17.0)) for (bearing in listOf(0.0, 37.0, 200.0))
            for (pitch in listOf(0.0, 30.0, 55.0, 60.0)) {
                val c = MapCamera(LAT, LNG, zoom, bearing, pitch, W, H)
                val span = 300.0 * 360.0 / c.worldSize   // 300 world pixels, in degrees
                for (dLat in listOf(-span, 0.0, span * 0.7)) for (dLng in listOf(-span, span * 0.4))
                    for (h in listOf(-400.0, 0.0, 250.0, 900.0)) {
                        val lat = LAT + dLat; val lng = LNG + dLng
                        val s = c.project(lat, lng, h) ?: continue
                        val back = c.unproject(s[0].toDouble(), s[1].toDouble(), h)
                        val at = "z=$zoom b=$bearing p=$pitch ($lat, $lng, $h)"
                        assertNotNull("unproject must hit the plane it was projected from, $at", back)
                        assertEquals("lat $at", lat, back!![0], 1e-7)
                        assertEquals("lng $at", lng, back[1], 1e-7)
                    }
            }
    }

    @Test
    fun unprojectReturnsNullWhenTheRayMissesThePlane() {
        // At pitch 85 the top edge of the screen looks ~13 degrees above the horizon.
        val steep = MapCamera(LAT, LNG, 15.0, 0.0, 85.0, W, H)
        assertNull("above the horizon", steep.unproject(W / 2.0, 0.0, 0.0))
        assertNotNull("below the horizon on the same camera", steep.unproject(W / 2.0, H.toDouble(), 0.0))
        // A plane above the camera cannot be reached by a ray that points down.
        val c = MapCamera(LAT, LNG, 15.0, 0.0, 55.0, W, H)
        assertNull("plane above the camera", c.unproject(W / 2.0, H / 2.0, 50_000.0))
        assertNotNull("ground under the centre", c.unproject(W / 2.0, H / 2.0, 0.0))
    }

    // ------------------------------------------------------------------ 5. clampCentre

    @Test
    fun clampCentreKeepsTheCentreInsideAndLeavesAnInsideCentreAlone() {
        val n = 35.60; val w = -83.05; val s = 35.52; val e = -82.95
        val inside = ViewCamera(35.57, -83.01, 15.3, 41.0, 52.0)
        assertEquals("an inside centre is unchanged", inside, CameraMath.clampCentre(inside, n, w, s, e))
        for ((lat, lng) in listOf(
            36.0 to -83.0, 35.0 to -83.0, 35.56 to -84.0, 35.56 to -82.0, 37.0 to -81.0, 34.0 to -85.0,
        )) {
            val out = ViewCamera(lat, lng, 14.2, 120.0, 48.0)
            val c = CameraMath.clampCentre(out, n, w, s, e)
            assertTrue("lat $lat clamped into [$s, $n], got ${c.lat}", c.lat in s..n)
            assertTrue("lng $lng clamped into [$w, $e], got ${c.lng}", c.lng in w..e)
            if (lat in s..n) assertEquals("an inside lat is kept", lat, c.lat, 0.0)
            if (lng in w..e) assertEquals("an inside lng is kept", lng, c.lng, 0.0)
            assertEquals("zoom, bearing, pitch kept", out.copy(lat = c.lat, lng = c.lng), c)
        }
    }

    // ------------------------------------------------------------------ 6. to3d / to2d

    @Test
    fun switchingViewsKeepsCentreAndBearingAndClampsZoomAndPitch() {
        val flat = ViewCamera(35.5612345678, -83.0012345678, 9.25, 213.7, 20.0)
        val a = CameraMath.to3d(flat, minZoom = 12.0, maxZoom = 17.0)
        assertEquals(flat.lat, a.lat, 0.0); assertEquals(flat.lng, a.lng, 0.0)
        assertEquals(flat.bearing, a.bearing, 0.0)
        assertEquals("zoom clamped up to min", 12.0, a.zoom, 0.0)
        assertEquals("pitch raised to 45", 45.0, a.pitch, 0.0)

        val close = flat.copy(zoom = 18.6, pitch = 58.0)
        val b = CameraMath.to3d(close, minZoom = 12.0, maxZoom = 17.0)
        assertEquals("zoom clamped down to max", 17.0, b.zoom, 0.0)
        assertEquals("a pitch above 45 is kept", 58.0, b.pitch, 0.0)
        assertEquals("an in-range zoom is kept", 14.4, CameraMath.to3d(flat.copy(zoom = 14.4), 12.0, 17.0).zoom, 0.0)

        val tilted = ViewCamera(35.5698765432, -82.9987654321, 15.75, 301.25, 72.0)
        val c = CameraMath.to2d(tilted)
        assertEquals(tilted.lat, c.lat, 0.0); assertEquals(tilted.lng, c.lng, 0.0)
        assertEquals(tilted.bearing, c.bearing, 0.0)
        assertEquals("pitch capped at 60 for the 2D map", 60.0, c.pitch, 0.0)
        assertEquals("an in-range zoom is kept", 15.75, c.zoom, 0.0)
        assertEquals("a pitch under 60 is kept", 50.0, CameraMath.to2d(tilted.copy(pitch = 50.0)).pitch, 0.0)
        assertEquals("zoom clamped to MapLibre's maximum", 25.5, CameraMath.to2d(tilted.copy(zoom = 30.0)).zoom, 0.0)
        assertEquals("zoom clamped to MapLibre's minimum", 0.0, CameraMath.to2d(tilted.copy(zoom = -2.0)).zoom, 0.0)
    }

    private companion object {
        const val SLOPE = 0.3
        const val SEARCH_TOP_M = 2000.0
        const val SEARCH_BOTTOM_M = -2000.0
        const val SEARCH_STEP_M = 2.0
    }
}
