package com.ginsengo.steward

import android.content.Context
import android.content.SharedPreferences
import com.ginsengo.steward.compliance.ComplianceEngine
import com.ginsengo.steward.data.db.AppDatabase
import com.ginsengo.steward.data.reference.ReferenceRepository
import com.ginsengo.steward.field.CompassProvider
import com.ginsengo.steward.field.LocationProvider
import com.ginsengo.steward.field.PhotoStore
import com.ginsengo.steward.geo.DemGrid
import com.ginsengo.steward.habitat.HabitatEngine
import com.ginsengo.steward.terrain.DemTileStore

/**
 * Manual dependency container.
 *
 * Hilt was the obvious choice and was rejected: it adds a kapt/KSP processor and a plugin
 * to a build whose single hardest requirement is that it actually produces an APK, in
 * exchange for wiring roughly a dozen singletons that have no scopes and no test doubles.
 * The annotation processor is the cost; the graph is not complex enough to be the benefit.
 */
class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.get(context) }
    val reference: ReferenceRepository by lazy { ReferenceRepository(context) }
    val compliance: ComplianceEngine by lazy { ComplianceEngine(reference) }
    val location: LocationProvider by lazy { LocationProvider(context) }
    val compass: CompassProvider by lazy { CompassProvider(context) }
    val photos: PhotoStore by lazy { PhotoStore(context) }
    val settings: SettingsStore by lazy { SettingsStore(context) }

    val habitat: HabitatEngine? by lazy { HabitatEngine.load(context) }

    /**
     * Streaming elevation tiles for the map's relief, height overlay and habitat heatmap.
     *
     * Deliberately separate from [dem], the bundled offline grid. The bundled grid feeds
     * the habitat MODEL and must keep working with no signal; this one feeds the map's
     * VISUALISATION and streams like the basemap does, caching what it fetches so ground
     * already walked stays available offline.
     */
    val demTiles: DemTileStore by lazy { DemTileStore(context) }

    /**
     * The coarse elevation grid is optional: if the asset is absent the app still works,
     * it just leans on GPS altitude. Loading it is deferred because it is the largest
     * asset and most sessions never open habitat analysis.
     */
    val dem: DemGrid? by lazy {
        runCatching { context.assets.open(DEM_ASSET).use { DemGrid.read(it) } }.getOrNull()
    }

    companion object {
        const val DEM_ASSET = "geo/dem_grid.bin"
    }
}

/** Local-only preferences. Nothing here ever leaves the device. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gensingo", Context.MODE_PRIVATE)

    var manualStateCode: String?
        get() = prefs.getString(KEY_STATE, null)
        set(v) = prefs.edit().putString(KEY_STATE, v).apply()

    /** PRD §8.3: coordinates are blurred in list view until explicitly revealed. */
    var blurCoordinates: Boolean
        get() = prefs.getBoolean(KEY_BLUR, true)
        set(v) = prefs.edit().putBoolean(KEY_BLUR, v).apply()

    /**
     * Phase 3 opt-in cloud sync. Present so Settings can state the app's actual posture
     * rather than implying a capability; it is false and unchangeable in this build.
     */
    val cloudSyncEnabled: Boolean get() = false

    private companion object {
        const val KEY_STATE = "manual_state_code"
        const val KEY_BLUR = "blur_coordinates"
    }
}
