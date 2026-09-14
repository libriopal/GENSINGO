package com.ginsengo.steward.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStartTest {

    /**
     * The reported bug, pinned. The fallback must be in western North Carolina - not the
     * 37.8, -81.2 that put a Haywood County user in Charleston, West Virginia.
     */
    @Test
    fun theNoFixFallbackIsInWesternNorthCarolina() {
        val s = CameraStart.initial(null, null)
        assertFalse(s.isRealFix)
        assertTrue("fallback latitude ${s.lat} is not in the NC mountains", s.lat in 35.0..36.3)
        assertTrue("fallback longitude ${s.lon} is not in the NC mountains", s.lon in -84.5..-82.0)
    }

    /** And it must not open so far out that the habitat layers are meaningless. */
    @Test
    fun theFallbackZoomIsCloseEnoughForTheLayersToMeanSomething() {
        assertTrue(
            "zoom ${CameraStart.FALLBACK_ZOOM} is too wide; the DEM layers do nothing there",
            CameraStart.FALLBACK_ZOOM >= 8.0,
        )
    }

    @Test
    fun aRealFixOpensOnTheFixAtFieldZoom() {
        val s = CameraStart.initial(35.6144, -83.0319)
        assertTrue(s.isRealFix)
        assertEquals(35.6144, s.lat, 1e-9)
        assertEquals(-83.0319, s.lon, 1e-9)
        assertEquals(CameraStart.FIELD_ZOOM, s.zoom, 1e-9)
    }

    /**
     * The first fix JUMPS. Animating it was the defect: an interrupted animation leaves the
     * camera where it started, which is how Following stayed on while the map never moved.
     */
    @Test
    fun theFirstFixJumpsAndOnlyTheFirst() {
        assertTrue(CameraStart.shouldJumpToFix(hasFix = true, alreadyCentred = false))
        assertFalse(
            "must not keep jumping afterwards",
            CameraStart.shouldJumpToFix(hasFix = true, alreadyCentred = true),
        )
        assertFalse(
            "nothing to jump to without a fix",
            CameraStart.shouldJumpToFix(hasFix = false, alreadyCentred = false),
        )
    }

    @Test
    fun followingAnimatesOnlyAfterTheFirstLanding() {
        assertFalse(
            "the first fix must jump, not animate",
            CameraStart.shouldAnimateFollow(true, alreadyCentred = false, following = true),
        )
        assertTrue(CameraStart.shouldAnimateFollow(true, alreadyCentred = true, following = true))
        assertFalse(
            "Following off means the camera stays put",
            CameraStart.shouldAnimateFollow(true, alreadyCentred = true, following = false),
        )
    }

    /** Negative control: the branches must be mutually exclusive, or the camera fights itself. */
    @Test
    fun jumpAndFollowAreNeverBothTrue() {
        for (fix in listOf(true, false)) for (centred in listOf(true, false))
            for (follow in listOf(true, false)) {
                val jump = CameraStart.shouldJumpToFix(fix, centred)
                val anim = CameraStart.shouldAnimateFollow(fix, centred, follow)
                assertFalse("both fired for fix=$fix centred=$centred follow=$follow", jump && anim)
            }
    }
}
