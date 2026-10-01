package com.ginsengo.steward.terrain3d

import kotlin.math.abs

/**
 * One camera for both views: the 2D map writes it when its camera settles, the 3D view starts
 * from it and writes it back as it moves. Same numbers on both sides: MapLibre's zoom (512 px
 * world tiles), bearing (degrees clockwise from north) and pitch (degrees from vertical), which
 * [MapCamera] mirrors.
 */
data class ViewCamera(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val bearing: Double,
    val pitch: Double,
)

/**
 * Camera maths shared by the 2D map and the 3D view (blueprint docs/blueprints/one-map.md, WP-B).
 * Pure JVM code: no Android, no MapLibre.
 */
object CameraMath {

    /** Fixed-point steps that move the touched point onto the terrain (blueprint §G, claim 4). */
    const val HEIGHT_ITERATIONS = 4

    /** Entering 3D, a flatter camera is raised to this pitch so the relief reads as relief. */
    const val MIN_3D_PITCH = 45.0

    /** MapLibre 13.6.1 `MapLibreConstants.MAXIMUM_TILT` (javap): the 2D map cannot tilt further. */
    const val MAX_2D_PITCH = 60.0

    /** MapLibre 13.6.1 `MapLibreConstants.MINIMUM_ZOOM` / `MAXIMUM_ZOOM` (javap). */
    const val MAP_MIN_ZOOM = 0.0
    const val MAP_MAX_ZOOM = 25.5

    fun mapCamera(cam: ViewCamera, viewportW: Int, viewportH: Int): MapCamera =
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
        cam: ViewCamera,
        fromX: Double, fromY: Double, toX: Double, toY: Double,
        viewportW: Int, viewportH: Int,
        heightAt: (lat: Double, lng: Double) -> Double,
        heightRange: ClosedFloatingPointRange<Double>? = null,
    ): ViewCamera {
        val mc = mapCamera(cam, viewportW, viewportH)
        var h = heightAt(cam.lat, cam.lng)
        repeat(HEIGHT_ITERATIONS) {
            val p = mc.unproject(fromX, fromY, h) ?: return cam
            h = heightAt(p[0], p[1])
        }
        val settled = mc.unproject(fromX, fromY, h)?.let { abs(heightAt(it[0], it[1]) - h) <= SETTLED_M } ?: false
        if (!settled && heightRange != null) marchRay(mc, fromX, fromY, heightAt, heightRange)?.let { h = it }
        val touched = mc.unproject(fromX, fromY, h) ?: return cam
        val target = mc.unproject(toX, toY, h) ?: return cam
        val x = mc.centerX + mc.worldX(touched[1]) - mc.worldX(target[1])
        val y = mc.centerY + mc.worldY(touched[0]) - mc.worldY(target[0])
        return cam.copy(
            lat = MapCamera.latFromMercatorY(y / mc.worldSize),
            lng = MapCamera.lngFromMercatorX(x / mc.worldSize),
        )
    }

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
    fun clampCentre(cam: ViewCamera, north: Double, west: Double, south: Double, east: Double): ViewCamera =
        cam.copy(lat = cam.lat.coerceIn(south, north), lng = cam.lng.coerceIn(west, east))

    /** Into 3D: same centre and bearing, zoom clamped to the 3D view's range, pitch at least 45. */
    fun to3d(cam2d: ViewCamera, minZoom: Double, maxZoom: Double): ViewCamera = cam2d.copy(
        zoom = cam2d.zoom.coerceIn(minZoom, maxZoom),
        pitch = maxOf(cam2d.pitch, MIN_3D_PITCH),
    )

    /** Back to 2D: same centre and bearing, zoom clamped to MapLibre's range, pitch at most 60. */
    fun to2d(cam3d: ViewCamera): ViewCamera = cam3d.copy(
        zoom = cam3d.zoom.coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM),
        pitch = minOf(cam3d.pitch, MAX_2D_PITCH),
    )
}
