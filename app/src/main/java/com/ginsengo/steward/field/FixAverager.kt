package com.ginsengo.steward.field

import kotlin.math.cos

/**
 * Averages a burst of fixes before a find is marked.
 *
 * Ginseng grows in coves under closed canopy, which is where phone fixes are worst. One fix
 * there can be 30 m off; standing still for twenty seconds and weighting each fix by its
 * reported precision removes most of the jitter.
 *
 * WHAT IT DOES NOT CLAIM. Textbook averaging would report accuracy / sqrt(n). That assumes
 * independent errors, and GNSS errors over twenty seconds are strongly correlated: multipath
 * off the same hillside does not average out. So the reported accuracy is the BEST single
 * fix in the burst, never the sqrt(n) shrink. The averaged position is better than any one
 * fix; the stated uncertainty is not allowed to pretend it is better than the best fix.
 */
object FixAverager {

    data class Result(
        val lat: Double,
        val lng: Double,
        val accuracyM: Float,
        val fixCount: Int,
        val newestTime: Long,
    )

    const val MIN_FIXES = 3

    /** Returns null when there is nothing usable. Fixes with non-positive accuracy are ignored. */
    fun average(fixes: List<FieldLocation>): Result? {
        val usable = fixes.filter { it.accuracyM > 0f }
        if (usable.isEmpty()) return null
        var wSum = 0.0
        var latSum = 0.0
        // Longitude is averaged in local metres-per-degree so the weights mean the same thing
        // on both axes; at 36 degrees N a degree of longitude is 0.81 of a degree of latitude.
        val lat0 = usable.first().lat
        val kx = cos(Math.toRadians(lat0))
        var xSum = 0.0
        val lng0 = usable.first().lng
        for (f in usable) {
            val w = 1.0 / (f.accuracyM.toDouble() * f.accuracyM)
            wSum += w
            latSum += w * f.lat
            xSum += w * (f.lng - lng0) * kx
        }
        return Result(
            lat = latSum / wSum,
            lng = lng0 + (xSum / wSum) / kx,
            accuracyM = usable.minOf { it.accuracyM },
            fixCount = usable.size,
            newestTime = usable.maxOf { it.timestamp },
        )
    }
}
