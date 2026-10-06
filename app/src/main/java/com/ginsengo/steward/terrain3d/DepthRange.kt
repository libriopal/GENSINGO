package com.ginsengo.steward.terrain3d

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.tan

/**
 * The 3D view's near plane, from the ground it can actually see (exe.md B7).
 *
 * A depth buffer of b bits resolves, at eye depth d, a step of about d² (f − n) / (f · n · 2^b):
 * precision is set by the near plane n far more than by the far plane f. MapLibre's near plane,
 * viewportHeight / 50 (48 px on a 2400-px phone) wherever the camera is, leaves a 16-bit buffer
 * (the fallback some GPUs give the 3D view) resolving only ~16 m of depth at the far corner of the
 * fitted square: coarse enough for a ridge to bleed into the slope behind it.
 *
 * No terrain the camera sees can be nearer the eye than the eye's height above the highest ground,
 * and a point inside the frustum lies at most the frustum's half-diagonal off the view axis, so its
 * eye depth is at least that height times the half-diagonal's cosine. A near plane at [SAFETY] of
 * that bound clips nothing and buys 10–35× in precision. When the eye is level with a ridge or
 * below it (zoomed close into steep ground) the bound vanishes and MapLibre's plane stands.
 */
object DepthRange {

    const val SAFETY = 0.9

    /**
     * @param highestAbovePlanePx the highest ground above the camera's target plane, in the
     *   view's pixels, with the relief scale and the exaggeration it is drawn with applied
     */
    fun near(cam: MapCamera, highestAbovePlanePx: Double): Double {
        val eyeHeight = cam.cameraToCenterDistance * cos(Math.toRadians(cam.pitchDeg.coerceIn(0.0, 85.0)))
        val aspect = cam.viewportWidth.toDouble() / cam.viewportHeight
        val halfDiagonal = atan(tan(MapCamera.FOV / 2.0) * hypot(1.0, aspect))
        val bound = (eyeHeight - highestAbovePlanePx) * cos(halfDiagonal) * SAFETY
        return maxOf(cam.nearZ, bound)
    }

    /** The eye-depth step a [bits]-bit depth buffer resolves at eye depth [d], planes [n] and [f]. */
    fun step(d: Double, n: Double, f: Double, bits: Int): Double =
        d * d * (f - n) / (f * n * Math.pow(2.0, bits.toDouble()))
}
