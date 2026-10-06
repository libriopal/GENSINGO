package com.ginsengo.steward

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import com.ginsengo.steward.compliance.ComplianceEngine
import com.ginsengo.steward.data.db.AppDatabase
import com.ginsengo.steward.data.reference.ReferenceRepository
import com.ginsengo.steward.field.LocationProvider
import com.ginsengo.steward.field.TravelRecorder
import com.ginsengo.steward.memory.FieldMemoryRepository
import com.ginsengo.steward.perf.MemoryBudget
import com.ginsengo.steward.research.KeyVault
import com.ginsengo.steward.research.Provider
import com.ginsengo.steward.research.ResearchRepository
import com.ginsengo.steward.terrain.DemTileStore

/**
 * Manual dependency container.
 *
 * Hilt was the obvious choice and was rejected: it adds an annotation processor and a plugin
 * to a build whose hardest requirement is that it actually produces an APK, to wire about a
 * dozen singletons with no scopes and no test doubles.
 */
class AppContainer(val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.get(context) }
    val reference: ReferenceRepository by lazy { ReferenceRepository(context) }
    val compliance: ComplianceEngine by lazy { ComplianceEngine(reference) }
    val location: LocationProvider by lazy { LocationProvider(context) }
    val settings: SettingsStore by lazy { SettingsStore(context) }
    val keys: KeyVault by lazy { KeyVault(context) }

    /**
     * Streaming elevation tiles, cached to app-private storage so ground already seen (or
     * saved for offline) keeps working with no signal. Feeds the heatmap, the radius scan
     * and the 3D view.
     */
    val demTiles: DemTileStore by lazy { DemTileStore(context, memoryBudget) }

    /**
     * The one memory ceiling for the large caches of both views (A13): a fifth of this app's heap
     * class. GensingoApp lowers it when the system asks for memory back; the 3D build trims it when
     * it runs out of memory and tries once more.
     */
    val memoryBudget: MemoryBudget by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        MemoryBudget(MemoryBudget.ceilingFor(am.memoryClass))
    }

    val memory: FieldMemoryRepository by lazy { FieldMemoryRepository(database) }

    /** Process-long work that must outlive a screen: the travel memory's batched writes. */
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /**
     * The travel memory's one recorder (owner directive J31): the map on screen and the track
     * service in the pocket both feed it, so "where I've been" is every fix the app receives.
     */
    val travel: TravelRecorder by lazy {
        TravelRecorder({ cells, t -> database.visitedDao().add(cells, t) }, appScope)
    }

    /** What the 3D map reads of the travel memory: the table over a square plus what is not yet written. */
    val travelSource: com.ginsengo.steward.field.TravelSource by lazy {
        object : com.ginsengo.steward.field.TravelSource {
            override suspend fun cellsIn(north: Double, west: Double, south: Double, east: Double): LongArray {
                val band = com.ginsengo.steward.field.TravelCells.band(south, north)
                return database.visitedDao().between(band.first, band.last)
                    .filter { com.ginsengo.steward.field.TravelCells.inBox(it, north, west, south, east) }.toLongArray()
            }
            override val version get() = travel.version
            override fun recentSince(index: Int) = travel.recentSince(index)
        }
    }

    val research: ResearchRepository by lazy {
        ResearchRepository(
            db = database,
            dem = demTiles,
            compliance = compliance,
            settings = settings,
            keys = keys,
            isOnline = ::isOnline,
        )
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Local-only preferences. Nothing here ever leaves the device. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gensingo", Context.MODE_PRIVATE)

    var manualStateCode: String?
        get() = prefs.getString(KEY_STATE, null)
        set(v) = prefs.edit { putString(KEY_STATE, v) }

    var provider: Provider
        get() = runCatching { Provider.valueOf(prefs.getString(KEY_PROVIDER, null) ?: "") }
            .getOrDefault(Provider.CLAUDE)
        set(v) = prefs.edit { putString(KEY_PROVIDER, v.name) }

    fun model(p: Provider): String = prefs.getString("$KEY_MODEL.${p.name}", null)
        ?.takeIf { it.isNotBlank() } ?: p.defaultModel

    fun setModel(p: Provider, model: String) =
        prefs.edit { putString("$KEY_MODEL.${p.name}", model.trim()) }

    /**
     * Consent to send the coarse region, state rules, anonymised terrain numbers and outcome
     * counts to the chosen provider. OFF by default: until the user turns it on, research runs
     * entirely on the phone and nothing is sent anywhere.
     */
    var researchConsent: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(v) = prefs.edit { putBoolean(KEY_CONSENT, v) }

    /** Refresh suggestions automatically after moving 5 km (only with consent, battery > 30%). */
    var autoResearch: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(v) = prefs.edit { putBoolean(KEY_AUTO, v) }

    /** J24: the battery level at or below which the 3D map runs lighter (unplugged). */
    var batterySaverPct: Int
        get() = prefs.getInt(KEY_SAVER, com.ginsengo.steward.field.BatteryMode.DEFAULT_THRESHOLD_PCT)
        set(v) = prefs.edit { putInt(KEY_SAVER, v) }

    private companion object {
        const val KEY_SAVER = "battery_saver_pct"
        const val KEY_STATE = "manual_state_code"
        const val KEY_PROVIDER = "research_provider"
        const val KEY_MODEL = "research_model"
        const val KEY_CONSENT = "research_consent"
        const val KEY_AUTO = "auto_research"
    }
}
