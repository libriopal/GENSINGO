package com.ginsengo.steward.terrain3d

import com.ginsengo.steward.geo.Projection
import com.ginsengo.steward.terrain.DemTileStore
import kotlin.math.hypot
import kotlin.math.max

/**
 * Where the flat map hands the screen to the mesh (exe.md A7, A8), from measured agreement
 * rather than a constant.
 *
 * The flat map draws every point on the camera's ground plane. The mesh draws it at its height
 * above that plane, (elevation - anchor) x exaggeration, times a relief scale r in 0..1
 * ([MapCamera.mvpForMeshBuiltAt]'s `relief`). At r = 0 the mesh IS that plane, and the two
 * pictures agree to the conformance tolerance ([CameraConformanceTest]). As r grows, each point
 * slides along the image of its own vertical, away from where the map draws it. The hand-off is
 * the largest r at which no control point on screen has slid further than [TOLERANCE_DP].
 *
 * Why relief and not tilt (the register's first wording): the flat map draws no relief at any
 * tilt, so in steep country no tilt makes the two agree. Measured in [HandoffTest] on the Boone
 * tile: the disagreement at full relief is tens of pixels even looking straight down.
 *
 * The cross-fade is then one number, [blend]: the mesh fades in with its relief held under the
 * hand-off, and rises the rest of the way only once the map underneath is covered.
 */
object Handoff {

    /** A visible disagreement: about the width of a contour hairline. */
    const val TOLERANCE_DP = 2f

    /** Bisection steps for [relief]: 2^-20 of the relief range, far below a pixel. */
    private const val BISECT_STEPS = 20

    /** A control point: where it is, and its height in [MapCamera.project]'s convention at full relief. */
    class Sample(val lat: Double, val lng: Double, val heightM: Double)

    /**
     * The worst screen distance, px, between where the flat map draws a sample (height 0) and
     * where the mesh draws it at [relief], over the samples the map draws inside the viewport.
     *
     * Only points whose FLAT position is on screen count: that position is fixed, so the set
     * counted does not change with [relief], and each point's distance grows monotonically with
     * it (a vertical projects to a straight line on screen), so the worst distance does too.
     * [relief] relies on that.
     */
    fun disagreementPx(mc: MapCamera, samples: List<Sample>, relief: Double): Double {
        val project = mc.projector()
        var worst = 0.0
        for (s in samples) {
            val flat = project(s.lat, s.lng, 0.0) ?: continue
            if (flat[0] < 0f || flat[1] < 0f || flat[0] > mc.viewportWidth || flat[1] > mc.viewportHeight) continue
            val lifted = project(s.lat, s.lng, s.heightM * relief) ?: continue
            worst = max(worst, hypot((lifted[0] - flat[0]).toDouble(), (lifted[1] - flat[1]).toDouble()))
        }
        return worst
    }

    /** The largest relief in 0..1 at which [disagreementPx] stays within [tolerancePx]. */
    fun relief(mc: MapCamera, samples: List<Sample>, tolerancePx: Double): Double {
        if (disagreementPx(mc, samples, 1.0) <= tolerancePx) return 1.0
        var lo = 0.0; var hi = 1.0
        repeat(BISECT_STEPS) {
            val m = 0.5 * (lo + hi)
            if (disagreementPx(mc, samples, m) <= tolerancePx) lo = m else hi = m
        }
        return lo
    }

    /**
     * [n] x [n] control points over the displayed square of [s], at cell-row and cell-column
     * positions (Mercator-linear, as the mesh's rows are), with heights above [anchorM] as drawn.
     */
    fun samples(s: Terrain3D.Scene, anchorM: Double, n: Int = 9): List<Sample> {
        val m = s.mosaic
        val halo = m.haloPx.toDouble()
        val iw = m.grid.w - 2 * m.haloPx
        val ih = m.grid.h - 2 * m.haloPx
        val world = Projection.worldPx(m.zoom, DemTileStore.TILE)
        val out = ArrayList<Sample>(n * n)
        for (j in 0 until n) for (i in 0 until n) {
            // Cell centres spread over the square, the outermost half a cell inside its edges.
            val col = halo + (i + 0.5) / n * iw
            val row = halo + (j + 0.5) / n * ih
            val lat = Projection.lat((m.tileY0 * DemTileStore.TILE + row) / world)
            val lng = Projection.lng((m.tileX0 * DemTileStore.TILE + col) / world)
            val e = s.elevationAt(lat, lng) ?: continue
            out += Sample(lat, lng, (e - anchorM) * Terrain3D.EXAGGERATION)
        }
        return out
    }

    /** What the mesh draws at one moment of the transition. */
    data class Blend(val alpha: Float, val relief: Double)

    /** The end of the fade (the map is covered) and of the rise (full relief). */
    const val COVERED = 1f
    const val RISEN = 2f

    /**
     * The cross-fade as one number. [reveal] runs 0 → [COVERED] while the mesh fades in with its
     * relief rising from 0 to [handoff], then [COVERED] → [RISEN] while the relief rises the rest
     * of the way over a map that no longer shows. Run backwards it is the return to the map.
     *
     * The invariant A8 asks for: whenever any of the map shows through (alpha < 1), the relief
     * is at most [handoff], so no point is drawn further than the tolerance from where the map
     * draws it.
     */
    fun blend(reveal: Float, handoff: Double): Blend {
        val r = reveal.coerceIn(0f, RISEN)
        val h = handoff.coerceIn(0.0, 1.0)
        return if (r <= COVERED) Blend(alpha = r, relief = r * h)
        else Blend(alpha = 1f, relief = h + (1.0 - h) * (r - COVERED))
    }
}
