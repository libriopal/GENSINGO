package com.discomplemented.ginseng

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class with Hilt initialization.
 */
@HiltAndroidApp
class GinsengApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application initialization
    }
}
