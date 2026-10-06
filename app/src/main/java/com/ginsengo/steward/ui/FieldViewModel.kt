package com.ginsengo.steward.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginsengo.steward.AppContainer
import com.ginsengo.steward.GensingoApp
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.ResearchRun
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.data.db.TrackPoint
import com.ginsengo.steward.field.BatteryMode
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.field.FixAverager
import com.ginsengo.steward.field.PowerPolicy
import com.ginsengo.steward.field.TrackService
import com.ginsengo.steward.field.TravelMemory
import com.ginsengo.steward.prospect.Prospects
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.research.Provider
import com.ginsengo.steward.research.ResearchTrigger
import com.ginsengo.steward.terrain3d.MeshSession
import com.ginsengo.steward.terrain3d.CameraState
import com.ginsengo.steward.terrain3d.SharedCamera
import com.ginsengo.steward.ui.map.CameraStart
import com.ginsengo.steward.ui.map.MapLayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The one screen's state: where you are, where you've been, what you found, what to try next. */
@OptIn(ExperimentalCoroutinesApi::class)
class FieldViewModel(app: Application) : AndroidViewModel(app) {

    val container: AppContainer = (app as GensingoApp).container

    private val _location = MutableStateFlow<FieldLocation?>(null)
    val location: StateFlow<FieldLocation?> = _location.asStateFlow()

    private val _permission = MutableStateFlow(container.location.hasPermission())
    val permission: StateFlow<Boolean> = _permission.asStateFlow()

    val tracking: StateFlow<TrackService.State> = TrackService.state

    // ------------------------------------------------------------ where you've been
    /**
     * History is read once; points recorded after launch stream in from the live query. A
     * single all-time query re-run on every insert would re-read the whole history each batch,
     * which is exactly the battery cost the track batching exists to avoid.
     */
    private val sessionStart = System.currentTimeMillis()
    private val history = MutableStateFlow<List<TrackPoint>>(emptyList())
    val track: StateFlow<List<TrackPoint>> =
        combine(history, container.memory.trackSince(sessionStart)) { old, new -> old + new }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val finds: StateFlow<List<Find>> = container.memory.finds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------ research
    val latestRun: StateFlow<ResearchRun?> = container.database.researchRunDao().observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val suggestions: StateFlow<List<Suggestion>> = latestRun
        .flatMapLatest { run -> run?.let { container.database.suggestionDao().observeRun(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val verdict: StateFlow<FindLearner.Verdict?> = container.research.verdict
    val busy: StateFlow<String?> = container.research.busy

    // ------------------------------------------------------------ view state
    private val _layers = MutableStateFlow(
        container.settings.let { st ->
            MapLayerState(
                basemap = runCatching { com.ginsengo.steward.ui.map.Basemap.valueOf(st.mapStyle) }.getOrDefault(com.ginsengo.steward.ui.map.Basemap.DARK),
                relief = st.relief.takeIf { it in com.ginsengo.steward.ui.map.RELIEF_CHOICES } ?: 1.5f,
                legend = st.legend,
            )
        }
    )
    val layers: StateFlow<MapLayerState> = _layers.asStateFlow()
    fun setLayers(s: MapLayerState) {
        _layers.value = s
        container.settings.mapStyle = s.basemap.name
        container.settings.relief = s.relief
        container.settings.legend = s.legend
    }

    /**
     * The one camera (exe.md A1): the 3D map mirrors it under [SharedCamera]'s epoch rule. Seeded
     * where [CameraStart] says the map opens, then moved only here (app actions) or by the map under
     * the user's finger (gestures).
     */
    val camera = SharedCamera(
        CameraStart.initial(null, null).let { CameraState(it.lat, it.lon, it.zoom, 0.0, CameraStart.START_TILT) }
    )
    /**
     * The map's built square (A16): it survives a rotation, which recreates the views but not this
     * ViewModel. Since the 3D view is the only map (owner directive, wave M.1) it lives as long as
     * the screen does.
     */
    val meshSession = MeshSession(container.memoryBudget)

    override fun onCleared() {
        meshSession.end()
        super.onCleared()
    }

    /** How far the first-fix landing has got (CameraStart.landing, I18). */
    private var landed = CameraStart.Landing.NONE

    /** "Centre on me". */
    fun recenter() {
        val me = _location.value ?: return
        camera.move(camera.camera.copy(lat = me.lat, lng = me.lng), animate = true)
    }

    // ------------------------------------------------------------ search (P.1)
    private val _searchPin = MutableStateFlow<com.ginsengo.steward.ui.map.PlaceSearch.Place?>(null)
    /** The searched place, pinned on the map until cleared. */
    val searchPin: StateFlow<com.ginsengo.steward.ui.map.PlaceSearch.Place?> = _searchPin.asStateFlow()
    private val _searchResults = MutableStateFlow<com.ginsengo.steward.ui.map.PlaceSearch.Result?>(null)
    val searchResults: StateFlow<com.ginsengo.steward.ui.map.PlaceSearch.Result?> = _searchResults.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** Runs a search; one result is gone to at once, several are listed. */
    fun search(query: String) {
        if (query.isBlank() || _searching.value) return
        viewModelScope.launch {
            _searching.value = true
            val r = com.ginsengo.steward.ui.map.PlaceSearch.search(getApplication(), query)
            _searching.value = false
            if (r.places.size == 1 && r.error == null) goTo(r.places.single()) else _searchResults.value = r
        }
    }

    /** Flies the map to a place and pins it (the map builds the square there). */
    fun goTo(p: com.ginsengo.steward.ui.map.PlaceSearch.Place) {
        _placeChosen.value = true
        _searchPin.value = p
        _searchResults.value = null
        camera.move(camera.camera.copy(lat = p.lat, lng = p.lng, zoom = CameraStart.FOCUS_ZOOM, pitch = CameraStart.FOCUS_TILT))
    }

    fun clearSearch() { _searchPin.value = null; _searchResults.value = null }

    // ------------------------------------------------------------ field chat (F.1)
    val chat: StateFlow<com.ginsengo.steward.research.ChatState> = container.chat.state
    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()
    fun chatBlocker(): String? = container.chat.blocker()
    fun forgetNote(note: String) = container.chat.forgetNote(note)
    fun clearChat() = container.chat.clearConversation()

    fun sendChat(text: String) {
        if (text.isBlank() || _chatBusy.value) return
        viewModelScope.launch {
            _chatBusy.value = true
            _chatError.value = null
            _chatError.value = container.chat.send(text, chatContext())
            _chatBusy.value = false
        }
    }

    /**
     * What the chat model is told about the field with each message: the ~11 km cell (never a
     * coordinate), the date and season status, the ranked places, saved places and the walking
     * target as distance and direction from the owner, and counts of finds and walked ground.
     */
    private fun chatContext(): String = buildString {
        val today = java.time.LocalDate.now()
        appendLine("Date: $today (${today.month.name.lowercase().replaceFirstChar { it.uppercase() }})")
        val here = _location.value
        if (here == null) appendLine("Position: unknown (no GPS fix yet)") else {
            appendLine("Area: ${com.ginsengo.steward.research.SuggestionAssembler.coarseRegion(here.lat, here.lng)} (approximate, ~11 km)")
            runCatching { container.compliance.statusAt(here.lat, here.lng) }.getOrNull()?.let { st ->
                appendLine("State: ${st.state?.stateName ?: "not detected"} · season: ${st.season} · ${st.seasonDetail}")
                if (st.isProhibitedLand) appendLine("Land here: digging PROHIBITED (${st.landStatuses.joinToString { it.areaName }})")
                else if (st.needsPermit) appendLine("Land here: permit required (${st.landStatuses.joinToString { it.areaName }})")
            }
        }
        fun rel(lat: Double, lng: Double): String = here?.let {
            val leg = com.ginsengo.steward.field.Guidance.leg(it.lat, it.lng, lat, lng)
            com.ginsengo.steward.field.WayBack.describe(com.ginsengo.steward.field.WayBack.Leg(leg.distanceM, leg.bearingDeg)) + " from you"
        } ?: "distance unknown"
        val ranked = suggestions.value.sortedBy { it.rank }.take(8)
        if (ranked.isNotEmpty()) {
            appendLine("Ranked places within 10 miles (terrain model, best first):")
            ranked.forEach { s ->
                appendLine("  #${s.rank}: terrain score %.2f; %d m elevation; slope %.0f°, faces %s; %s; status %s; factors %s".format(
                    s.terrainScore, s.elevationM.toInt(), s.slopeDeg, com.ginsengo.steward.field.Guidance.compass(s.aspectDeg),
                    rel(s.lat, s.lng), s.status, s.factorsCsv.take(160)) + (if (s.lookFor.isNotBlank()) "; look for: ${s.lookFor.take(160)}" else ""))
            }
        } else appendLine("Ranked places: none yet (no terrain scan for this area)")
        val pl = places.value
        if (pl.isNotEmpty()) appendLine("Saved places: " + pl.take(12).joinToString("; ") { "${it.name} (${rel(it.lat, it.lng)})" })
        _target.value?.let { appendLine("Walking to: ${it.name} (${rel(it.lat, it.lng)})") }
        appendLine("Finds recorded: ${finds.value.size}" + (if (tracking.value.recording) " · Track is recording" else ""))
    }

    // ------------------------------------------------------------ navigation (N.1)
    private val _target = MutableStateFlow<com.ginsengo.steward.ui.map.PlaceSearch.Place?>(null)
    /** Where the owner is walking to: a line on the ground and a guidance chip until cleared. */
    val target: StateFlow<com.ginsengo.steward.ui.map.PlaceSearch.Place?> = _target.asStateFlow()
    fun goHere(name: String, lat: Double, lng: Double) { _target.value = com.ginsengo.steward.ui.map.PlaceSearch.Place(name, lat, lng) }
    fun clearTarget() { _target.value = null }

    private val _follow = MutableStateFlow(false)
    /** The map keeps you in the middle as you walk, until you move it yourself. */
    val follow: StateFlow<Boolean> = _follow.asStateFlow()
    fun setFollow(on: Boolean) {
        _follow.value = on
        if (on) recenter()
    }

    private val _places = MutableStateFlow(com.ginsengo.steward.field.SavedPlace.fromJson(container.settings.savedPlaces))
    val places: StateFlow<List<com.ginsengo.steward.field.SavedPlace>> = _places.asStateFlow()
    fun savePlace(name: String?, lat: Double, lng: Double) {
        val label = name?.takeIf { it.isNotBlank() } ?: "Place ${_places.value.size + 1}"
        _places.value = _places.value + com.ginsengo.steward.field.SavedPlace(label, lat, lng, System.currentTimeMillis())
        container.settings.savedPlaces = com.ginsengo.steward.field.SavedPlace.toJson(_places.value)
        _toast.value = "Saved \"$label\" (Places)"
    }
    fun deletePlace(p: com.ginsengo.steward.field.SavedPlace) {
        _places.value = _places.value - p
        container.settings.savedPlaces = com.ginsengo.steward.field.SavedPlace.toJson(_places.value)
    }

    private val _compass = MutableStateFlow(container.settings.compass)
    /** The phone's heading drawn on your position and used for "o'clock" guidance. */
    val compass: StateFlow<Boolean> = _compass.asStateFlow()
    fun setCompass(on: Boolean) { _compass.value = on; container.settings.compass = on }

    /** "Show on map": go to a suggestion (the map builds the square there). */
    fun focusOn(s: Suggestion) {
        _placeChosen.value = true
        camera.move(
            camera.camera.copy(lat = s.lat, lng = s.lng, zoom = CameraStart.FOCUS_ZOOM, pitch = CameraStart.FOCUS_TILT),
            animate = true,
        )
    }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    private val _placeChosen = MutableStateFlow(false)
    /**
     * F.1: true once the map should build where the camera is without waiting for a first fix: the
     * owner searched, picked a suggestion or a place, moved the map, or FIX_WAIT_MS passed. The
     * phone report "search works, terrain doesn't" was the map waiting for a fix that never came.
     */
    val placeChosen: StateFlow<Boolean> = _placeChosen.asStateFlow()
    fun placeChosen() { _placeChosen.value = true }

    init {
        viewModelScope.launch {
            history.value = container.database.trackDao().before(sessionStart)
        }
        // F.1: indoors a first fix can take minutes; the map stops waiting after FIX_WAIT_MS.
        viewModelScope.launch { delay(FIX_WAIT_MS); _placeChosen.value = true }
    }


    // ------------------------------------------------------------ battery (J32, J24)
    /** What the phone reports about its battery, read while the map is on screen. */
    data class Battery(val pct: Int?, val charging: Boolean, val systemSaver: Boolean)

    private val _battery = MutableStateFlow(Battery(null, false, false))
    val battery: StateFlow<Battery> = _battery.asStateFlow()

    private val _saverPct = MutableStateFlow(container.settings.batterySaverPct)
    /** J24: the owner's threshold for the battery mode. */
    val saverPct: StateFlow<Int> = _saverPct.asStateFlow()
    fun setSaverPct(pct: Int) {
        val v = pct.coerceIn(BatteryMode.THRESHOLD_RANGE.first, BatteryMode.THRESHOLD_RANGE.last)
        container.settings.batterySaverPct = v; _saverPct.value = v
    }

    /** J24: the 3D map runs lighter. */
    val batteryMode: StateFlow<Boolean> = combine(_battery, _saverPct) { b, t -> BatteryMode.on(b.pct, b.charging, b.systemSaver, t) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun readBattery() {
        val app = getApplication<Application>()
        val i = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pm = app.getSystemService(android.os.PowerManager::class.java)
        _battery.value = Battery(
            pct = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL,
            systemSaver = pm?.isPowerSaveMode == true,
        )
    }

    // ------------------------------------------------------------ live location
    private var liveJob: Job? = null
    private var tickJob: Job? = null
    private var livePlan: PowerPolicy.Plan? = null
    private var firstFixHandled = false
    // Stillness (J32): when the position last moved by more than its own accuracy.
    private var movedAt = System.currentTimeMillis()
    private var movedFrom: FieldLocation? = null

    /**
     * Called from onStart: the live position runs only while the map is on screen, with the request
     * [PowerPolicy] plans from the real battery, charging state and stillness (J32). It used to plan
     * from constants: a high-accuracy fix every 3 s for as long as the map was open.
     */
    fun onVisible() {
        if (!container.location.hasPermission()) return
        readBattery()
        if (liveJob == null) {
            replanLive(force = true)
            container.location.lastKnown { it?.let(::onFix) }
        }
        // Stillness and the battery change with no fix arriving (a still phone gets none past the
        // request's minimum distance): look again on a slow tick.
        if (tickJob == null) tickJob = viewModelScope.launch {
            while (true) { delay(REPLAN_TICK_MS); readBattery(); replanLive() }
        }
    }

    /** Called from onStop. Tracking, if on, carries on in its own service. */
    fun onHidden() {
        liveJob?.cancel(); liveJob = null
        tickJob?.cancel(); tickJob = null
        livePlan = null
        container.travel.flush()
    }

    private fun livePlanNow(): PowerPolicy.Plan {
        val b = _battery.value
        return PowerPolicy.plan(
            tracking = false, screenOn = true, batteryPct = b.pct, charging = b.charging,
            speedMps = null, stillForMs = System.currentTimeMillis() - movedAt,
        )
    }

    /** Swaps the location request when the plan changes; the same plan keeps the running one. */
    private fun replanLive(force: Boolean = false) {
        if (!container.location.hasPermission()) return
        val next = livePlanNow()
        if (!force && next == livePlan && liveJob != null) return
        livePlan = next
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            container.location.updates(next)
                .catch { /* permission revoked mid-session: keep the last fix */ }
                .collect { onFix(it) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permission.value = granted
        if (granted) onVisible()
    }

    private var refreshJob: Job? = null

    private fun onFix(loc: FieldLocation) {
        _location.value = loc
        com.ginsengo.steward.perf.FieldDiagnostics.gps = "fix ±${loc.accuracyM.toInt()} m, ${((System.currentTimeMillis() - loc.timestamp) / 1000).coerceAtLeast(0)} s old when received"
        // Every fix the app receives is remembered (J31), in batches.
        container.travel.record(TravelMemory.Fix(loc.lat, loc.lng, loc.accuracyM, loc.timestamp))
        val from = movedFrom
        if (from == null || Prospects.distanceMetres(from.lat, from.lng, loc.lat, loc.lng) > maxOf(loc.accuracyM.toDouble(), MOVED_M)) {
            val wasStill = livePlan?.mode == PowerPolicy.Mode.VIEWING_STILL
            movedFrom = loc; movedAt = System.currentTimeMillis()
            if (wasStill) replanLive()
        }
        // The first fresh fix JUMPS the camera (CameraStart: an animation can be interrupted, a jump
        // cannot); a stale last-known fix only lands provisionally, so it cannot steal that jump.
        val action = CameraStart.landing(System.currentTimeMillis() - loc.timestamp, landed, loc.accuracyM)
        if (action != CameraStart.Landing.NONE) {
            landed = action
            camera.move(camera.camera.copy(lat = loc.lat, lng = loc.lng, zoom = CameraStart.FIELD_ZOOM))
        } else if (_follow.value && landed == CameraStart.Landing.FINAL) {
            // N.1 follow: the map keeps you in the middle (bearing, tilt and zoom are left as set).
            camera.move(camera.camera.copy(lat = loc.lat, lng = loc.lng))
        }
        burst?.add(loc)
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val last = container.database.researchRunDao().latest()
            val refresh = ResearchTrigger.shouldRefresh(
                last?.centerLat, last?.centerLng, last?.time, loc.lat, loc.lng, loc.timestamp, now,
            )
            val modelOk = container.research.shouldAutoRun(loc.lat, loc.lng, powerAllowsAuto(), now)
            when {
                refresh || modelOk -> container.research.run(loc.lat, loc.lng, allowModel = modelOk)
                !firstFixHandled && now - loc.timestamp <= ResearchTrigger.FRESH_MS &&
                        container.database.findDao().all().isNotEmpty() ->
                    // Same area as last time and there is something to learn from: reload the
                    // scan so the learned layer is ready. With no finds this scan was
                    // pure cost at every launch (seen on the Phase 7 device run), so it is skipped.
                    container.research.scanAround(loc.lat, loc.lng)?.let { container.research.learn(it) }
            }
            if (now - loc.timestamp <= ResearchTrigger.FRESH_MS) firstFixHandled = true
        }
    }

    private fun powerAllowsAuto(): Boolean {
        val bm = getApplication<Application>().getSystemService(android.os.BatteryManager::class.java)
        val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        return PowerPolicy.plan(false, true, pct, bm?.isCharging == true, null, 0L).allowAutoResearch
    }

    // ------------------------------------------------------------ actions
    fun toggleTracking() {
        val ctx = getApplication<Application>()
        if (tracking.value.recording) TrackService.stop(ctx) else TrackService.start(ctx)
    }

    fun research() {
        val here = _location.value ?: run { _toast.value = "Waiting for a GPS fix"; return }
        viewModelScope.launch { container.research.run(here.lat, here.lng) }
    }

    fun markNotFound(s: Suggestion) {
        viewModelScope.launch { container.memory.markNotFound(s.id, System.currentTimeMillis()) }
    }

    // ------------------------------------------------------------ marking a find
    private var burst: MutableList<FieldLocation>? = null
    private val _burstCount = MutableStateFlow(BURST_IDLE)
    /** Fixes collected so far while "Hold still" runs; [BURST_IDLE] or [BURST_DONE] otherwise. */
    val burstCount: StateFlow<Int> = _burstCount.asStateFlow()

    /** Averages fixes for up to [FIND_AVERAGE_MS] (or until [stopBurst]). */
    fun startBurst() {
        burst = mutableListOf<FieldLocation>().also { l -> _location.value?.let { l += it } }
        _burstCount.value = burst!!.size
        viewModelScope.launch {
            val until = System.currentTimeMillis() + FIND_AVERAGE_MS
            while (burst != null && System.currentTimeMillis() < until) {
                delay(500)
                burst?.let { _burstCount.value = it.size }
            }
            if (burst != null) _burstCount.value = BURST_DONE
        }
    }

    fun stopBurst(): FixAverager.Result? {
        val fixes = burst.orEmpty()
        burst = null
        _burstCount.value = BURST_IDLE
        return FixAverager.average(fixes.ifEmpty { listOfNotNull(_location.value) })
    }

    fun saveFind(fix: FixAverager.Result, plantCount: Int, maxProngs: Int?, note: String) {
        viewModelScope.launch {
            val f = container.memory.addFind(fix, System.currentTimeMillis(), plantCount, maxProngs, note)
            _toast.value = "Find saved. The map learns from it."
            container.research.scanAround(f.lat, f.lng)?.let { container.research.learn(it) }
        }
    }

    // ------------------------------------------------------------ settings
    data class ResearchSettings(
        val provider: Provider,
        val model: String,
        val hasKey: Boolean,
        val consent: Boolean,
        val auto: Boolean,
    )

    private fun readSettings() = container.settings.let { st ->
        ResearchSettings(st.provider, st.model(st.provider), container.keys.has(st.provider), st.researchConsent, st.autoResearch)
    }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ResearchSettings> = _settings.asStateFlow()

    fun setProvider(p: Provider) { container.settings.provider = p; _settings.value = readSettings() }
    fun setKey(key: String) { container.keys.set(container.settings.provider, key); _settings.value = readSettings() }
    fun setModel(model: String) { container.settings.setModel(container.settings.provider, model); _settings.value = readSettings() }
    fun setConsent(on: Boolean) { container.settings.researchConsent = on; _settings.value = readSettings() }
    fun setAuto(on: Boolean) { container.settings.autoResearch = on; _settings.value = readSettings() }

    /** Season and state line from the sourced regulation data, for the Suggest sheet. */
    fun complianceLine(): String? {
        val loc = _location.value ?: return null
        val s = container.compliance.statusAt(loc.lat, loc.lng)
        val state = s.state ?: return "No state ginseng rules on file for this position."
        return "${state.stateName} · ${s.season.label} · ${s.seasonDetail}"
    }

    fun saveOffline() {
        val here = _location.value ?: run { _toast.value = "Waiting for a GPS fix"; return }
        viewModelScope.launch {
            val n = OfflineArea.prefetchDem(container.demTiles, here.lat, here.lng) { done, total ->
                _toast.value = "Saving elevation $done/$total"
            }
            _toast.value = "Saved $n elevation tiles for 10 miles around you. Saving the map…"
            OfflineArea.downloadBasemap(getApplication(), com.ginsengo.steward.ui.map.DARK_STYLE, here.lat, here.lng) {
                _toast.value = it
            }
        }
    }

    companion object {
        const val FIND_AVERAGE_MS = 20_000L
        /** F.1: how long the map waits for a first fix before showing where the camera is. */
        const val FIX_WAIT_MS = 15_000L
        /** How often stillness and the battery are looked at again while the map is on screen. */
        const val REPLAN_TICK_MS = 30_000L
        /** Moving means more than this, or the fix's own accuracy, from where it last moved. */
        const val MOVED_M = 10.0
        const val BURST_IDLE = -1
        const val BURST_DONE = -2
    }
}
