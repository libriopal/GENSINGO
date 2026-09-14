package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rebuild policy. Both failure modes are invisible in a screenshot and both are tested:
 *
 *  - LAGGING: never rebuilding during a gesture, so the screen shows terrain from wherever
 *    the camera used to be.
 *  - THRASHING: rebuilding on every camera move event, so builds queue faster than they
 *    finish and the mesh falls further behind the longer the gesture runs.
 */
class MeshCoverageTest {

    /** A viewport roughly 0.02 deg tall, the scale of one hillside at field zoom. */
    private fun viewport(
        centreLat: Double = 36.20,
        centreLng: Double = -81.67,
        zoom: Double = 14.0,
        latSpan: Double = 0.02,
        lngSpan: Double = 0.03,
    ) = MeshCoverage.Region(
        north = centreLat + latSpan / 2, south = centreLat - latSpan / 2,
        west = centreLng - lngSpan / 2, east = centreLng + lngSpan / 2,
        zoom = zoom,
    )

    @Test
    fun expandedRegionContainsTheViewportAndIsCentredOnIt() {
        val vp = viewport()
        val built = MeshCoverage.expand(vp)
        assertTrue("built region must cover the viewport", built.contains(vp))
        org.junit.Assert.assertEquals(vp.centreLat, built.centreLat, 1e-12)
        org.junit.Assert.assertEquals(vp.centreLng, built.centreLng, 1e-12)
        org.junit.Assert.assertEquals(
            vp.latSpan * MeshCoverage.MARGIN, built.latSpan, 1e-12
        )
    }

    @Test
    fun firstBuildIsAlwaysNeeded() {
        assertTrue(MeshCoverage.needsRebuild(null, viewport()))
    }

    @Test
    fun stayingPutNeedsNoRebuild() {
        val vp = viewport()
        val built = MeshCoverage.expand(vp)
        assertFalse("an unmoved camera must not rebuild", MeshCoverage.needsRebuild(built, vp))
    }

    /** The point of the margin: small pans are free. */
    @Test
    fun smallPansInsideTheMarginAreFree() {
        val start = viewport()
        val built = MeshCoverage.expand(start)
        // Margin is 0.4 of a viewport on each side; a tenth of a screen is well inside it.
        val nudged = viewport(centreLat = 36.20 + start.latSpan * 0.1)
        assertFalse(
            "a tenth-screen pan must not trigger a rebuild",
            MeshCoverage.needsRebuild(built, nudged),
        )
    }

    /** But it must rebuild BEFORE the screen leaves the built region, not after. */
    @Test
    fun rebuildsBeforeTheViewportReachesTheEdge() {
        val start = viewport()
        val built = MeshCoverage.expand(start)
        val marginLat = (built.latSpan - start.latSpan) / 2.0

        // Still fully inside the built region...
        val nearEdge = viewport(centreLat = 36.20 + marginLat * 0.8)
        assertTrue(
            "viewport still inside the mesh", built.contains(nearEdge)
        )
        // ...but far enough in that the rebuild should already have been requested.
        assertTrue(
            "must rebuild while live terrain still covers the screen",
            MeshCoverage.needsRebuild(built, nearEdge),
        )
    }

    @Test
    fun leavingTheBuiltRegionAlwaysRebuilds() {
        val start = viewport()
        val built = MeshCoverage.expand(start)
        val gone = viewport(centreLat = 36.40)
        assertFalse(built.contains(gone))
        assertTrue(MeshCoverage.needsRebuild(built, gone))
    }

    @Test
    fun zoomingRebuildsEvenWithoutPanning() {
        val start = viewport(zoom = 14.0)
        val built = MeshCoverage.expand(start)
        assertFalse(
            "a trivial zoom nudge should not rebuild",
            MeshCoverage.needsRebuild(built, viewport(zoom = 14.2)),
        )
        assertTrue(
            "a zoom level change must rebuild: vertex density and DEM level both move",
            MeshCoverage.needsRebuild(built, viewport(zoom = 15.0)),
        )
    }

    // ---------------------------------------------------------------- throttling

    @Test
    fun rebuildIsThrottledDuringAFling() {
        val start = viewport()
        val built = MeshCoverage.expand(start)
        val far = viewport(centreLat = 36.40)
        val t0 = 1_000_000L

        assertTrue(
            "first attempt should go",
            MeshCoverage.shouldStartRebuild(built, far, lastAttemptMs = 0L, nowMs = t0, buildInFlight = false),
        )
        assertFalse(
            "a second attempt 16 ms later (one frame) must be refused",
            MeshCoverage.shouldStartRebuild(built, far, lastAttemptMs = t0, nowMs = t0 + 16, buildInFlight = false),
        )
        assertTrue(
            "after the interval it may go again",
            MeshCoverage.shouldStartRebuild(
                built, far,
                lastAttemptMs = t0,
                nowMs = t0 + MeshCoverage.MIN_REBUILD_INTERVAL_MS,
                buildInFlight = false,
            ),
        )
    }

    /**
     * A 60 fps fling fires a move event every ~16 ms. Over a second that is ~60 events; the
     * policy must convert them into a handful of builds, not sixty.
     */
    @Test
    fun aFullSecondOfFlingProducesOnlyAFewBuilds() {
        var built: MeshCoverage.Region? = MeshCoverage.expand(viewport())
        var lastAttempt = 0L
        var builds = 0
        var lat = 36.20

        for (frame in 0 until 60) {
            val now = frame * 16L
            lat += 0.0015 // ~ one screen height per second
            val vp = viewport(centreLat = lat)
            if (MeshCoverage.shouldStartRebuild(built, vp, lastAttempt, now, buildInFlight = false)) {
                lastAttempt = now
                built = MeshCoverage.expand(vp)
                builds++
            }
        }
        assertTrue("a fling must produce SOME rebuilds, got $builds", builds >= 1)
        assertTrue(
            "a one-second fling must not queue a build per frame, got $builds",
            builds <= 5,
        )
    }

    @Test
    fun aBuildAlreadyCoveringTheScreenIsNotRestarted() {
        val start = viewport()
        val built = MeshCoverage.expand(start)
        val marginLat = (built.latSpan - start.latSpan) / 2.0
        val nearEdge = viewport(centreLat = 36.20 + marginLat * 0.8)

        assertTrue("baseline: this viewport does want a rebuild", MeshCoverage.needsRebuild(built, nearEdge))
        assertFalse(
            "must not cancel and restart a build while the old mesh still covers the screen",
            MeshCoverage.shouldStartRebuild(
                built, nearEdge, lastAttemptMs = 0L, nowMs = 10_000L, buildInFlight = true,
            ),
        )
    }

    /** If the screen has actually left the mesh, an in-flight build is superseded. */
    @Test
    fun anInFlightBuildIsSupersededOnceTheScreenLeavesTheMesh() {
        val built = MeshCoverage.expand(viewport())
        val gone = viewport(centreLat = 36.40)
        assertTrue(
            MeshCoverage.shouldStartRebuild(
                built, gone, lastAttemptMs = 0L, nowMs = 10_000L, buildInFlight = true,
            ),
        )
    }
}
