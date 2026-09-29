package com.ginsengo.steward.research

import com.ginsengo.steward.Fixtures
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.terrain.GinsengSuitability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ResearchValidatorTest {

    private val retrieved = listOf(
        SourceRef("https://extension.psu.edu/ginseng-habitat", "Penn State"),
        SourceRef("https://www.ncagr.gov/plant/ginseng/", "NCDA"),
    )

    private fun item(id: String, urls: List<String> = emptyList(), text: String = "fine") =
        ModelItem(id, "h", text, "look", urls)

    @Test
    fun unknownAndDuplicateIdsAreDroppedAndCounted() {
        val r = ResearchValidator.validate(
            ModelOutput("s", listOf(item("C2"), item("C9"), item("C2"), item("X1"))),
            setOf("C1", "C2", "C3"), retrieved, ResearchValidator.Witness.EXACT,
        )
        assertEquals(listOf("C2"), r.items.map { it.candidateId })
        assertEquals(2, r.idsRejected)
        assertEquals(1, r.duplicatesDropped)
    }

    @Test
    fun onlyRetrievedPagesSurviveAsCitations() {
        val r = ResearchValidator.validate(
            ModelOutput("s", listOf(item("C1", listOf(
                "https://extension.psu.edu/ginseng-habitat/",       // retrieved (trailing slash)
                "http://WWW.ncagr.gov/plant/ginseng",               // retrieved (case, www, scheme)
                "https://example.edu/remembered-from-training",     // NOT retrieved
            )))),
            setOf("C1"), retrieved, ResearchValidator.Witness.EXACT,
        )
        assertEquals(2, r.citationsKept)
        assertEquals(1, r.citationsRejected)
        assertEquals(1, r.items.single().citationsRejected)
        assertFalse(r.items.single().sources.any { "example.edu" in it.url })
    }

    @Test
    fun domainWitnessAcceptsARetrievedDomainAndNothingElse() {
        val grounding = listOf(SourceRef("https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc", "ncagr.gov"))
        val r = ResearchValidator.validate(
            ModelOutput("s", listOf(item("C1", listOf("https://www.ncagr.gov/plant/ginseng", "https://made-up.org/x")))),
            setOf("C1"), grounding, ResearchValidator.Witness.DOMAIN,
        )
        assertEquals(1, r.citationsKept)
        assertEquals(1, r.citationsRejected)
    }

    @Test
    fun coordinatesAndLegalityClaimsAreRemovedFromProse() {
        val r = ResearchValidator.validate(
            ModelOutput("Try 35.5512, -82.9501 first.", listOf(item("C1",
                text = "Good cove. It is legal to dig here in season. Look near 35.55 N 82.95 W."))),
            setOf("C1"), retrieved, ResearchValidator.Witness.EXACT,
        )
        val t = r.items.single().rationale + r.summary
        assertFalse(t, t.contains("35.55"))
        assertFalse(t, t.contains("legal to dig"))
        assertTrue(t.contains("Good cove."))
        assertEquals(3, r.textRedactions)
    }
}

class RadiusScanRealTerrainTest {

    private val mosaic = Fixtures.mosaic("scan_boone_z12.bin")
    private val centreLat = (mosaic.northLat + mosaic.southLat) / 2
    private val centreLng = (mosaic.westLon + mosaic.eastLon) / 2

    private fun scan(radius: Double = 6_000.0, lat: Double = centreLat, lng: Double = centreLng) =
        RadiusScan.of(mosaic, lat, lng, radius)

    @Test
    fun candidatesAreInsideTheRadiusSeparatedAndRanked() {
        val s = scan()
        val c = s.candidates(GinsengSuitability.PRIOR_WEIGHTS, count = 10, minSeparationM = 800.0)
        assertTrue("found ${c.size}", c.size >= 5)
        c.forEach { assertTrue("${it.distanceM}", it.distanceM <= 6_000.0) }
        for (i in c.indices) for (j in i + 1 until c.size) {
            assertTrue(Prospects.distanceMetres(c[i].lat, c[i].lng, c[j].lat, c[j].lng) >= 800.0)
        }
        assertEquals(c.sortedByDescending { it.score }.map { it.key }, c.map { it.key })
    }

    /** A candidate's factors are exactly what the shared model computes there. */
    @Test
    fun candidateFactorsAreTheModelsOwnNumbers() {
        val s = scan()
        val c = s.candidates(GinsengSuitability.PRIOR_WEIGHTS).first()
        assertTrue(c.factors.contentEquals(s.factorsAt(c.lat, c.lng)!!))
    }

    /**
     * The old engine put its "hotspots" at the same offsets from wherever you stood. A real
     * scan's picks are properties of the ground: from a different centre, the places that
     * are still in range are the same places, not the same offsets.
     */
    @Test
    fun picksBelongToTheGroundNotToTheCaller() {
        val a = scan(radius = 7_000.0).candidates(GinsengSuitability.PRIOR_WEIGHTS)
        val b = scan(radius = 7_000.0, lat = centreLat + 0.01).candidates(GinsengSuitability.PRIOR_WEIGHTS)
        val offsetsA = a.map { (it.lat - centreLat) to (it.lng - centreLng) }.toSet()
        val offsetsB = b.map { (it.lat - centreLat - 0.01) to (it.lng - centreLng) }.toSet()
        assertTrue("same relative offsets from two different callers", offsetsA.intersect(offsetsB).isEmpty())
        val shared = a.map { it.key }.intersect(b.map { it.key }.toSet())
        assertTrue("the same ground should rank from both places", shared.isNotEmpty())
    }

    @Test
    fun excludedGroundIsNeverSuggested() {
        val s = scan()
        val c = s.candidates(GinsengSuitability.PRIOR_WEIGHTS, exclude = { lat, _ -> lat > centreLat })
        assertTrue(c.isNotEmpty())
        c.forEach { assertTrue(it.lat <= centreLat) }
    }

    @Test
    fun backgroundIsInsideTheRadiusAndDescribedLikeFinds() {
        val s = scan()
        val bg = s.background(200)
        assertEquals(200, bg.size)
        bg.forEach {
            assertTrue(s.inRadius(it.lat, it.lng))
            assertTrue(it.factors.all { v -> v in 0.0..1.0 })
        }
    }
}

class SuggestionAssemblyTest {

    private val mosaic = Fixtures.mosaic("scan_boone_z12.bin")
    private val scan = RadiusScan.of(mosaic, (mosaic.northLat + mosaic.southLat) / 2, (mosaic.westLon + mosaic.eastLon) / 2, 6_000.0)
    private val cands = scan.candidates(GinsengSuitability.PRIOR_WEIGHTS, count = 5)

    @Test
    fun everyPositionComesFromACandidate() {
        val reply = ResearchValidator.validate(
            ModelOutput("s", listOf(ModelItem("C3", "best", "Walk to 36.1000, -81.6000 instead.", "", emptyList()))),
            cands.indices.map(SuggestionAssembler::idFor).toSet(), emptyList(), ResearchValidator.Witness.EXACT,
        )
        val out = SuggestionAssembler.assemble("run", cands, reply, 0L)
        val candidatePositions = cands.map { it.lat to it.lng }.toSet()
        out.forEach { assertTrue(it.lat to it.lng in candidatePositions) }
        assertEquals("C3", out.first().label)
        assertEquals(Suggestion.PROVENANCE_MODEL, out.first().provenance)
        assertEquals(cands[2].lat, out.first().lat, 0.0)
    }

    @Test
    fun groundTheModelSkippedIsKeptAndMarkedComputed() {
        val reply = ResearchValidator.validate(
            ModelOutput("s", listOf(ModelItem("C2", "h", "r", "l", emptyList()))),
            cands.indices.map(SuggestionAssembler::idFor).toSet(), emptyList(), ResearchValidator.Witness.EXACT,
        )
        val out = SuggestionAssembler.assemble("run", cands, reply, 0L)
        assertEquals(cands.size, out.size)
        assertEquals(1, out.count { it.provenance == Suggestion.PROVENANCE_MODEL })
        assertEquals((1..cands.size).toList(), out.map { it.rank })
    }

    @Test
    fun offlineEverySuggestionIsComputedFromItsOwnNumbers() {
        val out = SuggestionAssembler.assemble("run", cands, null, 0L)
        assertTrue(out.all { it.provenance == Suggestion.PROVENANCE_COMPUTED })
        out.zip(cands).forEach { (s, c) -> assertTrue(s.rationale.contains("%.2f".format(c.score))) }
    }

    @Test
    fun sourcesRoundTrip() {
        val src = listOf(SourceRef("https://a.edu/x", "A \"quoted\" title"))
        assertEquals(src, SuggestionAssembler.sourcesFromJson(SuggestionAssembler.sourcesToJson(src)))
    }
}

/**
 * The privacy property, tested on the actual prompt text. A canary fix with distinctive
 * digits must not appear anywhere in what is sent, and neither may any candidate position.
 */
class PromptPrivacyTest {

    private val mosaic = Fixtures.mosaic("scan_boone_z12.bin")

    @Test
    fun theFixAndCandidatePositionsNeverReachThePrompt() {
        val lat = 36.187_654_3
        val lng = -81.673_219_7
        val scan = RadiusScan.of(mosaic, lat, lng, 6_000.0)
        val cands = scan.candidates(GinsengSuitability.PRIOR_WEIGHTS)
        val req = ResearchRepository.buildRequest(
            lat, lng, "North Carolina", "rules", "SEASON OPEN: x", LocalDate.of(2026, 9, 28),
            "- memory", cands,
        )
        val text = ResearchPrompt.SYSTEM + ResearchPrompt.user(req)
        for (canary in listOf("36.18", "81.67", "36.1876", "81.6732")) {
            assertFalse("prompt leaks $canary", text.contains(canary))
        }
        for (c in cands) {
            assertFalse(text.contains("%.3f".format(c.lat)))
            assertFalse(text.contains("%.3f".format(-c.lng)))
        }
        assertTrue(text.contains("about 36.15 N, 81.65 W"))
    }

    @Test
    fun coarseRegionIsTheCellCentreWhereverYouStandInIt() {
        assertEquals(SuggestionAssembler.coarseRegion(35.501, -82.999), SuggestionAssembler.coarseRegion(35.599, -82.901))
        assertEquals("about 35.55 N, 82.95 W", SuggestionAssembler.coarseRegion(35.52, -82.93))
    }

    @Test
    fun theToolSchemaOnlyAdmitsTheSentIds() {
        @Suppress("UNCHECKED_CAST")
        val items = ((ResearchPrompt.toolSchema(listOf("C1", "C2"))["properties"] as Map<String, Any>)
            ["suggestions"] as Map<String, Any>)["items"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val id = (items["properties"] as Map<String, Map<String, Any>>)["candidate_id"]!!
        assertEquals(listOf("C1", "C2"), id["enum"])
        assertEquals(false, items["additionalProperties"])
    }
}
