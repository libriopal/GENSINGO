package com.ginsengo.steward.terrain3d

/**
 * Whether the terrain hides a point from the camera (exe.md A10's `OCCLUDED`, A18's "labels
 * occluded correctly").
 *
 * The 3D markers (finds, suggestions, you) are drawn on a canvas over the GL surface, with no depth
 * test, so a suggestion behind a ridge used to be drawn as if it sat on the slope in front of it.
 * Here the straight line from the point to the eye ([MapCamera.eyeWorld]) is walked; if the ground
 * anywhere along it stands above the line, the point is hidden. Samples are denser near the point,
 * where the ridges that matter are (the eye is kilometres up), and the walk stops once the line has
 * risen above the highest ground.
 *
 * Heights are in [MapCamera.project]'s convention: the 3D view passes its drawn heights,
 * `(elevation − anchor) × exaggeration × relief`, so nothing is hidden while the mesh is flat.
 */
object Occlusion {

    /** Ground within this of the line does not hide (bilinear sampling noise; markers sit on the ground). */
    const val CLEARANCE_M = 3.0

    /** The first stretch from the point is skipped: the point's own patch of ground is not a ridge. */
    const val SKIP_M = 12.0

    const val STEPS = 96

    fun hidden(
        mc: MapCamera, lat: Double, lng: Double, heightM: Double,
        heightAt: (lat: Double, lng: Double) -> Double,
        maxGroundM: Double,
    ): Boolean {
        val ppm = mc.pixelsPerMeter
        val x0 = mc.worldX(lng); val y0 = mc.worldY(lat); val z0 = heightM * ppm
        val eye = mc.eyeWorld()
        val dx = eye[0] - x0; val dy = eye[1] - y0; val dz = eye[2] - z0
        val run = kotlin.math.hypot(dx, dy)
        val tStart = if (run > 0) (SKIP_M * ppm / run).coerceAtMost(1.0) else 1.0
        val top = (maxGroundM + CLEARANCE_M) * ppm
        for (i in 1..STEPS) {
            val u = i.toDouble() / STEPS
            val t = tStart + (1.0 - tStart) * u * u            // denser near the point
            val z = z0 + dz * t
            if (z > top) return false                           // above every ridge from here on
            val x = x0 + dx * t; val y = y0 + dy * t
            val g = heightAt(
                MapCamera.latFromMercatorY(y / mc.worldSize), MapCamera.lngFromMercatorX(x / mc.worldSize),
            ) * ppm
            if (g > z + CLEARANCE_M * ppm) return true
        }
        return false
    }
}
