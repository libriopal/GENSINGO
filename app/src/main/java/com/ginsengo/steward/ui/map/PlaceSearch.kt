package com.ginsengo.steward.ui.map

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Address and place search for the one map (P.1, the owner's request for Google-Maps-like search).
 *
 * Three ways, cheapest first:
 *  1. **Coordinates typed in** ("35.5605, -82.9960", "35.5605 N 82.9960 W", "35°33'38\"N 82°59'46\"W"):
 *     parsed on the phone, nothing sent, works offline.
 *  2. **Android's own geocoder** (the platform service, usually Google's on a phone with Play
 *     services): no key, no new dependency.
 *  3. **OpenStreetMap's Nominatim**, when the platform has no geocoder or finds nothing: the
 *     public instance, one request per search (its usage policy: no autocomplete, a real
 *     User-Agent), results © OpenStreetMap contributors.
 *
 * Privacy: only the words typed leave the phone, and only when a search is run. Your position is
 * never sent (no "near me" bias), and neither the query nor the results are logged.
 */
object PlaceSearch {

    data class Place(val name: String, val lat: Double, val lng: Double)

    data class Result(val places: List<Place>, val source: String, val error: String? = null)

    suspend fun search(context: Context, query: String): Result {
        val q = query.trim()
        if (q.isEmpty()) return Result(emptyList(), "")
        parseCoordinates(q)?.let { return Result(listOf(it), "coordinates") }
        val platform = runCatching { platform(context, q) }.getOrNull().orEmpty()
        if (platform.isNotEmpty()) return Result(platform, "Android geocoder")
        return runCatching { Result(nominatim(q), "© OpenStreetMap contributors (Nominatim)") }
            .getOrElse { Result(emptyList(), "", "Search needs a connection (coordinates work offline).") }
    }

    @Suppress("DEPRECATION")   // the blocking form, on IO: the listener form needs API 33
    private suspend fun platform(context: Context, q: String): List<Place> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext emptyList()
        Geocoder(context, Locale.getDefault()).getFromLocationName(q, MAX_RESULTS).orEmpty().map { a ->
            val name = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
                .ifBlank { listOfNotNull(a.featureName, a.locality, a.adminArea).joinToString(", ") }
            Place(name.ifBlank { q }, a.latitude, a.longitude)
        }
    }

    private suspend fun nominatim(q: String): List<Place> = withContext(Dispatchers.IO) {
        val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$MAX_RESULTS&q=" +
            URLEncoder.encode(q, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000; conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "GensingoFieldApp/1.0 (Android; ginseng habitat map)")
            conn.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Place(o.optString("display_name", q), o.getString("lat").toDouble(), o.getString("lon").toDouble())
            }
        } finally {
            conn.disconnect()
        }
    }

    private val DECIMAL = Regex("""^\s*([-+]?\d{1,2}(?:\.\d+)?)\s*°?\s*([NnSs])?\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)\s*°?\s*([EeWw])?\s*$""")
    private val DMS = Regex("""(\d{1,3})\s*°\s*(\d{1,2})\s*['′]\s*(?:(\d{1,2}(?:\.\d+)?)\s*["″])?\s*([NnSsEeWw])""")

    /** "lat, lng" in decimal degrees (with optional N/S/E/W) or degrees-minutes-seconds; null if it is not coordinates. */
    fun parseCoordinates(q: String): Place? {
        DECIMAL.matchEntire(q)?.let { m ->
            var lat = m.groupValues[1].toDouble(); var lng = m.groupValues[3].toDouble()
            if (m.groupValues[2].equals("S", true)) lat = -kotlin.math.abs(lat)
            if (m.groupValues[4].equals("W", true)) lng = -kotlin.math.abs(lng)
            return valid(lat, lng)
        }
        val parts = DMS.findAll(q).toList()
        if (parts.size == 2) {
            var lat: Double? = null; var lng: Double? = null
            for (m in parts) {
                val deg = m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / 60 +
                    (m.groupValues[3].toDoubleOrNull() ?: 0.0) / 3600
                when (m.groupValues[4].uppercase()) {
                    "N" -> lat = deg; "S" -> lat = -deg
                    "E" -> lng = deg; "W" -> lng = -deg
                }
            }
            if (lat != null && lng != null) return valid(lat, lng)
        }
        return null
    }

    private fun valid(lat: Double, lng: Double): Place? =
        if (lat in -90.0..90.0 && lng in -180.0..180.0) Place("%.5f, %.5f".format(Locale.US, lat, lng), lat, lng) else null

    private const val MAX_RESULTS = 5
}
