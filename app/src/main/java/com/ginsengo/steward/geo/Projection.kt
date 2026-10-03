package com.ginsengo.steward.geo

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator, written once (exe.md A2). Longitude and latitude (WGS84 degrees) to and from
 * normalised Mercator coordinates: x and y run 0..1 across the world, y increasing south, as both
 * MapLibre's world pixels and the slippy-map tile grid do. Tiles, DEM cells, mesh vertices, the
 * 3D camera and the radius scan all convert through these four functions.
 *
 * Why one place: five copies of these formulas existed (MapCamera, DemTileStore, WaterLines,
 * Terrain3D, RadiusScan), in two algebraic families, and a sixth, linear stand-in
 * (`Mosaic.latAtRow`) was wrong because rows are linear in Mercator y, not in latitude. A copy
 * that drifts is invisible on screen; it only puts a layer a metre or two off.
 */
object Projection {

    /** The WGS84 equatorial circumference MapLibre's transform uses, metres. */
    const val EARTH_CIRCUMFERENCE = 40_075_016.686

    /** Where Web Mercator stops: the latitude whose y is exactly 0 (and 1 in the south). */
    const val MAX_LATITUDE = 85.0511287798066

    /** Normalised x of a longitude: 0 at −180°, 1 at +180°. */
    fun x(lng: Double): Double = (lng + 180.0) / 360.0

    /** Normalised y of a latitude: 0 at the north edge, 0.5 at the equator, 1 at the south edge. */
    fun y(lat: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        return (1.0 - asinh(tan(r)) / PI) / 2.0
    }

    /** Longitude of a normalised x. */
    fun lng(x: Double): Double = x * 360.0 - 180.0

    /** Latitude of a normalised y (the inverse Gudermannian). */
    fun lat(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))

    /** The world's width in pixels at an integer zoom for square tiles of [tilePx]. */
    fun worldPx(zoom: Int, tilePx: Int): Double = tilePx.toDouble() * (1 shl zoom)

    /** The world's width in pixels at a fractional zoom, as MapLibre's camera uses. */
    fun worldPx(zoom: Double, tilePx: Double): Double = tilePx * 2.0.pow(zoom)

    /** Ground metres covered by one world pixel at [lat] (Mercator's scale factor is 1/cos φ). */
    fun metresPerPixel(lat: Double, worldPx: Double): Double =
        EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)) / worldPx
}
