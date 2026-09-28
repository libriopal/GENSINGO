package com.ginsengo.steward.terrain

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * GIS Visual Field Map Layer Matrix & Eco-Suitability Index (ESI) Engine.
 *
 * Computes a rigorous 0.0 to 1.0 Eco-Suitability Index across 4 environmental research layers:
 *  1. Elevation Height Map: Continuous DEM interpolation (NC OneMap / OpenTopoData Appalachian model 600m-1200m).
 *  2. Slope & Aspect Filter: 10%-40% slope isolation with Aeolian/Phrygian solar radiation mask (315° NW through 0° N to 90° E).
 *  3. Overstory & Canopy Density: Deciduous hardwood indicators (Liriodendron, Acer saccharum, Carya) with 75%-85% canopy shade.
 *  4. Soil Moisture & Companion Plants: Well-drained loamy soil vectors and calcium-rich companion indicators (Podophyllum, Arisaema, Actaea).
 */
object EsiMatrixModel {

    enum class LayerMode(val displayName: String, val shortTag: String, val description: String) {
        COMPOSITE_ESI(
            "Composite ESI",
            "ESI",
            "Multi-factor weighted Eco-Suitability Index (0.0 - 1.0) combining all 4 environmental layers."
        ),
        ELEVATION_DEM(
            "Elevation DEM",
            "DEM",
            "Continuous raster interpolation simulating NC OneMap / OpenTopoData (optimal 600m–1200m)."
        ),
        SLOPE_ASPECT(
            "Slope & Aspect Filter",
            "SLOPE",
            "Isolates 10%–40% terrain slopes and Aeolian/Phrygian shade-retention solar radiation masks (315°–90°)."
        ),
        CANOPY_OVERSTORY(
            "Overstory & Canopy",
            "CANOPY",
            "Filters for mature Liriodendron, Acer saccharum, and Carya canopy providing 75%–85% continuous shade."
        ),
        SOIL_COMPANION(
            "Soil & Companion Flora",
            "SOIL",
            "Models loose well-drained loamy soil vectors with calcium flora indicators (Podophyllum, Arisaema, Actaea)."
        ),
        LUNAR_SOLAR_SHADE(
            "Photoperiodic Shade/Moon",
            "MOON",
            "Astronomical Sun-Shade & Nocturnal Moonlight Exposure / Photoperiodic Cove Sensing."
        )
    }

    data class MatrixWeights(
        val elevation: Float = 0.25f,
        val slopeAspect: Float = 0.30f,
        val canopyDensity: Float = 0.25f,
        val soilCompanion: Float = 0.20f,
        val lunarSolarShade: Float = 0.0f,
    ) {
        val sum: Float get() = elevation + slopeAspect + canopyDensity + soilCompanion + lunarSolarShade

        val normalizedElevation: Float
            get() = if (sum > 0f) elevation / sum else 0.25f
        val normalizedSlopeAspect: Float
            get() = if (sum > 0f) slopeAspect / sum else 0.30f
        val normalizedCanopy: Float
            get() = if (sum > 0f) canopyDensity / sum else 0.25f
        val normalizedSoil: Float
            get() = if (sum > 0f) soilCompanion / sum else 0.20f
        val normalizedLunarSolarShade: Float
            get() = if (sum > 0f) lunarSolarShade / sum else 0.0f
    }

    data class EsiBreakdown(
        val compositeEsi: Double,
        val elevationScore: Double,
        val slopeAspectScore: Double,
        val canopyScore: Double,
        val soilFloraScore: Double,
        val ratingLabel: String,
        val recommendation: String,
    )

    /**
     * Layer 1: Elevation Height Map Layer (600m - 1200m optimal Appalachian sweet spot).
     */
    fun computeElevationScore(elevationMeters: Double): Double {
        // Optimal range: 600m to 1200m (~1,968ft to 3,937ft).
        return when {
            elevationMeters in 700.0..1100.0 -> 1.0
            elevationMeters in 600.0..1200.0 -> 0.85
            elevationMeters in 450.0..1350.0 -> 0.60
            elevationMeters in 300.0..1500.0 -> 0.35
            else -> 0.10
        }
    }

    /**
     * Layer 2: Slope & Aspect Filter Layer.
     * Slope: Optimally 10% to 40% (approx 5.7° to 22.0°).
     * Aspect: Strictly favors north- and east-facing slopes (315° NW through 0° N to 90° E).
     */
    fun computeSlopeAspectScore(slopeDeg: Double, aspectDeg: Double): Double {
        // Slope factor: 10% to 40% grade (approx 5.7° to 21.8°)
        val slopeFactor = when {
            slopeDeg in 7.0..18.0 -> 1.0
            slopeDeg in 5.5..24.0 -> 0.85
            slopeDeg in 3.0..32.0 -> 0.50
            else -> 0.20
        }

        // Aspect factor: Favor 315° to 90° (north, northeast, east).
        // Aspect difference from optimum (due northeast = 45°):
        val normAspect = (aspectDeg % 360.0 + 360.0) % 360.0
        val aspectFactor = when {
            normAspect in 0.0..90.0 -> 1.0   // North to East
            normAspect in 315.0..360.0 -> 0.90 // Northwest to North
            normAspect in 90.0..135.0 -> 0.70  // East to Southeast
            normAspect in 270.0..315.0 -> 0.45 // West to Northwest
            else -> 0.20                        // South and Southwest (high solar drying load)
        }

        return (slopeFactor * 0.55 + aspectFactor * 0.45).coerceIn(0.0, 1.0)
    }

    /**
     * Layer 3: Overstory & Canopy Density Layer.
     * Filters for thick deciduous hardwood overstory (Liriodendron, Acer saccharum, Carya)
     * with 75% to 85% continuous canopy shade.
     */
    fun computeCanopyScore(
        canopyCoverPercent: Double,
        hardwoodIndicatorIndex: Double = 0.85
    ): Double {
        // Optimal canopy cover: 75% to 85% shade.
        val coverFactor = when {
            canopyCoverPercent in 75.0..85.0 -> 1.0
            canopyCoverPercent in 70.0..90.0 -> 0.85
            canopyCoverPercent in 60.0..95.0 -> 0.55
            else -> 0.25
        }
        return (coverFactor * 0.70 + hardwoodIndicatorIndex.coerceIn(0.0, 1.0) * 0.30).coerceIn(0.0, 1.0)
    }

    /**
     * Layer 4: Soil Moisture & Companion Plant Layer.
     * Models loose, well-drained loamy soil vectors (Saunook, Cullasaja, Santeetlah coves)
     * and calcium-rich companion flora (Podophyllum, Arisaema, Actaea).
     */
    fun computeSoilCompanionScore(
        coveFormConcavity: Double,
        drainageIndex: Double,
        companionRichness: Double = 0.80
    ): Double {
        // High concavity (cove amphitheater) + well-drained (drainageIndex ~ 0.85)
        val soilFactor = (coveFormConcavity * 0.45 + drainageIndex * 0.55).coerceIn(0.0, 1.0)
        return (soilFactor * 0.60 + companionRichness.coerceIn(0.0, 1.0) * 0.40).coerceIn(0.0, 1.0)
    }

    /**
     * Layer 5: Astronomical Sun-Shade & Nocturnal Moonlight Exposure Layer.
     * Evaluates topoclimatic solar shade (blocking scorching midday SW solar load)
     * and nocturnal moon exposure / sky clearance for dew condensation and photoperiodic sensing.
     */
    fun computeLunarSolarShadeScore(
        lat: Double,
        lon: Double,
        slopeDeg: Double,
        aspectDeg: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): Double {
        val ephem = AstroEphemerisEngine.computeEphemeris(lat, lon, timestampMs)
        val radiation = AstroEphemerisEngine.evaluateTopoclimaticRadiation(ephem, slopeDeg, aspectDeg)
        return radiation.photoperiodicCoveIndex
    }

    /**
     * Computes complete ESI breakdown using weights.
     */
    fun evaluate(
        elevationMeters: Double,
        slopeDeg: Double,
        aspectDeg: Double,
        canopyCoverPercent: Double = 80.0,
        coveConcavity: Double = 0.80,
        soilDrainage: Double = 0.85,
        weights: MatrixWeights = MatrixWeights(),
    ): EsiBreakdown {
        val sElev = computeElevationScore(elevationMeters)
        val sSlope = computeSlopeAspectScore(slopeDeg, aspectDeg)
        val sCanopy = computeCanopyScore(canopyCoverPercent)
        val sSoil = computeSoilCompanionScore(coveConcavity, soilDrainage)

        val composite = (sElev * weights.normalizedElevation +
                sSlope * weights.normalizedSlopeAspect +
                sCanopy * weights.normalizedCanopy +
                sSoil * weights.normalizedSoil).coerceIn(0.0, 1.0)

        val (label, rec) = when {
            composite >= 0.80 -> "Prime Micro-Habitat" to "Exceptional Appalachian cove benchmark: protected NE aspect, 75-85% hardwood canopy, and high calcium loamy drainage."
            composite >= 0.65 -> "High Probability" to "Promising slope position; ground check for companion flora (black cohosh, maidenhair fern) and soil drainage."
            composite >= 0.50 -> "Moderate / Conditional" to "Viable elevation and slope; verify canopy cover and absence of heavy clay or waterlogged seeps."
            composite >= 0.35 -> "Marginal" to "Sub-optimal aspect or steepness; likely excessive solar radiation or insufficient topsoil accumulation."
            else -> "Low Potential" to "Unsuitable terrain profile: exposed ridge, south-facing dry aspect, or flooded valley bottom."
        }

        return EsiBreakdown(
            compositeEsi = composite,
            elevationScore = sElev,
            slopeAspectScore = sSlope,
            canopyScore = sCanopy,
            soilFloraScore = sSoil,
            ratingLabel = label,
            recommendation = rec,
        )
    }
}
