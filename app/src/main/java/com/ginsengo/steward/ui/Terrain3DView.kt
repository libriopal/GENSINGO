package com.ginsengo.steward.ui

import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain3d.CameraMath
import com.ginsengo.steward.terrain3d.Terrain3D
import com.ginsengo.steward.terrain3d.TerrainGlRenderer
import com.ginsengo.steward.terrain3d.TerrainTextures
import com.ginsengo.steward.terrain3d.SharedCamera
import com.ginsengo.steward.ui.map.Basemap
import com.ginsengo.steward.ui.map.MapDrape
import com.ginsengo.steward.ui.map.MapLayerState
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The map in 3D: the same place, layers and camera as the 2D map (one-map blueprint), on
 * real terrain about 3 km square at the best elevation the tiles offer (3.9 m cells at zoom 15).
 *
 * - **One camera.** It starts from the 2D map's camera ([camera]) and reports where it ends up
 *   ([onCamera]), so switching back lands the map on the same view.
 * - **Moves like the map.** One finger drags the ground under it (the touched point on the
 *   terrain stays under the finger, [CameraMath.pan]); two fingers pinch to zoom, twist to
 *   rotate, and slide up or down to tilt. When a gesture ends the camera is put back on the
 *   ground without moving the eye ([CameraMath.reanchor]), and when its centre has left the
 *   built square the terrain is rebuilt there.
 * - **Same layers.** The Layers sheet's habitat, creeks, contours, finds, suggestions and track
 *   apply here. The 2D map's own basemap is drawn on the ground by MapLibre ([MapDrape]) when
 *   its style is available; otherwise the terrain is coloured by habitat, or by elevation when
 *   the habitat layer is off.
 *
 * It is its own screen, not a layer over the map: a GLSurfaceView over the TextureView map
 * blacked the screen (Phase 5, measured). Battery: RENDERMODE_WHEN_DIRTY, a frame only when the
 * camera moves or new colour arrives; terrain is built once per square, colour re-baked only
 * when a layer or the basemap changes.
 */
@Composable
fun Terrain3DView(
    me: FieldLocation?,
    /** The one camera (exe.md A1). This view mirrors it: gestures report, the app moves it. */
    camera: SharedCamera,
    track: List<TrackPoint>,
    finds: List<Find>,
    suggestions: List<Suggestion>,
    layers: MapLayerState,
    weights: DoubleArray,
    demStore: DemTileStore,
    /** The 2D map's style to drape, or null when there is none to drape (offline fallback, Topo). */
    styleUri: String?,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember { TerrainGlRenderer() }
    val glRef = remember { arrayOfNulls<GLSurfaceView>(1) }
    val textMeasurer = rememberTextMeasurer()
    val status by rememberUpdatedState(onStatus)
    val currentLayers by rememberUpdatedState(layers)

    var size by remember { mutableStateOf(0 to 0) }
    // The shared camera, as Compose state: every report or move redraws.
    val snap by camera.state.collectAsState()
    val cam = snap.camera
    // This view's side of the epoch rule: app moves are applied once; its own reports never.
    val follower = remember { SharedCamera.Follower(camera.state.value.epoch) }
    // Zoom is fitted to the 3D range once the square's size is known.
    var fitted by remember { mutableStateOf(false) }
    var scene by remember { mutableStateOf<Terrain3D.Scene?>(null) }
    var buildAt by remember { mutableStateOf(camera.camera.let { it.lat to it.lng }) }
    // True when the user dragged the camera off the square (settle onto the new ground keeping
    // the eye); false after an app move, which teleports (ground on the new centre instead).
    var pannedOut by remember { mutableStateOf(false) }
    // The camera looks at the plane through the ground under its centre, in metres (see
    // MapCamera.mvpForMeshBuiltAt); fixed during a gesture, re-based when it ends.
    var anchorM by remember { mutableDoubleStateOf(Double.NaN) }
    var drape by remember { mutableStateOf<Pair<Terrain3D.Scene, IntArray>?>(null) }
    var sceneNote by remember { mutableStateOf("") }
    var drapeNote by remember { mutableStateOf("") }

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

    // App moves of the shared camera (the first fix, recentre, "Show on map", the switch, this
    // view's own fit and compass): ground the camera on its new centre, or build the square
    // there when the centre is outside the one on screen.
    LaunchedEffect(snap.epoch) {
        val moved = follower.take(snap) ?: return@LaunchedEffect
        val c = moved.camera
        val sc = scene
        pannedOut = false
        sc?.elevationAt(c.lat, c.lng)?.let { anchorM = it }
        if (sc == null || !sc.contains(c.lat, c.lng)) buildAt = c.lat to c.lng
    }

    // ---- terrain: built around the camera's centre, once per elevation tile (~1 km).
    val (bLat, bLng) = buildAt
    val buildKey = "${DemTileStore.lonToTileX(bLng, 15)}:${DemTileStore.latToTileY(bLat, 15)}"
    LaunchedEffect(buildKey, weights.contentHashCode()) {
        status("Loading elevation…")
        val area = loadArea(demStore, bLat, bLng)
        if (area == null) {
            status("No elevation tiles here yet. Connect once, or save the area for offline use.")
            return@LaunchedEffect
        }
        status("Tracing creeks and colouring the ground…")
        val s = withContext(Dispatchers.Default) {
            runCatching { Terrain3D.build(area.first, weights) }
                .onFailure { Log.e(TAG, "3D build failed", it) }.getOrNull()
        }
        if (s == null) { status("Could not build the 3D terrain here."); return@LaunchedEffect }
        // Colour first, then mesh and colour together: the new ground never wears the old
        // square's texture. (The colour effect below finds this texture in the scene's cache.)
        val l = currentLayers
        val px = withContext(Dispatchers.Default) {
            s.texture(if (l.habitat) TerrainTextures.Mode.HABITAT else TerrainTextures.Mode.ELEVATION,
                TerrainTextures.Layers(habitat = l.habitat, water = l.water, contours = l.contours))
        }
        renderer.submitMesh(s.mesh)
        renderer.submitTexture(TerrainGlRenderer.Texture(px, s.textureSize))
        drape = null
        scene = s
        val c = camera.camera
        val (vw, vh) = size
        if (pannedOut && fitted && !anchorM.isNaN() && vw > 0 && vh > 0) {
            // The user dragged off the old square: put the camera on the new ground without
            // moving the eye, as after a gesture (off the old square the ground under it was
            // unknown, so re-anchoring could not run when the gesture ended).
            val (next, ground) = Terrain3D.settle(c, vw, vh, s, anchorM)
            camera.report(next); anchorM = ground
        } else {
            anchorM = s.elevationAt(c.lat, c.lng) ?: ((s.mesh.minElevationM + s.mesh.maxElevationM) / 2.0)
        }
        pannedOut = false
        sceneNote = s.describe() + area.second
        publish()
    }

    // ---- the 2D map's basemap, drawn by MapLibre for this square, under the app's layers.
    LaunchedEffect(scene, styleUri) {
        val s = scene ?: return@LaunchedEffect
        // No style to drape (Topo, or the 2D map's offline fallback): take the old drape off
        // too, or the Dark map would stay on the ground under the Topo choice.
        if (styleUri == null) { drape = null; drapeNote = ""; publish(); return@LaunchedEffect }
        MapDrape.installRequestCounter()   // debug builds only: the device gate's instrument
        drapeNote = " · drawing the map on the ground…"
        publish()
        val px = MapDrape.render(context, styleUri, s.north, s.west, s.south, s.east, s.textureSize, DRAPE_TIMEOUT_MS)
        if (scene !== s) return@LaunchedEffect
        if (px != null) { drape = s to px; drapeNote = " · map on the ground" }
        else drapeNote = " · map not cached here (Save 10 miles to have it offline)"
        publish()
    }

    // ---- colour: re-baked only when the layers, the basemap or the terrain change.
    val drapePx = drape?.takeIf { it.first === scene }?.second
    val mode = when {
        drapePx != null -> TerrainTextures.Mode.MAP
        layers.habitat -> TerrainTextures.Mode.HABITAT
        else -> TerrainTextures.Mode.ELEVATION
    }
    val texLayers = TerrainTextures.Layers(habitat = layers.habitat, water = layers.water, contours = layers.contours)
    LaunchedEffect(scene, mode, texLayers, drapePx) {
        val s = scene ?: return@LaunchedEffect
        val px = withContext(Dispatchers.Default) { s.texture(mode, texLayers, drapePx) }
        renderer.submitTexture(TerrainGlRenderer.Texture(px, s.textureSize))
        glRef[0]?.requestRender()
    }

    // ---- camera: fitted once to the 3D range (in an effect: never written during
    // composition), then drawn every time it changes.
    LaunchedEffect(scene, size.first) {
        val s0 = scene ?: return@LaunchedEffect
        if (fitted || size.first <= 0) return@LaunchedEffect
        val fit = fitZoomFor(s0, size.first)
        fitted = true
        camera.move(CameraMath.to3d(camera.camera, fit - 1.5, fit + 3.5))
    }
    val (w, h) = size
    val s = scene
    val view = cam.takeIf { fitted && w > 0 && h > 0 && !anchorM.isNaN() }
    val mc = view?.let { CameraMath.mapCamera(it, w, h) }
    // A GL frame only when what it draws changed: a GPS fix or a track point recomposes this
    // screen (the markers are on the Compose canvas), and must not redraw the terrain.
    remember(view, anchorM, s, w, h) {
        if (mc != null && s != null) {
            renderer.submitFrame(
                TerrainGlRenderer.Frame(
                    mvp = mc.mvpForMeshBuiltAt(Terrain3D.BUILD_ZOOM, s.mesh.originWorldX, s.mesh.originWorldY,
                        groundZ = anchorM * s.mesh.pixelsPerMeter * Terrain3D.EXAGGERATION),
                    hazeStart = (mc.cameraToCenterDistance * 1.1).toFloat(),
                    hazeEnd = (mc.cameraToCenterDistance * 3.6).toFloat(),
                )
            )
            glRef[0]?.requestRender()
        }
        0
    }

    // The track line, decimated once per change of track or terrain, not per frame.
    val trackLines = remember(track, s) { s?.let { trackSegments(track, it) } ?: emptyList() }
    val showTrack = layers.trackLine || layers.visited

    Box(modifier.background(Color(0xFF0E191E)).onSizeChanged { size = it.width to it.height }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(DepthFallbackChooser())
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glRef[0] = this
                }
            },
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        do {
                            val event = awaitPointerEvent()
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
                                    val fit = fitZoomFor(s0, vw)
                                    val zoom = c0.zoom + ln(event.calculateZoom().toDouble()) / ln(2.0)
                                    camera.report(c0.copy(
                                        // A re-anchored camera may sit past the pinch range; it is
                                        // kept, not snapped, and only pinching further is refused.
                                        zoom = zoom.coerceIn(min(fit - 1.5, c0.zoom), max(fit + 3.5, c0.zoom)),
                                        bearing = ((c0.bearing - event.calculateRotation()) % 360.0 + 360.0) % 360.0,
                                        pitch = (c0.pitch - event.calculatePan().y * 0.15).coerceIn(15.0, 80.0),
                                    ))
                                    moved = true
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (moved) {
                            val s1 = scene
                            val c1 = camera.camera
                            val (vw, vh) = size
                            if (s1 != null && vw > 0 && vh > 0) {
                                val (done, ground) = Terrain3D.settle(c1, vw, vh, s1, anchorM)
                                camera.report(done); anchorM = ground
                                if (!s1.contains(done.lat, done.lng)) { buildAt = done.lat to done.lng; pannedOut = true }
                            }
                        }
                    }
                }
        ) {
            if (mc == null || s == null) return@Canvas
            val anchor = anchorM
            fun at(lat: Double, lng: Double): Offset? {
                val e = s.elevationAt(lat, lng) ?: return null
                val p = mc.project(lat, lng, (e - anchor) * Terrain3D.EXAGGERATION) ?: return null
                return Offset(p[0], p[1])
            }
            val dark = Color(0xE6061008)
            if (showTrack && trackLines.isNotEmpty()) {
                val project = mc.projector()
                trackLines.forEach { seg ->
                    val path = Path()
                    var open = false
                    var k = 0
                    while (k < seg.size) {
                        val p = project(seg[k], seg[k + 1], (seg[k + 2] - anchor) * Terrain3D.EXAGGERATION)
                        if (p == null) open = false
                        else if (!open) { path.moveTo(p[0], p[1]); open = true }
                        else path.lineTo(p[0], p[1])
                        k += 3
                    }
                    drawPath(path, Color(0xCC6FB7FF), style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (layers.suggestions) suggestions.forEach { sg ->
                val o = at(sg.lat, sg.lng) ?: return@forEach
                val ring = if (sg.provenance == Suggestion.PROVENANCE_MODEL) Color(0xFF00FF88) else Color(0xFFE6F4EC)
                drawCircle(dark, 17f, o)
                drawCircle(ring, 17f, o, style = Stroke(3.5f))
                val t = textMeasurer.measure("${sg.rank}", TextStyle(color = ring, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
            }
            if (layers.finds) finds.forEach { f ->
                val o = at(f.lat, f.lng) ?: return@forEach
                drawCircle(dark, 9f, o)
                drawCircle(Color(0xFFFFB02E), 6.5f, o)
            }
            me?.let { here ->
                at(here.lat, here.lng)?.let { o ->
                    drawCircle(Color(0x6600FF88), 22f, o)
                    drawCircle(dark, 11f, o)
                    drawCircle(Color(0xFFE6F4EC), 8f, o)
                    val t = textMeasurer.measure("You", TextStyle(color = Color(0xFFE6F4EC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                    drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - 26f - t.size.height))
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
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).offset(y = (-100).dp)
                .semantics { contentDescription = "Face north and reset the view" },
        ) { Icon(Icons.Filled.Navigation, null, modifier = Modifier.rotate(-cam.bearing.toFloat())) }

        if (s != null) Legend(
            s, mode, layers, showTrack && trackLines.isNotEmpty(),
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 96.dp),
        )
    }
}

@Composable
private fun Legend(s: Terrain3D.Scene, mode: TerrainTextures.Mode, layers: MapLayerState, track: Boolean, modifier: Modifier) {
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
        if (layers.water) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFF3FB2F7)))
            Spacer(Modifier.size(6.dp))
            Text("Creeks, from elevation", color = Gen.TextDim, fontSize = 11.sp)
        }
        if (track) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFF6FB7FF)))
            Spacer(Modifier.size(6.dp))
            Text("Where you've walked", color = Gen.TextDim, fontSize = 11.sp)
        }
        Text(
            (if (layers.contours) "Contours every %.0f m · ".format(s.contourM) else "") +
                "relief ×%.1f".format(Terrain3D.EXAGGERATION),
            color = Gen.TextDim, fontSize = 11.sp,
        )
        // The draped basemap's attribution, owed because the snapshot is drawn without it.
        if (mode == TerrainTextures.Mode.MAP) Text(Basemap.DARK.attribution, color = Gen.TextDim, fontSize = 10.sp)
    }
}

/**
 * The best area the cached tiles allow: zoom 15, then 14, then 13, each needing its whole
 * interior; failing that, the most complete one, with a note. Missing halo tiles are
 * edge-extended by the store and do not block a build.
 */
private suspend fun loadArea(demStore: DemTileStore, lat: Double, lng: Double): Pair<DemTileStore.Mosaic, String>? {
    var best: DemTileStore.Mosaic? = null
    for (z in Terrain3D.ZOOMS) {
        val b = Terrain3D.areaBounds(lat, lng, z)
        val m = demStore.grid(b[0], b[1], b[2], b[3], z, haloTiles = 1) ?: continue
        if (m.missingInterior == 0) return m to ""
        if (best == null || m.missingInterior < best.missingInterior) best = m
    }
    val m = best ?: return null
    return m to " · ${m.missingInterior} tile(s) missing, flattened"
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

private const val TAG = "Terrain3D"
private const val DEFAULT_PITCH = 55f
private const val DRAPE_TIMEOUT_MS = 25_000L
private const val MAX_TRACK_POINTS = 3_000

/**
 * 24-bit depth where the device has it (the flat base and the terrain are far apart in
 * depth, and 16 bits made them fight), else 16. The stock chooser throws when its one
 * request cannot be met, which would take the whole app down with the 3D view.
 */
private class DepthFallbackChooser : GLSurfaceView.EGLConfigChooser {
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
            if (egl.eglChooseConfig(display, attribs, configs, 1, found) && found[0] > 0) return configs[0]!!
        }
        throw IllegalArgumentException("No OpenGL ES 3 configuration on this device")
    }
}

private const val EGL_OPENGL_ES3_BIT_KHR = 0x40
