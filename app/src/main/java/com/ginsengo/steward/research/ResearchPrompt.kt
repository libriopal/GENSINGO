package com.ginsengo.steward.research

import com.ginsengo.steward.terrain.GinsengSuitability

/**
 * The prompt, built only from what the device computed and what the app's sourced data says.
 *
 * WHAT IS DELIBERATELY ABSENT. The fix, the finds, and every candidate's coordinates. The
 * model gets a 0.1-degree cell (about 11 km) so it can reason about region, state rules and
 * geology, which is what "research-based" needs, and nothing that locates a patch. See
 * IDEA_CANDIDATES.md Step 4 for the first version of this rule ("send no location at all")
 * and why it broke.
 *
 * The system prompt is constant, so it is the cacheable prefix; everything that varies
 * (date, region, candidates) is in the user turn.
 */
object ResearchPrompt {

    const val TOOL_NAME = "submit_suggestions"

    val SYSTEM = """
You help a wild American ginseng (Panax quinquefolius) steward decide which of several places to walk first. The places were computed on the steward's phone from elevation data. You did not choose them, and you must not add, move, merge or invent a place. Refer to places only by their IDs.

Each candidate comes with measurements and six terrain factor scores between 0 and 1 from a published model (McCune & Keon heat load, Weiss topographic position, Beven & Kirkby wetness with multiple-flow routing):
- heat: 1 = coolest aspect (north to east facing), 0 = hot south-west slope
- position: 1 = lower-slope bench, lower on a slope but above the valley floor
- wetness: 1 = moist but well drained; low means dry ground, or waterlogged ground at the wet end
- steepness: 1 = within 6-20 degrees
- cove: 1 = concave cove or hollow form
- elevation: 1 = inside the broad Appalachian band

What to do:
1. Use web search to find current, reputable sources (university extension, state agencies, peer-reviewed work) on ginseng habitat relevant to this region and these terrain conditions.
2. Choose the candidates worth walking and order them best first. You may leave any out.
3. For each, give a short headline, a rationale that ties its numbers to what the sources say, and what to look for on foot to confirm the site (for example calcium-indicator plants).
4. In source_urls, list only URLs that your web searches returned in this conversation. Do not cite anything you did not retrieve here.

Rules:
- Never state or imply that digging or harvesting is legal or allowed anywhere. You do not know who owns the land or what permission the steward has.
- Never write coordinates.
- Quote harvest dates only from the state rules given; do not supply your own.
- If a candidate's numbers do not support it, say so plainly or leave it out.
- The terrain model cannot see soil calcium, canopy or recent logging. Say what the steward must check in person.
- Finish by calling the $TOOL_NAME tool exactly once.
""".trim()

    fun user(r: ResearchRequest): String = buildString {
        appendLine("Date: ${r.dateIso}")
        appendLine("Region: ${r.coarseRegion}${r.stateName?.let { ", $it" } ?: ""}. This is the centre of a roughly 11 km cell; the candidates lie within 16 km of the steward.")
        r.seasonLine?.let { appendLine("Season status (from the app's sourced data): $it") }
        r.stateRules?.let {
            appendLine()
            appendLine("State rules, verbatim from the app's sourced data:")
            appendLine(it)
        }
        appendLine()
        appendLine("What the steward's own records say:")
        appendLine(r.memory)
        appendLine()
        appendLine("Candidates (computed on the phone, best terrain score first):")
        appendLine("id | distance | elevation m | slope deg | faces | terrain score | heat position wetness steepness cove elevation")
        for (c in r.candidates) {
            appendLine(
                "${c.id} | ${c.distanceBand} | ${c.elevationM} | ${c.slopeDeg} | ${c.aspect} | " +
                        "%.2f | ".format(c.terrainScore) +
                        c.factors.joinToString(" ") { "%.2f".format(it) }
            )
        }
    }

    /** JSON schema for the submission tool. The candidate ID is an enum of the IDs sent. */
    fun toolSchema(ids: List<String>): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("summary", "suggestions"),
        "properties" to mapOf(
            "summary" to mapOf(
                "type" to "string",
                "description" to "Two or three sentences of regional context for these candidates.",
            ),
            "suggestions" to mapOf(
                "type" to "array",
                "description" to "Candidates worth walking, best first.",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("candidate_id", "headline", "rationale", "look_for", "source_urls"),
                    "properties" to mapOf(
                        "candidate_id" to mapOf("type" to "string", "enum" to ids),
                        "headline" to mapOf("type" to "string"),
                        "rationale" to mapOf("type" to "string"),
                        "look_for" to mapOf("type" to "string"),
                        "source_urls" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    ),
                ),
            ),
        ),
    )

    const val TOOL_DESCRIPTION =
        "Submit the ranked suggestions. Call exactly once, after researching. Only candidate IDs " +
                "from the list; only URLs your searches returned in this conversation."

    /** Compass octant for an aspect in degrees. */
    fun octant(deg: Double): String {
        val names = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        return names[(((deg % 360 + 360) % 360 + 22.5) / 45.0).toInt() % 8]
    }

    val FACTOR_NAMES: List<String> = GinsengSuitability.Factor.entries.map { it.display }
}
