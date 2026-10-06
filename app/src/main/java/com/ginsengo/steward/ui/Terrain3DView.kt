package com.ginsengo.steward.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.data.db.TrackPoint
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.research.RadiusScan
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain3d.CameraMath
import com.ginsengo.steward.terrain3d.DepthRange
import com.ginsengo.steward.terrain3d.GestureMath
import com.ginsengo.steward.terrain3d.SquareLevel
import com.ginsengo.steward.terrain3d.TravelMask
import com.ginsengo.steward.terrain3d.CameraState
import com.ginsengo.steward.field.BatteryMode
import com.ginsengo.steward.field.FramePacer
import com.ginsengo.steward.field.TravelSource
import com.ginsengo.steward.terrain3d.MeshSession
import com.ginsengo.steward.terrain3d.Occlusion
import com.ginsengo.steward.perf.MemoryBudget
import com.ginsengo.steward.terrain3d.Terrain3D
import com.ginsengo.steward.terrain3d.TerrainGlRenderer
import com.ginsengo.steward.terrain3d.TerrainTextures
import com.ginsengo.steward.terrain3d.SharedCamera
import com.ginsengo.steward.terrain3d.gl.GLTextureView
import com.ginsengo.steward.ui.map.Basemap
import com.ginsengo.steward.ui.map.MapDrape
import com.ginsengo.steward.ui.map.MapLayerState
import com.ginsengo.steward.ui.map.MeshLayers
import com.ginsengo.steward.ui.map.SceneLayer
import com.ginsengo.steward.ui.map.ring
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The one map (owner directive, wave M.1): real terrain in 3D, built as one square at a time around
 * the camera, at the elevation zoom its own zoom calls for ([SquareLevel]: 3 km at 3.9 m cells when
 * close, up to 48 km at ~61 m when zoomed out, enough for the whole 10-mile radius).
 *
 * - **One camera.** The app moves it (the first fix, recentre, "Show on map", the compass); the
 *   gestures here report it. When a gesture ends the camera is put back on the ground without
 *   moving the eye ([CameraMath.reanchor]); when its centre has left the square, or its zoom calls
 *   for another level, the square is rebuilt there.
 * - **Relief first** (J30): the mesh and its relief colour show as soon as the tiles are read; the
 *   habitat scores and the creeks follow on the same mesh. The old square stays until then.
 * - **Gestures:** one finger drags the ground under it ([CameraMath.pan]); two fingers pinch to
 *   zoom, twist to rotate and slide to tilt, on MapLibre's own constants ([GestureMath], A17).
 * - **Layers:** habitat, creeks, contours and hillshade are baked into the ground's texture; where
 *   you've been and the J20 grey-out come from the travel memory's own mask ([TravelMask]), so a
 *   new step does not re-bake the colour; finds, suggestions, the track and you are drawn on top.
 *
 * Battery (J32, J24): a frame only when something drawn changed, at most one per
 * [BatteryMode.FRAME_MS] ([FramePacer]; 20 a second in the battery mode, with half the mesh, a
 * quarter of the texture and no map snapshot); the GL thread pauses with the screen; nothing is
 * built while a gesture runs, and nothing at all before the first fix ([waitForFix]).
 */
@Composable
fun Terrain3DView(
    me: FieldLocation?,
    /** The one camera (exe.md A1): gestures report it, the app moves it. */
    camera: SharedCamera,
    track: List<TrackPoint>,
    finds: List<Find>,
    suggestions: List<Suggestion>,
    layers: MapLayerState,
    weights: DoubleArray,
    demStore: DemTileStore,
    /** The map to drape on the ground ([Basemap.NONE]: none; the battery mode passes NONE). */
    basemap: Basemap,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The scan's centre: its radius ring is drawn where it crosses the square. */
    radiusCenter: Pair<Double, Double>? = null,
    /** The built square, held by the ViewModel (A16): a rotation picks it up instead of rebuilding it. */
    session: MeshSession = remember { MeshSession(null) },
    /** The app's memory budget (A13): emptied, and the build tried once more, when a build runs out of memory. */
    budget: MemoryBudget? = null,
    /** The travel memory (J31): what it holds over a square, and what this process has added since. */
    travel: TravelSource? = null,
    /** The battery mode (J24): lighter mesh and texture, no map snapshot, 20 frames a second. */
    lighter: Boolean = false,
    /** True until the first fix: nothing is built at the start position's fallback, far from the owner. */
    waitForFix: Boolean = false,
    /** A searched place (P.1), pinned on the ground. */
    pin: com.ginsengo.steward.ui.map.PlaceSearch.Place? = null,
    /** The legend box shown or collapsed (P.1); the view reports a tap on it. */
    onLegendChange: (Boolean) -> Unit = {},
    /** N.1: which way the phone faces (degrees from true north), drawn as a cone on your position. */
    heading: Float? = null,
    /** N.1: where the owner is walking to: a line on the ground from you to it. */
    target: com.ginsengo.steward.ui.map.PlaceSearch.Place? = null,
    /** N.1: the owner's saved places, marked on the ground. */
    places: List<com.ginsengo.steward.field.SavedPlace> = emptyList(),
    /** N.1: the inspect card's "Go here" and "Save". */
    onGoHere: (String, Double, Double) -> Unit = { _, _, _ -> },
    onSave: (Double, Double) -> Unit = { _, _ -> },
    /** N.1: the owner moved the map by hand (follow mode stops). */
    onUserMove: () -> Unit = {},
) {
    val context = LocalContext.current
    val renderer = remember { TerrainGlRenderer() }
    val glRef = remember { arrayOfNulls<GLTextureView>(1) }
    val textMeasurer = rememberTextMeasurer()
    val status by rememberUpdatedState(onStatus)
    val currentLayers by rememberUpdatedState(layers)

    var size by remember { mutableStateOf(0 to 0) }
    fun viewportW() = size.first.takeIf { it > 0 } ?: DEFAULT_VIEWPORT_W
    // The shared camera, as Compose state: every report or move redraws.
    val snap by camera.state.collectAsState()
    val cam = snap.camera
    // This view's side of the epoch rule: app moves are applied once; its own reports never.
    val follower = remember { SharedCamera.Follower(camera.state.value.epoch) }
    // A square held from before a rotation (A16), if it still covers the camera.
    val relief = layers.relief
    val held = remember { session.scene?.takeIf { it.contains(camera.camera.lat, camera.camera.lng) && session.lighter == lighter && it.exaggeration == relief.toDouble() } }
    var fitted by remember { mutableStateOf(held != null && session.fitted) }
    var scene by remember { mutableStateOf(held) }
    var buildAt by remember { mutableStateOf(camera.camera.let { it.lat to it.lng }) }
    // The square's elevation zoom (SquareLevel): chosen from the camera's zoom.
    var level by remember {
        mutableIntStateOf(held?.level ?: SquareLevel.forZoom(camera.camera.zoom, camera.camera.lat, DEFAULT_VIEWPORT_W, null))
    }
    // True when the user dragged the camera off the square (settle onto the new ground keeping
    // the eye); false after an app move, which teleports (ground on the new centre instead).
    var pannedOut by remember { mutableStateOf(false) }
    // Tap-to-inspect (P.1): the ground point last tapped, with what the app knows about it.
    var inspect by remember { mutableStateOf<Inspection?>(null) }
    // The camera looks at the plane through the ground under its centre, in metres (see
    // MapCamera.mvpForMeshBuiltAt); fixed during a gesture, re-based when it ends.
    var anchorM by remember { mutableDoubleStateOf(held?.elevationAt(camera.camera.lat, camera.camera.lng) ?: Double.NaN) }
    var drape by remember { mutableStateOf(held?.let { h -> session.drape?.let { h.mosaic to it } }) }
    var sceneNote by remember { mutableStateOf(held?.describe() ?: "") }
    var drapeNote by remember { mutableStateOf("") }
    // Set (once, on the main thread) when the GL thread first draws terrain.
    var glDrawn by remember { mutableStateOf(false) }
    DisposableEffect(renderer) {
        val main = Handler(Looper.getMainLooper())
        val posted = java.util.concurrent.atomic.AtomicBoolean(false)
        renderer.onTerrainDrawn = { if (posted.compareAndSet(false, true)) main.post { glDrawn = true } }
        // F.1: a drawing failure says so on screen (it used to be logged only, and the map stayed blank).
        renderer.onError = { msg -> main.post { status("3D drawing failed on this phone: $msg (Layers → Field diagnostics)") } }
        onDispose { renderer.onTerrainDrawn = null; renderer.onError = null }
    }

    // Frames on demand, paced (J32): the last request of a gesture is always drawn.
    val pacer = remember { FramePacer(BatteryMode.FRAME_MS) }
    pacer.minIntervalMs = if (lighter) BatteryMode.FRAME_MS_SAVER else BatteryMode.FRAME_MS
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    fun requestFrame() {
        val wait = pacer.request(SystemClock.uptimeMillis())
        if (wait == 0L) glRef[0]?.requestRender()
        else if (wait > 0) mainHandler.postDelayed({ pacer.trailingDrawn(SystemClock.uptimeMillis()); glRef[0]?.requestRender() }, wait)
    }

    // The GL thread must pause and resume with the screen, or it keeps a context (and a
    // battery) alive in the background; the renderer re-uploads after a context loss.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> glRef[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> glRef[0]?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Published on every change, even to the same text: "Tracing creeks…" must always be replaced.
    fun publish() { if (sceneNote.isNotEmpty()) status(sceneNote + drapeNote) }

    /** Rebuilds when the camera's centre has left the square or its zoom calls for another level. */
    fun rebuildIfNeeded(c: CameraState) {
        val want = SquareLevel.forZoom(c.zoom, c.lat, viewportW(), scene?.level)
        val sc = scene
        if (sc == null || !sc.contains(c.lat, c.lng) || want != sc.level) { level = want; buildAt = c.lat to c.lng }
    }

    // App moves of the shared camera (the first fix, recentre, "Show on map", the compass): ground
    // the camera on its new centre, or build the square there.
    LaunchedEffect(snap.epoch) {
        val moved = follower.take(snap) ?: return@LaunchedEffect
        val c = moved.camera
        pannedOut = false
        scene?.elevationAt(c.lat, c.lng)?.let { anchorM = it }
        rebuildIfNeeded(c)
    }

    // ---- terrain: relief first, then habitat and creeks on the same mesh (J30).
    val (bLat, bLng) = buildAt
    val buildKey = "$level:${DemTileStore.lonToTileX(bLng, level)}:${DemTileStore.latToTileY(bLat, level)}"
    LaunchedEffect(buildKey, weights.contentHashCode(), lighter, waitForFix, relief) {
        if (waitForFix && scene == null) {
            status("Waiting for a GPS fix… (or search a place)")
            com.ginsengo.steward.perf.FieldDiagnostics.square = "waiting for the first GPS fix"
            return@LaunchedEffect
        }
        val have = scene
        if (have != null && have === session.scene && !have.quick && have.level == level && have.contains(bLat, bLng) &&
            session.weightsKey == weights.contentHashCode() && session.lighter == lighter && have.exaggeration == relief.toDouble()) {
            publish(); return@LaunchedEffect          // the square held across a rotation (A16)
        }
        val t0 = SystemClock.elapsedRealtime()
        status("Loading elevation…")
        val area = loadArea(demStore, bLat, bLng, level)
        if (area == null) {
            com.ginsengo.steward.perf.FieldDiagnostics.square = "no elevation tiles for level $level here (offline, or the tile server unreachable)"
            status("No elevation tiles here yet. Connect once, or save the area for offline use.")
            return@LaunchedEffect
        }
        val gridCap = if (lighter) BatteryMode.GRID_CAP else Terrain3D.MAX_GRID
        val textureCap = if (lighter) BatteryMode.TEXTURE_CAP else TerrainTextures.MAX_SIZE
        val l = currentLayers
        val quick = withContext(Dispatchers.Default) {
            afterTrimIfOutOfMemory(budget, "3D relief") {
                val q = Terrain3D.buildQuick(area.first, exaggeration = relief, gridCap = gridCap, textureCap = textureCap)
                q to q.texture(if (SceneLayer.HABITAT.shown(l)) TerrainTextures.Mode.HABITAT else TerrainTextures.Mode.ELEVATION, MeshLayers.baked(l))
            }
        }
        if (quick == null) { status("Could not build the 3D terrain here."); return@LaunchedEffect }
        val q = quick.first
        // Colour first, then mesh and colour together: the new ground never wears the old square's texture.
        renderer.submitMesh(q.mesh)
        renderer.submitTexture(TerrainGlRenderer.Texture(quick.second, q.textureSize))
        if (drape?.first !== q.mosaic) drape = null
        session.adopt(q, weights.contentHashCode(), lighter)     // the old square goes back to the budget (A13)
        scene = q
        val c = camera.camera
        val (vw, vh) = size
        if (pannedOut && fitted && !anchorM.isNaN() && vw > 0 && vh > 0) {
            // The user dragged off the old square: put the camera on the new ground without moving
            // the eye, as after a gesture.
            val (next, ground) = Terrain3D.settle(c, vw, vh, q, anchorM)
            camera.report(next); anchorM = ground
        } else {
            anchorM = q.elevationAt(c.lat, c.lng) ?: ((q.mesh.minElevationM + q.mesh.maxElevationM) / 2.0)
        }
        pannedOut = false
        sceneNote = q.describe() + area.second
        publish()
        //noinspection LogNotTimber
        Log.i(TAG, "relief: level ${q.level} in ${SystemClock.elapsedRealtime() - t0} ms")
        com.ginsengo.steward.perf.FieldDiagnostics.square = "level ${q.level}, %.1f km, relief in %.1f s, colouring…".format(q.widthM / 1000, (SystemClock.elapsedRealtime() - t0) / 1000.0)
        // The habitat scores and the creeks, on the same mesh: the camera and its anchor stay put.
        val tpi = SquareLevel.tpiRadiusM(q.level, bLat, viewportW())
        val full = withContext(Dispatchers.Default) {
            afterTrimIfOutOfMemory(budget, "3D build") {
                Terrain3D.build(area.first, weights, exaggeration = relief, tpiRadiusM = tpi, gridCap = gridCap, textureCap = textureCap, mesh = q.mesh)
            }
        }
        if (full == null) { status(q.describe().replace("colouring the habitat…", "could not colour the habitat here")); return@LaunchedEffect }
        session.adopt(full, weights.contentHashCode(), lighter)
        scene = full
        sceneNote = full.describe() + area.second
        publish()
        //noinspection LogNotTimber
        Log.i(TAG, "habitat: level ${full.level} in ${SystemClock.elapsedRealtime() - t0} ms")
        com.ginsengo.steward.perf.FieldDiagnostics.square = "level ${full.level}, %.1f km, complete in %.1f s".format(full.widthM / 1000, (SystemClock.elapsedRealtime() - t0) / 1000.0)
    }

    // ---- the chosen map (streets, satellite, topo), drawn by MapLibre for this square, under the
    // app's layers (once per square and map type).
    val square = scene?.mosaic
    var drapeOf by remember { mutableStateOf(if (drape != null) basemap else null) }
    LaunchedEffect(square, basemap) {
        val s = scene ?: return@LaunchedEffect
        val style = basemap.style
        if (style == null) { drape = null; drapeOf = null; drapeNote = ""; publish(); return@LaunchedEffect }
        if (drape?.first === s.mosaic && drapeOf == basemap) return@LaunchedEffect
        MapDrape.installRequestCounter()   // debug builds only: the device gate's instrument
        drapeNote = " · drawing the ${basemap.label.lowercase()} map on the ground…"
        publish()
        val px = MapDrape.render(context, style, s.north, s.west, s.south, s.east, s.textureSize, DRAPE_TIMEOUT_MS, basemap.maxZoom)
        if (scene?.mosaic !== s.mosaic) return@LaunchedEffect
        com.ginsengo.steward.perf.FieldDiagnostics.mapDrape = "${basemap.label}: " + (if (px != null) "drawn" else "failed (offline or not cached)")
        if (px != null) {
            drape = s.mosaic to px; drapeOf = basemap; drapeNote = " · ${basemap.label.lowercase()} map on the ground"
            // Under the shared budget (A13): evicted, the ground is coloured without the map.
            session.keepDrape(px) { drape = null }
        } else {
            drape = null; drapeOf = null
            drapeNote = if (basemap == Basemap.DARK) " · map not cached here (Save 10 miles to have it offline)"
            else " · ${basemap.label.lowercase()} needs a connection here"
        }
        publish()
    }

    // ---- N.1: roads and trails (OpenStreetMap), a transparent snapshot baked over everything. Kept in
    // the battery mode too: the way out matters more than the frame rate.
    val roadsOn = SceneLayer.ROADS.shown(layers)
    var overlay by remember { mutableStateOf<Pair<DemTileStore.Mosaic, IntArray>?>(null) }
    var roadsNote by remember { mutableStateOf("") }
    LaunchedEffect(square, roadsOn) {
        val s = scene ?: return@LaunchedEffect
        if (!roadsOn) { overlay = null; roadsNote = ""; return@LaunchedEffect }
        if (overlay?.first === s.mosaic) return@LaunchedEffect
        val px = MapDrape.render(context, com.ginsengo.steward.ui.map.ROADS_STYLE, s.north, s.west, s.south, s.east,
            s.textureSize, DRAPE_TIMEOUT_MS, maxZoom = 14.0, pixelRatio = 1f)
        if (scene?.mosaic !== s.mosaic) return@LaunchedEffect
        overlay = px?.takeIf { MapDrape.mostlyClear(it) }?.let { s.mosaic to it }
        com.ginsengo.steward.perf.FieldDiagnostics.roads = when {
            px == null -> "failed (offline or not cached)"
            overlay == null -> "refused: the snapshot came back opaque"
            else -> "drawn"
        }
        roadsNote = if (overlay != null) "" else " · roads & trails need a connection here (or Save 10 miles)"
        if (roadsNote.isNotEmpty()) { drapeNote += roadsNote; publish() }
    }

    // A square held across a rotation reaches a new GL surface here (a built one was submitted above).
    LaunchedEffect(scene) { scene?.let { if (!renderer.hasMesh(it.mesh)) renderer.submitMesh(it.mesh) } }

    // ---- colour: re-baked only when the layers, the basemap or the terrain change.
    val drapePx = drape?.takeIf { it.first === scene?.mosaic }?.second
    val mode = when {
        drapePx != null -> TerrainTextures.Mode.MAP
        SceneLayer.HABITAT.shown(layers) -> TerrainTextures.Mode.HABITAT
        else -> TerrainTextures.Mode.ELEVATION
    }
    val texLayers = MeshLayers.baked(layers)
    val overlayPx = overlay?.takeIf { it.first === scene?.mosaic }?.second
    LaunchedEffect(scene, mode, texLayers, drapePx, overlayPx) {
        val s = scene ?: return@LaunchedEffect
        val px = withContext(Dispatchers.Default) { s.texture(mode, texLayers, drapePx, overlayPx) }
        renderer.submitTexture(TerrainGlRenderer.Texture(px, s.textureSize))
        requestFrame()
    }

    // ---- the travel memory over this square (J31) and its 15 m buffer for J20.
    val memoryOn = MeshLayers.onMemory(SceneLayer.VISITED, layers)
    val unwalkedOn = MeshLayers.onMemory(SceneLayer.UNWALKED, layers)
    val noTravel = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
    val travelVersion by (travel?.version ?: noTravel).collectAsState()
    var mask by remember { mutableStateOf<TravelMask?>(null) }
    var maskSquare by remember { mutableStateOf<DemTileStore.Mosaic?>(null) }
    var maskSeen by remember { mutableIntStateOf(0) }
    LaunchedEffect(square, travel) {
        val s = scene ?: return@LaunchedEffect
        val t = travel ?: return@LaunchedEffect
        val seen = t.version.value
        val m = withContext(Dispatchers.IO) {
            TravelMask(s.north, s.west, s.south, s.east, s.widthM).also {
                it.markAll(t.cellsIn(s.north, s.west, s.south, s.east))
                it.markAll(t.recentSince(0))
            }
        }
        if (scene?.mosaic !== s.mosaic) return@LaunchedEffect
        mask = m; maskSquare = s.mosaic; maskSeen = seen
    }
    LaunchedEffect(mask, travelVersion, unwalkedOn) {
        val m = mask ?: return@LaunchedEffect
        val t = travel ?: return@LaunchedEffect
        // Steps arrive every few seconds while walking: coalesced, at most one upload per burst.
        if (maskSeen < travelVersion) kotlinx.coroutines.delay(MEMORY_COALESCE_MS)
        val fresh = t.recentSince(maskSeen)
        maskSeen = t.version.value
        m.markAll(fresh)
        val rg = withContext(Dispatchers.Default) { m.rg(if (unwalkedOn) m.dilated() else null) }
        renderer.submitMemory(TerrainGlRenderer.Memory(rg, m.size))
        requestFrame()
    }

    // ---- camera: brought into the one map's range once (in an effect: never written during
    // composition), then drawn every time it changes.
    LaunchedEffect(scene, size.first) {
        val s0 = scene ?: return@LaunchedEffect
        if (fitted || size.first <= 0) return@LaunchedEffect
        fitted = true
        session.fitted = true
        val lat = (s0.north + s0.south) / 2
        camera.move(CameraMath.to3d(camera.camera, SquareLevel.minZoom(lat, size.first), SquareLevel.maxZoom(lat, size.first)))
    }
    val (w, h) = size
    val s = scene
    val view = cam.takeIf { fitted && w > 0 && h > 0 && !anchorM.isNaN() }
    val mc = view?.let { CameraMath.mapCamera(it, w, h) }
    val ready = s != null && mc != null && glDrawn && s.contains(cam.lat, cam.lng)
    LaunchedEffect(ready) {
        // The device gate's measurement (no position in it).
        // The app logs through android.util.Log throughout (Timber is not a dependency).
        //noinspection LogNotTimber
        if (ready) Log.i(TAG, "ready: level ${s?.level} (%.1f km) at pitch %.0f".format((s?.widthM ?: 0.0) / 1000, cam.pitch) +
            " · depth ${DepthFallbackChooser.chosenBits} bits" + (if (s?.hasHoles == true) " · holes (no elevation)" else "") +
            (if (lighter) " · battery mode" else "") + (budget?.let { " · " + it.report() } ?: ""))
    }
    // A GL frame only when what it draws changed: a GPS fix or a track point recomposes this
    // screen (the markers are on the Compose canvas), and must not redraw the terrain.
    remember(view, anchorM, s, w, h, memoryOn, unwalkedOn) {
        if (mc != null && s != null) {
            renderer.submitFrame(
                TerrainGlRenderer.Frame(
                    mvp = mc.mvpForMeshBuiltAt(Terrain3D.BUILD_ZOOM, s.mesh.originWorldX, s.mesh.originWorldY,
                        groundZ = anchorM * s.mesh.pixelsPerMeter * s.exaggeration,
                        // Depth precision from the ground in view (exe.md B7), not a fixed 48 px.
                        nearPx = DepthRange.near(mc, Terrain3D.highestAbovePlanePx(s, anchorM, mc.zoom, 1.0))),
                    hazeStart = (mc.cameraToCenterDistance * 1.1).toFloat(),
                    hazeEnd = (mc.cameraToCenterDistance * 3.6).toFloat(),
                    visitedOn = memoryOn,
                    unwalkedOn = unwalkedOn,
                )
            )
            requestFrame()
        }
        0
    }

    // The track line, decimated once per change of track or terrain, not per frame.
    val trackLines = remember(track, s) { s?.let { trackSegments(track, it) } ?: emptyList() }
    val showTrack = MeshLayers.onCanvas(SceneLayer.TRACK, layers)
    val ringLines = remember(radiusCenter, s) { if (s != null && radiusCenter != null) ringSegments(radiusCenter, s) else emptyList() }

    Box(modifier.background(Color(0xFF0E191E)).onSizeChanged { size = it.width to it.height }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLTextureView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(DepthFallbackChooser())
                    setPreserveEGLContextOnPause(true)
                    setRenderer(renderer)
                    setRenderMode(GLTextureView.RENDERMODE_WHEN_DIRTY)
                    glRef[0] = this
                }
            },
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        // A tap (P.1): one finger, within the touch slop, released quickly.
                        var multi = false; var far = false; var tEnd = first.uptimeMillis
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { tEnd = it.uptimeMillis }
                            if (event.changes.count { it.pressed } >= 2) multi = true
                            event.changes.firstOrNull { it.id == first.id }?.let {
                                if ((it.position - first.position).getDistance() > viewConfiguration.touchSlop) far = true
                            }
                            // The shared camera now (not the composition's snapshot): every
                            // pointer event builds on the camera the last one reported.
                            val c0 = camera.camera
                            val s0 = scene
                            val (vw, vh) = size
                            if (s0 != null && fitted && vw > 0 && vh > 0 && !anchorM.isNaN()) {
                                val down = event.changes.filter { it.pressed && it.previousPressed }
                                if (down.size == 1) {
                                    val p = down[0]
                                    if (p.position != p.previousPosition) {
                                        camera.report(CameraMath.pan(
                                            c0,
                                            p.previousPosition.x.toDouble(), p.previousPosition.y.toDouble(),
                                            p.position.x.toDouble(), p.position.y.toDouble(),
                                            vw, vh, Terrain3D.heightFn(s0, anchorM), Terrain3D.rangeFor(s0, anchorM),
                                        ))
                                        moved = true
                                    }
                                } else if (down.size >= 2) {
                                    val lat = c0.lat
                                    // MapLibre's own gesture constants (GestureMath, A17); the zoom range is the
                                    // one map's, across every level (SquareLevel).
                                    val zoom = c0.zoom + GestureMath.zoomDelta(event.calculateZoom())
                                    camera.report(c0.copy(
                                        zoom = zoom.coerceIn(min(SquareLevel.minZoom(lat, vw), c0.zoom), max(SquareLevel.maxZoom(lat, vw), c0.zoom)),
                                        bearing = ((c0.bearing + GestureMath.bearingDelta(event.calculateRotation())) % 360.0 + 360.0) % 360.0,
                                        pitch = (c0.pitch + GestureMath.pitchDelta(event.calculatePan().y)).coerceIn(15.0, 80.0),
                                    ))
                                    moved = true
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (!multi && !far && tEnd - first.uptimeMillis < TAP_MS) {
                            val s1 = scene
                            val (vw, vh) = size
                            if (s1 != null && vw > 0 && vh > 0 && !anchorM.isNaN()) {
                                val g = CameraMath.groundAt(camera.camera, first.position.x.toDouble(), first.position.y.toDouble(),
                                    vw, vh, Terrain3D.heightFn(s1, anchorM), Terrain3D.rangeFor(s1, anchorM))
                                inspect = g?.takeIf { s1.contains(it[0], it[1]) }?.let { Inspection.of(s1, it[0], it[1]) }
                            }
                        } else if (moved) {
                            val s1 = scene
                            val c1 = camera.camera
                            val (vw, vh) = size
                            if (s1 != null && vw > 0 && vh > 0) {
                                val (done, ground) = Terrain3D.settle(c1, vw, vh, s1, anchorM)
                                camera.report(done); anchorM = ground
                                if (!s1.contains(done.lat, done.lng)) pannedOut = true
                                rebuildIfNeeded(done)
                                onUserMove()
                            }
                        }
                    }
                }
        ) {
            if (mc == null || s == null) return@Canvas
            val anchor = anchorM
            val lift = s.exaggeration
            fun at(lat: Double, lng: Double): Offset? {
                val e = s.elevationAt(lat, lng) ?: return null
                val p = mc.project(lat, lng, (e - anchor) * lift) ?: return null
                return Offset(p[0], p[1])
            }
            fun lines(segments: List<DoubleArray>, colour: Color, stroke: Stroke) {
                val project = mc.projector()
                segments.forEach { seg ->
                    val path = Path()
                    var open = false
                    var k = 0
                    while (k < seg.size) {
                        val p = project(seg[k], seg[k + 1], (seg[k + 2] - anchor) * lift)
                        if (p == null) open = false
                        else if (!open) { path.moveTo(p[0], p[1]); open = true }
                        else path.lineTo(p[0], p[1])
                        k += 3
                    }
                    drawPath(path, colour, style = stroke)
                }
            }
            val dark = Color(0xE6061008)
            // SceneLayer order, bottom to top: the track, finds, ring, suggestions, you.
            if (showTrack && trackLines.isNotEmpty()) {
                lines(trackLines, Color(0xCC6FB7FF), Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // OCCLUDED layers (A18): a marker the terrain hides from the eye is drawn faint, so a
            // suggestion behind a ridge no longer looks as if it sat on the slope in front of it.
            val heightAt = { la: Double, lo: Double -> ((s.elevationAt(la, lo) ?: anchor) - anchor) * lift }
            val topM = (s.mesh.maxElevationM - anchor) * lift
            fun seen(lat: Double, lng: Double): Float {
                val e = s.elevationAt(lat, lng) ?: return 1f
                return if (Occlusion.hidden(mc, lat, lng, (e - anchor) * lift, heightAt, topM)) HIDDEN_ALPHA else 1f
            }
            if (MeshLayers.onCanvas(SceneLayer.FINDS, layers)) finds.forEach { f ->
                val o = at(f.lat, f.lng) ?: return@forEach
                val a = seen(f.lat, f.lng)
                drawCircle(dark, 9f, o, alpha = a)
                drawCircle(Color(0xFFFFB02E), 6.5f, o, alpha = a)
            }
            if (MeshLayers.onCanvas(SceneLayer.RING, layers) && ringLines.isNotEmpty()) {
                lines(ringLines, Color(0x73E6F4EC), Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))))
            }
            if (MeshLayers.onCanvas(SceneLayer.SUGGESTIONS, layers)) suggestions.forEach { sg ->
                val o = at(sg.lat, sg.lng) ?: return@forEach
                val a = seen(sg.lat, sg.lng)
                val ring = (if (sg.provenance == Suggestion.PROVENANCE_MODEL) Color(0xFF00FF88) else Color(0xFFE6F4EC)).copy(alpha = a)
                drawCircle(dark, 17f, o, alpha = a)
                drawCircle(ring, 17f, o, style = Stroke(3.5f))
                val t = textMeasurer.measure("${sg.rank}", TextStyle(color = ring, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
            }
            if (MeshLayers.onCanvas(SceneLayer.ME, layers)) me?.let { here ->
                at(here.lat, here.lng)?.let { o ->
                    val a = seen(here.lat, here.lng)
                    drawCircle(Color(0x6600FF88), 22f, o, alpha = a)
                    drawCircle(dark, 11f, o, alpha = a)
                    drawCircle(Color(0xFFE6F4EC), 8f, o, alpha = a)
                    val t = textMeasurer.measure("You", TextStyle(color = Color(0xFFE6F4EC).copy(alpha = a), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                    drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - 26f - t.size.height))
                }
            }
            // N.1: saved places (a diamond and the name), the line to the target, your heading.
            places.forEach { pl ->
                val o = at(pl.lat, pl.lng) ?: return@forEach
                val a = seen(pl.lat, pl.lng)
                val d = Path().apply { moveTo(o.x, o.y - 11f); lineTo(o.x + 9f, o.y); lineTo(o.x, o.y + 11f); lineTo(o.x - 9f, o.y); close() }
                drawPath(d, dark, alpha = a, style = Stroke(5f))
                drawPath(d, Color(0xFFE6F4EC), alpha = a)
                val t = textMeasurer.measure(pl.name.take(18), TextStyle(color = Color(0xFFE6F4EC).copy(alpha = a), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - 16f - t.size.height))
            }
            target?.let { tg ->
                val here = me
                if (here != null) {
                    val steps = 64
                    val seg = ArrayList<Double>()
                    val runs = ArrayList<DoubleArray>()
                    for (k in 0..steps) {
                        val la = here.lat + (tg.lat - here.lat) * k / steps
                        val lo = here.lng + (tg.lng - here.lng) * k / steps
                        val e = s.elevationAt(la, lo)
                        if (e == null) { if (seg.size >= 6) runs += seg.toDoubleArray(); seg.clear() }
                        else { seg += la; seg += lo; seg += e }
                    }
                    if (seg.size >= 6) runs += seg.toDoubleArray()
                    lines(runs, Color(0xE600FF88), Stroke(4.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))))
                }
                at(tg.lat, tg.lng)?.let { o ->
                    drawLine(dark, o, o.copy(y = o.y - 36f), strokeWidth = 6f)
                    drawLine(Color(0xFF00FF88), o, o.copy(y = o.y - 36f), strokeWidth = 3f)
                    val flag = Path().apply { moveTo(o.x, o.y - 36f); lineTo(o.x + 20f, o.y - 29f); lineTo(o.x, o.y - 22f); close() }
                    drawPath(flag, Color(0xFF00FF88))
                }
            }
            if (heading != null) me?.let { here ->
                val o = at(here.lat, here.lng)
                val tipLL = com.ginsengo.steward.field.Guidance.destination(here.lat, here.lng, heading.toDouble(), 60.0)
                val tip = at(tipLL.first, tipLL.second)
                if (o != null && tip != null) {
                    val dx = tip.x - o.x; val dy = tip.y - o.y
                    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                    val ux = dx / len; val uy = dy / len
                    val cone = Path().apply {
                        moveTo(o.x + ux * 46f, o.y + uy * 46f)
                        lineTo(o.x - uy * 13f + ux * 12f, o.y + ux * 13f + uy * 12f)
                        lineTo(o.x + uy * 13f + ux * 12f, o.y - ux * 13f + uy * 12f)
                        close()
                    }
                    drawPath(cone, Color(0x9900FF88))
                }
            }
            // P.1: the searched place, a pin; the tapped point, a ring.
            pin?.let { pl ->
                at(pl.lat, pl.lng)?.let { o ->
                    drawLine(dark, o, o.copy(y = o.y - 34f), strokeWidth = 6f)
                    drawLine(Color(0xFFFF6B5A), o, o.copy(y = o.y - 34f), strokeWidth = 3f)
                    drawCircle(dark, 12f, o.copy(y = o.y - 40f))
                    drawCircle(Color(0xFFFF6B5A), 9.5f, o.copy(y = o.y - 40f))
                    drawCircle(Color(0xFFE6F4EC), 3.5f, o.copy(y = o.y - 40f))
                }
            }
            inspect?.let { ins ->
                at(ins.lat, ins.lng)?.let { o ->
                    drawCircle(dark, 15f, o, style = Stroke(6f))
                    drawCircle(Color(0xFFE6F4EC), 15f, o, style = Stroke(3f))
                    drawCircle(Color(0xFFE6F4EC), 2.5f, o)
                }
            }
        }

        // Compass: shows where north is; tap to face north, reset the tilt and fit the square.
        SmallFloatingActionButton(
            onClick = {
                val s0 = scene
                if (s0 != null && size.first > 0) {
                    camera.move(camera.camera.copy(bearing = 0.0, pitch = DEFAULT_PITCH.toDouble(), zoom = fitZoomFor(s0, size.first)))
                }
            },
            containerColor = Gen.SurfaceHigh, contentColor = Gen.Text,
            // Above the screen's side buttons, clear of the status chips.
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).offset(y = (-50).dp)
                .semantics { contentDescription = "Face north and reset the view" },
        ) { Icon(Icons.Filled.Navigation, null, modifier = Modifier.rotate(-cam.bearing.toFloat())) }

        val ins = inspect
        if (ins != null) {
            InspectCard(ins, me, context, { inspect = null },
                onGoHere = { onGoHere("Point", ins.lat, ins.lng); inspect = null },
                onSave = { onSave(ins.lat, ins.lng); inspect = null },
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 92.dp))
        } else if (s != null && layers.legend) Legend(
            s, mode, layers, showTrack && trackLines.isNotEmpty(), memoryOn, unwalkedOn, basemap,
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 96.dp)
                .clickable { onLegendChange(false) },
        ) else if (s != null) Text(
            "Legend", color = Gen.TextDim, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 96.dp)
                .background(Gen.Bg.copy(alpha = 0.78f), RoundedCornerShape(10.dp)).clickable { onLegendChange(true) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** What the app knows about a tapped ground point (P.1, Earth-style "what is here"). All on the phone. */
private data class Inspection(
    val lat: Double, val lng: Double, val elevationM: Double?, val slopeDeg: Double?, val aspectDeg: Double?, val score: Double?,
) {
    companion object {
        fun of(s: Terrain3D.Scene, lat: Double, lng: Double): Inspection {
            val sa = s.slopeAspectAt(lat, lng)
            return Inspection(lat, lng, s.elevationAt(lat, lng), sa?.first, sa?.second?.takeIf { it >= 0 }, s.scoreAt(lat, lng))
        }
    }
}

private val COMPASS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private fun compass(deg: Double) = COMPASS[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

@Composable
private fun InspectCard(
    ins: Inspection, me: FieldLocation?, context: android.content.Context, onClose: () -> Unit,
    onGoHere: () -> Unit, onSave: () -> Unit, modifier: Modifier,
) {
    val coords = "%.5f, %.5f".format(java.util.Locale.US, ins.lat, ins.lng)
    Column(
        modifier.fillMaxWidth().background(Gen.Surface.copy(alpha = 0.96f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(coords, color = Gen.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("✕", color = Gen.TextDim, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        ins.elevationM?.let { Text("Elevation %,d ft · %,d m".format((it * 3.28084).roundToInt(), it.roundToInt()), color = Gen.Text, fontSize = 13.sp) }
        if (ins.slopeDeg != null) Text(
            "Slope %.0f°".format(ins.slopeDeg) + (ins.aspectDeg?.let { " · faces ${compass(it)} (%.0f°)".format(it) } ?: " · flat"),
            color = Gen.Text, fontSize = 13.sp,
        )
        ins.score?.let { sc ->
            val band = when { sc >= 0.7 -> "strong"; sc >= 0.55 -> "good"; sc >= TerrainTextures.MIN_SCORE -> "fair"; else -> "weak" }
            Text("Terrain score %.2f · %s (model estimate, not a sighting)".format(sc, band), color = Gen.Accent, fontSize = 13.sp)
        }
        me?.let { m ->
            val d = com.ginsengo.steward.prospect.Prospects.distanceMetres(m.lat, m.lng, ins.lat, ins.lng)
            val b = com.ginsengo.steward.prospect.Prospects.bearingTrue(m.lat, m.lng, ins.lat, ins.lng)
            Text(com.ginsengo.steward.field.WayBack.describe(com.ginsengo.steward.field.WayBack.Leg(d, b)) + " from you", color = Gen.TextDim, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            androidx.compose.material3.Button(onClick = onGoHere) { Text("Go here") }
            androidx.compose.material3.OutlinedButton(onClick = onSave) { Text("Save") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = {
                // Opens Google Maps (or the browser) with this point as the destination: the owner's tap
                // is what sends the point there.
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$coords".replace(" ", "")))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Directions") }
            androidx.compose.material3.OutlinedButton(onClick = {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Coordinates", coords))
            }) { Text("Copy") }
        }
    }
}

@Composable
private fun Legend(
    s: Terrain3D.Scene, mode: TerrainTextures.Mode, layers: MapLayerState, track: Boolean,
    memory: Boolean, unwalked: Boolean, basemap: Basemap, modifier: Modifier,
) {
    Column(
        modifier.widthIn(max = 220.dp).background(Gen.Bg.copy(alpha = 0.78f), RoundedCornerShape(10.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (mode == TerrainTextures.Mode.ELEVATION || layers.habitat) {
            val stops = if (mode == TerrainTextures.Mode.ELEVATION) TerrainTextures.ELEVATION_RAMP else SuitabilityRasterizer.RAMP
            Box(Modifier.width(132.dp).height(8.dp).background(
                Brush.horizontalGradient(stops.map { Color(0xFF000000 or it.toLong()) }), RoundedCornerShape(4.dp)))
            Text(
                if (mode == TerrainTextures.Mode.ELEVATION)
                    "Elevation: %,d → %,d m".format(s.mesh.minElevationM.roundToInt(), s.mesh.maxElevationM.roundToInt())
                else "Habitat: weak → strong",
                color = Gen.Text, fontSize = 11.sp,
            )
        }
        // Unknown ground (exe.md B5): a hole showing the sky, not weak ground. Only when there is one.
        if (s.hasHoles) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(10.dp).background(Color(0xFF0E191E)).border(1.dp, Gen.TextDim))
            Spacer(Modifier.size(6.dp))
            Text("Hole: no elevation data", color = Gen.TextDim, fontSize = 11.sp)
        }
        if (layers.water) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFF3FB2F7)))
            Spacer(Modifier.size(6.dp))
            Text("Creeks, from elevation", color = Gen.TextDim, fontSize = 11.sp)
        }
        if (layers.roads) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFFFFB02E)))
                Spacer(Modifier.size(6.dp))
                Text("Logging / forest roads (OSM tracks)", color = Gen.TextDim, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFFFF6B5A)))
                Spacer(Modifier.size(6.dp))
                Text("Trails", color = Gen.TextDim, fontSize = 11.sp)
            }
        }
        if (memory) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(10.dp).background(Gen.Text.copy(alpha = 0.5f)))
            Spacer(Modifier.size(6.dp))
            Text("Where I've been (every fix, map open or Track on)", color = Gen.TextDim, fontSize = 11.sp)
        }
        if (unwalked) Text("Grey: within 15 m of where I've been", color = Gen.TextDim, fontSize = 11.sp)
        if (track) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFF6FB7FF)))
            Spacer(Modifier.size(6.dp))
            Text("Track line", color = Gen.TextDim, fontSize = 11.sp)
        }
        Text(
            (if (layers.contours) "Contours every %.0f m · ".format(s.contourM) else "") +
                "relief ×%.1f".format(s.exaggeration),
            color = Gen.TextDim, fontSize = 11.sp,
        )
        // The draped basemap's attribution, owed because the snapshot is drawn without it.
        if (mode == TerrainTextures.Mode.MAP && basemap.attribution.isNotEmpty()) Text(basemap.attribution, color = Gen.TextDim, fontSize = 10.sp)
        if (layers.roads) Text("Roads & trails © OpenFreeMap © OpenStreetMap contributors", color = Gen.TextDim, fontSize = 10.sp)
        Text("Tap the ground for details · tap here to hide", color = Gen.TextDim, fontSize = 10.sp)
    }
}

/**
 * The best square the cached tiles allow for [level]: that level, then the two coarser ones, each
 * needing its whole interior; failing that, the most complete one, with a note. Missing halo tiles
 * are edge-extended by the store and do not block a build.
 */
private suspend fun loadArea(demStore: DemTileStore, lat: Double, lng: Double, level: Int): Pair<DemTileStore.Mosaic, String>? {
    var best: DemTileStore.Mosaic? = null
    for (z in level downTo maxOf(SquareLevel.COARSEST, level - 2)) {
        val b = Terrain3D.areaBounds(lat, lng, z)
        val m = demStore.grid(b[0], b[1], b[2], b[3], z, haloTiles = 1) ?: continue
        if (m.missingInterior == 0) return m to (if (z != level) " · zoom-$z elevation (zoom $level not saved here)" else "")
        if (best == null || m.missingInterior < best.missingInterior) best = m
    }
    val m = best ?: return null
    return m to " · ${m.missingInterior} tile(s) without elevation, left as holes"
}

private fun fitZoomFor(s: Terrain3D.Scene, viewportWidthPx: Int): Double =
    Terrain3D.fitZoom(s.widthM, (s.north + s.south) / 2, viewportWidthPx)

/**
 * The recorded track inside the square, one flat (lat, lng, elevation) array per session,
 * thinned to at most [MAX_TRACK_POINTS] in all: drawn every frame, so its cost is capped.
 */
private fun trackSegments(track: List<TrackPoint>, s: Terrain3D.Scene): List<DoubleArray> {
    val inside = track.filter { s.contains(it.lat, it.lng) }
    if (inside.size < 2) return emptyList()
    val stride = (inside.size + MAX_TRACK_POINTS - 1) / MAX_TRACK_POINTS
    return inside.groupBy { it.sessionId }.values.mapNotNull { seg ->
        val kept = seg.filterIndexed { i, _ -> i % stride == 0 || i == seg.size - 1 }
        if (kept.size < 2) null
        else DoubleArray(kept.size * 3).also { out ->
            kept.forEachIndexed { i, p ->
                out[3 * i] = p.lat; out[3 * i + 1] = p.lng
                out[3 * i + 2] = s.elevationAt(p.lat, p.lng) ?: 0.0
            }
        }
    }
}

/**
 * Runs [work]; if it runs out of memory, empties the shared caches (the budget's purpose, A13) and
 * runs it once more. Found on A.3's device run: a 3D build ran the emulator's 192 MB heap out while
 * the 10-mile scan's tiles filled the cache. Any other failure, or a second out-of-memory, is logged
 * and gives null: the caller says so on screen.
 */
@SuppressLint("LogNotTimber")   // the app logs through android.util.Log throughout; exempt by name
private inline fun <T> afterTrimIfOutOfMemory(budget: MemoryBudget?, what: String, work: () -> T): T? {
    for (attempt in 0..1) {
        try {
            return work()
        } catch (e: OutOfMemoryError) {
            if (attempt == 1 || budget == null) { Log.e(TAG, "$what failed: out of memory", e); return null }
            budget.trim(MemoryBudget.TRIM_RUNNING_CRITICAL)
            Log.w(TAG, "$what: out of memory; caches trimmed (${budget.report()}), trying once more")
        } catch (e: Exception) {
            Log.e(TAG, "$what failed", e); return null
        }
    }
    return null
}

/**
 * The scan's radius ring where it crosses the square, as (lat, lng, elevation) runs, densified to
 * about one point per 50 m of the 16 km circle so the few hundred metres inside the square bend
 * with the ground.
 */
private fun ringSegments(center: Pair<Double, Double>, s: Terrain3D.Scene): List<DoubleArray> {
    val vertices = (2 * Math.PI * RadiusScan.RADIUS_M / RING_STEP_M).toInt()
    val out = ArrayList<DoubleArray>()
    var run = ArrayList<Double>()
    fun flush() { if (run.size >= 6) out += run.toDoubleArray(); run = ArrayList() }
    for ((la, lo) in ring(center.first, center.second, RadiusScan.RADIUS_M, vertices)) {
        val e = s.elevationAt(la, lo)
        if (e == null) flush() else { run += la; run += lo; run += e }
    }
    flush()
    return out
}

private const val RING_STEP_M = 50.0
/** A touch released within this, without moving past the slop, is a tap (P.1 inspect). */
private const val TAP_MS = 400L
private const val TAG = "Terrain3D"
private const val DEFAULT_PITCH = 55f
/** Until the view is measured: a typical portrait phone's width, for the first level choice. */
private const val DEFAULT_VIEWPORT_W = 1080
/** Steps arrive every few seconds while walking: the memory's upload waits this long for more. */
private const val MEMORY_COALESCE_MS = 1_500L
/** A marker behind a ridge (A18): still there, plainly behind. */
private const val HIDDEN_ALPHA = 0.35f
private const val DRAPE_TIMEOUT_MS = 25_000L
private const val MAX_TRACK_POINTS = 3_000

/**
 * 24-bit depth where the device has it (the flat base and the terrain are far apart in
 * depth, and 16 bits made them fight), else 16. The stock chooser throws when its one
 * request cannot be met, which would take the whole app down with the 3D view.
 */
private class DepthFallbackChooser : GLTextureView.EGLConfigChooser {
    companion object {
        /** The depth size the last chooser got (logged on ready: B7's precision depends on it). */
        @Volatile var chosenBits = 0
    }

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for (depth in intArrayOf(24, 16)) {
            val attribs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, depth,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL10.EGL_NONE,
            )
            val found = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            if (egl.eglChooseConfig(display, attribs, configs, 1, found) && found[0] > 0) { chosenBits = depth; return configs[0]!! }
        }
        throw IllegalArgumentException("No OpenGL ES 3 configuration on this device")
    }
}

private const val EGL_OPENGL_ES3_BIT_KHR = 0x40
