package com.ginsengo.steward.research

/** Which research model to call. The user supplies the key; nothing is baked into the APK. */
enum class Provider(val label: String, val defaultModel: String) {
    CLAUDE("Claude", "claude-opus-5-5"),
    GEMINI("Gemini", "gemini-2.5-flash"),
}

/** A page the model's own search tool retrieved during this call. */
data class SourceRef(val url: String, val title: String)

/** What the model submitted, before validation. Positions are NOT part of this type. */
data class ModelItem(
    val candidateId: String,
    val headline: String,
    val rationale: String,
    val lookFor: String,
    val sourceUrls: List<String>,
)

data class ModelOutput(val summary: String, val items: List<ModelItem>)

/**
 * The raw result of one provider call: what the model said, and what its search tool
 * actually retrieved. The two are kept apart so the validator can check one against the other.
 */
data class ProviderReply(
    val output: ModelOutput,
    val retrieved: List<SourceRef>,
    val model: String,
)

/** A provider call that did not produce a usable reply. Shown as-is; never as "no results". */
class ResearchFailure(val status: Status, message: String) : Exception(message) {
    enum class Status { OFFLINE, NO_KEY, NO_CONSENT, AUTH, RATE_LIMITED, REFUSED, TRUNCATED, NO_SUBMISSION, BAD_REPLY, ERROR }
}

/** What the prompt is built from. Holds no coordinates at all. */
data class ResearchRequest(
    /** "about 35.6 N, 83.0 W" style: the 0.1-degree cell centre, never the fix. */
    val coarseRegion: String,
    val stateName: String?,
    val stateRules: String?,
    val seasonLine: String?,
    val dateIso: String,
    val memory: String,
    val candidates: List<PromptCandidate>,
)

data class PromptCandidate(
    val id: String,
    val distanceBand: String,
    val elevationM: Int,
    val slopeDeg: Int,
    val aspect: String,
    val terrainScore: Double,
    val factors: DoubleArray,
    /** Coarse: "creek 250 m NW, 40 m below" (50 m and 10 m steps), never a position. */
    val nearestCreek: String = "none within 2 km",
)
