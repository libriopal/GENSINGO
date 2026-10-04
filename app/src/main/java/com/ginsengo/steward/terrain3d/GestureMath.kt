package com.ginsengo.steward.terrain3d

import kotlin.math.PI
import kotlin.math.ln

/**
 * One gesture mapping for both views (exe.md A17): the same finger movement makes the same camera
 * change on the 2D map (MapLibre's own gesture detector) and in the 3D view (its Compose detector).
 * MapLibre 13.6.1 is the reference; its constants and formulas were read from its bytecode
 * (`docs/eincol/evidence/A.3-01-gesture-javap.txt`):
 *
 * - two fingers dragged up or down ("shove"): `tilt − 0.1 × Δy` px (`SHOVE_PIXEL_CHANGE_FACTOR`);
 * - pinch: `zoom + ln(scale) / ln(π/2) × 0.65` (`ZOOM_RATE`, the default zoom rate of 1);
 * - twist: the bearing changes by the fingers' angle, 1:1, the map turning with the fingers.
 *
 * One-finger pan already matches: both keep the ground under the finger ([CameraMath.pan]).
 * Before A.3 the 3D view tilted at 0.15 °/px, half as fast again as the map, and zoomed by log₂.
 *
 * Not matched, and said so: the register asks for one gesture *handler*; with two surfaces
 * MapLibre handles its own touches, so what is shared is the mapping. MapLibre's flings (pan,
 * zoom and rotate momentum) have no counterpart in 3D.
 */
object GestureMath {

    /** `MapLibreConstants.SHOVE_PIXEL_CHANGE_FACTOR` (a float in MapLibre: 0.1f). */
    const val TILT_DEG_PER_PX = 0.1f

    /** `MapLibreConstants.ZOOM_RATE` (a float in MapLibre: 0.65f). */
    const val ZOOM_RATE = 0.65f

    private val LN_HALF_PI = ln(PI / 2)

    /** Zoom change for one pinch event whose span changed by [scaleFactor] (Compose `calculateZoom`). */
    fun zoomDelta(scaleFactor: Float): Double =
        if (scaleFactor <= 0f) 0.0 else ln(scaleFactor.toDouble()) / LN_HALF_PI * ZOOM_RATE.toDouble()

    /** Pitch change for a two-finger drag of [dyPx] screen pixels (down is positive, as on screen). */
    fun pitchDelta(dyPx: Float): Double = -(TILT_DEG_PER_PX * dyPx).toDouble()

    /**
     * Bearing change for a twist of [clockwiseDeg] (Compose `calculateRotation`, clockwise on screen
     * positive). MapLibre adds `atan2(previous) − atan2(current)`, the negative of that angle.
     */
    fun bearingDelta(clockwiseDeg: Float): Double = -clockwiseDeg.toDouble()
}
