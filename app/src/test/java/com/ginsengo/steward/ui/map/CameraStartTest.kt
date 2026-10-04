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

    /**
     * I18: the phone's cached last-known fix (Play services' `lastLocation`, which can be hours old
     * and far away) arrives first, then the live fix. The stale one may open the map near it, but
     * the live one must still land, and once it has, nothing jumps again.
     */
    @Test
    fun aStaleFixCannotStealTheLanding() {
        val hours = 3 * 3_600_000L
        var landed = CameraStart.Landing.NONE
        val first = CameraStart.landing(fixAgeMs = hours, landed = landed)
        assertEquals("a stale fix lands only provisionally", CameraStart.Landing.PROVISIONAL, first)
        landed = first
        assertEquals("a second stale fix does not move the map again",
            CameraStart.Landing.NONE, CameraStart.landing(hours, landed))
        val live = CameraStart.landing(fixAgeMs = 1_500, landed = landed)
        assertEquals("the first fresh fix still lands", CameraStart.Landing.FINAL, live)
        landed = live
        for (age in listOf(0L, 1_000L, hours)) assertEquals("nothing jumps after the landing",
            CameraStart.Landing.NONE, CameraStart.landing(age, landed))
    }

    @Test
    fun aFreshFirstFixLandsAtOnceAndTheBoundaryIsInclusive() {
        assertEquals(CameraStart.Landing.FINAL, CameraStart.landing(0, CameraStart.Landing.NONE))
        assertEquals(CameraStart.Landing.FINAL, CameraStart.landing(CameraStart.FRESH_FIX_MS, CameraStart.Landing.NONE))
        assertEquals(CameraStart.Landing.PROVISIONAL, CameraStart.landing(CameraStart.FRESH_FIX_MS + 1, CameraStart.Landing.NONE))
        assertEquals("a clock-skewed fix from the future counts as fresh",
            CameraStart.Landing.FINAL, CameraStart.landing(-5_000, CameraStart.Landing.NONE))
    }
}

/**
 * Defaults, pinned. These changed deliberately and the reason should not be re-litigated by
 * accident: with the camera bug present, every DEM layer was computed for ground nobody was
 * standing on and drew nothing, so defaulting them OFF hid a broken app behind a clean one.
 */
class MapLayerDefaultsTest {

    @Test
    fun everyDrawableLayerIsOnByDefault() {
        val s = MapLayerState()
        assertTrue("hillshade should default on", s.hillshade)
        assertTrue("habitat heatmap should default on", s.habitat)
        assertTrue("where-I've-been heatmap should default on", s.visited)
        assertTrue("finds heatmap should default on", s.finds)
        assertTrue("suggestions should default on", s.suggestions)
        // The one deliberate exception: the visited heatmap already summarises the track.
        assertFalse("track line defaults off", s.trackLine)
    }

    /**
     * The GL mesh over the map blacked the screen (a SurfaceView punches through the
     * TextureView map). It used to be a field pinned to false; now the option does not exist
     * at all, and the real 3D terrain is its own screen. Pin the absence.
     */
    @Test
    fun thereIsNoMeshOverTheMapOption() {
        val fields = MapLayerState::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("a mesh-over-map layer option reappeared: $fields", fields.any { "mesh" in it })
    }
}
