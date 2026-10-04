package com.ginsengo.steward

import android.app.Application
import android.util.Log
import com.ginsengo.steward.ui.map.MapLogging

class GensingoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Before the first map: MapLibre's native log names every tile it fails to load (I19).
        MapLogging.install()
        container = AppContainer(this)
    }

    /**
     * The system asks for memory back: one ceiling for both views' caches comes down (A13), and
     * the counters go to the log (no positions in them).
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::container.isInitialized) return
        container.memoryBudget.trim(level)
        Log.i(TAG, "trim level $level: " + container.memoryBudget.report())
    }

    private companion object {
        const val TAG = "GensingoMemory"
    }
}
