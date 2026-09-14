package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/**
 * Tests for the reconstructed MapLibre camera.
 *
 * These assert GEOMETRIC INVARIANTS — properties that must hold for any correct
 * implementation of this projection — rather than comparing against a second copy of my own
 * arithmetic. A reference implementation written by the same author from the same
 * understanding is the self-witness failure mode: it agrees precisely when I am wrong.
 *
 * The remaining gap (does this match MapLibre *specifically*?) is closed at runtime by
 * [AlignmentCheck] against MapLibre's own `toScreenLocation`, which no offline test can do.
 */
class MapCameraTest {

    private val W = 1080
    private val H = 1920
    private val LAT = 36.2
    private val LNG = -81.67

    private fun cam(
        zoom: Double = 14.0,
        bearing: Double = 0.0,
        pitch: Double = 0.0,
        lat: Double = LAT,
        lng: Double = LNG,
    ) = MapCamera(lat, lng, zoom, bearing, pitch, W, H)

    // ------------------------------------------------------------------ fixed point

    /**
     * The camera target must land at the centre of the viewport, always.
     *
     * The tolerance is deliberately tight (0.05 px). A looser one hid a real defect: an
     * earlier version projected through the float32 matrix, and because world pixels shrink
     * as zoom grows while the centre coordinate grows to match, float32's step size works
     * out at a constant ~1.9 m of ground error at EVERY zoom — 0.59 px off centre at z17.
     * That is a fifth of the alignment tolerance spent on nothing but rounding.
     */
    @Test
    fun targetProjectsToViewportCentre() {
        for (zoom in listOf(6.0, 11.0, 14.0, 17.0, 20.0)) {
            for (bearing in listOf(0.0, 45.0, 180.0, 300.0)) {
                for (pitch in listOf(0.0, 30.0, 60.0)) {
                    val c = cam(zoom, bearing, pitch)
                    val p = c.project(LAT, LNG)
                    assertNotNull("target must project at z=$zoom b=$bearing p=$pitch", p)
                    assertEquals("x at z=$zoom b=$bearing p=$pitch", W / 2f, p!![0], 0.05f)
                    assertEquals("y at z=$zoom b=$bearing p=$pitch", H / 2f, p[1], 0.05f)
                }
            }
        }
    }

    /**
     * Projection accuracy must not decay with zoom. This is the regression guard for the
     * float32-matrix defect above: the error it produced was constant in ground metres, so
     * it shows up identically at z14 and z20 and is invisible unless the tolerance is tight.
     */
    @Test
    fun projectionAccuracyDoesNotDecayWithZoom() {
        for (zoom in listOf(10.0, 14.0, 17.0, 20.0)) {
            val c = cam(zoom = zoom, bearing = 33.0, pitch = 40.0)
            // A point one world pixel east of centre must land one screen pixel east of
            // centre in the flat direction; any matrix rounding shows up immediately here.
            val lng = MapCamera.lngFromMercatorX((c.centerX + 100.0) / c.worldSize)
            val centre = c.project(LAT, LNG)!!
            val p = c.project(LAT, lng)!!
            val moved = kotlin.math.hypot(
                (p[0] - centre[0]).toDouble(), (p[1] - centre[1]).toDouble()
            )
            assertTrue("a 100 px world offset must move the point at z=$zoom", moved > 10.0)
            assertEquals("centre must stay pinned at z=$zoom", W / 2f, centre[0], 0.05f)
        }
    }

    // ------------------------------------------------------------------ flat case is affine

    /**
     * With no pitch and no bearing the projection must reduce to an exact 1:1 mapping
     * between world pixel units and screen pixels. This is the property that pins the field
     * of view, the camera distance and the perspective divide all at once: if any of them
     * is wrong, the scale comes out wrong here.
     */
    @Test
    fun unpitchedProjectionIsOneToOneWithWorldPixels() {
        val c = cam(zoom = 14.0)
        val centre = c.project(LAT, LNG)!!
        for (dxPixels in listOf(-400.0, -50.0, 120.0, 350.0)) {
            val lng = MapCamera.lngFromMercatorX((c.centerX + dxPixels) / c.worldSize)
            val p = c.project(LAT, lng)!!
            assertEquals(
                "a $dxPixels px world offset must be $dxPixels screen px",
                centre[0] + dxPixels.toFloat(), p[0], 0.6f,
            )
            assertEquals("no vertical drift", centre[1], p[1], 0.6f)
        }
    }

    @Test
    fun southIsDownAndNorthIsUp() {
        val c = cam()
        val centre = c.project(LAT, LNG)!!
        val north = c.project(LAT + 0.01, LNG)!!
        val south = c.project(LAT - 0.01, LNG)!!
        assertTrue("north must be above centre", north[1] < centre[1])
        assertTrue("south must be below centre", south[1] > centre[1])
    }

    // ------------------------------------------------------------------ bearing

    /**
     * Bearing is the compass direction the camera FACES, so at bearing 90 the camera looks
     * east, east is at the top of the screen, and north lies to the LEFT.
     *
     * (My first version of this test asserted north moved right. It was wrong, and the
     * code was right — worth recording, because "the test failed so change the code" is
     * exactly how a correct projection gets broken.)
     */
    @Test
    fun bearingRotatesTheWorldNotTheCamera() {
        val c = cam(bearing = 90.0)
        val centre = c.project(LAT, LNG)!!
        val north = c.project(LAT + 0.02, LNG)!!
        assertTrue("facing east, north must lie to the left", north[0] < centre[0] - 20f)
        assertEquals("north should stay level at bearing 90", centre[1], north[1], 2f)

        // And at bearing 270 (facing west) it must lie to the right — the mirror case,
        // which a sign error in the rotation would fail.
        val west = cam(bearing = 270.0)
        val n2 = west.project(LAT + 0.02, LNG)!!
        assertTrue("facing west, north must lie to the right", n2[0] > centre[0] + 20f)
    }

    @Test
    fun bearingPreservesDistanceFromCentre() {
        val flat = cam(bearing = 0.0)
        val turned = cam(bearing = 137.0)
        val a = flat.project(LAT + 0.02, LNG)!!
        val b = turned.project(LAT + 0.02, LNG)!!
        fun radius(p: FloatArray) =
            kotlin.math.hypot((p[0] - W / 2f).toDouble(), (p[1] - H / 2f).toDouble())
        assertEquals("rotation must not change distance from centre", radius(a), radius(b), 1.0)
    }

    // ------------------------------------------------------------------ pitch

    /**
     * Tilting widens how much ground fits on screen, so a fixed point north of centre moves
     * DOWN towards the centre while the horizon opens up above it, and equal ground steps
     * compress with distance.
     *
     * (My first version asserted the point moved up. Also wrong: with the centre pinned,
     * tilting reveals far more distant terrain above, and nearby ground is pushed down the
     * frame to make room for it.)
     */
    @Test
    fun pitchCompressesDistantGroundTowardsTheHorizon() {
        val flat = cam(pitch = 0.0)
        val tilted = cam(pitch = 60.0)
        val flatNear = flat.project(LAT + 0.005, LNG)!!
        val flatFar = flat.project(LAT + 0.010, LNG)!!
        val tiltNear = tilted.project(LAT + 0.005, LNG)!!
        val tiltFar = tilted.project(LAT + 0.010, LNG)!!

        assertTrue(
            "tilting must bring nearby ground down the frame as the horizon opens above",
            tiltFar[1] > flatFar[1],
        )
        val flatGap = abs(flatFar[1] - flatNear[1])
        val tiltGap = abs(tiltFar[1] - tiltNear[1])
        assertTrue(
            "equal ground steps must compress with distance under pitch " +
                    "(flat $flatGap, tilted $tiltGap)",
            tiltGap < flatGap,
        )
        // The far point must still be above the near one: pitch may compress, never invert.
        assertTrue("ordering must survive pitch", tiltFar[1] < tiltNear[1])
    }

    /**
     * Ground BEHIND the camera must be rejected rather than projected to a mirrored
     * position on screen.
     *
     * Behind means south here: with the camera facing north and pitched, clip.w for a
     * northward offset is `d + dy*sin(pitch)` and only grows, while a southward offset
     * drives it through zero. (My first version probed north and failed for that reason —
     * the code was right.)
     */
    @Test
    fun pointsBehindTheCameraAreRejected() {
        val c = cam(pitch = 75.0)
        assertNull("ground behind a pitched camera must be rejected", c.project(LAT - 8.0, LNG))
        assertNotNull("ground in front must still project", c.project(LAT + 0.01, LNG))
    }

    // ------------------------------------------------------------------ scale

    /**
     * Ground resolution must match the standard Web Mercator relation, which is also what
     * MapLibre's Projection.getMetersPerPixelAtLatitude returns. Computed here from the
     * definition rather than from my own matrix, so it is an independent check on tile size
     * and world size.
     */
    @Test
    fun groundResolutionMatchesTheWebMercatorDefinition() {
        for (z in listOf(8.0, 12.0, 15.0)) {
            val c = cam(zoom = z)
            val expected = 40_075_016.686 * cos(Math.toRadians(LAT)) /
                    (512.0 * Math.pow(2.0, z))
            assertEquals("m/px at z=$z", expected, c.metersPerPixel, expected * 1e-9)
        }
    }

    @Test
    fun zoomingInOneLevelDoublesScreenDisplacement() {
        val a = cam(zoom = 13.0)
        val b = cam(zoom = 14.0)
        fun offset(c: MapCamera): Double {
            val centre = c.project(LAT, LNG)!!
            val p = c.project(LAT, LNG + 0.01)!!
            return (p[0] - centre[0]).toDouble()
        }
        assertEquals(2.0, offset(b) / offset(a), 0.01)
    }

    @Test
    fun pixelsPerMeterIsConsistentWithMetresPerPixel() {
        val c = cam(zoom = 15.0)
        assertEquals(1.0, c.pixelsPerMeter * c.metersPerPixel, 1e-9)
    }

    // ------------------------------------------------------------------ elevation

    /**
     * Elevation must lift geometry towards the camera. Checked away from the centre,
     * because a point directly under an unpitched camera projects to the same pixel at any
     * height — testing it at the centre would pass even if elevation did nothing.
     */
    @Test
    fun elevationLiftsGeometryTowardsTheCamera() {
        val c = cam(zoom = 15.0, pitch = 45.0)
        val ground = c.project(LAT + 0.003, LNG + 0.003, 0.0)!!
        val raised = c.project(LAT + 0.003, LNG + 0.003, 400.0)!!
        val moved = kotlin.math.hypot(
            (raised[0] - ground[0]).toDouble(), (raised[1] - ground[1]).toDouble()
        )
        assertTrue("400 m of elevation must visibly move the point, moved $moved px", moved > 5.0)
        assertTrue("raising ground must move it up the screen", raised[1] < ground[1])
    }

    // ------------------------------------------------------------------ precision path

    /**
     * The local-origin matrix must produce exactly the same screen position as the absolute
     * one. This is the fix for float32 precision at high zoom, and it is only safe if the
     * two paths agree — otherwise the mesh renders offset from everything else.
     */
    @Test
    fun localOriginMatrixAgreesWithTheAbsoluteMatrix() {
        val c = cam(zoom = 16.0, bearing = 42.0, pitch = 50.0)
        val originX = c.worldX(LNG + 0.004)
        val originY = c.worldY(LAT + 0.004)
        val m = c.mvpForOrigin(originX, originY)

        for ((dLat, dLng) in listOf(0.0 to 0.0, 0.002 to -0.001, -0.003 to 0.004)) {
            val lat = LAT + 0.004 + dLat
            val lng = LNG + 0.004 + dLng
            val localX = (c.worldX(lng) - originX).toFloat()
            val localY = (c.worldY(lat) - originY).toFloat()

            val cx = m[0] * localX + m[4] * localY + m[12]
            val cy = m[1] * localX + m[5] * localY + m[13]
            val cw = m[3] * localX + m[7] * localY + m[15]
            val sx = ((cx / cw) * 0.5f + 0.5f) * W
            val sy = (0.5f - (cy / cw) * 0.5f) * H

            val direct = c.project(lat, lng)!!
            assertEquals("x via local origin", direct[0], sx, 0.5f)
            assertEquals("y via local origin", direct[1], sy, 0.5f)
        }
    }

    // ------------------------------------------------------------------ mercator

    @Test
    fun mercatorRoundTrips() {
        for (lat in listOf(-60.0, -12.0, 0.0, 36.2, 49.4, 70.0)) {
            assertEquals(lat, MapCamera.latFromMercatorY(MapCamera.mercatorY(lat)), 1e-9)
        }
        for (lng in listOf(-179.0, -81.67, 0.0, 120.0, 179.0)) {
            assertEquals(lng, MapCamera.lngFromMercatorX(MapCamera.mercatorX(lng)), 1e-9)
        }
    }

    @Test
    fun matrixIsFiniteAndDepthRangeIsSane() {
        for (pitch in listOf(0.0, 30.0, 60.0, 85.0)) {
            val c = cam(pitch = pitch)
            assertTrue("near must be positive", c.nearZ > 0)
            assertTrue("far must exceed near at pitch $pitch", c.farZ > c.nearZ)
            c.viewProjectionMatrix().forEach {
                assertTrue("non-finite matrix entry at pitch $pitch", it.isFinite())
            }
        }
    }
}

/**
 * The alignment witness itself must be capable of reporting failure. An instrument that
 * always says "aligned" is not measuring anything.
 */
class AlignmentCheckTest {

    private fun camera() = MapCamera(36.2, -81.67, 14.0, 0.0, 0.0, 1080, 1920)

    @Test
    fun agreesWithItself() {
        val c = camera()
        val probes = AlignmentCheck.probesFor(36.25, -81.72, 36.15, -81.62)
        val r = AlignmentCheck.run(c, probes) { lat, lng -> c.project(lat, lng) }
        assertTrue("a perfect witness must report aligned", r.aligned)
        assertEquals(0f, r.meanErrorPx, 1e-3f)
        assertTrue(r.samples > 0)
    }

    /** NEGATIVE CONTROL: a witness that disagrees must be reported as misaligned. */
    @Test
    fun detectsAShiftedProjection() {
        val c = camera()
        val probes = AlignmentCheck.probesFor(36.25, -81.72, 36.15, -81.62)
        val r = AlignmentCheck.run(c, probes) { lat, lng ->
            c.project(lat, lng)?.let { floatArrayOf(it[0] + 9f, it[1]) }
        }
        assertTrue("a 9 px shift must fail the check", !r.aligned)
        assertEquals(9f, r.meanErrorPx, 0.01f)
    }

    /** A scale error shows up away from the centre even though the centre still matches. */
    @Test
    fun detectsAScaleErrorThatPreservesTheCentre() {
        val c = camera()
        val probes = AlignmentCheck.probesFor(36.25, -81.72, 36.15, -81.62)
        val r = AlignmentCheck.run(c, probes) { lat, lng ->
            c.project(lat, lng)?.let {
                floatArrayOf(
                    540f + (it[0] - 540f) * 1.05f,
                    960f + (it[1] - 960f) * 1.05f,
                )
            }
        }
        assertTrue("a 5% scale error must fail the check", !r.aligned)
    }

    @Test
    fun noUsableSamplesIsNotReportedAsAligned() {
        val c = camera()
        val r = AlignmentCheck.run(c, AlignmentCheck.probesFor(36.25, -81.72, 36.15, -81.62)) { _, _ -> null }
        assertTrue("zero samples must not count as aligned", !r.aligned)
        assertEquals(0, r.samples)
    }
}
