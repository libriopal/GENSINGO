package com.ginsengo.steward.prospect

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled Haywood sites, and the arithmetic a digger's life could depend on.
 *
 * The asset is read from the source tree because these run on a plain JVM with no asset manager.
 */
class ProspectsTest {

    private fun set(): ProspectSet {
        val f = File("src/main/assets/${Prospects.ASSET}")
        assertTrue("bundled prospects missing at ${f.absolutePath}", f.exists())
        return Json { ignoreUnknownKeys = true }.decodeFromString(f.readText())
    }

    @Test
    fun theBundleIsRealAndForHaywood() {
        val s = set()
        assertEquals("Haywood", s.county)
        assertEquals("NC", s.state)
        assertTrue("expected a populated site list, got ${s.sites.size}", s.sites.size >= 50)
        assertTrue("the caveat must survive into the bundle", s.caveat.contains("does NOT know"))
    }

    /** Every site must be inside Haywood County's actual envelope, not somewhere in the ocean. */
    @Test
    fun everySiteIsInWesternNorthCarolina() {
        set().sites.forEach {
            assertTrue("lat ${it.lat} outside NC mountains", it.lat in 35.0..36.2)
            assertTrue("lon ${it.lon} outside NC mountains", it.lon in -84.5..-82.0)
        }
    }

    /**
     * The axis-order regression guard. An earlier version of this pipeline read WFS coordinates
     * as lon,lat when the service returns lat,lon, which put every centroid in the Indian Ocean
     * and made a legal check report "clear" for ground inside a national forest. If lat and lon
     * are ever swapped again, latitude goes negative-ish and longitude goes positive - this
     * catches it before it ships.
     */
    @Test
    fun latitudeAndLongitudeAreNotSwapped() {
        set().sites.forEach {
            assertTrue("latitude ${it.lat} looks like a longitude", it.lat > 0)
            assertTrue("longitude ${it.lon} looks like a latitude", it.lon < 0)
        }
    }

    @Test
    fun sitesAreOrderedBestFirstAndScoresAreSane() {
        val s = set().sites
        assertTrue(s.first().score >= s.last().score)
        s.forEach { assertTrue("score ${it.score} out of range", it.score in 0.0..1.0) }
    }

    /** Picks are places, not pixels: nothing closer than roughly the 150 m separation used. */
    @Test
    fun sitesAreSpreadOutRatherThanClustered() {
        val s = set().sites
        var tooClose = 0
        for (i in s.indices) for (j in i + 1 until s.size) {
            if (Prospects.distanceMetres(s[i].lat, s[i].lon, s[j].lat, s[j].lon) < 100.0) tooClose++
        }
        assertEquals("sites must not stack on top of each other", 0, tooClose)
    }

    // ------------------------------------------------------------------ navigation arithmetic

    @Test
    fun distanceMatchesAKnownSeparation() {
        // One degree of latitude is about 111 km.
        val d = Prospects.distanceMetres(35.0, -83.0, 36.0, -83.0)
        assertEquals(111_195.0, d, 500.0)
        assertEquals(0.0, Prospects.distanceMetres(35.6, -83.0, 35.6, -83.0), 1e-6)
    }

    @Test
    fun bearingPointsTheRightWay() {
        assertEquals("due north", 0.0, Prospects.bearingTrue(35.0, -83.0, 36.0, -83.0), 0.5)
        assertEquals("due east", 90.0, Prospects.bearingTrue(35.0, -83.0, 35.0, -82.0), 0.5)
        assertEquals("due south", 180.0, Prospects.bearingTrue(36.0, -83.0, 35.0, -83.0), 0.5)
        assertEquals("due west", 270.0, Prospects.bearingTrue(35.0, -82.0, 35.0, -83.0), 0.5)
    }

    @Test
    fun nearestIsActuallyNearest() {
        val s = set().sites
        val near = Prospects.nearest(s, 35.614, -83.032, limit = 5)
        assertEquals(5, near.size)
        assertTrue("distances must ascend", near.zipWithNext().all { it.first.second <= it.second.second })
        assertTrue("the closest should be within a few km", near.first().second < 5_000)
    }

    // ------------------------------------------------------------------ the explanation

    @Test
    fun whyReadsBackRecordedFactsNotTheScore() {
        val cove = set().sites.first { it.landform.equals("COVE", true) }
        val why = cove.why()
        assertTrue("must say it is a cove", why.any { it.contains("COVE") })
        assertTrue("must not just restate the number", why.none { it.contains(cove.score.toString()) })
    }

    /**
     * The honesty case. A generic rock name carries no calcium information, and the explanation
     * has to say so rather than let the reader assume the survey found something good.
     */
    @Test
    fun genericRockIsReportedAsCarryingNoInformation() {
        val generic = ProspectSite(
            lat = 35.6, lon = -83.0, score = 0.9, landform = "COVE",
            drainage = "Well drained", parentRock = "Igneous and metamorphic rock",
        )
        assertTrue(
            "a generic rock name must be flagged as uninformative",
            generic.why().any { it.contains("says nothing about calcium") },
        )
        val rich = generic.copy(parentRock = "Amphibolite")
        assertTrue(
            "amphibolite must be called out as the calcium signal",
            rich.why().any { it.contains("base-rich") },
        )
    }

    @Test
    fun aspectCompassIsCorrectAndFlatGroundHasNone() {
        assertEquals("N", ProspectSite(0.0, 0.0, 0.0, aspect = 0.0).aspectCompass)
        assertEquals("NE", ProspectSite(0.0, 0.0, 0.0, aspect = 45.0).aspectCompass)
        assertEquals("S", ProspectSite(0.0, 0.0, 0.0, aspect = 180.0).aspectCompass)
        assertEquals("NW", ProspectSite(0.0, 0.0, 0.0, aspect = 315.0).aspectCompass)
        assertEquals(null, ProspectSite(0.0, 0.0, 0.0, aspect = -1.0).aspectCompass)
    }

    /**
     * Negative control. If `why()` ignored its inputs, every assertion above could pass on a
     * constant string. Prove two different sites explain themselves differently.
     */
    @Test
    fun differentSitesExplainThemselvesDifferently() {
        val a = ProspectSite(35.6, -83.0, 0.9, landform = "COVE", drainage = "Well drained",
            aspect = 45.0, series = "Whiteoak", parentMaterial = "Colluvium")
        val b = ProspectSite(35.6, -83.0, 0.4, landform = "ridge", drainage = "Poorly drained",
            aspect = 180.0, series = "Ashe", parentMaterial = "Residuum")
        assertTrue("two different sites produced the same explanation", a.why() != b.why())
        assertTrue(a.why().any { it.contains("cool side") })
        assertTrue(b.why().any { it.contains("warmer") })
    }
}
