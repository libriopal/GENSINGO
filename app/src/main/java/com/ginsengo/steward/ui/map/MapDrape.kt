package com.ginsengo.steward.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.ginsengo.steward.BuildConfig
import com.ginsengo.steward.terrain3d.MapCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.snapshotter.MapSnapshotter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Diagnostics only. Never log a coordinate, a URL, or a MapLibre error text: error texts can
 * carry a tile URL, and a tile URL encodes where the user is.
 */
private const val TAG = "GensingoDrape"

/**
 * The basemap for the 3D view: MapLibre's own render of the dark map style over the 3D
 * square, which [com.ginsengo.steward.terrain3d.TerrainTextures] bakes under the app's layers
 * (one-map blueprint, WP-A). The map library draws it, so roads, names and the offline cache
 * are MapLibre's, not a second renderer's. MapLibre is headless here: no map view is on screen.
 *
 * WHY THE PIXEL RATIO AND THE ZOOM CAP (audit of claim 2). The snapshot only works offline if
 * it asks for what "Save this area" stored: an offline region at zoom 8-14 at the SCREEN's
 * pixel ratio ([com.ginsengo.steward.ui.OfflineArea.downloadBasemap]). The snapshotter defaults
 * to ratio 1, whose sprites were never cached, and in still mode one missing resource fails the
 * whole render. So it renders at the screen's density with logical size = texture / density,
 * and the logical size is cut, if needed, so the zoom never passes the region's top zoom; the
 * bitmap is then scaled to the texture. A failure or a timeout returns null, and the 3D view
 * keeps the terrain-only relief.
 */
object MapDrape {

    /** The top zoom of the saved offline region (OfflineArea.downloadBasemap). */
    const val MAX_ZOOM = 14.0

    /**
     * The zoom at which [widthM] spans [logicalPx] logical pixels: MapLibre's 512-px tiles, as
     * in [MapCamera] and Terrain3D.fitZoom, so the three agree on what a zoom means.
     */
    fun snapshotZoom(logicalPx: Int, widthM: Double, lat: Double): Double =
        ln(MapCamera.EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)) * logicalPx / (MapCamera.TILE_SIZE * widthM)) / ln(2.0)

    /**
     * The snapshot's logical size: [sizePx] / [density], cut to the largest size whose
     * [snapshotZoom] is still at most [maxZoom]. A higher zoom would ask for tiles the saved
     * region does not hold, which fails the whole snapshot offline.
     */
    fun logicalSize(sizePx: Int, density: Float, widthM: Double, lat: Double, maxZoom: Double = MAX_ZOOM): Int {
        val wanted = (sizePx / density.coerceAtLeast(0.1f).toDouble()).roundToInt().coerceAtLeast(1)
        if (!(widthM > 0.0)) return wanted
        var ceiling = floor(MapCamera.TILE_SIZE * widthM * 2.0.pow(maxZoom) /
            (MapCamera.EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)))).toInt()
        // Floor of the exact inverse; the loop only absorbs a last-bit rounding of the logarithm.
        while (ceiling > 1 && snapshotZoom(ceiling, widthM, lat) > maxZoom) ceiling--
        return minOf(wanted, ceiling).coerceAtLeast(1)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Renders [styleUri] over (north, west, south, east) and returns exactly [sizePx] x
     * [sizePx] ARGB pixels, north up, or null on failure or after [timeoutMs].
     *
     * Suspends instead of blocking: MapSnapshotter is @UiThread and calls back on the main
     * thread, so a blocking call made there would wait for itself forever. The snapshotter is
     * created and started on the main thread, started once (its API allows no more), and
     * cancelled there if the time runs out; the bitmap is turned into pixels off the main thread.
     */
    suspend fun render(
        context: Context,
        styleUri: String,
        north: Double, west: Double, south: Double, east: Double,
        sizePx: Int,
        timeoutMs: Long,
    ): IntArray? {
        if (sizePx <= 0 || !(north > south) || !(east > west)) return null
        val app = context.applicationContext ?: context
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val lat = (north + south) / 2
        // Width along the centre parallel: the same metres MapCamera and the texture use.
        val widthM = (east - west) / 360.0 * MapCamera.EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat))
        val logical = logicalSize(sizePx, density, widthM, lat)
        val bounds = LatLngBounds.Builder()
            .include(LatLng(north, west))
            .include(LatLng(south, east))
            .build()

        resetRequestCounts()
        val t0 = SystemClock.elapsedRealtime()
        var outcome = "timed out"
        val bitmap = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) { snapshot(app, styleUri, bounds, logical, density) }
                .also { outcome = if (it == null) "failed" else "ready" }
        }
        Log.i(TAG, "drape: snapshot $outcome in ${SystemClock.elapsedRealtime() - t0} ms, logical $logical " +
            "at ratio $density, zoom %.2f".format(snapshotZoom(logical, widthM, lat)))
        logRequestCounts()
        bitmap ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { toPixels(bitmap, sizePx) }
                .onFailure { Log.w(TAG, "drape: pixels unreadable (${it.javaClass.simpleName})") }
                .getOrNull()
        }
    }

    /** Main thread only. Resumes with the bitmap, or null; never throws. */
    private suspend fun snapshot(
        context: Context, styleUri: String, bounds: LatLngBounds, logical: Int, density: Float,
    ): Bitmap? = suspendCancellableCoroutine { cont ->
        val snapshotter = runCatching {
            MapLibre.getInstance(context)
            MapSnapshotter(
                context,
                MapSnapshotter.Options(logical, logical)
                    .withRegion(bounds)
                    .withPixelRatio(density)
                    // Off, so neither is baked onto the ground; the 3D legend owes the attribution.
                    .withLogo(false)
                    .withAttribution(false)
                    .withStyle(styleUri),
            )
        }.getOrElse {
            Log.w(TAG, "drape: snapshotter not created (${it.javaClass.simpleName})")
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        // Cancellation (the timeout) can arrive on any thread; the snapshotter is @UiThread.
        cont.invokeOnCancellation { mainHandler.post { runCatching { snapshotter.cancel() } } }
        runCatching {
            snapshotter.start(
                { snap -> if (cont.isActive) cont.resume(snap.bitmap) },
                { _ ->
                    // The error text is withheld on purpose: it can name the failing tile's URL.
                    Log.w(TAG, "drape: snapshot error; the 3D view keeps the terrain-only relief")
                    if (cont.isActive) cont.resume(null)
                },
            )
        }.onFailure {
            Log.w(TAG, "drape: snapshot not started (${it.javaClass.simpleName})")
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Exactly [sizePx] squared pixels: the bitmap is scaled when ratio rounding or the zoom cap changed its size. */
    private fun toPixels(bitmap: Bitmap, sizePx: Int): IntArray {
        val fitted = if (bitmap.width == sizePx && bitmap.height == sizePx) bitmap
        else Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
        val out = IntArray(sizePx * sizePx)
        fitted.getPixels(out, 0, sizePx, 0, 0, sizePx, sizePx)
        if (fitted !== bitmap) fitted.recycle()
        bitmap.recycle()
        return out
    }

    // ------------------------------------------------------------------ debug request counter

    /** What a request fetched, judged from its path; the path itself is never kept. */
    enum class RequestKind { STYLE, SPRITE, GLYPHS, TILE, OTHER }

    private val TILE_PATH = Regex("""/\d+/\d+/\d+(@\d+(\.\d+)?x)?\.[a-z0-9]+$""")
    private val GLYPH_RANGE = Regex("""/\d+-\d+\.pbf$""")

    internal fun kindOf(path: String): RequestKind {
        val p = path.lowercase()
        return when {
            "sprite" in p -> RequestKind.SPRITE
            "/fonts/" in p || "/glyphs/" in p || GLYPH_RANGE.containsMatchIn(p) -> RequestKind.GLYPHS
            TILE_PATH.containsMatchIn(p) -> RequestKind.TILE
            "/styles/" in p || p.endsWith("style.json") -> RequestKind.STYLE
            else -> RequestKind.OTHER
        }
    }

    private val counts = AtomicIntegerArray(RequestKind.entries.size)
    private val counting = AtomicBoolean(false)

    /**
     * Debug builds only: the auditor's instrument for the device gate. MapLibre answers from
     * its cache and offline regions in native code, so a request that reaches OkHttp missed
     * them (or is revalidating an expired entry), and with no signal that request fails, and
     * the whole snapshot with it. Counts by kind are logged after each snapshot; nothing else
     * is kept or logged. The window counts every MapLibre request, not only the snapshot's.
     *
     * OkHttp is only on this module's RUNTIME classpath (MapLibre and the Anthropic SDK both
     * declare it runtime-scoped), and the build file is outside this work package, so the
     * client is assembled by reflection: the same client MapLibre builds by default (a
     * dispatcher allowing 20 requests per host, HttpRequestImpl) plus one counting interceptor.
     */
    fun installRequestCounter() {
        if (!BuildConfig.DEBUG || !counting.compareAndSet(false, true)) return
        runCatching {
            val callFactory = Class.forName("okhttp3.Call\$Factory")
            HttpRequestUtil::class.java.getMethod("setOkHttpClient", callFactory).invoke(null, countingClient())
            Log.i(TAG, "drape: request counter installed (debug build)")
        }.onFailure {
            counting.set(false)
            Log.w(TAG, "drape: request counter not installed (${it.javaClass.simpleName})")
        }
    }

    private fun resetRequestCounts() {
        for (k in 0 until counts.length()) counts.set(k, 0)
    }

    /** The counts so far, indexed by [RequestKind.ordinal]. */
    internal fun requestCounts(): IntArray = IntArray(counts.length()) { counts.get(it) }

    private fun logRequestCounts() {
        if (!counting.get()) return
        Log.i(TAG, "drape: requests " + RequestKind.entries.joinToString(" ") { "${it.name.lowercase()}=${counts.get(it.ordinal)}" })
    }

    /** An okhttp3.OkHttpClient, typed Any because OkHttp is not on the compile classpath. */
    internal fun countingClient(): Any {
        val interceptorType = Class.forName("okhttp3.Interceptor")
        val chainType = Class.forName("okhttp3.Interceptor\$Chain")
        val requestType = Class.forName("okhttp3.Request")
        val request = chainType.getMethod("request")
        val proceed = chainType.getMethod("proceed", requestType)
        val url = requestType.getMethod("url")
        val encodedPath = Class.forName("okhttp3.HttpUrl").getMethod("encodedPath")
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "intercept" -> {
                    val chain = args!![0]
                    val req = request.invoke(chain)
                    runCatching { counts.incrementAndGet(kindOf(encodedPath.invoke(url.invoke(req)) as String).ordinal) }
                    // OkHttp must see its own IOException, not the reflection wrapper.
                    try { proceed.invoke(chain, req) } catch (e: InvocationTargetException) { throw e.targetException }
                }
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "GensingoRequestCounter"
                else -> null
            }
        }
        val interceptor = Proxy.newProxyInstance(interceptorType.classLoader, arrayOf(interceptorType), handler)
        val dispatcherType = Class.forName("okhttp3.Dispatcher")
        val dispatcher = dispatcherType.getConstructor().newInstance()
        dispatcherType.getMethod("setMaxRequestsPerHost", Int::class.javaPrimitiveType).invoke(dispatcher, 20)
        val builderType = Class.forName("okhttp3.OkHttpClient\$Builder")
        val builder = builderType.getConstructor().newInstance()
        builderType.getMethod("dispatcher", dispatcherType).invoke(builder, dispatcher)
        builderType.getMethod("addInterceptor", interceptorType).invoke(builder, interceptor)
        return builderType.getMethod("build").invoke(builder)!!
    }
}
