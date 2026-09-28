package com.ginsengo.steward.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Tests the offline astronomical ephemeris and solar/lunar topoclimatic radiation model.
 *
 * Verifies:
 * 1. Celestial orbital mechanics invariants (bounds on altitude, azimuth, moon distance, illumination fraction).
 * 2. Solar diurnal variation (positive altitude at noon, negative altitude at midnight).
 * 3. Solar shade vs moonlight exposure sensitivity (N/NE slope protects from drying insolation).
 * 4. Ground illuminance lux models (peak full moon <= 0.35 lux, zero below horizon).
 */
class AstroEphemerisEngineTest {

    private val appalachianLat = 35.550 // Haywood County, NC
    private val appalachianLon = -82.950

    @Test
    fun orbitalMechanicsInvariantsHold() {
        val now = System.currentTimeMillis()
        val ephem = AstroEphemerisEngine.computeEphemeris(appalachianLat, appalachianLon, now)

        // Sun invariants
        assertTrue("Sun altitude within [-90, 90]", ephem.sun.altitudeDeg in -90.0..90.0)
        assertTrue("Sun azimuth within [0, 360)", ephem.sun.azimuthDeg in 0.0..360.0)
        assertTrue("Sun declination within [-24, 24]", ephem.sun.declinationDeg in -24.0..24.0)

        // Moon invariants
        assertTrue("Moon altitude within [-90, 90]", ephem.moon.altitudeDeg in -90.0..90.0)
        assertTrue("Moon azimuth within [0, 360)", ephem.moon.azimuthDeg in 0.0..360.0)
        assertTrue(
            "Moon distance in physical perigee/apogee range (356k - 407k km), was ${ephem.moon.distanceKm}",
            ephem.moon.distanceKm in 350000.0..415000.0
        )
        assertTrue(
            "Illumination fraction must be in [0.0, 1.0], was ${ephem.moon.illuminationFraction}",
            ephem.moon.illuminationFraction in 0.0..1.0
        )
        assertTrue(
            "Topocentric illuminance lux must be non-negative and <= 0.35 lux, was ${ephem.moon.topocentricIlluminanceLux}",
            ephem.moon.topocentricIlluminanceLux in 0.0..0.35
        )
    }

    @Test
    fun solarDiurnalCycleReflectsReality() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        cal.set(2026, Calendar.SEPTEMBER, 23, 13, 15, 0) // Solar noon approx 13:15 EDT
        val noonMs = cal.timeInMillis

        cal.set(2026, Calendar.SEPTEMBER, 23, 1, 15, 0) // Deep night 01:15 EDT
        val midnightMs = cal.timeInMillis

        val noonEphem = AstroEphemerisEngine.computeEphemeris(appalachianLat, appalachianLon, noonMs)
        val midnightEphem = AstroEphemerisEngine.computeEphemeris(appalachianLat, appalachianLon, midnightMs)

        assertTrue("Sun must be high in sky at solar noon in September", noonEphem.sun.altitudeDeg > 45.0)
        assertTrue("Sun must be above horizon at noon", noonEphem.sun.isAboveHorizon)
        assertTrue("Sun azimuth at solar noon in Northern Hemisphere should be roughly South (160° - 200°)",
            noonEphem.sun.azimuthDeg in 160.0..200.0)

        assertTrue("Sun must be below horizon at 01:15 AM", midnightEphem.sun.altitudeDeg < -20.0)
        assertFalse("Sun is not above horizon at night", midnightEphem.sun.isAboveHorizon)
    }

    @Test
    fun northEastSlopesReceiveSuperiorSunShadeThanSouthFacingBakingSlopes() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        cal.set(2026, Calendar.SEPTEMBER, 23, 13, 0, 0)
        val middayEphem = AstroEphemerisEngine.computeEphemeris(appalachianLat, appalachianLon, cal.timeInMillis)

        // North-East cove slope (45° aspect, 20° slope)
        val protectedCove = AstroEphemerisEngine.evaluateTopoclimaticRadiation(
            ephemeris = middayEphem,
            slopeDeg = 20.0,
            aspectDeg = 45.0,
            skyViewFactor = 0.80
        )

        // South-Southwest baking ridge slope (210° aspect, 20° slope)
        val exposedRidge = AstroEphemerisEngine.evaluateTopoclimaticRadiation(
            ephemeris = middayEphem,
            slopeDeg = 20.0,
            aspectDeg = 210.0,
            skyViewFactor = 0.80
        )

        assertTrue(
            "Direct solar incidence on baking SW slope must exceed NE protected cove. SW: ${exposedRidge.directSolarIncidence} vs NE: ${protectedCove.directSolarIncidence}",
            exposedRidge.directSolarIncidence > protectedCove.directSolarIncidence + 0.25
        )

        assertTrue(
            "Solar shade score in NE cove must be significantly higher than SW ridge. NE: ${protectedCove.solarShadeScore} vs SW: ${exposedRidge.solarShadeScore}",
            protectedCove.solarShadeScore > exposedRidge.solarShadeScore + 0.35
        )

        assertTrue(
            "Overall photoperiodic cove index must strictly favor protected cove",
            protectedCove.photoperiodicCoveIndex > exposedRidge.photoperiodicCoveIndex
        )
    }

    @Test
    fun negativeControlDirectMoonlightZeroWhenMoonBelowHorizon() {
        // Construct an ephemeris where moon altitude is forced negative (below horizon)
        val ephem = AstroEphemerisEngine.computeEphemeris(appalachianLat, appalachianLon, 0L)
        val forcedBelowHorizonEphem = ephem.copy(
            moon = ephem.moon.copy(
                altitudeDeg = -15.0,
                isAboveHorizon = false
            )
        )

        val radiation = AstroEphemerisEngine.evaluateTopoclimaticRadiation(
            ephemeris = forcedBelowHorizonEphem,
            slopeDeg = 15.0,
            aspectDeg = 45.0
        )

        assertEquals(
            "Direct moonlight incidence must be exactly 0.0 when moon is below horizon",
            0.0,
            radiation.directMoonlightIncidence,
            0.0001
        )
    }
}
