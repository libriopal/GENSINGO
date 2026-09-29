package com.ginsengo.steward.research

import com.ginsengo.steward.SettingsStore
import com.ginsengo.steward.compliance.ComplianceEngine
import com.ginsengo.steward.data.db.AppDatabase
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.ResearchRun
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/**
 * compute -> learn -> rank -> (optionally) research -> validate -> remember.
 *
 * Every step before "research" runs on the phone from cached elevation, so a run always
 * produces suggestions, with or without a network. The research model is an annotation layer
 * over computed places, never their source.
 */
class ResearchRepository(
    private val db: AppDatabase,
    private val dem: DemTileStore,
    private val compliance: ComplianceEngine,
    private val settings: SettingsStore,
    private val keys: KeyVault,
    private val isOnline: () -> Boolean,
    /** Elevation for a scan's bounds (N, W, S, E). Tests supply a fixture; the app streams tiles. */
    /** Search radius. Ten statute miles; tests use a smaller one to fit their fixture. */
    private val radiusM: Double = RadiusScan.RADIUS_M,
    private val mosaicFor: suspend (DoubleArray) -> DemTileStore.Mosaic? = { b ->
        dem.grid(b[0], b[1], b[2], b[3], RadiusScan.SCAN_ZOOM, haloTiles = 0)
    },
    private val clientFor: (Provider, String, String) -> ResearchClient = { p, key, model ->
        when (p) {
            Provider.CLAUDE -> ClaudeResearchClient(key, model)
            Provider.GEMINI -> GeminiResearchClient(key, model)
        }
    },
) {
    private val mutex = Mutex()

    @Volatile private var scan: RadiusScan? = null

    private val _verdict = MutableStateFlow<FindLearner.Verdict?>(null)
    /** The learner's latest decision, for the Layers sheet and the learned heatmap. */
    val verdict: StateFlow<FindLearner.Verdict?> = _verdict.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    /** Weights the heatmap should draw with right now. */
    val activeWeights: DoubleArray
        get() = _verdict.value?.active ?: GinsengSuitability.PRIOR_WEIGHTS

    /**
     * The terrain scan around a point, reused while the user stays within [RESCAN_M] of the
     * centre it was computed for. Rescanning costs a few seconds of CPU; walking a hillside
     * should never trigger it.
     */
    suspend fun scanAround(lat: Double, lng: Double): RadiusScan? {
        scan?.let {
            if (Prospects.distanceMetres(it.centerLat, it.centerLng, lat, lng) < RESCAN_M) return it
        }
        _busy.value = "Reading terrain across 10 miles…"
        try {
            val mosaic = mosaicFor(RadiusScan.bounds(lat, lng, radiusM)) ?: return null
            val s = withContext(Dispatchers.Default) { RadiusScan.of(mosaic, lat, lng, radiusM) }
            scan = s
            return s
        } finally {
            _busy.value = null
        }
    }

    /**
     * Describes every find inside the scan with the scan's own factors (stored, so they
     * survive offline), then lets the learner decide whether its weights have earned a place.
     */
    suspend fun learn(s: RadiusScan): FindLearner.Verdict = withContext(Dispatchers.Default) {
        val all = db.findDao().all()
        val inRadius = all.filter { s.inRadius(it.lat, it.lng) }
        val updated = ArrayList<Find>()
        val samples = ArrayList<FindLearner.Sample>()
        for (f in inRadius) {
            val factors = s.factorsAt(f.lat, f.lng) ?: continue
            if (f.featureZoom != s.zoom || f.factors()?.contentEquals(factors) != true) {
                updated += f.withFactors(factors, s.zoom)
            }
            // Every find is the user's word (UserFinds): all of them teach the learner.
            samples += FindLearner.Sample(factors, f.lat, f.lng)
        }
        if (updated.isNotEmpty()) db.findDao().updateAll(updated)
        FindLearner.evaluate(
            samples, s.background(), GinsengSuitability.PRIOR_WEIGHTS,
            linkM = s.correlationRangeM(),
        ).also { _verdict.value = it }
    }

    /**
     * One full run. Never throws; every failure becomes a stored run with a status.
     *
     * @param allowModel false for automatic refreshes the auto-research policy has not
     *   approved: the on-device ranking is refreshed and nothing is sent anywhere.
     */
    suspend fun run(lat: Double, lng: Double, allowModel: Boolean = true): ResearchRun = mutex.withLock {
        val started = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        val s = scanAround(lat, lng)
        if (s == null) {
            return@withLock store(ResearchRun(
                id = runId, centerLat = lat, centerLng = lng, radiusM = radiusM,
                provider = null, model = null, status = "NO_TERRAIN",
                message = "No elevation data for this area yet. Connect once, or save the area for offline use.",
                summary = null, candidatesComputed = 0, idsRejected = 0, citationsKept = 0,
                citationsRejected = 0, weights = "PRIOR", durationMs = System.currentTimeMillis() - started,
            ))
        }

        val verdict = learn(s)
        var excluded = 0
        _busy.value = "Ranking places…"
        val candidates = withContext(Dispatchers.Default) {
            // Recommending a place is telling someone to go there, so ground inside a
            // PROHIBITED area (national park and similar) is left out, and counted. The
            // compliance engine only WARNS on these polygons because they over-cover; ranking
            // them last was the rejected alternative, because last is still a recommendation.
            s.candidates(verdict.active, exclude = { la, lo ->
                compliance.landStatusAt(la, lo).any { it.rule == "PROHIBITED" }.also { if (it) excluded++ }
            })
        }
        _busy.value = null

        val status = compliance.statusAt(lat, lng)
        val state = status.state
        val provider = settings.provider
        val model = settings.model(provider)
        val key = keys.get(provider)

        var validated: ResearchValidator.Result? = null
        var runStatus = "OK"
        var message: String? = null
        var usedModel: String? = null

        val skip = when {
            candidates.isEmpty() -> "No candidate ground inside the radius."
            !settings.researchConsent -> "Research model is off. These are computed on your phone."
            key.isNullOrBlank() -> "No ${provider.label} API key set. These are computed on your phone."
            !isOnline() -> "Offline. These are computed on your phone; ask again with signal."
            !allowModel -> "Updated on this phone for where you are now. Tap Refresh to ask ${provider.label}."
            else -> null
        }
        if (skip != null) {
            runStatus = when {
                candidates.isEmpty() -> "NO_CANDIDATES"
                !settings.researchConsent -> "NO_CONSENT"
                key.isNullOrBlank() -> "NO_KEY"
                !isOnline() -> "OFFLINE"
                else -> "LOCAL_REFRESH"
            }
            message = skip
        } else {
            _busy.value = "Asking ${provider.label}…"
            try {
                val past = db.suggestionDao().all().filter { s.inRadius(it.lat, it.lng) }
                val findsIn = db.findDao().all().filter { s.inRadius(it.lat, it.lng) }
                val bg = s.background()
                val bgMean = DoubleArray(6) { i -> bg.sumOf { it.factors[i] } / bg.size.coerceAtLeast(1) }
                val request = buildRequest(
                    lat = lat, lng = lng,
                    stateName = state?.stateName,
                    stateRules = state?.harvestNotes,
                    seasonLine = "${status.season.label}: ${status.seasonDetail}",
                    today = LocalDate.now(),
                    memory = MemorySummary.describe(past, findsIn, bgMean, verdict),
                    candidates = candidates,
                )
                val reply = clientFor(provider, key!!, model).research(request)
                usedModel = reply.model
                validated = ResearchValidator.validate(
                    reply.output,
                    candidates.indices.map(SuggestionAssembler::idFor).toSet(),
                    reply.retrieved,
                    if (provider == Provider.CLAUDE) ResearchValidator.Witness.EXACT else ResearchValidator.Witness.DOMAIN,
                )
            } catch (e: ResearchFailure) {
                runStatus = e.status.name
                message = e.message + ". Showing the on-device ranking."
            } catch (e: Exception) {
                runStatus = "ERROR"
                message = "Research failed (${e.javaClass.simpleName}). Showing the on-device ranking."
            } finally {
                _busy.value = null
            }
        }
        if (excluded > 0) {
            message = listOfNotNull(message, "$excluded place(s) inside protected land were left out.").joinToString(" ")
        }

        val now = System.currentTimeMillis()
        db.suggestionDao().insertAll(SuggestionAssembler.assemble(runId, candidates, validated, now))
        store(ResearchRun(
            id = runId, time = now, centerLat = lat, centerLng = lng, radiusM = s.radiusM,
            provider = if (validated != null) provider.name else null,
            model = usedModel,
            status = runStatus,
            message = message,
            summary = validated?.summary,
            candidatesComputed = candidates.size,
            idsRejected = (validated?.idsRejected ?: 0) + (validated?.duplicatesDropped ?: 0),
            citationsKept = validated?.citationsKept ?: 0,
            citationsRejected = validated?.citationsRejected ?: 0,
            weights = if (verdict.adopted) "LEARNED" else "PRIOR",
            durationMs = now - started,
        ))
    }

    private suspend fun store(run: ResearchRun): ResearchRun {
        db.researchRunDao().insert(run)
        return run
    }

    /**
     * Whether an automatic run may start: never without consent and a key, never below the
     * power policy's battery floor, and only after real movement and time since the last
     * model-backed run, so walking a hillside does not bill the user's key.
     */
    suspend fun shouldAutoRun(lat: Double, lng: Double, powerAllows: Boolean, now: Long): Boolean {
        if (!settings.autoResearch || !settings.researchConsent || !powerAllows) return false
        if (!keys.has(settings.provider) || !isOnline()) return false
        val last = db.researchRunDao().latestModelRun() ?: return true
        val moved = Prospects.distanceMetres(last.centerLat, last.centerLng, lat, lng)
        return moved >= AUTO_MOVE_M && now - last.time >= AUTO_MIN_INTERVAL_MS
    }

    companion object {
        /**
         * The request, from the exact fix. Pure, so the privacy property is tested directly:
         * the fix is reduced to its 0.1-degree cell here, and candidates are reduced to terrain
         * numbers and a distance band. Nothing else about position leaves this function.
         */
        fun buildRequest(
            lat: Double, lng: Double,
            stateName: String?, stateRules: String?, seasonLine: String?,
            today: LocalDate, memory: String,
            candidates: List<RadiusScan.Candidate>,
        ) = ResearchRequest(
            coarseRegion = SuggestionAssembler.coarseRegion(lat, lng),
            stateName = stateName,
            stateRules = stateRules?.takeIf { it.isNotBlank() },
            seasonLine = seasonLine,
            dateIso = today.toString(),
            memory = memory,
            candidates = candidates.mapIndexed { i, c ->
                PromptCandidate(
                    id = SuggestionAssembler.idFor(i),
                    distanceBand = RadiusScan.distanceBand(c.distanceM),
                    elevationM = (c.elevationM / 10).roundToInt() * 10,
                    slopeDeg = c.slopeDeg.roundToInt(),
                    aspect = ResearchPrompt.octant(c.aspectDeg),
                    terrainScore = c.score,
                    factors = c.factors,
                )
            },
        )

        const val RESCAN_M = 3_000.0
        const val AUTO_MOVE_M = 5_000.0
        const val AUTO_MIN_INTERVAL_MS = 30 * 60_000L
    }
}
