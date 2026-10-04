package com.ginsengo.steward.ui.map

import android.annotation.SuppressLint
import android.util.Log
import com.ginsengo.steward.geo.LogRedaction
import org.maplibre.android.log.Logger
import org.maplibre.android.log.LoggerDefinition

/**
 * MapLibre's log, tile ids removed (I19). Its native code logs every tile it fails to load by id
 * (`Mbgl: Failed to load tile 14/4475/6422…`), and offline that is every tile on screen. Installed
 * once, before the first map, through MapLibre's own hook; the JNI log calls go through it too.
 * Throwables are reduced to one redacted line: their messages can carry tile URLs.
 *
 * A log adapter: calling android.util.Log is its job (Timber is not a dependency), so lint's
 * LogNotTimber is exempted here by name.
 */
@SuppressLint("LogNotTimber")
object MapLogging {

    fun install() = Logger.setLoggerDefinition(Redacting)

    private object Redacting : LoggerDefinition {
        private fun m(msg: String?, t: Throwable? = null) =
            LogRedaction.redact(msg) + (if (t != null) " · " + LogRedaction.describe(t) else "")

        override fun v(tag: String, msg: String) { Log.v(tag, m(msg)) }
        override fun v(tag: String, msg: String, tr: Throwable) { Log.v(tag, m(msg, tr)) }
        override fun d(tag: String, msg: String) { Log.d(tag, m(msg)) }
        override fun d(tag: String, msg: String, tr: Throwable) { Log.d(tag, m(msg, tr)) }
        override fun i(tag: String, msg: String) { Log.i(tag, m(msg)) }
        override fun i(tag: String, msg: String, tr: Throwable) { Log.i(tag, m(msg, tr)) }
        override fun w(tag: String, msg: String) { Log.w(tag, m(msg)) }
        override fun w(tag: String, msg: String, tr: Throwable) { Log.w(tag, m(msg, tr)) }
        override fun e(tag: String, msg: String) { Log.e(tag, m(msg)) }
        override fun e(tag: String, msg: String, tr: Throwable) { Log.e(tag, m(msg, tr)) }
    }
}
