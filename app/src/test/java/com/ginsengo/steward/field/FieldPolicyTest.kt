package com.ginsengo.steward.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPolicyTest {

    private fun plan(
        tracking: Boolean = true, screenOn: Boolean = true, battery: Int? = 80,
        charging: Boolean = false, speed: Double? = 1.2, still: Long = 0L,
    ) = PowerPolicy.plan(tracking, screenOn, battery, charging, speed, still)

    @Test
    fun walkingWithScreenOnIsLiveHighAccuracy() {
        val p = plan()
        assertEquals(PowerPolicy.Mode.TRACKING_MOVING, p.mode)
        assertEquals(PowerPolicy.Accuracy.HIGH, p.accuracy)
        assertEquals(0L, p.maxDelayMs)
    }

    @Test
    fun screenOffBatchesButKeepsEveryFix() {
        val on = plan(screenOn = true)
        val off = plan(screenOn = false)
        assertTrue("screen off must batch", off.maxDelayMs > 0)
        assertEquals("batching must not thin the track", on.intervalMs, off.intervalMs)
        assertEquals(on.accuracy, off.accuracy)
    }

    @Test
    fun standingStillSlowsDownAndDropsToBalanced() {
        val p = plan(speed = 0.0, still = PowerPolicy.STILL_AFTER_MS + 1)
        assertEquals(PowerPolicy.Mode.TRACKING_STILL, p.mode)
        assertEquals(PowerPolicy.Accuracy.BALANCED, p.accuracy)
        assertTrue(p.intervalMs > plan().intervalMs)
    }

    @Test
    fun recentMovementCountsAsMovingEvenWithoutSpeed() {
        assertEquals(PowerPolicy.Mode.TRACKING_MOVING, plan(speed = null, still = 10_000).mode)
    }

    @Test
    fun lowAndCriticalBatteryStretchTheIntervalAndStopAutoResearch() {
        val low = plan(battery = PowerPolicy.LOW_BATTERY_PCT)
        val crit = plan(battery = PowerPolicy.CRITICAL_BATTERY_PCT)
        assertEquals(PowerPolicy.Mode.LOW_BATTERY, low.mode)
        assertEquals(PowerPolicy.Mode.CRITICAL_BATTERY, crit.mode)
        assertTrue(low.intervalMs > plan().intervalMs)
        assertTrue(crit.intervalMs > low.intervalMs)
        assertFalse(low.allowAutoResearch)
        assertFalse(crit.allowAutoResearch)
    }

    @Test
    fun chargingOverridesALowReading() {
        assertEquals(PowerPolicy.Mode.TRACKING_MOVING, plan(battery = 5, charging = true).mode)
    }

    @Test
    fun anUnknownBatteryIsTreatedAsHealthyNotEmpty() {
        assertEquals(PowerPolicy.Mode.TRACKING_MOVING, plan(battery = null).mode)
    }

    @Test
    fun autoResearchNeedsThirtyPercent() {
        assertFalse(plan(tracking = false, battery = 29).allowAutoResearch)
        assertTrue(plan(tracking = false, battery = 30).allowAutoResearch)
    }

    @Test
    fun viewingIsResponsiveAndNeverBatched() {
        val p = plan(tracking = false)
        assertEquals(PowerPolicy.Mode.VIEWING, p.mode)
        assertEquals(0L, p.maxDelayMs)
        assertTrue(p.intervalMs <= 5_000L)
    }
}

class TrackFilterTest {

    private fun fix(dLatM: Double, dLngM: Double, acc: Float, t: Long, speed: Float? = null) = TrackFilter.Fix(
        35.55 + dLatM / 111_320.0, -82.95 + dLngM / (111_320.0 * Math.cos(Math.toRadians(35.55))), acc, t, speed,
    )

    private fun standStill(speed: Float?): TrackFilter {
        val f = TrackFilter()
        val r = java.util.Random(3)
        for (i in 0 until 600) {   // ten minutes at 1 Hz, 6 m jitter per axis, 12 m accuracy
            f.accept(fix(r.nextGaussian() * 6, r.nextGaussian() * 6, 12f, i * 1000L, speed))
        }
        return f
    }

    /** The failure raw GPS produces: a standing phone "walks" around its own error circle. */
    @Test
    fun aStillPhoneWithDopplerSpeedStoresAlmostNothing() {
        val f = standStill(speed = 0.05f)
        println("MEASURED still+speed: distance=${f.distanceM}")
        assertTrue("phantom distance ${f.distanceM} m while standing still", f.distanceM < 40)
    }

    /**
     * No speed on the fix, INDEPENDENT jitter (the worst case; real jitter is correlated and
     * wanders slower). The single-fix rule measured 3,432 m of phantom track here in ten
     * minutes; the centroid rule must stay an order of magnitude under that.
     */
    @Test
    fun withoutSpeedTheCentroidRuleKeepsPhantomDistanceSmall() {
        val f = standStill(speed = null)
        println("MEASURED still+nospeed: distance=${f.distanceM}")
        assertTrue("phantom distance ${f.distanceM} m", f.distanceM < 150)
    }

    /**
     * Walk, stop, walk, stop, with no speed on the fixes. This is where the centroid window
     * matters: straight after each store the window restarts, and a single-fix rule would
     * store jitter every time the user stops. (Added because mutant T2 survived a test that
     * only ever stood still once.)
     */
    @Test
    fun withoutSpeedStopsDoNotInflateTheTrack() {
        val f = TrackFilter()
        val r = java.util.Random(11)
        var t = 0L; var x = 0.0
        repeat(15) {
            repeat(12) { x += 5.0; t += 4_000; f.accept(fix(x + r.nextGaussian() * 3, r.nextGaussian() * 3, 6f, t)) }
            repeat(30) { t += 2_000; f.accept(fix(x + r.nextGaussian() * 6, r.nextGaussian() * 6, 12f, t)) }
        }
        println("MEASURED walk-stop+nospeed: true=${x} distance=${f.distanceM}")
        // Measured: 903 m of 900. With the centroid window disabled (mutant T2): 1,107 m.
        assertEquals(x, f.distanceM, x * 0.10)
    }

    @Test
    fun withoutSpeedWalkingIsStillRecorded() {
        val f = TrackFilter()
        var stored = 0
        for (i in 0..200) if (f.accept(fix(i * 5.0, 0.0, 6f, i * 4000L, null))) stored++
        println("MEASURED walking+nospeed: stored=$stored distance=${f.distanceM}")
        assertTrue("stored $stored", stored >= 20)
        assertEquals(1000.0, f.distanceM, 40.0)
    }

    @Test
    fun walkingIsRecordedAndMeasured() {
        val f = TrackFilter()
        var stored = 0
        for (i in 0..100) if (f.accept(fix(i * 5.0, 0.0, 6f, i * 4000L, 1.3f))) stored++
        println("MEASURED walking: stored=$stored distance=${f.distanceM}")
        assertTrue("stored $stored: fewer than one point per ~12 m", stored >= 40)
        assertEquals(500.0, f.distanceM, 12.0)
    }

    @Test
    fun slowProspectingPaceIsStillRecordedJustCoarser() {
        val f = TrackFilter()
        var stored = 0
        for (i in 0..300) if (f.accept(fix(i * 1.0, 0.0, 8f, i * 6000L, 0.1f))) stored++
        println("MEASURED slow: stored=$stored")
        assertTrue("stored $stored over 300 m at prospecting pace", stored >= 10)
    }

    @Test
    fun badFixesAreNotPositions() {
        val f = TrackFilter()
        assertFalse(f.accept(fix(0.0, 0.0, 80f, 0)))
        assertFalse(f.accept(fix(0.0, 0.0, 0f, 0)))
        assertTrue(f.accept(fix(0.0, 0.0, 10f, 0)))
    }

    @Test
    fun aSpikeIsRejectedAndDoesNotMoveTheTrack() {
        val f = TrackFilter()
        assertTrue(f.accept(fix(0.0, 0.0, 5f, 0, 1.3f)))
        assertFalse("400 m in one second is a spike", f.accept(fix(400.0, 0.0, 5f, 1_000, 1.3f)))
        assertTrue(f.accept(fix(10.0, 0.0, 5f, 5_000, 1.3f)))
        assertEquals(10.0, f.distanceM, 0.5)
    }

    @Test
    fun aSpikeNeverEntersTheNoSpeedCentroid() {
        val f = TrackFilter()
        assertTrue(f.accept(fix(0.0, 0.0, 5f, 0)))
        // Four quiet fixes, one spike, then the fifth quiet fix: the spike must not drag
        // the centroid 80 m and store a place nobody stood.
        for (i in 1..4) assertFalse(f.accept(fix(0.5, 0.0, 5f, i * 1000L)))
        assertFalse(f.accept(fix(400.0, 0.0, 5f, 5_000)))
        assertFalse(f.accept(fix(0.5, 0.0, 5f, 6_000)))
        assertEquals(0.0, f.distanceM, 0.0)
    }
}

class FixAveragerTest {

    private fun loc(lat: Double, lng: Double, acc: Float, t: Long = 0) = FieldLocation(lat, lng, acc, null, t)

    @Test
    fun preciseFixesPullTheAverage() {
        val r = FixAverager.average(listOf(loc(35.0, -82.0, 5f), loc(35.001, -82.001, 50f)))!!
        assertTrue("average should sit near the 5 m fix", r.lat < 35.0001)
    }

    /** The claim it refuses to make: averaging correlated GNSS error is not sqrt(n). */
    @Test
    fun reportedAccuracyIsTheBestFixNeverTheSqrtNShrink() {
        val fixes = (0 until 16).map { loc(35.0, -82.0, 16f, it.toLong()) }
        assertEquals(16f, FixAverager.average(fixes)!!.accuracyM)
    }

    @Test
    fun unusableFixesAreIgnoredAndNothingIsInvented() {
        assertNull(FixAverager.average(emptyList()))
        assertNull(FixAverager.average(listOf(loc(35.0, -82.0, 0f))))
        val r = FixAverager.average(listOf(loc(35.0, -82.0, 0f), loc(36.0, -83.0, 9f, 7)))
        assertNotNull(r)
        assertEquals(36.0, r!!.lat, 1e-9)
        assertEquals(1, r.fixCount)
        assertEquals(7L, r.newestTime)
    }
}
