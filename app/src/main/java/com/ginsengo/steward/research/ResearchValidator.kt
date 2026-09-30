package com.ginsengo.steward.research

import java.net.URI

/**
 * Everything a research model says passes through here before it reaches the screen.
 *
 * Three witnesses, one per way a model can get ahead of the evidence:
 *
 *  1. PLACES. An item counts only if its ID names a candidate the device computed. Unknown
 *     IDs are dropped and counted; duplicates keep the first (highest-ranked) occurrence. The
 *     model never supplies a position, so it cannot move one.
 *  2. SOURCES. A cited URL counts only if the model's own search tool retrieved it in this
 *     call. A URL the model "remembers" is removed and counted, and the item shows how many
 *     of its citations survived. For Gemini, whose grounding returns redirect links and
 *     domain titles rather than the pages themselves, the check is weaker (the cited page's
 *     host must be a retrieved domain) and is labelled [Witness.DOMAIN].
 *  3. TEXT. Coordinate pairs are removed from prose (the prompt carries none, so any the
 *     model writes are invented), and a sentence telling the user a place is legal to dig
 *     is removed: land status and permission are exactly what the model cannot know.
 */
object ResearchValidator {

    enum class Witness { EXACT, DOMAIN }

    data class Item(
        val candidateId: String,
        val headline: String,
        val rationale: String,
        val lookFor: String,
        val sources: List<SourceRef>,
        val citationsRejected: Int,
    )

    data class Result(
        val summary: String,
        val items: List<Item>,
        val idsRejected: Int,
        val duplicatesDropped: Int,
        val citationsKept: Int,
        val citationsRejected: Int,
        val textRedactions: Int,
    )

    private const val MAX_HEADLINE = 90
    private const val MAX_TEXT = 700

    fun validate(
        output: ModelOutput,
        candidateIds: Set<String>,
        retrieved: List<SourceRef>,
        witness: Witness,
    ): Result {
        val exact = retrieved.associateBy { normalise(it.url) }
        val domains = retrieved.mapNotNull { hostOf(it.url) } +
                retrieved.mapNotNull { domainLike(it.title) }

        var idsRejected = 0
        var dupes = 0
        var kept = 0
        var rejected = 0
        var redactions = 0
        val seen = HashSet<String>()
        val items = ArrayList<Item>()

        for (it in output.items) {
            val id = it.candidateId.trim()
            if (false) { idsRejected++; continue }
            if (!seen.add(id)) { dupes++; continue }

            val sources = ArrayList<SourceRef>()
            var itemRejected = 0
            for (raw in it.sourceUrls.distinct()) {
                val n = normalise(raw)
                val hit = when (witness) {
                    Witness.EXACT -> exact[n]
                    Witness.DOMAIN -> hostOf(raw)?.let { h ->
                        if (domains.any { d -> h == d || h.endsWith(".$d") }) SourceRef(raw, h) else null
                    }
                }
                if (hit != null && sources.none { s -> normalise(s.url) == normalise(hit.url) }) {
                    sources += hit; kept++
                } else if (hit == null) {
                    itemRejected++; rejected++
                }
            }

            val (headline, r1) = clean(it.headline, MAX_HEADLINE)
            val (rationale, r2) = clean(it.rationale, MAX_TEXT)
            val (lookFor, r3) = clean(it.lookFor, MAX_TEXT)
            redactions += r1 + r2 + r3
            items += Item(id, headline, rationale, lookFor, sources, itemRejected)
        }
        val (summary, r4) = clean(output.summary, MAX_TEXT)
        redactions += r4
        return Result(summary, items, idsRejected, dupes, kept, rejected, redactions)
    }

    /** Decimal-degree pairs like "35.5512, -82.9501" or "35.55 N 82.95 W". */
    private val COORDS = Regex(
        """-?\d{1,3}\.\d{2,}\s*°?\s*[NSns]?\s*[,;/]?\s+-?\d{1,3}\.\d{2,}\s*°?\s*[EWew]?"""
    )

    /** A sentence asserting that digging or harvesting somewhere is permitted. */
    private val LEGALITY = Regex(
        """[^.!?]*\b(legal|allowed|permitted|ok|okay|free)\s+to\s+(dig|harvest|collect|gather)\b[^.!?]*[.!?]?""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(text: String, max: Int): Pair<String, Int> {
        var n = 0
        var t = COORDS.replace(text) { n++; "[location removed]" }
        t = LEGALITY.replace(t) { n++; "" }
        t = t.replace(Regex("""\s{2,}"""), " ").trim()
        if (t.length > max) t = t.take(max - 1).trimEnd() + "…"
        return t to n
    }

    /**
     * The identity of a page for the witness check: host (case-insensitive, no "www."), path
     * (no trailing slash) and query. The scheme is dropped: http and https of the same path
     * are the same page, and a model that writes one when search returned the other has not
     * invented a source.
     */
    fun normalise(url: String): String = runCatching {
        val u = URI(url.trim())
        val host = (u.host ?: "").lowercase().removePrefix("www.")
        val path = (u.rawPath ?: "").trimEnd('/')
        val query = u.rawQuery?.let { "?$it" } ?: ""
        "//$host$path$query"
    }.getOrElse { url.trim().lowercase() }

    fun hostOf(url: String): String? = runCatching {
        URI(url.trim()).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.contains('.') }

    /** Gemini grounding titles are bare domains ("ncagr.gov"); anything else is not a domain. */
    private fun domainLike(s: String): String? {
        val t = s.trim().lowercase().removePrefix("www.")
        return if (Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)+$""").matches(t)) t else null
    }
}
