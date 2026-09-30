package com.ginsengo.steward.ui

import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain3d.MapCamera
import com.ginsengo.steward.terrain3d.Terrain3D
import com.ginsengo.steward.terrain3d.TerrainGlRenderer
import com.ginsengo.steward.terrain3d.TerrainTextures
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Real 3D terrain around you: about 3 km square of the best elevation the tiles offer (3.9 m
 * cells at zoom 15), draped with the same habitat surface the 2D map draws, relief shading,
 * contours and creeks traced from the elevation, with your position, finds and numbered
 * suggestions on it.
 *
 * It is its own screen, not a layer over the map: a GLSurfaceView drawn over the TextureView
 * map blacked the screen (Phase 5, measured). Battery: RENDERMODE_WHEN_DIRTY, a frame only
 * when a gesture moves the camera or new colour arrives; the scene is built once per ~1 km
 * the user moves, and switching the colouring re-bakes only the texture.
 */
@Composable
fun Terrain3DView(
    center: FieldLocation?,
    finds: List<Find>,
    suggestions: List<Suggestion>,
    weights: DoubleArray,
    demStore: DemTileStore,
    habitatTint: Boolean,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { TerrainGlRenderer() }
    val glRef = remember { arrayOfNulls<GLSurfaceView>(1) }
    var size by remember { mutableStateOf(0 to 0) }
    var bearing by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(DEFAULT_PITCH) }
    var zoom by remember { mutableFloatStateOf(Float.NaN) }
    var scene by remember { mutableStateOf<Terrain3D.Scene?>(null) }
    val textMeasurer = rememberTextMeasurer()

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

    val cLat = center?.lat
    val cLng = center?.lng
    // Rebuild when the user walks into another zoom-15 tile (~1 km), not on every fix.
    val tileKey = if (cLat == null || cLng == null) null
    else "${DemTileStore.lonToTileX(cLng, 15)}:${DemTileStore.latToTileY(cLat, 15)}"

    LaunchedEffect(tileKey, weights.contentHashCode()) {
        if (cLat == null || cLng == null) { onStatus("Waiting for a GPS fix"); return@LaunchedEffect }
        onStatus("Loading elevation…")
        val area = loadArea(demStore, cLat, cLng)
        if (area == null) {
            onStatus("No elevation tiles here yet. Connect once, or save the area for offline use.")
            return@LaunchedEffect
        }
        onStatus("Tracing creeks and colouring the ground…")
        val s = withContext(Dispatchers.Default) {
            runCatching { Terrain3D.build(area.first, weights) }
                .onFailure { Log.e(TAG, "3D build failed", it) }.getOrNull()
        }
        if (s == null) { onStatus("Could not build the 3D terrain here."); return@LaunchedEffect }
        renderer.submitMesh(s.mesh)
        scene = s
        onStatus(s.describe() + area.second)
    }

    LaunchedEffect(scene, habitatTint) {
        val s = scene ?: return@LaunchedEffect
        val mode = if (habitatTint) TerrainTextures.Mode.HABITAT else TerrainTextures.Mode.ELEVATION
        val px = withContext(Dispatchers.Default) { s.texture(mode) }
        renderer.submitTexture(TerrainGlRenderer.Texture(px, s.textureSize))
        glRef[0]?.requestRender()
    }

    val (w, h) = size
    val s = scene
    if (s != null && w > 0 && zoom.isNaN()) zoom = fitZoomFor(s, w).toFloat()
    val cam = if (w > 0 && h > 0 && cLat != null && cLng != null && !zoom.isNaN())
        MapCamera(cLat, cLng, zoom.toDouble(), bearing.toDouble(), pitch.toDouble(), w, h) else null
    // The ground under the user is the camera's target plane (see mvpForMeshBuiltAt).
    val groundM = if (s == null || cLat == null || cLng == null) 0.0
    else elevationAt(s.mosaic, cLat, cLng) ?: ((s.mesh.minElevationM + s.mesh.maxElevationM) / 2.0)
    if (cam != null && s != null) {
        renderer.submitFrame(
            TerrainGlRenderer.Frame(
                mvp = cam.mvpForMeshBuiltAt(Terrain3D.BUILD_ZOOM, s.mesh.originWorldX, s.mesh.originWorldY,
                    groundZ = groundM * s.mesh.pixelsPerMeter * Terrain3D.EXAGGERATION),
                hazeStart = (cam.cameraToCenterDistance * 1.1).toFloat(),
                hazeEnd = (cam.cameraToCenterDistance * 3.6).toFloat(),
            )
        )
        glRef[0]?.requestRender()
    }

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
                .pointerInput(s) {
                    val fit = s?.let { fitZoomFor(it, size.first) } ?: 14.0
                    detectTransformGestures { _, pan, gestureZoom, rotation ->
                        bearing = (bearing - pan.x * 0.25f - rotation + 360f) % 360f
                        pitch = (pitch - pan.y * 0.15f).coerceIn(15f, 80f)
                        if (!zoom.isNaN()) {
                            zoom = (zoom + (ln(gestureZoom.toDouble()) / ln(2.0)).toFloat())
                                .coerceIn((fit - 1.5).toFloat(), (fit + 3.5).toFloat())
                        }
                    }
                }
        ) {
            if (cam == null || s == null) return@Canvas
            fun at(lat: Double, lng: Double): Offset? {
                val e = elevationAt(s.mosaic, lat, lng) ?: return null
                val p = cam.project(lat, lng, (e - groundM) * Terrain3D.EXAGGERATION) ?: return null
                return Offset(p[0], p[1])
            }
            val dark = Color(0xE6061008)
            suggestions.forEach { sg ->
                val o = at(sg.lat, sg.lng) ?: return@forEach
                val ring = if (sg.provenance == Suggestion.PROVENANCE_MODEL) Color(0xFF00FF88) else Color(0xFFE6F4EC)
                drawCircle(dark, 17f, o)
                drawCircle(ring, 17f, o, style = Stroke(3.5f))
                val t = textMeasurer.measure("${sg.rank}", TextStyle(color = ring, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
            }
            finds.forEach { f ->
                val o = at(f.lat, f.lng) ?: return@forEach
                drawCircle(dark, 9f, o)
                drawCircle(Color(0xFFFFB02E), 6.5f, o)
            }
            at(cLat!!, cLng!!)?.let { o ->
                drawCircle(Color(0x6600FF88), 22f, o)
                drawCircle(dark, 11f, o)
                drawCircle(Color(0xFFE6F4EC), 8f, o)
                val t = textMeasurer.measure("You", TextStyle(color = Color(0xFFE6F4EC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - 26f - t.size.height))
            }
        }

        // Compass: shows where north is; tap to face north, reset the tilt and fit the area.
        SmallFloatingActionButton(
            onClick = {
                bearing = 0f; pitch = DEFAULT_PITCH
                s?.let { zoom = fitZoomFor(it, size.first).toFloat() }
            },
            containerColor = Gen.SurfaceHigh, contentColor = Gen.Text,
            // Above the screen's side buttons, clear of the status chips.
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).offset(y = (-100).dp)
                .semantics { contentDescription = "Face north and reset the view" },
        ) { Icon(Icons.Filled.Navigation, null, modifier = Modifier.rotate(-bearing)) }

        if (s != null) Legend(s, habitatTint, Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 96.dp))
    }
}

@Composable
private fun Legend(s: Terrain3D.Scene, habitat: Boolean, modifier: Modifier) {
    val stops = if (habitat) SuitabilityRasterizer.RAMP else TerrainTextures.ELEVATION_RAMP
    Column(
        modifier.background(Gen.Bg.copy(alpha = 0.78f), RoundedCornerShape(10.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.width(132.dp).height(8.dp).background(
            Brush.horizontalGradient(stops.map { Color(0xFF000000 or it.toLong()) }), RoundedCornerShape(4.dp)))
        Text(
            if (habitat) "Habitat: weak → strong" else
                "Elevation: %,d → %,d m".format(s.mesh.minElevationM.roundToInt(), s.mesh.maxElevationM.roundToInt()),
            color = Gen.Text, fontSize = 11.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFF3FB2F7)))
            Spacer(Modifier.size(6.dp))
            Text("Creeks, from elevation", color = Gen.TextDim, fontSize = 11.sp)
        }
        Text("Contours every %.0f m · relief ×%.1f".format(s.contourM, Terrain3D.EXAGGERATION), color = Gen.TextDim, fontSize = 11.sp)
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
    Terrain3D.fitZoom(s.widthM, (s.mosaic.northLat + s.mosaic.southLat) / 2, viewportWidthPx)

private const val TAG = "Terrain3D"
private const val DEFAULT_PITCH = 55f

/**
 * Elevation at a position, or null outside the displayed square: the halo around it is
 * sampled for the terrain analysis but not drawn, so a marker there would float in the air.
 */
private fun elevationAt(m: DemTileStore.Mosaic, lat: Double, lng: Double): Double? {
    val n = (1 shl m.zoom).toDouble()
    val fx = ((lng + 180.0) / 360.0 * n - m.tileX0) * DemTileStore.TILE
    val r = Math.toRadians(lat)
    val fy = ((1.0 - ln(Math.tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * n - m.tileY0) * DemTileStore.TILE
    val x = fx.toInt(); val y = fy.toInt()
    if (x !in m.haloPx until m.grid.w - m.haloPx || y !in m.haloPx until m.grid.h - m.haloPx) return null
    return m.grid[x, y].toDouble()
}

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
