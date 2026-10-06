package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.geo.Projection
import com.ginsengo.steward.terrain.DemTileStore
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which square the one map builds for the camera (owner directive J30: the 3D view is the only
 * map). A square is always 3 × 3 elevation tiles, so its DEM zoom sets its size: zoom 15 is ~3 km
 * at 3.9 m cells, 14 is 6 km, 13 is 12 km, 12 is 24 km, 11 is 48 km at ~61 m cells, which holds
 * the whole 10-mile radius and its ring. Each level is the square that fits the screen at the
 * camera's zoom; one level per halving of the view.
 *
 * Pure, so the choice is tested: every level fits the zoom that picks it, and a camera resting
 * near a boundary does not flip between two levels (each switch is a rebuild).
 */
object SquareLevel {
    const val FINEST = 15
    const val COARSEST = 11

    /** How far past a level's own fit zoom the camera may go before the next level is built. */
    const val HYSTERESIS = 0.25

    /** Width of a level's square at [lat], metres. */
    fun widthM(level: Int, lat: Double): Double =
        Terrain3D.AREA_TILES * DemTileStore.TILE * Projection.metresPerPixel(lat, Projection.worldPx(level, DemTileStore.TILE))

    /** The camera zoom at which a level's square fills the screen's width (Terrain3D.fitZoom). */
    fun fitZoom(level: Int, lat: Double, viewportWidthPx: Int): Double =
        Terrain3D.fitZoom(widthM(level, lat), lat, viewportWidthPx)

    /** The zoom range the one map allows: a little past the coarsest square, deep into the finest. */
    fun minZoom(lat: Double, viewportWidthPx: Int) = fitZoom(COARSEST, lat, viewportWidthPx) - 1.0
    fun maxZoom(lat: Double, viewportWidthPx: Int) = fitZoom(FINEST, lat, viewportWidthPx) + 4.0

    /**
     * The level for a camera at [zoom]: the one whose fit zoom is nearest, but [current] is kept
     * while the camera is within half a level plus [HYSTERESIS] of its fit.
     */
    fun forZoom(zoom: Double, lat: Double, viewportWidthPx: Int, current: Int?): Int {
        val fitFinest = fitZoom(FINEST, lat, viewportWidthPx)
        // Each level down halves the zoom at which its square fits: fit(L) = fit(15) − (15 − L).
        val ideal = (FINEST + (zoom - fitFinest)).roundToInt().coerceIn(COARSEST, FINEST)
        if (current == null || current == ideal) return ideal
        // Past either end [ideal] is clamped to [current] and returned above: nothing rebuilds.
        val fitCurrent = fitFinest - (FINEST - current)
        return if (abs(zoom - fitCurrent) > 0.5 + HYSTERESIS) ideal else current
    }

    /**
     * Position-on-slope radius for a level: the 2D map's own rule at the zoom where that level
     * fills the screen ([DemTileStore.tpiRadiusMetresFor]), so a cove reads as the same cove at
     * every level (300 m for the 3 km square, as before; 1,500 m for the 48 km one).
     */
    fun tpiRadiusM(level: Int, lat: Double, viewportWidthPx: Int): Double =
        DemTileStore.tpiRadiusMetresFor(fitZoom(level, lat, viewportWidthPx))
}
