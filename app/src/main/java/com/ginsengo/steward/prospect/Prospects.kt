package com.ginsengo.steward.prospect

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pre-computed ginseng habitat sites for Haywood County, bundled so they work with the radio off.
 *
 * Every site is the centroid of a ~3.9 m cell that sat inside a soil map unit the USDA survey
 * recorded as a well-drained COVE, scored on terrain the DEM measured at that cell - aspect,
 * slope, position - and on what the soil formed in.
 *
 * Why bundled rather than fetched: the soil survey and the elevation tiles are both network
 * services, and the ground this points at has no signal. A prospecting tool that needs a bar of
 * LTE is a prospecting tool for the car park.
 *
 * WHAT THIS IS NOT. It does not know where ginseng is. No occurrence dataset exists at usable
 * precision - iNaturalist and GBIF obscure this species' coordinates deliberately - so nothing
 * here has ever been checked against a plant. It ranks habitat, and habitat is where to look,
 * not what you will find.
 *
 * And the resolution is softer than the coordinates imply: cells are 3.9 m but the USGS data
 * underneath is mostly ~10 m, so anything finer than about 10 m is interpolation. Walk the bench,
 * not the pixel.
 */
@Serializable
data class ProspectSite(
    val lat: Double,
    val lon: Double,
    val score: Double,
    val soil: Double? = null,
    val terrain: Double? = null,
    val elev: Int? = null,
    val slope: Double? = null,
    val aspect: Double? = null,
    val series: String? = null,
    val landform: String? = null,
    val drainage: String? = null,
    @SerialName("pmkind") val parentMaterial: String? = null,
    @SerialName("pmorigin") val parentRock: String? = null,
) {
    /** Compass point of the downslope direction, or null on flat ground. */
    val aspectCompass: String?
        get() {
            val a = aspect ?: return null
            if (a < 0) return null
            val i = (((a + 22.5) % 360.0) / 45.0).toInt()
            return listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[i]
        }

    /**
     * Why this spot, in the order a digger would actually check it. Reads the recorded facts
     * back rather than restating the score, because "0.90" tells you nothing on a hillside.
     */
    fun why(): List<String> = buildList {
        landform?.let { if (it.equals("COVE", true)) add("Soil survey mapped this as a COVE.") }
        drainage?.let { add("Drainage: $it.") }
        aspectCompass?.let { add("Faces $it — ${if (it in setOf("N","NE","E")) "the cool side" else "warmer than ginseng prefers"}.") }
        slope?.let { add("Slope about ${it.roundToInt()}°.") }
        elev?.let { add("Elevation ${it} m.") }
        series?.let { add("Soil series: $it.") }
        parentMaterial?.let { k ->
            if (k.equals("Colluvium", true)) {
                add("Formed in COLLUVIUM — soil that accumulated from upslope, so it is deep.")
            } else add("Formed in $k.")
        }
        parentRock?.let { r ->
            when {
                r.contains("amphibolite", true) || r.contains("hornblende", true) ->
                    add("Rock: $r — base-rich, the calcium signal ginseng wants.")
                r.contains("marble", true) || r.contains("limestone", true) ||
                    r.contains("dolomite", true) -> add("Rock: $r — carbonate, strongly base-rich.")
                r.contains("igneous and metamorphic", true) || r.equals("Metamorphic rock", true) ->
                    add("Rock recorded only generically ($r), so it says nothing about calcium.")
                else -> add("Rock: $r.")
            }
        }
    }
}

@Serializable
data class ProspectSet(
    val county: String,
    val state: String,
    val generated: String,
    val source: String,
    val caveat: String,
    val sites: List<ProspectSite>,
)

object Prospects {

    const val ASSET = "geo/haywood_prospects.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): ProspectSet? = runCatching {
        context.assets.open(ASSET).use { json.decodeFromString<ProspectSet>(it.readBytes().decodeToString()) }
    }.getOrNull()

    /** Great-circle metres. Haversine, because a flat approximation drifts on a long walk. */
    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Initial bearing, degrees clockwise from TRUE north.
     *
     * True, not magnetic. Declination in western North Carolina runs several degrees west, so a
     * compass needle does not point where this number says - and a steel mattock or a truck will
     * swing it much further than declination does. Trust the terrain and the map over the needle.
     */
    fun bearingTrue(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Sites nearest first. What you want standing in the woods, rather than a global ranking. */
    fun nearest(sites: List<ProspectSite>, lat: Double, lon: Double, limit: Int = 25):
            List<Pair<ProspectSite, Double>> =
        sites.map { it to distanceMetres(lat, lon, it.lat, it.lon) }
            .sortedBy { it.second }
            .take(limit)
}
