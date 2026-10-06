package com.ginsengo.steward.field

import com.ginsengo.steward.data.db.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The way back (J21), the battery field mode (J24) and frame pacing (J32). */
class FieldPowerTest {

    private fun pt(session: String, lat: Double, lng: Double, t: Long) = TrackPoint(sessionId = session, lat = lat, lng = lng, accuracyM = 5f, altitudeM = null, time = t)

    // Great-circle distance and initial bearing, written out here independently.
    private fun haversine(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2)
        val dp = p2 - p1; val dl = Math.toRadians(lo2 - lo1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * 6_371_000.0 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearing(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2); val dl = Math.toRadians(lo2 - lo1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    @Test
    fun theWayBackLeadsToWhereTodaysTrackBegan() {
        val track = listOf(
            pt("today", 35.560, -82.940, 2_000),
            pt("today", 35.550, -82.950, 1_000),   // the earliest: the start, though listed second
            pt("yesterday", 35.400, -83.200, 500), // another session, earlier still
            pt("today", 35.565, -82.935, 3_000),
        )
        val leg = WayBack.toStart(track, "today", 35.565, -82.935)!!
        assertEquals(haversine(35.565, -82.935, 35.550, -82.950), leg.distanceM, 2.0)
        assertEquals(bearing(35.565, -82.935, 35.550, -82.950), leg.bearingDeg, 0.5)
        assertTrue(leg.bearingDeg in 180.0..270.0)
    }

    @Test
    fun noSessionNoWayBack() {
        assertNull(WayBack.toStart(listOf(pt("a", 35.0, -83.0, 0)), null, 35.0, -83.0))
        assertNull(WayBack.toStart(listOf(pt("a", 35.0, -83.0, 0)), "b", 35.0, -83.0))
        assertNull(WayBack.toStart(emptyList(), "a", 35.0, -83.0))
    }

    @Test
    fun theChipSaysDistanceAndCompassPoint() {
        assertEquals("340 m S", WayBack.describe(WayBack.Leg(340.7, 180.0)))
        assertEquals("1.2 km NE", WayBack.describe(WayBack.Leg(1_234.0, 45.0)))
        assertEquals("999 m N", WayBack.describe(WayBack.Leg(999.9, 359.0)))
        assertEquals("1.0 km N", WayBack.describe(WayBack.Leg(1_000.0, 22.49)))
        assertEquals("10 m NE", WayBack.describe(WayBack.Leg(10.0, 22.5)))
        assertEquals("10 m W", WayBack.describe(WayBack.Leg(10.0, -90.0)))
        assertEquals("10 m NW", WayBack.describe(WayBack.Leg(10.0, 337.4)))
    }

    @Test
    fun batteryModeFollowsTheThresholdThePlugAndThePhonesOwnSaver() {
        val t = BatteryMode.DEFAULT_THRESHOLD_PCT
        assertTrue(BatteryMode.on(t, charging = false, systemSaver = false, thresholdPct = t))
        assertFalse(BatteryMode.on(t + 1, charging = false, systemSaver = false, thresholdPct = t))
        assertFalse("plugged in, a low reading is not a reason", BatteryMode.on(5, charging = true, systemSaver = false, thresholdPct = t))
        assertTrue("the owner's own battery saver always counts", BatteryMode.on(90, charging = true, systemSaver = true, thresholdPct = t))
        assertFalse("an unknown battery is not an empty one", BatteryMode.on(null, charging = false, systemSaver = false, thresholdPct = t))
        assertTrue(t in BatteryMode.THRESHOLD_RANGE)
    }

    @Test
    fun batteryModeIsLighterOnEveryLever() {
        assertTrue(BatteryMode.GRID_CAP < com.ginsengo.steward.terrain3d.Terrain3D.MAX_GRID)
        assertTrue(BatteryMode.FRAME_MS_SAVER > BatteryMode.FRAME_MS)
        assertTrue(BatteryMode.TEXTURE_CAP < com.ginsengo.steward.terrain3d.TerrainTextures.MAX_SIZE)
    }

    @Test
    fun framesArePacedAndTheLastRequestIsNeverLost() {
        val p = FramePacer(33)
        assertEquals(0L, p.request(1_000))
        assertEquals(23L, p.request(1_010))      // too soon: one trailing frame in 23 ms
        assertEquals(-1L, p.request(1_020))      // already scheduled; it will draw the latest camera
        assertEquals(-1L, p.request(1_040))      // even past the interval, until it has drawn
        p.trailingDrawn(1_033)
        assertEquals(26L, p.request(1_040))
        p.trailingDrawn(1_066)
        assertEquals(0L, p.request(1_200))       // a quiet camera draws at once
        p.minIntervalMs = 50
        assertEquals(50L, p.request(1_200))
    }

    @Test
    fun aGestureDrawsAtMostOneFramePerInterval() {
        val p = FramePacer(33)
        var drawn = 0
        var trailingAt = -1L
        // 120 Hz touch events for one second.
        var t = 0L
        while (t < 1_000) {
            if (trailingAt in 0..t) { p.trailingDrawn(trailingAt); drawn++; trailingAt = -1 }
            when (val r = p.request(t)) {
                0L -> drawn++
                -1L -> Unit
                else -> trailingAt = t + r
            }
            t += 8
        }
        assertTrue("$drawn frames", drawn in 25..31)
    }
}
