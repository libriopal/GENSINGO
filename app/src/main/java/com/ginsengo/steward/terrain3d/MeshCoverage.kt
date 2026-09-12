package com.ginsengo.steward.terrain3d

import kotlin.math.abs
import kotlin.math.max

/**
 * Decides when the terrain mesh has to be rebuilt.
 *
 * Rebuilding only on camera idle leaves stale terrain on screen for the whole of a pan.
 * Rebuilding on every camera move event is worse: a move listener fires per frame, and a
 * rebuild is tens of milliseconds of DEM work, so the build queue would never drain and the
 * terrain would lag further behind the longer the gesture lasted.
 *
 * The way out is to stop tying the mesh to the viewport. The mesh is built over a region
 * LARGER than the screen, and is only rebuilt when the camera has moved far enough that the
 * screen is approaching the edge of what was built, or zoomed far enough that the vertex
 * density or elevation tiles are wrong. Panning inside the built region needs nothing.
 *
 * Pure and unit-tested, because this policy is what decides between a map that thrashes and
 * a map that lags, and neither failure is visible in a screenshot.
 */
object MeshCoverage {

    /**
     * How much wider than the viewport to build.
     *
     * 1.8 means the mesh covers 80% more ground than is visible, so the camera can travel
     * ~40% of a screen width in any direction before anything is rebuilt. Larger wastes DEM
     * work on ground behind the user; smaller rebuilds too often. At 1.8 a typical pan
     * crosses one rebuild rather than none or ten.
     */
    const val MARGIN = 1.8

    /**
     * Rebuild once the viewport has eaten this fraction of the margin. Below 1.0 so the new
     * mesh is requested while the old one still covers the screen — the rebuild happens
     * behind live terrain rather than behind a hole.
     */
    const val REBUILD_AT = 0.6

    /** Zoom change that invalidates vertex density or the DEM tile level. */
    const val ZOOM_EPSILON = 0.6

    data class Region(
        val north: Double, val west: Double, val south: Double, val east: Double,
        val zoom: Double,
    ) {
        val latSpan: Double get() = north - south
        val lngSpan: Double get() = east - west
        val centreLat: Double get() = (north + south) / 2.0
        val centreLng: Double get() = (east + west) / 2.0

        fun contains(other: Region): Boolean =
            other.north <= north && other.south >= south &&
                    other.west >= west && other.east <= east
    }

    /** Expands a viewport into the region a mesh should be built over. */
    fun expand(viewport: Region, margin: Double = MARGIN): Region {
        val dLat = viewport.latSpan * (margin - 1.0) / 2.0
        val dLng = viewport.lngSpan * (margin - 1.0) / 2.0
        return Region(
            north = viewport.north + dLat,
            west = viewport.west - dLng,
            south = viewport.south - dLat,
            east = viewport.east + dLng,
            zoom = viewport.zoom,
        )
    }

    /**
     * @param built    the region the current mesh was built over, or null if none
     * @param viewport what the camera can currently see
     */
    fun needsRebuild(built: Region?, viewport: Region): Boolean {
        if (built == null) return true

        // Zoom moved enough to change vertex density or DEM tile level.
        if (abs(built.zoom - viewport.zoom) >= ZOOM_EPSILON) return true

        // Viewport no longer inside what was built: already too late, rebuild now.
        if (!built.contains(viewport)) return true

        // How much headroom is left on the tightest side, as a fraction of the margin that
        // was built. Rebuild before it runs out, not after.
        val marginLat = (built.latSpan - viewport.latSpan) / 2.0
        val marginLng = (built.lngSpan - viewport.lngSpan) / 2.0
        if (marginLat <= 0.0 || marginLng <= 0.0) return true

        val usedNorth = (viewport.north - (built.north - marginLat)) / marginLat
        val usedSouth = ((built.south + marginLat) - viewport.south) / marginLat
        val usedWest = ((built.west + marginLng) - viewport.west) / marginLng
        val usedEast = (viewport.east - (built.east - marginLng)) / marginLng

        val used = max(max(usedNorth, usedSouth), max(usedWest, usedEast))
        return used >= REBUILD_AT
    }

    /**
     * Minimum gap between rebuild attempts, in milliseconds.
     *
     * Even with margin coverage, a fast fling crosses region after region. Without a floor
     * the overlay would start a build it is about to throw away, several times a second.
     * 250 ms is long enough that a build (measured at 40-66 ms of DEM work on a JVM, so
     * perhaps 200 ms on a handset) usually completes before the next is considered.
     */
    const val MIN_REBUILD_INTERVAL_MS = 250L

    fun shouldStartRebuild(
        built: Region?,
        viewport: Region,
        lastAttemptMs: Long,
        nowMs: Long,
        buildInFlight: Boolean,
    ): Boolean {
        if (!needsRebuild(built, viewport)) return false
        // A build already running for a region that still covers the screen is left alone;
        // cancelling and restarting mid-fling is how a mesh never finishes at all.
        if (buildInFlight && built != null && built.contains(viewport)) return false
        return nowMs - lastAttemptMs >= MIN_REBUILD_INTERVAL_MS
    }
}
