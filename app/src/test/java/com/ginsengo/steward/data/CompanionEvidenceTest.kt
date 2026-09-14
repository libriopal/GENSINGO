package com.ginsengo.steward.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The companion-plant list is folklore until something tests it, and something did.
 *
 * Turner & McGraw (2015), *Ecological Indicators* 57:110-117, took 20 of the species that land
 * managers and growers recommend as ginseng "indicators" and checked them against 26 real
 * ginseng populations, measuring relative growth rate of leaf area. The result is unkind to the
 * folklore:
 *
 *  - **Exactly one** species predicted BETTER performance: tulip poplar, within about 10 m.
 *  - Most putative indicators were **unreliable** as a site-quality measure.
 *  - **Four were CONTRA-indicators** - their presence predicted WORSE ginseng performance:
 *    wild sarsaparilla, red maple, black birch, and spicebush.
 *
 * This app shipped spicebush as a "strong" indicator with provenance "verified_reference". That
 * is the EINCOL §3 two-crosses row exactly: a claim pinned by neither time nor witness, and
 * carrying a label asserting it had been. It is now corrected, and this test exists so it
 * cannot quietly revert.
 *
 * The file is read from the source tree rather than through Android's asset manager, because
 * these tests run on a plain JVM.
 */
class CompanionEvidenceTest {

    private fun plants(): List<JsonObject> {
        val f = File("src/main/assets/data/companion_plants.json")
        assertTrue("companion_plants.json not found at ${f.absolutePath}", f.exists())
        val root = Json.parseToJsonElement(f.readText())
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root.values.first().jsonArray
            else -> error("unexpected JSON shape")
        }
        return arr.map { it.jsonObject }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content ?: ""

    private fun bySci(name: String) = plants().firstOrNull { it.str("scientific_name") == name }

    @Test
    fun spicebushIsLabelledAContraIndicatorNotAStrongOne() {
        val p = bySci("Lindera benzoin")
        assertNotNull("spicebush is missing from the list entirely", p)
        assertEquals(
            "Turner & McGraw found Lindera benzoin a CONTRA-indicator; the app must not " +
            "present it as a sign of good ginseng ground",
            "contra", p!!.str("indicator_strength"),
        )
        assertTrue(
            "a corrected claim must carry the citation that corrected it",
            p.str("evidence").contains("10.1016/j.ecolind.2015.04.010"),
        )
    }

    @Test
    fun theOneValidatedPositiveIndicatorIsActuallyShipped() {
        val p = bySci("Liriodendron tulipifera")
        assertNotNull(
            "tulip poplar is the only species Turner & McGraw found predicted BETTER ginseng " +
            "growth, and it was absent from this app's companion list",
            p,
        )
        assertEquals("strong", p!!.str("indicator_strength"))
        assertTrue(p.str("evidence").contains("10.1016/j.ecolind.2015.04.010"))
    }

    /**
     * No species may claim to be a verified reference without naming what verified it. This is
     * the general form of the defect: the label "verified" was doing work that no source
     * supported.
     */
    @Test
    fun nothingClaimsVerifiedProvenanceWithoutEvidenceBehindIt() {
        val offenders = plants()
            .filter { it.str("provenance") == "verified_reference" }
            .filter { it.str("evidence").isBlank() }
            .map { it.str("name") }
        assertTrue(
            "these claim verified provenance with no evidence field: $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * Negative control. If the loader silently returned an empty list, or if `str()` returned
     * "" for everything, every assertion above would pass vacuously. Prove the file is real and
     * the field reader actually reads fields.
     */
    @Test
    fun theListIsRealAndTheFieldReaderReadsFields() {
        val all = plants()
        assertTrue("expected a populated companion list, got ${all.size}", all.size >= 12)
        assertTrue(
            "every entry must have a scientific name; the reader may be returning blanks",
            all.all { it.str("scientific_name").isNotBlank() },
        )
        // And a species the study did NOT reclassify must still read as it always did.
        val cohosh = bySci("Caulophyllum thalictroides")
        assertNotNull(cohosh)
        assertFalse(
            "blue cohosh was not among the contra-indicators and must not have been swept up",
            cohosh!!.str("indicator_strength") == "contra",
        )
    }

    /** Exactly one species should carry the strong+evidence combination, and it is the tree. */
    @Test
    fun onlyTheTestedSpeciesCarriesStrongWithEvidence() {
        val strongWithEvidence = plants()
            .filter { it.str("indicator_strength") == "strong" && it.str("evidence").isNotBlank() }
            .map { it.str("scientific_name") }
        assertEquals(listOf("Liriodendron tulipifera"), strongWithEvidence)
    }
}
