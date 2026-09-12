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
import androidx.compose.ui.graphics.RectangleShape
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
 * draws convincing terrain in the wrong place, [AlignmentCheck] compares the reconstruction
 * against MapLibre's own `toScreenLocation` on every camera settle. If they disagree by
 * more than a couple of pixels the mesh is not drawn and the UI says why. Failing visibly
 * beats floating a hillside.
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
    val buildJob = remember { arrayOfNulls<Job>(1) }
    val lastMeshKey = remember { arrayOfNulls<String>(1) }
    val glView = remember { arrayOfNulls<GLSurfaceView>(1) }

    val curOpacity = rememberUpdatedState(opacity)
    val curMix = rememberUpdatedState(suitabilityMix)
    val curExaggeration = rememberUpdatedState(exaggeration)
    val curEnabled = rememberUpdatedState(enabled)

    fun publish(s: Terrain3DStatus) {
        status = s
        onStatus(s)
    }

    /** Recomputes the matrix and the alignment witness, then asks for a frame. */
    fun syncCamera(m: MapLibreMap, meshOriginX: Double, meshOriginY: Double,
                   elevMin: Float, elevMax: Float) {
        val (w, h) = viewSize
        if (w <= 0 || h <= 0) return
        val cam = m.cameraPosition
        val target = cam.target ?: return
        val camera = MapCamera(
            centerLat = target.latitude,
            centerLng = target.longitude,
            zoom = cam.zoom,
            bearingDeg = cam.bearing,
            pitchDeg = cam.tilt,
            viewportWidth = w,
            viewportHeight = h,
        )

        val region = runCatching { m.projection.visibleRegion.latLngBounds }.getOrNull()
        val alignment = if (region == null) null else AlignmentCheck.run(
            camera = camera,
            probes = AlignmentCheck.probesFor(
                region.latitudeNorth, region.longitudeWest,
                region.latitudeSouth, region.longitudeEast,
            ),
            mapProject = { lat, lng ->
                runCatching {
                    val p = m.projection.toScreenLocation(
                        org.maplibre.android.geometry.LatLng(lat, lng)
                    )
                    floatArrayOf(p.x, p.y)
                }.getOrNull()
            },
        )

        renderer.submitFrame(
            if (alignment?.aligned != false) {
                TerrainGlRenderer.Frame(
                    mvp = camera.mvpForOrigin(meshOriginX, meshOriginY),
                    opacity = curOpacity.value,
                    suitabilityMix = curMix.value,
                    minScore = 0.35f,
                    elevMin = elevMin,
                    elevMax = elevMax,
                )
            } else null
        )
        publish(
            status.copy(
                alignment = alignment,
                glError = renderer.lastError,
                triangles = renderer.trianglesDrawn,
            )
        )
        glView[0]?.requestRender()
    }

    val meshOrigin = remember { doubleArrayOf(0.0, 0.0) }
    val elevRange = remember { floatArrayOf(0f, 1f) }

    fun rebuildMesh(m: MapLibreMap) {
        val (w, h) = viewSize
        if (w <= 0 || h <= 0 || !curEnabled.value) return
        val cam = m.cameraPosition
        val target = cam.target ?: return
        val region = runCatching { m.projection.visibleRegion.latLngBounds }.getOrNull() ?: return

        val demZoom = DemTileStore.demZoomFor(cam.zoom)
        val key = "$demZoom:${"%.4f".format(region.latitudeNorth)}:" +
                "${"%.4f".format(region.longitudeWest)}:" +
                "${"%.4f".format(region.latitudeSouth)}:" +
                "${"%.4f".format(region.longitudeEast)}:${curExaggeration.value}"
        if (key == lastMeshKey[0]) return
        lastMeshKey[0] = key

        buildJob[0]?.cancel()
        publish(status.copy(enabled = true, building = true))
        buildJob[0] = scope.launch {
            val mosaic = demStore.grid(
                north = region.latitudeNorth, west = region.longitudeWest,
                south = region.latitudeSouth, east = region.longitudeEast,
                z = demZoom, haloTiles = 1,
            )
            if (mosaic == null) {
                publish(status.copy(building = false))
                return@launch
            }
            val camera = MapCamera(
                centerLat = target.latitude, centerLng = target.longitude,
                zoom = cam.zoom, bearingDeg = cam.bearing, pitchDeg = cam.tilt,
                viewportWidth = w, viewportHeight = h,
            )
            val gridN = TerrainMesh.gridSizeFor(cam.zoom)
            val mesh = TerrainMesh.build(
                mosaic = mosaic,
                camera = camera,
                gridN = gridN,
                tpiRadiusM = DemTileStore.tpiRadiusMetresFor(cam.zoom),
                exaggeration = curExaggeration.value,
                withSuitability = curMix.value > 0f,
            )
            meshOrigin[0] = mesh.originWorldX
            meshOrigin[1] = mesh.originWorldY
            elevRange[0] = mesh.minElevationM
            elevRange[1] = mesh.maxElevationM
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
            syncCamera(m, mesh.originWorldX, mesh.originWorldY,
                mesh.minElevationM, mesh.maxElevationM)
        }
    }

    // Camera listeners live and die with the map instance.
    DisposableEffect(map, enabled, viewSize) {
        val m = map
        if (m == null || !enabled) {
            if (!enabled) publish(Terrain3DStatus(enabled = false))
            onDispose { }
        } else {
            val onMove = MapLibreMap.OnCameraMoveListener {
                syncCamera(m, meshOrigin[0], meshOrigin[1], elevRange[0], elevRange[1])
            }
            val onIdle = MapLibreMap.OnCameraIdleListener { rebuildMesh(m) }
            m.addOnCameraMoveListener(onMove)
            m.addOnCameraIdleListener(onIdle)
            rebuildMesh(m)
            syncCamera(m, meshOrigin[0], meshOrigin[1], elevRange[0], elevRange[1])
            onDispose {
                m.removeOnCameraMoveListener(onMove)
                m.removeOnCameraIdleListener(onIdle)
                buildJob[0]?.cancel()
            }
        }
    }

    // Opacity/mix/exaggeration changes need no new mesh unless suitability turns on.
    LaunchedEffect(opacity, suitabilityMix, exaggeration) {
        val m = map ?: return@LaunchedEffect
        lastMeshKey[0] = null
        rebuildMesh(m)
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
