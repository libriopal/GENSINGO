package com.ginsengo.steward.terrain

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Astronomical Ephemeris & Topoclimatic Radiation Matrix Engine.
 *
 * Implements high-precision, offline celestial trigonometry (Jean Meeus / NOAA algorithms)
 * to calculate exact topocentric Sun and Moon positions, lunar phase angles, disk illumination
 * fractions, and topoclimatic radiation budgets.
 *
 * This engine powers the non-ordinary "Solar Shade & Nocturnal Moonlight Exposure" layer:
 *  - Maximizes diurnal shade from harsh scorching midday solar radiation.
 *  - Maximizes nocturnal moon exposure, sky-view radiative cooling, and night dew accumulation.
 *  - Models the Appalachian cove microclimate niche favored by Panax quinquefolius.
 */
object AstroEphemerisEngine {

    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI

    data class SunPosition(
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val isAboveHorizon: Boolean,
        val declinationDeg: Double,
        val rightAscensionDeg: Double,
    )

    data class MoonPosition(
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val isAboveHorizon: Boolean,
        val phaseAngleDeg: Double,
        val illuminationFraction: Double,
        val phaseName: String,
        val distanceKm: Double,
        val topocentricIlluminanceLux: Double,
    )

    data class EphemerisSnapshot(
        val timestampMs: Long,
        val lat: Double,
        val lon: Double,
        val sun: SunPosition,
        val moon: MoonPosition,
    )

    data class TopoclimaticRadiation(
        val directSolarIncidence: Double,
        val solarShadeScore: Double,
        val directMoonlightIncidence: Double,
        val nocturnalMoonScore: Double,
        val photoperiodicCoveIndex: Double,
    )

    /**
     * Computes the complete Sun & Moon celestial ephemeris at the given location and time.
     */
    fun computeEphemeris(
        lat: Double,
        lon: Double,
        timestampMs: Long = System.currentTimeMillis(),
    ): EphemerisSnapshot {
        // Julian Day from Unix Epoch
        val jd = 2440587.5 + (timestampMs / 86400000.0)
        val t = (jd - 2451545.0) / 36525.0

        // Greenwich Mean Sidereal Time in degrees
        var gmst = (280.46061837 + 360.98564736629 * (jd - 2451545.0)) % 360.0
        if (gmst < 0.0) gmst += 360.0
        var lst = (gmst + lon) % 360.0
        if (lst < 0.0) lst += 360.0

        val sun = computeSun(t, lat, lst)
        val moon = computeMoon(t, lat, lst, sun)

        return EphemerisSnapshot(
            timestampMs = timestampMs,
            lat = lat,
            lon = lon,
            sun = sun,
            moon = moon,
        )
    }

    /**
     * Evaluates topoclimatic solar shade and nocturnal moonlight exposure for a specific
     * terrain slope and aspect.
     *
     * @param slopeDeg Terrain slope angle (0° = flat, 90° = vertical cliff).
     * @param aspectDeg Terrain aspect (0° = North, 90° = East, 180° = South, 270° = West).
     * @param skyViewFactor Proportion of unblocked hemispherical sky (0.0 to 1.0).
     */
    fun evaluateTopoclimaticRadiation(
        ephemeris: EphemerisSnapshot,
        slopeDeg: Double,
        aspectDeg: Double,
        skyViewFactor: Double = 0.85,
    ): TopoclimaticRadiation {
        val slopeRad = slopeDeg * DEG_TO_RAD
        val aspectRad = aspectDeg * DEG_TO_RAD

        // 1. Solar incident angle on sloping terrain:
        // cos(theta_i) = sin(alt) * cos(slope) + cos(alt) * sin(slope) * cos(az - aspect)
        val sunAltRad = ephemeris.sun.altitudeDeg * DEG_TO_RAD
        val sunAzRad = ephemeris.sun.azimuthDeg * DEG_TO_RAD

        val directSunIncidence = if (ephemeris.sun.isAboveHorizon) {
            val cosInc = sin(sunAltRad) * cos(slopeRad) +
                    cos(sunAltRad) * sin(slopeRad) * cos(sunAzRad - aspectRad)
            max(0.0, cosInc)
        } else {
            0.0
        }

        // Solar Shade: High when slope/aspect shields against diurnal heat load.
        // North & East slopes receive gentle morning sun and avoid blistering afternoon southwest insolation.
        // High slope facing North/East gives maximum shade during peak solar elevation.
        val normAspect = (aspectDeg % 360.0 + 360.0) % 360.0
        val aspectShadeFactor = when {
            normAspect in 315.0..360.0 || normAspect in 0.0..60.0 -> 0.95 // North & NNE
            normAspect in 60.0..105.0 -> 0.82                             // East
            normAspect in 285.0..315.0 -> 0.60                            // Northwest
            normAspect in 105.0..150.0 -> 0.45                            // Southeast
            else -> 0.15                                                  // South & Southwest (baking exposure)
        }

        val solarShadeScore = (aspectShadeFactor * 0.70 + (1.0 - directSunIncidence) * 0.30).coerceIn(0.0, 1.0)

        // 2. Nocturnal Moonlight incident angle:
        val moonAltRad = ephemeris.moon.altitudeDeg * DEG_TO_RAD
        val moonAzRad = ephemeris.moon.azimuthDeg * DEG_TO_RAD

        val directMoonIncidence = if (ephemeris.moon.isAboveHorizon) {
            val cosInc = sin(moonAltRad) * cos(slopeRad) +
                    cos(moonAltRad) * sin(slopeRad) * cos(moonAzRad - aspectRad)
            max(0.0, cosInc)
        } else {
            0.0
        }

        // Nocturnal Moon score: Product of disk illumination fraction, sky view factor,
        // and terrain exposure to the moon's nocturnal trajectory.
        val moonExposure = if (ephemeris.moon.isAboveHorizon) {
            (directMoonIncidence * 0.60 + skyViewFactor * 0.40) * ephemeris.moon.illuminationFraction
        } else {
            // Even when moon is below horizon, diffuse night sky / high sky view factor enables nocturnal radiative cooling
            skyViewFactor * 0.25 * ephemeris.moon.illuminationFraction
        }
        val nocturnalMoonScore = moonExposure.coerceIn(0.0, 1.0)

        // 3. Composite Photoperiodic Cove Index:
        // Maximizes conjunction of high solar shade (protecting against diurnal drying)
        // and open nocturnal sky/moonlight (enabling nocturnal dew accumulation, cold air drainage, and vegetative rest).
        val photoperiodicCoveIndex = (solarShadeScore * 0.65 + nocturnalMoonScore * 0.35).coerceIn(0.0, 1.0)

        return TopoclimaticRadiation(
            directSolarIncidence = directSunIncidence,
            solarShadeScore = solarShadeScore,
            directMoonlightIncidence = directMoonIncidence,
            nocturnalMoonScore = nocturnalMoonScore,
            photoperiodicCoveIndex = photoperiodicCoveIndex,
        )
    }

    private fun computeSun(t: Double, latDeg: Double, lstDeg: Double): SunPosition {
        // Solar geometric mean longitude
        var l0 = (280.46646 + 36000.76983 * t) % 360.0
        if (l0 < 0.0) l0 += 360.0

        // Solar mean anomaly
        var m = (357.52911 + 35999.05029 * t) % 360.0
        if (m < 0.0) m += 360.0
        val mRad = m * DEG_TO_RAD

        // Equation of center
        val c = (1.914602 - 0.004817 * t) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2.0 * mRad) +
                0.000289 * sin(3.0 * mRad)

        // True solar longitude
        val sunTrueLon = l0 + c
        val sunTrueLonRad = sunTrueLon * DEG_TO_RAD

        // Mean obliquity of the ecliptic
        val eps = (23.439291 - 0.0130042 * t) * DEG_TO_RAD

        // Right ascension and declination
        val sinDec = sin(eps) * sin(sunTrueLonRad)
        val decRad = asin(sinDec)
        val raRad = atan2(cos(eps) * sin(sunTrueLonRad), cos(sunTrueLonRad))
        var raDeg = raRad * RAD_TO_DEG
        if (raDeg < 0.0) raDeg += 360.0

        // Hour angle
        var haDeg = (lstDeg - raDeg) % 360.0
        if (haDeg > 180.0) haDeg -= 360.0
        if (haDeg < -180.0) haDeg += 360.0
        val haRad = haDeg * DEG_TO_RAD

        val latRad = latDeg * DEG_TO_RAD
        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
        val altDeg = altRad * RAD_TO_DEG

        val cosAz = (sin(decRad) - sin(latRad) * sin(altRad)) / (cos(latRad) * cos(altRad)).coerceAtLeast(1e-6)
        var azDeg = acos(cosAz.coerceIn(-1.0, 1.0)) * RAD_TO_DEG
        if (sin(haRad) > 0.0) azDeg = 360.0 - azDeg

        return SunPosition(
            altitudeDeg = altDeg,
            azimuthDeg = azDeg,
            isAboveHorizon = altDeg > -0.833, // atmospheric refraction threshold
            declinationDeg = decRad * RAD_TO_DEG,
            rightAscensionDeg = raDeg,
        )
    }

    private fun computeMoon(
        t: Double,
        latDeg: Double,
        lstDeg: Double,
        sun: SunPosition,
    ): MoonPosition {
        // Moon orbital parameters (Meeus truncated)
        var lp = (218.3164477 + 481267.88128157 * t) % 360.0
        var d = (297.8501921 + 445267.1114034 * t) % 360.0
        var mSun = (357.5291092 + 35999.0502909 * t) % 360.0
        var mMoon = (134.9633964 + 477198.8675055 * t) % 360.0
        var f = (93.2720950 + 483202.0175233 * t) % 360.0

        val dRad = d * DEG_TO_RAD
        val mSunRad = mSun * DEG_TO_RAD
        val mMoonRad = mMoon * DEG_TO_RAD
        val fRad = f * DEG_TO_RAD

        // Moon longitude perturbations
        val lonPerturb = 6.288774 * sin(mMoonRad) +
                1.274027 * sin(2.0 * dRad - mMoonRad) +
                0.658309 * sin(2.0 * dRad) +
                0.213618 * sin(2.0 * mMoonRad) -
                0.185116 * sin(mSunRad) -
                0.114332 * sin(2.0 * fRad)
        var moonLon = (lp + lonPerturb) % 360.0
        if (moonLon < 0.0) moonLon += 360.0
        val moonLonRad = moonLon * DEG_TO_RAD

        // Moon latitude perturbations
        val latPerturb = 5.128154 * sin(fRad) +
                0.280602 * sin(mMoonRad + fRad) +
                0.277693 * sin(mMoonRad - fRad) +
                0.173237 * sin(2.0 * dRad - fRad)
        val moonLatRad = latPerturb * DEG_TO_RAD

        // Distance in km
        val distanceKm = 385000.56 -
                20905.355 * cos(mMoonRad) -
                3699.111 * cos(2.0 * dRad - mMoonRad) -
                2955.968 * cos(2.0 * dRad) -
                569.925 * cos(2.0 * mMoonRad)

        // Ecliptic to equatorial coordinates
        val eps = (23.439291 - 0.0130042 * t) * DEG_TO_RAD
        val sinDec = sin(moonLatRad) * cos(eps) + cos(moonLatRad) * sin(eps) * sin(moonLonRad)
        val decRad = asin(sinDec.coerceIn(-1.0, 1.0))
        val y = -sin(moonLatRad) * sin(eps) + cos(moonLatRad) * cos(eps) * sin(moonLonRad)
        val x = cos(moonLatRad) * cos(moonLonRad)
        var raDeg = atan2(y, x) * RAD_TO_DEG
        if (raDeg < 0.0) raDeg += 360.0

        // Topocentric altitude and azimuth
        var haDeg = (lstDeg - raDeg) % 360.0
        if (haDeg > 180.0) haDeg -= 360.0
        if (haDeg < -180.0) haDeg += 360.0
        val haRad = haDeg * DEG_TO_RAD

        val latRad = latDeg * DEG_TO_RAD
        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
        val altDeg = altRad * RAD_TO_DEG

        val cosAz = (sin(decRad) - sin(latRad) * sin(altRad)) / (cos(latRad) * cos(altRad)).coerceAtLeast(1e-6)
        var azDeg = acos(cosAz.coerceIn(-1.0, 1.0)) * RAD_TO_DEG
        if (sin(haRad) > 0.0) azDeg = 360.0 - azDeg

        // Phase angle calculation (elongation from sun)
        val sunRaRad = sun.rightAscensionDeg * DEG_TO_RAD
        val sunDecRad = sun.declinationDeg * DEG_TO_RAD
        val cosElongation = sin(sunDecRad) * sin(decRad) +
                cos(sunDecRad) * cos(decRad) * cos(sunRaRad - (raDeg * DEG_TO_RAD))
        val elongationDeg = acos(cosElongation.coerceIn(-1.0, 1.0)) * RAD_TO_DEG
        val phaseAngleDeg = 180.0 - elongationDeg

        // Disk illumination fraction: k = (1 + cos(phaseAngle)) / 2
        val illuminationFraction = ((1.0 + cos(phaseAngleDeg * DEG_TO_RAD)) / 2.0).coerceIn(0.0, 1.0)

        val phaseName = when {
            illuminationFraction < 0.03 -> "New Moon"
            illuminationFraction in 0.03..0.45 -> if (d in 0.0..180.0) "Waxing Crescent" else "Waning Crescent"
            illuminationFraction in 0.45..0.55 -> if (d in 0.0..180.0) "First Quarter" else "Last Quarter"
            illuminationFraction in 0.55..0.97 -> if (d in 0.0..180.0) "Waxing Gibbous" else "Waning Gibbous"
            else -> "Full Moon"
        }

        // Topocentric illuminance at ground level (lux)
        // Full Moon at zenith ~ 0.27 lux at mean distance 384,400 km
        val distanceScale = (384400.0 / distanceKm) * (384400.0 / distanceKm)
        val sinMoonAlt = max(0.0, sin(altRad))
        val topocentricIlluminanceLux = 0.27 * distanceScale * illuminationFraction * sinMoonAlt

        return MoonPosition(
            altitudeDeg = altDeg,
            azimuthDeg = azDeg,
            isAboveHorizon = altDeg > 0.0,
            phaseAngleDeg = phaseAngleDeg,
            illuminationFraction = illuminationFraction,
            phaseName = phaseName,
            distanceKm = distanceKm,
            topocentricIlluminanceLux = topocentricIlluminanceLux,
        )
    }
}
