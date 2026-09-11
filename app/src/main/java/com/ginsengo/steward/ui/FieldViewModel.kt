package com.ginsengo.steward.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginsengo.steward.AppContainer
import com.ginsengo.steward.GensingoApp
import com.ginsengo.steward.compliance.ComplianceStatus
import com.ginsengo.steward.compliance.SeasonStatus
import com.ginsengo.steward.data.db.GinsengPatch
import com.ginsengo.steward.data.db.HabitatReadingRecord
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.geo.SlopeAspect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared field state: where we are, what the law says here, and what is already logged.
 * Screen-specific state stays in the screens; this is only the stuff several screens need.
 */
class FieldViewModel(app: Application) : AndroidViewModel(app) {

    val container: AppContainer = (app as GensingoApp).container

    private val _location = MutableStateFlow<FieldLocation?>(null)
    val location: StateFlow<FieldLocation?> = _location.asStateFlow()

    private val _compliance = MutableStateFlow<ComplianceStatus?>(null)
    val compliance: StateFlow<ComplianceStatus?> = _compliance.asStateFlow()

    private val _bearing = MutableStateFlow<Float?>(null)
    val bearing: StateFlow<Float?> = _bearing.asStateFlow()

    private val _permissionGranted = MutableStateFlow(container.location.hasPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    val patches: StateFlow<List<GinsengPatch>> =
        container.database.patchDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val readings: StateFlow<List<HabitatReadingRecord>> =
        container.database.habitatReadingDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        startCompass()
        if (_permissionGranted.value) startLocation()
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) {
            startLocation()
            container.location.lastKnown { it?.let(::applyLocation) }
        }
    }

    private var locationStarted = false

    private fun startLocation() {
        if (locationStarted) return
        locationStarted = true
        viewModelScope.launch {
            container.location.updates()
                .catch { /* permission revoked mid-session; keep the last fix on screen */ }
                .collect { applyLocation(it) }
        }
        container.location.lastKnown { it?.let(::applyLocation) }
    }

    private fun startCompass() {
        viewModelScope.launch {
            container.compass.bearings().catch { }.collect { _bearing.value = it }
        }
    }

    private fun applyLocation(loc: FieldLocation) {
        _location.value = loc
        val manual = container.reference.stateByCode(container.settings.manualStateCode)
        _compliance.value = container.compliance.statusAt(loc.lat, loc.lng, manualState = manual)
    }

    /** Re-evaluates compliance after the user picks a state by hand in Settings. */
    fun refreshCompliance() {
        _location.value?.let(::applyLocation)
    }

    /**
     * Elevation for the habitat model.
     *
     * GPS altitude is preferred over the bundled DEM, which inverts what the PRD assumed.
     * The reason is that it is a real measurement at the digger's actual position, where
     * the bundled grid is an ~11 km cell average - and elevation is the ONLY model input
     * with a non-zero weight that is not derived from the checklist, so it is the one place
     * a genuine measurement is worth having.
     */
    fun elevationFor(loc: FieldLocation): Pair<Double?, Boolean> {
        loc.altitudeM?.let { return it to true }
        val dem = container.dem?.elevation(loc.lat, loc.lng)
        return dem to (dem != null)
    }

    fun slopeAspectFor(loc: FieldLocation): SlopeAspect? =
        container.dem?.slopeAspect(loc.lat, loc.lng)

    fun seasonAccent(): SeasonStatus = _compliance.value?.season ?: SeasonStatus.UNKNOWN
}
