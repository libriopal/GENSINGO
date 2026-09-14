package com.ginsengo.steward.terrain

/**
 * Terrain-derived ginseng habitat suitability.
 *
 * WHAT THIS IS
 * A transparent, literature-grounded weighted model over terrain indices computed from a
 * digital elevation model. Every factor traces to a published source, named below.
 *
 * WHAT THIS IS NOT
 * A fitted species distribution model. No occurrence dataset was used, no coefficients were
 * estimated, and nothing here has been validated against known ginseng populations. The
 * weights are the author's reading of how emphatically the site-selection literature
 * stresses each factor, not regression output. It is labelled RESEARCH-GRADE ESTIMATE in
 * the UI for that reason, and it says so next to every number it produces.
 *
 * It is nevertheless a considerably better-grounded surface than the bundled 235-byte ONNX
 * graph, which weights slope and aspect at exactly zero (see EINCOL_REPORT.md).
 *
 * WHY IT DELIBERATELY REFUSES TO BE MONOTONIC
 * The obvious heatmap makes suitability increase with wetness and decrease with elevation
 * on the slope: lower = wetter = better. The sources say otherwise at the bottom end.
 * Virginia Cooperative Extension, *Growing American Ginseng in Forestlands*: ginseng
 * "will not grow in waterlogged soil, compacted areas (such as old roadbeds), leaf-filled
 * depressions, rocky outcrops, water flows, or heavy clay soils", and "flat sites with poor
 * drainage or a history of flooding will not support ginseng growth." A monotonic surface
 * paints creek bottoms and seeps as the best ground on the map. Those are precisely the
 * places not to send a digger. Wetness, slope angle and slope position are therefore all
 * OPTIMUM BANDS, not ramps.
 *
 * SOURCES
 *  - Virginia Cooperative Extension / USDA National Agroforestry Center,
 *    *Growing American Ginseng in Forestlands*: north or east facing, near the bottom of
 *    slopes, not too steep, hollows productive, ~75% shade / 25% sunlight, moist
 *    well-drained soil high in calcium and organic matter.
 *  - McCune & Keon (2002) J. Veg. Sci. 13:603-606 — heat load index, Eq. 3.
 *  - Weiss (2001) — topographic position index.
 *  - Beven & Kirkby (1979) — topographic wetness index.
 *  - Kopecky & Cizkova (2010) Appl. Veg. Sci. 13:450-459 — multiple-flow routing for TWI.
 *  - Burkhart (Penn State) — soil calcium threshold ~3,360 kg/ha marks promising sites;
 *    calcium-indicator species. NOT computable from a DEM, so it is surfaced in the UI as
 *    a field check rather than folded into the raster. See [CALCIUM_NOTE].
 */
object GinsengSuitability {

    /**
     * Soil calcium is one of the strongest published predictors of ginseng site quality and
     * is the largest factor this raster CANNOT see. No DEM implies calcium. Rather than
     * quietly omit it and let the heatmap imply completeness, the UI states it and points
     * the digger at the indicator species that do reveal it.
     */
    const val CALCIUM_NOTE =
        "This surface reads terrain only. Soil calcium is one of the strongest predictors " +
        "of ginseng ground and no elevation model can see it. Confirm on foot: sugar maple, " +
        "tulip poplar, black walnut and basswood overhead, with jack-in-the-pulpit, " +
        "maidenhair fern or blue cohosh underfoot, mark the calcium-rich sites."

    /** Relative weights. They sum to 1.0 and are expert-assigned, not fitted. */
    const val W_HEAT_LOAD = 0.28
    const val W_SLOPE_POSITION = 0.24
    const val W_WETNESS = 0.18
    const val W_SLOPE_ANGLE = 0.14
    const val W_CURVATURE = 0.10
    const val W_ELEVATION = 0.06

    enum class Factor(val display: String, val weight: Double) {
        HEAT_LOAD("Aspect & heat load", W_HEAT_LOAD),
        SLOPE_POSITION("Position on slope", W_SLOPE_POSITION),
        WETNESS("Moisture (drained)", W_WETNESS),
        SLOPE_ANGLE("Slope steepness", W_SLOPE_ANGLE),
        CURVATURE("Cove / hollow form", W_CURVATURE),
        ELEVATION("Elevation", W_ELEVATION),
    }

    data class Breakdown(
        val score: Double,
        val factors: Map<Factor, Double>,
    ) {
        /** The factor contributing the most to (or subtracting the most from) this score. */
        fun dominant(): Factor? = factors.maxByOrNull { it.value * it.key.weight }?.key
        fun weakest(): Factor? = factors.minByOrNull { it.value }?.key
    }

    /**
     * Scores one location. All inputs are terrain-derived.
     *
     * @param heatLoadRaw  raw McCune-Keon value from [TerrainMath.heatLoadIndex]
     * @param tpiMeters    topographic position index in metres (negative = lower slope/cove)
     * @param twi          topographic wetness index, ln(a/tan b)
     * @param slopeDeg     slope in degrees
     * @param curvature    profile curvature; positive = concave
     * @param elevationM   elevation in metres
     */
    fun score(
        heatLoadRaw: Double,
        tpiMeters: Double,
        twi: Double,
        slopeDeg: Double,
        curvature: Double,
        elevationM: Double,
    ): Breakdown {

        // 1. Heat load. NE coolest -> best. Cooler is monotonically better here; there is
        //    no "too cool" end in the eastern deciduous forest.
        val heat = 1.0 - TerrainMath.normaliseHeatLoad(heatLoadRaw)

        // 2. Position on slope. "Near the bottom of slopes" - but a valley FLOOR is
        //    waterlogged bottomland, not ginseng ground. Best band sits below mid-slope
        //    and above the channel: TPI roughly -8 m to -1 m.
        val position = TerrainMath.band(tpiMeters, -8.0, -1.0, 6.0)

        // 3. Wetness. Moist but well drained. TWI over ~11 is saturated ground.
        val wetness = TerrainMath.band(twi, 6.0, 10.5, 2.5)

        // 4. Slope angle. Too steep loses topsoil to erosion; flat drains poorly.
        //    ~6-20 degrees (roughly 10-36%) is the productive band.
        val steep = TerrainMath.band(slopeDeg, 6.0, 20.0, 7.0)

        // 5. Form. Concave coves and hollows collect deep fertile soil and hold shade.
        val cove = TerrainMath.smoothStep((curvature + 0.02) / 0.04)

        // 6. Elevation. Broad Appalachian band; a weak constraint that mostly trims
        //    obviously-wrong ground rather than ranking good ground.
        val elev = TerrainMath.band(elevationM, 250.0, 1200.0, 250.0)

        val factors = mapOf(
            Factor.HEAT_LOAD to heat,
            Factor.SLOPE_POSITION to position,
            Factor.WETNESS to wetness,
            Factor.SLOPE_ANGLE to steep,
            Factor.CURVATURE to cove,
            Factor.ELEVATION to elev,
        )
        val s = factors.entries.sumOf { it.value * it.key.weight }
        return Breakdown(s.coerceIn(0.0, 1.0), factors)
    }

    /** Field-facing label for a score. Deliberately hedged: this is a hint, not a find. */
    fun label(score: Double): String = when {
        score >= 0.75 -> "Worth walking"
        score >= 0.55 -> "Possible"
        score >= 0.35 -> "Marginal"
        else -> "Unlikely"
    }
}
