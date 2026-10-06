package com.ginsengo.steward.terrain3d

import kotlin.math.abs

/**
 * The 3D view's camera maths (blueprint docs/blueprints/one-map.md, WP-B); the 2D conversions
 * went with the flat map in M.1.
 * Pure JVM code: no Android, no MapLibre.
 */
object CameraMath {

    /** Fixed-point steps that move the touched point onto the terrain (blueprint §G, claim 4). */
    const val HEIGHT_ITERATIONS = 4

    /** A flatter camera is raised to this pitch on the one map, so the relief reads as relief. */
    const val MIN_3D_PITCH = 45.0

    fun mapCamera(cam: CameraState, viewportW: Int, viewportH: Int): MapCamera =
        MapCamera(cam.lat, cam.lng, cam.zoom, cam.bearing, cam.pitch, viewportW, viewportH)

    /**
     * One-finger pan that keeps the TERRAIN under the finger: the point on the ground seen at
     * ([fromX], [fromY]) is seen at ([toX], [toY]) by the returned camera, which differs from
     * [cam] only in its centre.
     *
     * 1. The touched terrain point: unproject the touch onto the plane at the centre's height,
     *    read the terrain height there, unproject again at that height, [HEIGHT_ITERATIONS]
     *    times. The result lies on the touch ray at height h.
     * 2. For a fixed zoom, bearing and pitch the projection depends only on (point - centre).
     *    So if Q is the point at the same height h that [cam] shows at ([toX], [toY]), moving
     *    the centre by (X - Q) shows the touched point X there.
     *
     * [heightAt] uses [MapCamera.project]'s `elevationM` convention: the 3D view passes
     * `(e - ground) * exaggeration`. A touch or target above the horizon leaves [cam] unchanged.
     *
     * The iteration converges while the terrain's slope along the ray is gentler than the
     * ray's own dip below the horizon. Near the top of a steeply pitched screen, looking
     * along a steep slope, it does not (contractor B measured 5-107 px misses), so when it has
     * not settled and [heightRange] bounds the terrain, the ray is marched from the top of
     * that range down to the first point below the ground and the crossing bisected: the
     * first crossing is the surface the user sees.
     */
    fun pan(
        cam: CameraState,
        fromX: Double, fromY: Double, toX: Double, toY: Double,
        viewportW: Int, viewportH: Int,
        heightAt: (lat: Double, lng: Double) -> Double,
        heightRange: ClosedFloatingPointRange<Double>? = null,
    ): CameraState {
        val mc = mapCamera(cam, viewportW, viewportH)
        val h = groundHeight(mc, cam, fromX, fromY, heightAt, heightRange) ?: return cam
        val touched = mc.unproject(fromX, fromY, h) ?: return cam
        val target = mc.unproject(toX, toY, h) ?: return cam
        val x = mc.centerX + mc.worldX(touched[1]) - mc.worldX(target[1])
        val y = mc.centerY + mc.worldY(touched[0]) - mc.worldY(target[0])
        return cam.copy(
            lat = MapCamera.latFromMercatorY(y / mc.worldSize),
            lng = MapCamera.lngFromMercatorX(x / mc.worldSize),
        )
    }

    /**
     * The ground point (lat, lng) under screen ([sx], [sy]): where the ray through it first meets
     * the terrain, as [pan] finds it. Null when the ray misses the ground. Used by tap-to-inspect (P.1).
     */
    fun groundAt(
        cam: CameraState, sx: Double, sy: Double, viewportW: Int, viewportH: Int,
        heightAt: (lat: Double, lng: Double) -> Double,
        heightRange: ClosedFloatingPointRange<Double>? = null,
    ): DoubleArray? {
        val mc = mapCamera(cam, viewportW, viewportH)
        val h = groundHeight(mc, cam, sx, sy, heightAt, heightRange) ?: return null
        return mc.unproject(sx, sy, h)
    }

    /**
     * The height (heightAt's convention) at which the ray through screen ([sx], [sy]) meets the
     * terrain: [HEIGHT_ITERATIONS] fixed-point steps, then the ray march when they have not
     * settled and [heightRange] is known. Null when the ray misses the ground plane.
     */
    private fun groundHeight(
        mc: MapCamera, cam: CameraState, sx: Double, sy: Double,
        heightAt: (Double, Double) -> Double, heightRange: ClosedFloatingPointRange<Double>?,
    ): Double? {
        var h = heightAt(cam.lat, cam.lng)
        repeat(HEIGHT_ITERATIONS) {
            val p = mc.unproject(sx, sy, h) ?: return null
            h = heightAt(p[0], p[1])
        }
        val settled = mc.unproject(sx, sy, h)?.let { abs(heightAt(it[0], it[1]) - h) <= SETTLED_M } ?: false
        if (!settled && heightRange != null) marchRay(mc, sx, sy, heightAt, heightRange)?.let { h = it }
        return h
    }

    /**
     * After a gesture, puts the camera's target back on the ground under the screen centre
     * WITHOUT moving the eye, so nothing on screen moves. Returns the new camera and the
     * new target's height in [heightAt]'s convention (the caller adds it, divided by its
     * exaggeration, to its ground reference), or null when no change is needed or possible.
     *
     * Why: the 3D view looks at the plane through the ground under the centre. Panning moves
     * the centre over ground that is higher or lower, and re-basing that plane by itself would
     * jump the picture up or down. Ported from MapLibre GL JS
     * (`TransformHelper.recalculateZoomAndCenter`, BSD-3-Clause, see THIRD_PARTY_NOTICES.md):
     * the new target is where the centre ray meets the terrain, and the zoom is the one whose
     * camera distance keeps the eye where it was.
     *
     * Geometry: the eye sits [MapCamera.cameraToCenterDistance] world pixels from the target
     * along the view ray, which rises at cos(pitch) per unit length. The terrain point on that
     * ray at height h is h * pixelsPerMeter / cos(pitch) closer to the eye, so the new distance
     * D' = D - that, and 2^zoom' = 2^zoom * (D / D') * cos(lat') / cos(lat): the same eye-to-
     * target distance in metres, at the new target's latitude.
     */
    fun reanchor(
        cam: CameraState,
        viewportW: Int, viewportH: Int,
        heightAt: (lat: Double, lng: Double) -> Double,
        heightRange: ClosedFloatingPointRange<Double>? = null,
    ): Pair<CameraState, Double>? {
        val mc = mapCamera(cam, viewportW, viewportH)
        val cx = viewportW / 2.0; val cy = viewportH / 2.0
        val h = groundHeight(mc, cam, cx, cy, heightAt, heightRange) ?: return null
        if (abs(h) < 1e-6) return null
        val t = mc.unproject(cx, cy, h) ?: return null
        val cosPitch = kotlin.math.cos(Math.toRadians(cam.pitch.coerceIn(0.0, 85.0)))
        val d = mc.cameraToCenterDistance
        val dNew = d - h * mc.pixelsPerMeter / cosPitch
        if (dNew < d * MIN_DISTANCE_FRACTION) return null       // the ground would reach the eye
        val zoom = cam.zoom + ln2(d / dNew) +
            ln2(kotlin.math.cos(Math.toRadians(t[0])) / kotlin.math.cos(Math.toRadians(cam.lat)))
        return cam.copy(lat = t[0], lng = t[1], zoom = zoom) to h
    }

    private fun ln2(x: Double) = kotlin.math.ln(x) / kotlin.math.ln(2.0)

    /** A re-anchor that would leave the eye nearer than this share of its distance is refused. */
    private const val MIN_DISTANCE_FRACTION = 0.05

    /** The iteration counts as settled when the ray point is within this of the ground. */
    const val SETTLED_M = 0.5
    private const val MARCH_STEPS = 256
    private const val BISECT_STEPS = 40

    /**
     * The height at which the ray through ([sx], [sy]) first meets the terrain, marching down
     * from the top of [range]; null if it never does within it.
     */
    private fun marchRay(
        mc: MapCamera, sx: Double, sy: Double,
        heightAt: (Double, Double) -> Double, range: ClosedFloatingPointRange<Double>,
    ): Double? {
        fun gap(h: Double): Double? = mc.unproject(sx, sy, h)?.let { heightAt(it[0], it[1]) - h }
        val step = (range.endInclusive - range.start) / MARCH_STEPS
        var above = range.endInclusive
        if ((gap(above) ?: return null) >= 0) return null      // starts under the ground: no visible crossing
        var h = above
        while (h > range.start) {
            h -= step
            val g = gap(h) ?: continue
            if (g >= 0) {
                var lo = h; var hi = above
                repeat(BISECT_STEPS) {
                    val m = 0.5 * (lo + hi)
                    if ((gap(m) ?: -1.0) >= 0) lo = m else hi = m
                }
                return 0.5 * (lo + hi)
            }
            above = h
        }
        return null
    }

    /** Keeps the centre inside the box; an inside centre comes back unchanged. */
    fun clampCentre(cam: CameraState, north: Double, west: Double, south: Double, east: Double): CameraState =
        cam.copy(lat = cam.lat.coerceIn(south, north), lng = cam.lng.coerceIn(west, east))

    /** Into the one map's range: same centre and bearing, zoom clamped, pitch at least [MIN_3D_PITCH]. */
    fun to3d(cam: CameraState, minZoom: Double, maxZoom: Double): CameraState = cam.copy(
        zoom = cam.zoom.coerceIn(minZoom, maxZoom),
        pitch = maxOf(cam.pitch, MIN_3D_PITCH),
    )
}
