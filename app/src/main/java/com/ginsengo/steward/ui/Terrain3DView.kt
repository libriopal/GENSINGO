package com.ginsengo.steward.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain3d.MapCamera
import com.ginsengo.steward.terrain3d.TerrainGlRenderer
import com.ginsengo.steward.terrain3d.TerrainMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln

/**
 * Real 3D terrain around you: a triangle mesh from the elevation tiles, coloured by the
 * habitat surface (or by elevation), with your position, finds and suggestions on it.
 *
 * It is its own screen, not a layer over the map: a GLSurfaceView drawn over the
 * TextureView map punched a hole through it and blacked the screen (Phase 5, measured), and
 * the TextureView is what keeps the map's controls tappable. Alone, the GL surface has
 * nothing to fight with.
 *
 * Battery: RENDERMODE_WHEN_DIRTY. A frame is drawn when a gesture moves the camera or a new
 * mesh arrives, never continuously.
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
    var pitch by remember { mutableFloatStateOf(60f) }
    var zoom by remember { mutableFloatStateOf(BUILD_ZOOM.toFloat() + 0.3f) }
    var built by remember { mutableStateOf<Built?>(null) }

    val cLat = center?.lat ?: return
    val cLng = center.lng
    // Rebuild only when the user has moved ~500 m, not on every fix.
    val cellKey = "%.3f:%.3f".format(floor(cLat * 200) / 200, floor(cLng * 200) / 200)

    LaunchedEffect(cellKey, weights.contentHashCode()) {
        onStatus("Building terrain…")
        val b = withContext(Dispatchers.Default) {
            val dLat = AREA_M / 111_320.0
            val dLng = AREA_M / (111_320.0 * cos(Math.toRadians(cLat)))
            val mosaic = demStore.grid(cLat + dLat, cLng - dLng, cLat - dLat, cLng + dLng, DEM_ZOOM, haloTiles = 1)
                ?: return@withContext null
            val cam = MapCamera(cLat, cLng, BUILD_ZOOM, 0.0, 0.0, 1000, 1000)
            val mesh = TerrainMesh.build(mosaic, cam, gridN = 192, tpiRadiusM = 300.0,
                exaggeration = EXAGGERATION, withSuitability = true, weights = weights)
            Built(mesh, mosaic)
        }
        if (b == null) { onStatus("No elevation tiles for this area yet"); return@LaunchedEffect }
        renderer.submitMesh(b.mesh)
        built = b
        onStatus("%,d triangles · %.0f m cells".format(b.mesh.triangleCount, b.mosaic.grid.cellSizeM))
    }

    val (w, h) = size
    val cam = if (w > 0 && h > 0) MapCamera(cLat, cLng, zoom.toDouble(), bearing.toDouble(), pitch.toDouble(), w, h) else null
    val b = built
    if (cam != null && b != null) {
        renderer.submitFrame(
            TerrainGlRenderer.Frame(
                mvp = cam.mvpForMeshBuiltAt(BUILD_ZOOM, b.mesh.originWorldX, b.mesh.originWorldY),
                opacity = 1f,
                suitabilityMix = if (habitatTint) 1f else 0f,
                minScore = 0.35f,
                elevMin = b.mesh.minElevationM,
                elevMax = b.mesh.maxElevationM,
            )
        )
        glRef[0]?.requestRender()
    }

    Box(modifier.onSizeChanged { size = it.width to it.height }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
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
                    detectTransformGestures { _, pan, gestureZoom, rotation ->
                        bearing = (bearing - pan.x * 0.25f - rotation + 360f) % 360f
                        pitch = (pitch - pan.y * 0.15f).coerceIn(15f, 78f)
                        zoom = (zoom + (ln(gestureZoom.toDouble()) / ln(2.0)).toFloat()).coerceIn(11.5f, 16.5f)
                    }
                }
        ) {
            if (cam == null || b == null) return@Canvas
            fun mark(lat: Double, lng: Double, color: Color, r: Float, filled: Boolean) {
                val e = elevationAt(b.mosaic, lat, lng) ?: return
                val p = cam.project(lat, lng, e * EXAGGERATION) ?: return
                val o = Offset(p[0], p[1])
                if (filled) drawCircle(color, r, o) else drawCircle(color, r, o, style = Stroke(3f))
            }
            suggestions.forEach { s ->
                mark(s.lat, s.lng, if (s.provenance == Suggestion.PROVENANCE_MODEL) Color(0xFF00FF88) else Color(0xFF8FA89A), 14f, false)
            }
            finds.forEach { f -> mark(f.lat, f.lng, Color(0xFFFFB02E), 8f, f.verification == "VERIFIED") }
            mark(cLat, cLng, Color(0xFFE6F4EC), 10f, true)
        }
    }
}

private class Built(val mesh: TerrainMesh.Mesh, val mosaic: DemTileStore.Mosaic)

private const val DEM_ZOOM = 13
private const val BUILD_ZOOM = 13.0
private const val AREA_M = 4_000.0
private const val EXAGGERATION = 1.6f

/** Elevation at a position from the mosaic, or null outside it. */
private fun elevationAt(m: DemTileStore.Mosaic, lat: Double, lng: Double): Double? {
    val n = (1 shl m.zoom).toDouble()
    val fx = ((lng + 180.0) / 360.0 * n - m.tileX0) * DemTileStore.TILE
    val r = Math.toRadians(lat)
    val fy = ((1.0 - ln(Math.tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * n - m.tileY0) * DemTileStore.TILE
    val x = fx.toInt(); val y = fy.toInt()
    if (x !in 0 until m.grid.w || y !in 0 until m.grid.h) return null
    return m.grid[x, y].toDouble()
}
