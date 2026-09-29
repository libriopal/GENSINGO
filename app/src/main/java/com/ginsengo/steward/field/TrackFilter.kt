package com.ginsengo.steward.field

import com.ginsengo.steward.prospect.Prospects
import kotlin.math.max

/**
 * Decides which fixes become track points.
 *
 * The track feeds three things that all go wrong in the same way when fed raw GPS: the
 * "where I've been" heatmap, the walked distance, and the automatic VISITED mark on a
 * suggestion. A phone standing still under canopy wanders several metres a second, so raw
 * fixes paint a bright blob where the user stood eating lunch, add phantom distance, and
 * can "visit" a suggestion the user never walked to.
 *
 * Rules, each pinned by a test:
 *  1. Drop fixes worse than [maxAccuracyM]. They are not positions, they are regions.
 *  2. Store a fix only once it is further from the last stored point than
 *     max([minStepM], accuracy). The comparison is between two noisy fixes, so the noise
 *     floor is the error of BOTH: with 6 m of jitter per axis their separation averages
 *     ~8.5 m. The first version of this rule used accuracy / 2 and the stationary test
 *     measured 456 of 600 jitter fixes stored; see EINCOL_REPORT Phase 7.
 *  3. When the receiver's Doppler speed says the phone is still (< [stillSpeedMps]), require
 *     3 x accuracy. Doppler speed is measured independently of position and is steady when
 *     positions wander, so it is the one signal that separates standing from walking.
 *     Slow prospecting pace can read as "still"; it is then recorded every ~3 accuracies
 *     instead of being lost.
 *  4. Drop an implied speed above [maxSpeedMps]. Real GPS spikes are hundreds of metres in a
 *     second; a car on a forest road is not.
 *
 * NO SPEED ON THE FIX. Then a single fix cannot be told from jitter: the first version of
 * this rule, fed independent 6 m jitter for ten minutes with no speed, stored 3.4 km of
 * phantom track. Without speed the filter instead compares the CENTROID of at least
 * [MIN_WINDOW] recent fixes with the last stored point (itself a centroid), which shrinks
 * the noise of the comparison by sqrt(n) on both sides, and stores the centroid. The track
 * is coarser in that mode (about one point per [MIN_WINDOW] fixes) but not invented.
 * TrackFilterTest pins both modes' measured numbers.
 *
 * The previous tour tracker reported "steps" as distance / 0.76. Nothing here counts steps.
 */
class TrackFilter(
    val maxAccuracyM: Float = 50f,
    val minStepM: Double = 5.0,
    val maxSpeedMps: Double = 60.0,
    val stillSpeedMps: Float = 0.2f,
) {
    data class Fix(
        val lat: Double, val lng: Double, val accuracyM: Float, val time: Long,
        /** Doppler ground speed from the receiver, when the fix carries one. */
        val speedMps: Float? = null,
    )

    private var last: Fix? = null
    private val window = ArrayDeque<Fix>()
    var distanceM: Double = 0.0
        private set

    /**
     * Offers a fix. Returns the point to store (the fix itself, or in no-speed mode a
     * centroid of recent fixes), or null when nothing should be stored.
     */
    fun offer(fix: Fix): Fix? {
        if (!(fix.accuracyM > 0f) || fix.accuracyM > maxAccuracyM) return null
        val prev = last ?: return store(fix, 0.0)

        val dtS = (fix.time - prev.time) / 1000.0
        if (dtS <= 0.0) return null
        if (Prospects.distanceMetres(prev.lat, prev.lng, fix.lat, fix.lng) / dtS > maxSpeedMps) return null

        if (fix.speedMps == null) {
            window.addLast(fix)
            while (window.size > MAX_WINDOW) window.removeFirst()
            if (window.size < MIN_WINDOW) return null
            val c = centroid(window)
            val d = Prospects.distanceMetres(prev.lat, prev.lng, c.lat, c.lng)
            return if (d >= max(minStepM, c.accuracyM.toDouble())) store(c, d) else null
        }

        val d = Prospects.distanceMetres(prev.lat, prev.lng, fix.lat, fix.lng)
        val still = fix.speedMps < stillSpeedMps
        val noise = fix.accuracyM.toDouble() * if (still) 3.0 else 1.0
        return if (d >= max(minStepM, noise)) store(fix, d) else null
    }

    /** Boolean form for callers that only need to know whether something was stored. */
    fun accept(fix: Fix): Boolean = offer(fix) != null

    private fun store(p: Fix, d: Double): Fix {
        distanceM += d
        last = p
        window.clear()
        return p
    }

    private fun centroid(fixes: Collection<Fix>): Fix {
        var w = 0.0; var la = 0.0; var lo = 0.0
        for (f in fixes) {
            val k = 1.0 / (f.accuracyM.toDouble() * f.accuracyM)
            w += k; la += k * f.lat; lo += k * f.lng
        }
        return Fix(la / w, lo / w, fixes.minOf { it.accuracyM }, fixes.maxOf { it.time }, null)
    }

    companion object {
        const val MIN_WINDOW = 5
        const val MAX_WINDOW = 10
    }
}
