package com.ginsengo.steward.terrain3d

import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ginsengo.steward.terrain.DemTileStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap

/**
 * A transparent GL surface layered over the map, drawing a real 3D terrain mesh.
 *
 * This is the "Managed Overlay Injection" pattern `DESIGN_LOD_HYBRID.md` specified and the
 * previous tree never implemented — its `MicroTerrainRendererImpl.onDraw()` built vertex
 * buffers and then contained no GL calls at all.
 *
 * HOW IT STAYS ON THE MAP
 * The overlay cannot borrow MapLibre's matrix (Android exposes none) so [MapCamera]
 * reconstructs it from the public CameraPosition. Because a wrong reconstruction still
 * draws convincing terrain in the wrong place, [AlignmentCheck] compares it against
 * MapLibre's own `toScreenLocation`. If they disagree by more than a couple of pixels the
 * mesh is not drawn and the UI says why.
 *
 * HOW IT KEEPS UP WITH THE CAMERA
 * Three separate rates, because the three jobs cost wildly different amounts:
 *
 *  - EVERY FRAME, while the camera moves: recompute the MVP and render. This is pure matrix
 *    arithmetic, nanoseconds, and it is what stops the mesh swimming against the map.
 *  - THROTTLED, while the camera moves: rebuild the mesh, but only once the viewport has
 *    eaten into the margin the current mesh was built with, and never more often than
 *    [MeshCoverage.MIN_REBUILD_INTERVAL_MS]. See [MeshCoverage].
 *  - ON SETTLE ONLY: run the alignment check and publish status. The check costs eighteen
 *    projections and publishing status recomposes Compose; doing either per frame during a
 *    fling was a self-inflicted stutter in the first version of this file.
 */
data class Terrain3DStatus(
    val enabled: Boolean = false,
    val alignment: AlignmentCheck.Result? = null,
    val triangles: Int = 0,
    val gridN: Int = 0,
    val demZoom: Int = 0,
    val metresPerCell: Double = 0.0,
    val building: Boolean = false,
    val glError: String? = null,
) {
    val drawing: Boolean
        get() = enabled && glError == null && triangles > 0 && alignment?.aligned == true

    fun describe(): String = when {
        !enabled -> "Off"
        glError != null -> "GL error: $glError"
        building && triangles == 0 -> "Building mesh…"
        alignment != null && !alignment.aligned -> alignment.describe()
        triangles > 0 ->
            "%,d triangles · %d× grid · zoom %d · %.1f m/cell".format(
                triangles, gridN, demZoom, metresPerCell
            ) + (alignment?.let { " · ±%.2f px".format(it.meanErrorPx) } ?: "")
        else -> "Waiting for elevation tiles…"
    }
}

@Composable
fun Terrain3DOverlay(
    map: MapLibreMap?,
    demStore: DemTileStore,
    enabled: Boolean,
    opacity: Float,
    /** 0 = hypsometric tint, 1 = habitat forecast colouring. */
    suitabilityMix: Float,
    exaggeration: Float,
    onStatus: (Terrain3DStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val renderer = remember { TerrainGlRenderer() }
    var viewSize by remember { mutableStateOf(0 to 0) }
    var status by remember { mutableStateOf(Terrain3DStatus()) }
    val glView = remember { arrayOfNulls<GLSurfaceView>(1) }

    // Mutable render state, deliberately not Compose state: these change every frame during
    // a gesture and routing them through recomposition is exactly the stutter being fixed.
    val meshState = remember { MeshState() }

    val curOpacity = rememberUpdatedState(opacity)
    val curMix = rememberUpdatedState(suitabilityMix)
    val curExaggeration = rememberUpdatedState(exaggeration)
    val curEnabled = rememberUpdatedState(enabled)

    fun publish(s: Terrain3DStatus) {
        if (s != status) {
            status = s
            onStatus(s)
        }
    }

    fun viewportOf(m: MapLibreMap): MeshCoverage.Region? {
        val region = runCatching { m.projection.visibleRegion.latLngBounds }.getOrNull() ?: return null
        return MeshCoverage.Region(
            north = region.latitudeNorth, west = region.longitudeWest,
            south = region.latitudeSouth, east = region.longitudeEast,
            zoom = m.cameraPosition.zoom,
        )
    }

    fun cameraOf(m: MapLibreMap): MapCamera? {
        val (w, h) = viewSize
        if (w <= 0 || h <= 0) return null
        val cam = m.cameraPosition
        val target = cam.target ?: return null
        return MapCamera(
            centerLat = target.latitude, centerLng = target.longitude,
            zoom = cam.zoom, bearingDeg = cam.bearing, pitchDeg = cam.tilt,
            viewportWidth = w, viewportHeight = h,
        )
    }

    /** Per-frame. Matrix arithmetic only — no allocation of consequence, no status, no check. */
    fun syncMatrix(m: MapLibreMap) {
        val camera = cameraOf(m) ?: return
        if (meshState.alignmentFailed) {
            renderer.submitFrame(null)
            return
        }
        renderer.submitFrame(
            TerrainGlRenderer.Frame(
                mvp = camera.mvpForOrigin(meshState.originX, meshState.originY),
                opacity = curOpacity.value,
                suitabilityMix = curMix.value,
                minScore = 0.35f,
                elevMin = meshState.elevMin,
                elevMax = meshState.elevMax,
            )
        )
        glView[0]?.requestRender()
    }

    /** On settle only: the expensive verification and the Compose-visible status. */
    fun verifyAlignment(m: MapLibreMap) {
        val camera = cameraOf(m) ?: return
        val vp = viewportOf(m) ?: return
        val result = AlignmentCheck.run(
            camera = camera,
            probes = AlignmentCheck.probesFor(vp.north, vp.west, vp.south, vp.east),
            mapProject = { lat, lng ->
                runCatching {
                    val p = m.projection.toScreenLocation(
                        org.maplibre.android.geometry.LatLng(lat, lng)
                    )
                    floatArrayOf(p.x, p.y)
                }.getOrNull()
            },
        )
        meshState.alignmentFailed = !result.aligned
        publish(
            status.copy(
                enabled = true,
                alignment = result,
                glError = renderer.lastError,
                triangles = renderer.trianglesDrawn,
                building = meshState.job?.isActive == true,
            )
        )
        syncMatrix(m)
    }

    fun maybeRebuild(m: MapLibreMap, force: Boolean) {
        if (!curEnabled.value) return
        val vp = viewportOf(m) ?: return
        val now = System.currentTimeMillis()
        val inFlight = meshState.job?.isActive == true
        if (!force && !MeshCoverage.shouldStartRebuild(
                built = meshState.builtRegion,
                viewport = vp,
                lastAttemptMs = meshState.lastAttemptMs,
                nowMs = now,
                buildInFlight = inFlight,
            )
        ) return

        meshState.lastAttemptMs = now
        meshState.job?.cancel()

        val camera = cameraOf(m) ?: return
        val target = MeshCoverage.expand(vp)
        val gridN = TerrainMesh.gridSizeFor(vp.zoom)
        val demZoom = DemTileStore.demZoomFor(vp.zoom)
        val wantSuitability = curMix.value > 0f
        val exaggeration = curExaggeration.value

        publish(status.copy(enabled = true, building = true))
        meshState.job = scope.launch {
            val mosaic = demStore.grid(
                north = target.north, west = target.west,
                south = target.south, east = target.east,
                z = demZoom, haloTiles = 1,
            )
            if (mosaic == null) {
                publish(status.copy(building = false))
                return@launch
            }
            val mesh = TerrainMesh.build(
                mosaic = mosaic,
                camera = camera,
                gridN = gridN,
                tpiRadiusM = DemTileStore.tpiRadiusMetresFor(vp.zoom),
                exaggeration = exaggeration,
                withSuitability = wantSuitability,
            )
            meshState.originX = mesh.originWorldX
            meshState.originY = mesh.originWorldY
            meshState.elevMin = mesh.minElevationM
            meshState.elevMax = mesh.maxElevationM
            meshState.builtRegion = target
            renderer.submitMesh(mesh)
            publish(
                status.copy(
                    enabled = true,
                    building = false,
                    triangles = mesh.triangleCount,
                    gridN = mesh.gridN,
                    demZoom = mosaic.zoom,
                    metresPerCell = mosaic.grid.cellSizeM,
                    glError = renderer.lastError,
                )
            )
            syncMatrix(m)
        }
    }

    DisposableEffect(map, enabled, viewSize) {
        val m = map
        if (m == null || !enabled) {
            if (!enabled) {
                meshState.reset()
                publish(Terrain3DStatus(enabled = false))
            }
            onDispose { }
        } else {
            val onMove = MapLibreMap.OnCameraMoveListener {
                // Continuous rendering while the camera is in motion. WHEN_DIRTY plus a
                // requestRender per move event leaves the overlay a frame behind the map,
                // which during a fling reads as the terrain sliding around underneath it.
                if (!meshState.moving) {
                    meshState.moving = true
                    glView[0]?.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
                syncMatrix(m)
                maybeRebuild(m, force = false)
            }
            val onIdle = MapLibreMap.OnCameraIdleListener {
                if (meshState.moving) {
                    meshState.moving = false
                    // Back to on-demand the moment the camera settles: this app is carried
                    // for hours on a hillside and a permanently spinning GL thread is a
                    // battery cost with nothing on the other side of it.
                    glView[0]?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
                maybeRebuild(m, force = false)
                verifyAlignment(m)
            }
            m.addOnCameraMoveListener(onMove)
            m.addOnCameraIdleListener(onIdle)
            maybeRebuild(m, force = true)
            verifyAlignment(m)
            onDispose {
                m.removeOnCameraMoveListener(onMove)
                m.removeOnCameraIdleListener(onIdle)
                meshState.job?.cancel()
            }
        }
    }

    // Appearance changes that need no new geometry are applied straight to the next frame.
    LaunchedEffect(opacity, suitabilityMix) {
        map?.let { syncMatrix(it) }
    }

    // Exaggeration and the forecast tint DO change the vertex buffer.
    LaunchedEffect(exaggeration, suitabilityMix) {
        map?.let { maybeRebuild(it, force = true) }
    }

    if (!enabled) return

    AndroidView(
        modifier = modifier.onSizeChanged { viewSize = it.width to it.height },
        factory = {
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3)
                // An alpha channel and a translucent holder are what let the map show
                // through; without both, the overlay paints an opaque black rectangle.
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                setZOrderMediaOverlay(true)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glView[0] = this
            }
        },
        onRelease = { glView[0] = null },
    )
}

/** Render state shared between the UI thread and the build coroutine. */
private class MeshState {
    @Volatile var originX: Double = 0.0
    @Volatile var originY: Double = 0.0
    @Volatile var elevMin: Float = 0f
    @Volatile var elevMax: Float = 1f
    @Volatile var alignmentFailed: Boolean = false
    @Volatile var moving: Boolean = false
    var builtRegion: MeshCoverage.Region? = null
    var lastAttemptMs: Long = 0L
    var job: Job? = null

    fun reset() {
        job?.cancel()
        job = null
        builtRegion = null
        lastAttemptMs = 0L
        alignmentFailed = false
        moving = false
    }
}
