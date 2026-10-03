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
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.field.FixAverager
import com.ginsengo.steward.field.PowerPolicy
import com.ginsengo.steward.field.TrackService
import com.ginsengo.steward.learn.FindLearner
import com.ginsengo.steward.research.Provider
import com.ginsengo.steward.research.ResearchTrigger
import com.ginsengo.steward.terrain3d.CameraMath
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
    private val _layers = MutableStateFlow(MapLayerState())
    val layers: StateFlow<MapLayerState> = _layers.asStateFlow()
    fun setLayers(s: MapLayerState) { _layers.value = s }

    private val _view3d = MutableStateFlow(false)
    val view3d: StateFlow<Boolean> = _view3d.asStateFlow()

    /**
     * The one camera (exe.md A1): both views mirror it under [SharedCamera]'s epoch rule; neither
     * keeps its own. Seeded where [CameraStart] says the map opens, then moved only here (app
     * actions) or by the view under the user's finger (gestures).
     */
    val camera = SharedCamera(
        CameraStart.initial(null, null).let { CameraState(it.lat, it.lon, it.zoom, 0.0, CameraStart.START_TILT) }
    )
    private var cameraOnFix = false

    /** One switch, one camera: the view changes, the place does not ([CameraMath.to3d]/[to2d]). */
    fun setView3d(on: Boolean) {
        if (_view3d.value == on) return
        camera.move(CameraMath.forView(camera.camera, on))
        _view3d.value = on
    }

    /** "Centre on me", in whichever view is showing. */
    fun recenter() {
        val me = _location.value ?: return
        camera.move(camera.camera.copy(lat = me.lat, lng = me.lng), animate = true)
    }

    /** "Show on map": fly to a suggestion, in whichever view is showing. */
    fun focusOn(s: Suggestion) {
        camera.move(
            camera.camera.copy(lat = s.lat, lng = s.lng, zoom = CameraStart.FOCUS_ZOOM, pitch = CameraStart.FOCUS_TILT),
            animate = true,
        )
    }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    init {
        viewModelScope.launch {
            history.value = container.database.trackDao().before(sessionStart)
        }
    }

    // ------------------------------------------------------------ live location
    private var liveJob: Job? = null
    private var firstFixHandled = false

    /** Called from onStart: the live position runs only while the map is on screen. */
    fun onVisible() {
        if (!container.location.hasPermission() || liveJob != null) return
        val plan = PowerPolicy.plan(
            tracking = false, screenOn = true, batteryPct = null, charging = false,
            speedMps = null, stillForMs = 0L,
        )
        liveJob = viewModelScope.launch {
            container.location.updates(plan)
                .catch { /* permission revoked mid-session: keep the last fix */ }
                .collect { onFix(it) }
        }
        container.location.lastKnown { it?.let(::onFix) }
    }

    /** Called from onStop. Tracking, if on, carries on in its own service. */
    fun onHidden() {
        liveJob?.cancel()
        liveJob = null
    }

    fun onPermissionResult(granted: Boolean) {
        _permission.value = granted
        if (granted) onVisible()
    }

    private var refreshJob: Job? = null

    private fun onFix(loc: FieldLocation) {
        _location.value = loc
        // The first fix JUMPS the camera (CameraStart: an animation can be interrupted, a jump cannot).
        if (CameraStart.shouldJumpToFix(hasFix = true, alreadyCentred = cameraOnFix)) {
            cameraOnFix = true
            camera.move(camera.camera.copy(lat = loc.lat, lng = loc.lng, zoom = CameraStart.FIELD_ZOOM))
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
        const val BURST_IDLE = -1
        const val BURST_DONE = -2
    }
}
