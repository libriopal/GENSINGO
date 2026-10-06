package com.ginsengo.steward.field

import com.ginsengo.steward.prospect.Prospects
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Walking guidance to a chosen point (N.1): distance, compass direction, and, when the phone knows
 * which way it faces, the clock direction ("at 2 o'clock") a walker can follow without reading a
 * bearing. Pure; the great-circle functions are [Prospects]'.
 */
object Guidance {

    data class Leg(val distanceM: Double, val bearingDeg: Double)

    fun leg(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double) =
        Leg(Prospects.distanceMetres(fromLat, fromLng, toLat, toLng), Prospects.bearingTrue(fromLat, fromLng, toLat, toLng))

    /** 12 is straight ahead, 3 to the right, 6 behind, 9 to the left. */
    fun clock(bearingDeg: Double, headingDeg: Double): Int {
        val rel = ((bearingDeg - headingDeg) % 360 + 360) % 360
        val h = (rel / 30.0).roundToInt() % 12
        return if (h == 0) 12 else h
    }

    private val POINTS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** The eight-point compass name of a bearing. */
    fun compass(deg: Double): String = POINTS[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

    /** Within this, or the fix's own accuracy if larger, the walker has arrived. */
    const val ARRIVED_M = 12.0

    fun arrived(leg: Leg, accuracyM: Float) = leg.distanceM <= maxOf(ARRIVED_M, accuracyM.toDouble())

    /** "640 m NE · at 2 o'clock", or "Arrived" near it. */
    fun describe(leg: Leg, headingDeg: Float?, accuracyM: Float): String {
        if (arrived(leg, accuracyM)) return "Arrived (within ±${maxOf(ARRIVED_M, accuracyM.toDouble()).roundToInt()} m)"
        val base = WayBack.describe(WayBack.Leg(leg.distanceM, leg.bearingDeg))
        return if (headingDeg == null) base else "$base · at ${clock(leg.bearingDeg, headingDeg.toDouble())} o'clock"
    }

    /** The point [distanceM] from (lat, lng) along [bearingDeg]: the heading cone's tip. */
    fun destination(lat: Double, lng: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val d = distanceM / 6_371_000.0
        val b = Math.toRadians(bearingDeg)
        val la = Math.toRadians(lat); val lo = Math.toRadians(lng)
        val la2 = asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lo2 = lo + atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(la2))
        return Math.toDegrees(la2) to Math.toDegrees(lo2)
    }
}

/**
 * A place the owner kept (N.1): a parking spot, a patch to come back to, a trailhead. Kept on the
 * phone only, in the app's preferences, which every backup excludes; never sent anywhere.
 */
data class SavedPlace(val name: String, val lat: Double, val lng: Double, val time: Long) {
    companion object {
        fun toJson(list: List<SavedPlace>): String = JSONArray().apply {
            list.forEach { put(JSONObject().put("n", it.name).put("a", it.lat).put("o", it.lng).put("t", it.time)) }
        }.toString()

        fun fromJson(json: String?): List<SavedPlace> = runCatching {
            val arr = JSONArray(json ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedPlace(o.optString("n", "Place"), o.getDouble("a"), o.getDouble("o"), o.optLong("t"))
            }
        }.getOrDefault(emptyList())
    }
}
