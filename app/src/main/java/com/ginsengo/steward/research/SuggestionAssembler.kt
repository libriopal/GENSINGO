package com.ginsengo.steward.research

import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.learn.FindVerifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.floor

/**
 * Turns computed candidates plus (optionally) a validated model reply into stored suggestions.
 *
 * The invariant this object exists to hold: EVERY position comes from a candidate. The model
 * reply contributes order and words; it is joined to candidates by ID and has no field that
 * could carry a location. Candidates the model did not pick are kept, after the picked ones,
 * with their on-device text and marked as computed, so the map never hides ground the
 * terrain rated just because a model left it out.
 */
object SuggestionAssembler {

    /** Prompt ID for the candidate at [index] in terrain order: C1, C2, ... */
    fun idFor(index: Int) = "C${index + 1}"

    fun assemble(
        runId: String,
        candidates: List<RadiusScan.Candidate>,
        reply: ResearchValidator.Result?,
        now: Long,
    ): List<Suggestion> {
        val byId = candidates.withIndex().associate { (i, c) -> idFor(i) to c }
        val out = ArrayList<Suggestion>()
        val used = HashSet<String>()

        reply?.items?.forEach { item ->
            val c = byId[item.candidateId] ?: return@forEach   // validator already dropped these
            if (!used.add(item.candidateId)) return@forEach
            out += build(runId, item.candidateId, c, out.size + 1, now,
                headline = item.headline.ifBlank { OnDeviceRationale.describe(c).headline },
                rationale = item.rationale,
                lookFor = item.lookFor.ifBlank { OnDeviceRationale.LOOK_FOR },
                sources = sourcesToJson(item.sources),
                provenance = Suggestion.PROVENANCE_MODEL)
        }
        candidates.forEachIndexed { i, c ->
            val id = idFor(i)
            if (id in used) return@forEachIndexed
            val t = OnDeviceRationale.describe(c)
            val note = if (reply != null) " Not picked by the research model." else ""
            out += build(runId, id, c, out.size + 1, now, t.headline, t.rationale + note, t.lookFor,
                "[]", Suggestion.PROVENANCE_COMPUTED)
        }
        return out
    }

    private fun build(
        runId: String, label: String, c: RadiusScan.Candidate, rank: Int, now: Long,
        headline: String, rationale: String, lookFor: String, sources: String, provenance: String,
    ) = Suggestion(
        id = "$runId:$label",
        runId = runId,
        candidateKey = c.key,
        label = label,
        lat = c.lat, lng = c.lng,
        rank = rank,
        terrainScore = c.score,
        factorsCsv = c.factors.joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) },
        elevationM = c.elevationM,
        slopeDeg = c.slopeDeg,
        aspectDeg = c.aspectDeg,
        headline = headline,
        rationale = rationale,
        lookFor = lookFor,
        sourcesJson = sources,
        provenance = provenance,
        createdAt = now,
    )

    fun sourcesToJson(sources: List<SourceRef>): String = buildJsonArray {
        sources.forEach { add(buildJsonObject { put("url", it.url); put("title", it.title) }) }
    }.toString()

    fun sourcesFromJson(json: String): List<SourceRef> = runCatching {
        (Json.parseToJsonElement(json) as JsonArray).mapNotNull { el ->
            val o = el.jsonObject
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            SourceRef(url, o["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }.getOrDefault(emptyList())

    /** The 0.1-degree cell centre: all the location the prompt ever carries. */
    fun coarseRegion(lat: Double, lng: Double): String {
        fun cell(v: Double) = floor(v * 10.0) / 10.0 + 0.05
        val la = cell(lat); val lo = cell(lng)
        return "about %.2f %s, %.2f %s".format(
            java.util.Locale.ROOT,
            kotlin.math.abs(la), if (la >= 0) "N" else "S",
            kotlin.math.abs(lo), if (lo >= 0) "E" else "W",
        )
    }
}

/**
 * What the prompt is told about the user's history: counts and averages only.
 * No coordinate of any find or suggestion appears in the returned text.
 */
object MemorySummary {

    fun describe(
        pastSuggestions: List<Suggestion>,
        findsInRadius: List<Find>,
        backgroundMean: DoubleArray?,
        verdict: FindLearner.Verdict?,
    ): String = buildString {
        val visited = pastSuggestions.count { it.status != Suggestion.STATUS_NEW }
        val found = pastSuggestions.count { it.status == Suggestion.STATUS_FOUND }
        val none = pastSuggestions.count { it.status == Suggestion.STATUS_NOT_FOUND }
        if (pastSuggestions.isEmpty()) {
            appendLine("- No earlier suggestions in this area.")
        } else {
            appendLine("- Earlier suggestions in this area: ${pastSuggestions.size}; walked to $visited; ginseng found at $found; walked with none found at $none.")
        }
        val verified = findsInRadius.filter { it.verification == FindVerifier.Level.VERIFIED.name }
        appendLine("- Verified finds in this area: ${verified.size} (plus ${findsInRadius.size - verified.size} unverified or imported).")
        val withF = verified.mapNotNull { it.factors() }
        if (withF.isNotEmpty() && backgroundMean != null) {
            val mean = DoubleArray(6) { i -> withF.sumOf { it[i] } / withF.size }
            appendLine("- Average terrain factors at verified finds (heat position wetness steepness cove elevation): " +
                    mean.joinToString(" ") { "%.2f".format(java.util.Locale.ROOT, it) })
            appendLine("- Average across the area for comparison: " +
                    backgroundMean.joinToString(" ") { "%.2f".format(java.util.Locale.ROOT, it) })
        }
        verdict?.let { append("- Learning: ").append(it.reason) }
    }.trim()
}
