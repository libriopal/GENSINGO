package com.ginsengo.steward.terrain3d

import kotlin.math.hypot

/**
 * Verifies that [MapCamera]'s reconstructed projection agrees with MapLibre's own.
 *
 * WHY THIS SHIPS IN THE APP RATHER THAN ONLY IN TESTS
 * The overlay draws a mesh in a *separate* GL surface from the map. If the reconstructed
 * camera is subtly wrong — a factor-of-two tile size, a bearing sign, a pitch convention —
 * the terrain still renders, and it still looks like terrain. It just sits somewhere the
 * ground is not. That is the one defect a screenshot cannot reveal and a unit test over my
 * own arithmetic cannot catch either, because both would be checking my understanding
 * against itself.
 *
 * MapLibre's `Projection.toScreenLocation` is a genuinely independent implementation of the
 * same transform, written by other people from the same specification. Projecting the same
 * coordinates through both and comparing is a WITNESS pin in the EINCOL sense: a second
 * thing computes it and agrees. When they disagree beyond tolerance the overlay hides
 * itself and says so, instead of quietly drawing a hillside in the wrong place.
 */
object AlignmentCheck {

    /** Maximum acceptable mean error, in screen pixels. */
    const val TOLERANCE_PX = 2.0f

    data class Result(
        val samples: Int,
        val meanErrorPx: Float,
        val maxErrorPx: Float,
    ) {
        val aligned: Boolean get() = samples > 0 && meanErrorPx <= TOLERANCE_PX
        fun describe(): String = when {
            samples == 0 -> "Alignment unverified — no sample points projected."
            aligned -> "Aligned with the map to %.2f px (max %.2f).".format(meanErrorPx, maxErrorPx)
            else -> "Overlay disabled: projection disagrees with the map by %.1f px (max %.1f)."
                .format(meanErrorPx, maxErrorPx)
        }
    }

    /**
     * @param camera       the reconstructed camera under test
     * @param probes       lat/lng pairs spread across the viewport
     * @param mapProject   MapLibre's own projection: lat,lng -> screen x,y, or null if
     *                     off-screen/behind the camera
     */
    fun run(
        camera: MapCamera,
        probes: List<Pair<Double, Double>>,
        mapProject: (Double, Double) -> FloatArray?,
    ): Result {
        var n = 0
        var sum = 0.0
        var worst = 0.0
        for ((lat, lng) in probes) {
            val mine = camera.project(lat, lng) ?: continue
            val theirs = mapProject(lat, lng) ?: continue
            if (!theirs[0].isFinite() || !theirs[1].isFinite()) continue
            val d = hypot((mine[0] - theirs[0]).toDouble(), (mine[1] - theirs[1]).toDouble())
            if (!d.isFinite()) continue
            n++
            sum += d
            if (d > worst) worst = d
        }
        return Result(
            samples = n,
            meanErrorPx = if (n == 0) Float.MAX_VALUE else (sum / n).toFloat(),
            maxErrorPx = worst.toFloat(),
        )
    }

    /**
     * Probe points spread over the visible region. Deliberately includes the corners and
     * not just the centre: the centre projects correctly under almost any scale error,
     * because it is the fixed point of the transform. An error in tile size or field of
     * view only shows up away from it.
     */
    fun probesFor(
        north: Double, west: Double, south: Double, east: Double,
    ): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(9)
        for (fy in listOf(0.15, 0.5, 0.85)) {
            for (fx in listOf(0.15, 0.5, 0.85)) {
                out += (north + (south - north) * fy) to (west + (east - west) * fx)
            }
        }
        return out
    }
}
