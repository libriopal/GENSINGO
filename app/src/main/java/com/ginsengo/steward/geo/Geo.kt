package com.ginsengo.steward.geo

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** One polygon ring plus its holes, in GeoJSON order (lng, lat). */
class Ring(val points: DoubleArray) {
    val size: Int get() = points.size / 2
    fun lng(i: Int) = points[i * 2]
    fun lat(i: Int) = points[i * 2 + 1]
}

class Poly(val outer: Ring, val holes: List<Ring> = emptyList()) {
    val minLat: Double
    val maxLat: Double
    val minLng: Double
    val maxLng: Double

    init {
        var nLat = Double.MAX_VALUE; var xLat = -Double.MAX_VALUE
        var nLng = Double.MAX_VALUE; var xLng = -Double.MAX_VALUE
        for (i in 0 until outer.size) {
            val la = outer.lat(i); val lo = outer.lng(i)
            if (la < nLat) nLat = la; if (la > xLat) xLat = la
            if (lo < nLng) nLng = lo; if (lo > xLng) xLng = lo
        }
        minLat = nLat; maxLat = xLat; minLng = nLng; maxLng = xLng
    }
}

data class GeoFeature(val properties: JSONObject, val polys: List<Poly>)

/**
 * Point-in-polygon by ray casting (PRD §8.6). Runs fully offline against bundled GeoJSON.
 *
 * Crossing rule: a ray is cast east from the test point and edge crossings are counted.
 * The half-open comparison `(latI > lat) != (latJ > lat)` counts a vertex exactly once,
 * which is what keeps a point lying on a horizontal grid line - and US state borders are
 * full of them - from being counted twice and reported as outside.
 */
object PointInPolygon {

    fun inRing(ring: Ring, lat: Double, lng: Double): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in 0 until ring.size) {
            val latI = ring.lat(i); val lngI = ring.lng(i)
            val latJ = ring.lat(j); val lngJ = ring.lng(j)
            if ((latI > lat) != (latJ > lat)) {
                val x = (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI
                if (lng < x) inside = !inside
            }
            j = i
        }
        return inside
    }

    fun inPoly(poly: Poly, lat: Double, lng: Double): Boolean {
        // Bounding-box reject first: the common case is "nowhere near", and a state
        // outline can carry a few thousand vertices.
        if (lat < poly.minLat || lat > poly.maxLat || lng < poly.minLng || lng > poly.maxLng) {
            return false
        }
        if (!inRing(poly.outer, lat, lng)) return false
        return poly.holes.none { inRing(it, lat, lng) }
    }

    fun inFeature(f: GeoFeature, lat: Double, lng: Double): Boolean =
        f.polys.any { inPoly(it, lat, lng) }
}

/** Minimal GeoJSON reader: FeatureCollection of Polygon / MultiPolygon. */
object GeoJson {

    fun parseFeatureCollection(json: String): List<GeoFeature> {
        val root = JSONObject(json)
        val feats = root.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<GeoFeature>(feats.length())
        for (i in 0 until feats.length()) {
            val f = feats.optJSONObject(i) ?: continue
            val geom = f.optJSONObject("geometry") ?: continue
            val props = f.optJSONObject("properties") ?: JSONObject()
            val polys = when (geom.optString("type")) {
                "Polygon" -> listOf(polyFrom(geom.optJSONArray("coordinates")))
                "MultiPolygon" -> {
                    val arr = geom.optJSONArray("coordinates")
                    (0 until (arr?.length() ?: 0)).mapNotNull { k ->
                        polyFrom(arr!!.optJSONArray(k))
                    }
                }
                else -> emptyList()
            }.filterNotNull()
            if (polys.isNotEmpty()) out.add(GeoFeature(props, polys))
        }
        return out
    }

    private fun polyFrom(rings: org.json.JSONArray?): Poly? {
        if (rings == null || rings.length() == 0) return null
        val outer = ringFrom(rings.optJSONArray(0)) ?: return null
        val holes = (1 until rings.length()).mapNotNull { ringFrom(rings.optJSONArray(it)) }
        return Poly(outer, holes)
    }

    private fun ringFrom(pts: org.json.JSONArray?): Ring? {
        if (pts == null || pts.length() < 4) return null
        val d = DoubleArray(pts.length() * 2)
        for (i in 0 until pts.length()) {
            val p = pts.optJSONArray(i) ?: return null
            d[i * 2] = p.optDouble(0)      // lng
            d[i * 2 + 1] = p.optDouble(1)  // lat
        }
        return Ring(d)
    }
}

object GeoMath {
    private const val EARTH_M = 6_371_000.0

    /** Great-circle distance in metres. */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(la1) * cos(la2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass bearing 0..360 where a reading is "north-facing". */
    fun compassName(degrees: Float): String {
        val d = ((degrees % 360) + 360) % 360
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(((d + 22.5f) / 45f).toInt()) % 8]
    }

    /**
     * Ginseng favours north- and east-facing slopes. Returns how ideal an aspect is,
     * 1.0 at due north-east, falling to 0.0 at south-west.
     */
    fun aspectFavourability(degrees: Double): Double {
        val ideal = 45.0 // NE
        var diff = abs(((degrees - ideal + 540) % 360) - 180)
        diff = diff.coerceIn(0.0, 180.0)
        return 1.0 - (diff / 180.0)
    }
}
