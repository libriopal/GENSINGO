package com.ginsengo.steward.ui.map

/**
 * Where the map opens, and when it is allowed to jump to a real fix.
 *
 * Pulled out of the composable so it can be tested, because getting it wrong is invisible in a
 * screenshot until someone is standing in the woods looking at the wrong state.
 *
 * The bug this exists to prevent: the map is built before the GPS has produced anything, so it
 * opens on a fallback. The fallback used to be 37.8, -81.2 at zoom 6 - southern West Virginia,
 * four states wide - and the follow branch was expected to rescue it once a fix arrived. It did
 * not. An ANIMATED camera move starts from wherever the camera is and can be interrupted by a
 * style load, a tilt change or the next position update, and an interrupted animation leaves the
 * camera where it started. A user in Haywood County with a 7 m fix and Following switched on was
 * looking at Charleston, with every DEM layer silently doing nothing because none of them mean
 * anything at zoom 6.
 */
object CameraStart {

    /** Western North Carolina. This is an NC tool; the fallback should be in NC. */
    const val FALLBACK_LAT = 35.55
    const val FALLBACK_LON = -82.95

    /** Wide enough to show which valley you are in, close enough for the DEM layers to mean something. */
    const val FALLBACK_ZOOM = 9.0

    /** Where the habitat layers are actually computed. */
    const val FIELD_ZOOM = 14.0

    data class Start(val lat: Double, val lon: Double, val zoom: Double, val isRealFix: Boolean)

    fun initial(fixLat: Double?, fixLon: Double?): Start =
        if (fixLat != null && fixLon != null) Start(fixLat, fixLon, FIELD_ZOOM, true)
        else Start(FALLBACK_LAT, FALLBACK_LON, FALLBACK_ZOOM, false)

    /**
     * True when the camera must JUMP rather than animate: the first real fix after opening on a
     * fallback. Instantaneous cannot be interrupted, so the first fix always lands.
     */
    fun shouldJumpToFix(hasFix: Boolean, alreadyCentred: Boolean): Boolean =
        hasFix && !alreadyCentred

    /** After the first landing, following is a smooth animation and may be switched off. */
    fun shouldAnimateFollow(hasFix: Boolean, alreadyCentred: Boolean, following: Boolean): Boolean =
        hasFix && alreadyCentred && following
}
