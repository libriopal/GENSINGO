package com.ginsengo.steward.ui.map

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.data.db.TrackPoint
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.research.RadiusScan
import com.ginsengo.steward.terrain.ContourLines
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.Hydrology
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import com.ginsengo.steward.terrain.WaterLines
import com.ginsengo.steward.terrain3d.TerrainTextures
import com.ginsengo.steward.terrain3d.CameraState
import com.ginsengo.steward.terrain3d.SharedCamera
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.heatmapDensity
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.rgba
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.sin

const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** Diagnostics only. Never log a coordinate: logcat is readable by bug-report tools. */
private const val TAG = "GensingoMap"

private const val SRC_BASE = "g-base"
private const val SRC_DEM = "g-dem"
private const val SRC_HABITAT = "g-habitat"
private const val SRC_VISITED = "g-visited"
private const val SRC_FINDS = "g-finds"
private const val SRC_TRACK = "g-track"
private const val SRC_SUGGEST = "g-suggest"
private const val SRC_RING = "g-ring"
private const val SRC_ME = "g-me"
private const val SRC_WATER = "g-water"
private const val SRC_CONTOUR = "g-contour"
private const val FOLLOW_ANIMATION_MS = 900
private const val CONTOUR_MIN_ZOOM = 10.5

/**
 * The map: 2.5D by default (50 degree pitch over GPU hillshade), with the three heat
 * surfaces, the track, the suggestions, the 10-mile ring and your position.
 *
 * The map renders into a TextureView (MapLibreMapOptions.textureMode): in the default
 * SurfaceView mode, controls drawn over the map never received taps (Phase 1 field report).
 * That is also why the real 3D mesh lives in its own screen instead of over this map.
 *
 * Real-time cost control: every GeoJSON push is keyed by what changed (count and last
 * timestamp), so a recomposition that changed nothing costs nothing, and the habitat raster
 * is recomputed only when the camera settles on new ground or the weights change.
 */
@Composable
fun FieldMap(
    me: FieldLocation?,
    track: List<TrackPoint>,
    finds: List<Find>,
    suggestions: List<Suggestion>,
    radiusCenter: Pair<Double, Double>?,
    layers: MapLayerState,
    weights: DoubleArray,
    demStore: DemTileStore,
    /**
     * The one camera (exe.md A1, A3). This map is a mirror of it: it opens where the camera is,
     * reports every move the user makes, and follows every app move once (the epoch rule).
     * MapLibre keeps the camera it draws from, but that camera is never the source of truth.
     */
    camera: SharedCamera,
    onHabitatStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the Dark vector style is loaded (true) or the map is on Topo or its offline
     * fallback (false): the 3D view drapes the style only when it actually loaded here.
     */
    onBasemap: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    remember { runCatching { MapLibre.getInstance(context) } }

    val st = remember { State() }
    val curLayers = rememberUpdatedState(layers)
    val curWeights = rememberUpdatedState(weights)
    val curMe = rememberUpdatedState(me)
    val curTrack = rememberUpdatedState(track)
    val curFinds = rememberUpdatedState(finds)
    val curSuggestions = rememberUpdatedState(suggestions)
    val curCenter = rememberUpdatedState(radiusCenter)
    val curOnBasemap = rememberUpdatedState(onBasemap)

    val mapView = remember {
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    }

    DisposableEffect(lifecycle) {
        mapView.onCreate(null)
        val obs = LifecycleEventObserver { _, e ->
            runCatching {
                when (e) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose {
            st.heatJob?.cancel()
            lifecycle.lifecycle.removeObserver(obs)
            runCatching { mapView.onStop(); mapView.onDestroy() }
        }
    }

    fun refreshHabitat(map: MapLibreMap, force: Boolean) {
        val style = st.style ?: return
        val wantHabitat = curLayers.value.habitat
        val wantWater = curLayers.value.water
        val cam = map.cameraPosition
        // Contours fade in from zoom 11 (their layer's opacity): below that they would be
        // computed, hundreds of thousands of vertices over steep country, and never seen.
        val wantContours = curLayers.value.contours && cam.zoom >= CONTOUR_MIN_ZOOM
        if (!wantHabitat && !wantWater && !wantContours) return
        val region = runCatching { map.projection.visibleRegion.latLngBounds }.getOrNull() ?: return
        val z = DemTileStore.zoomFitting(
            region.latitudeNorth, region.longitudeWest, region.latitudeSouth, region.longitudeEast,
            DemTileStore.demZoomFor(cam.zoom),
        )
        // The raster covers whole tiles, so what it draws depends on the tile range, not on the
        // exact viewport. Keying on the viewport recomputed an identical picture on every pan
        // (seconds of CPU each on a phone, Phase 7).
        val weights = curWeights.value
        val key = "$z:%d-%d:%d-%d:%d:%.0f:%s".format(
            DemTileStore.lonToTileX(region.longitudeWest, z), DemTileStore.lonToTileX(region.longitudeEast, z),
            DemTileStore.latToTileY(region.latitudeNorth, z), DemTileStore.latToTileY(region.latitudeSouth, z),
            DemTileStore.rasterSizeFor(cam.zoom), DemTileStore.tpiRadiusMetresFor(cam.zoom),
            weights.joinToString { "%.3f".format(it) },
        ) + ":h=$wantHabitat:w=$wantWater:c=$wantContours"
        if (!force) {
            if (key == st.habitatKey && st.habitatComplete) return                // drawn; nothing new can arrive
            if (key == st.pendingHabitatKey && st.heatJob?.isActive == true) return  // already computing it
        }
        st.pendingHabitatKey = key
        st.heatJob?.cancel()
        st.heatJob = scope.launch {
            val mosaic = demStore.grid(
                region.latitudeNorth, region.longitudeWest, region.latitudeSouth, region.longitudeEast,
                z, haloTiles = 1,
            )
            if (mosaic == null) {
                Log.i(TAG, "habitat: no elevation tiles at DEM zoom $z")
                onHabitatStatus("No elevation tiles here yet"); return@launch
            }
            // Offline with gaps: redraw only when more of the range has arrived.
            if (!force && key == st.habitatKey && mosaic.tilesLoaded == st.habitatTiles) return@launch
            Log.i(TAG, "habitat: DEM zoom $z, ${mosaic.tilesLoaded}/${mosaic.tilesRequested} tiles")
            var uploaded = true
            var status = "%.1f m cells · %d/%d tiles".format(mosaic.grid.cellSizeM, mosaic.tilesLoaded, mosaic.tilesRequested)
            if (wantHabitat) {
                val t0 = android.os.SystemClock.elapsedRealtime()
                val r = SuitabilityRasterizer.rasterise(
                    mosaic, DemTileStore.rasterSizeFor(cam.zoom), DemTileStore.tpiRadiusMetresFor(cam.zoom),
                    weights = weights,
                )
                Log.i(TAG, "habitat: rasterised ${r.bitmap.width}x${r.bitmap.height} in ${android.os.SystemClock.elapsedRealtime() - t0} ms " +
                    "(terrain analysis ${r.analysisMs} ms, scoring ${r.scoreMs} ms)")
                runCatching {
                    val src = style.getSourceAs<ImageSource>(SRC_HABITAT)
                    src?.setCoordinates(
                        LatLngQuad(
                            LatLng(r.north, r.west), LatLng(r.north, r.east),
                            LatLng(r.south, r.east), LatLng(r.south, r.west),
                        )
                    )
                    src?.setImage(r.bitmap)
                        ?: Log.w(TAG, "habitat: image source missing from the style")
                    if (src == null) uploaded = false
                }.onFailure { uploaded = false; Log.w(TAG, "habitat: image upload failed", it) }
            }
            if (wantWater) {
                // Creeks traced from the same elevation, off the main thread, clipped to the
                // tiles in view (their drainage is truncated at the edge of the loaded halo).
                val lines = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val hy = Hydrology.of(mosaic.grid)
                    WaterLines.of(mosaic, hy.lines(Hydrology.Kind.DRAINAGE))
                }
                runCatching {
                    style.getSourceAs<GeoJsonSource>(SRC_WATER)?.setGeoJson(FeatureCollection.fromFeatures(lines.map { l ->
                        Feature.fromGeometry(LineString.fromLngLats((0 until l.points).map {
                            Point.fromLngLat(l.lngLat[2 * it], l.lngLat[2 * it + 1])
                        })).apply { addStringProperty("k", l.kind.name) }
                    })) ?: run { uploaded = false }
                }.onFailure { uploaded = false; Log.w(TAG, "water: upload failed", it) }
                Log.i(TAG, "water: ${lines.size} channel lines")
            }
            if (wantContours) {
                // The maplibre-contour port over the same tiles, at the interval rule the 3D
                // texture uses; every fifth line is an index contour, drawn stronger.
                val (interval, lines) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val iv = TerrainTextures.contourInterval(ContourLines.interiorReliefM(mosaic))
                    iv to ContourLines.of(mosaic, iv)
                }
                runCatching {
                    style.getSourceAs<GeoJsonSource>(SRC_CONTOUR)?.setGeoJson(FeatureCollection.fromFeatures(lines.map { l ->
                        Feature.fromGeometry(LineString.fromLngLats((0 until l.points).map {
                            Point.fromLngLat(l.lngLat[2 * it], l.lngLat[2 * it + 1])
                        })).apply { addBooleanProperty("i", l.index) }
                    })) ?: run { uploaded = false }
                }.onFailure { uploaded = false; Log.w(TAG, "contours: upload failed", it) }
                Log.i(TAG, "contours: ${lines.size} lines")
                status += " · contours %.0f m".format(interval)
            }
            if (uploaded) {
                st.habitatKey = key
                st.habitatTiles = mosaic.tilesLoaded
                st.habitatComplete = mosaic.tilesLoaded == mosaic.tilesRequested
            }
            onHabitatStatus(status)
        }
    }

    /**
     * Pushes the current state into the current style.
     *
     * Called from update() AND when a style finishes loading. Found on the Phase 7 device run:
     * update() only runs on recomposition, and a stationary phone produces one position
     * change (the GPS provider drops repeats under 2 m). That one change arrived while the
     * offline style was still loading, update() returned early, and nothing ran it again:
     * no layer was pushed until something moved. (Landing on the first fix is the shared
     * camera's job now: FieldViewModel moves it, and this map follows like any app move.)
     */
    fun syncAll(map: MapLibreMap) {
        val style = st.style ?: return
        applyVisibility(style, curLayers.value)
        pushData(style, st, curMe.value, curTrack.value, curFinds.value, curSuggestions.value, curCenter.value)
        refreshHabitat(map, force = false)
    }

    /**
     * Follows an app move of the shared camera: a jump, never an animation, unless the move
     * asked for one (recentre, "Show on map"). A jump cannot be interrupted, which is why the
     * first fix and the 2D/3D switch always jump (CameraStart).
     */
    fun follow(map: MapLibreMap, s: SharedCamera.Snapshot) {
        val c = s.camera
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(c.lat, c.lng)).zoom(c.zoom).bearing(c.bearing).tilt(c.pitch).build()
        )
        runCatching { if (s.animate) map.animateCamera(update, FOLLOW_ANIMATION_MS) else map.moveCamera(update) }
            .onFailure { Log.w(TAG, "camera follow failed", it) }
    }

    // App moves of the shared camera, applied once each. Collected here, not passed as a
    // parameter: the camera changes on every frame of a gesture, and a recomposition per frame
    // would re-run update() (layer visibility, data pushes) for nothing.
    LaunchedEffect(camera) {
        camera.state.collect { s ->
            val map = st.map ?: return@collect
            st.follower?.take(s)?.let { follow(map, it) }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                st.map = map
                map.uiSettings.apply {
                    isCompassEnabled = true
                    isLogoEnabled = false
                    isAttributionEnabled = true
                    isTiltGesturesEnabled = true
                    isRotateGesturesEnabled = true
                }
                // Cap the frame rate: the map is read, not played; 30 fps is smooth for pans
                // and halves GPU work against the default 60 on most panels.
                mapView.setMaximumFps(30)
                // Open exactly where the shared camera is (the seed, the last 2D view, or where
                // the 3D view left it), and from then on follow only app moves made after now.
                val start = camera.state.value
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(start.camera.lat, start.camera.lng))
                    .zoom(start.camera.zoom).bearing(start.camera.bearing).tilt(start.camera.pitch)
                    .build()
                st.follower = SharedCamera.Follower(start.epoch)
                // Every move the user makes is reported, not only where it settles: the shared
                // camera is the truth at every frame, so a switch mid-gesture loses nothing.
                map.addOnCameraMoveListener {
                    val p = map.cameraPosition
                    p.target?.let { t -> camera.report(CameraState(t.latitude, t.longitude, p.zoom, p.bearing, p.tilt)) }
                }
                map.addOnCameraIdleListener { refreshHabitat(map, force = false) }
                // OFFLINE FALLBACK. Measured on the emulator with no network: the remote style
                // failed to load and, because every layer is installed in the style callback,
                // the map stayed a blank grey rectangle - no heatmaps, no track, no suggestions,
                // though all of them are computed on the phone. So when the remote style fails,
                // load a local style that needs no network and install the same layers on it.
                mapView.addOnDidFailLoadingMapListener {
                    if (st.style == null && !st.fallback) {
                        st.fallback = true
                        curOnBasemap.value(false)
                        Log.i(TAG, "basemap style failed; loading the offline fallback style")
                        onHabitatStatus("Basemap unavailable offline; your layers still work")
                        map.setStyle(Style.Builder().fromJson(RASTER_STYLE)) { style ->
                            st.style = style
                            st.basemap = curLayers.value.basemap
                            runCatching { install(style, null) }
                                .onSuccess { Log.i(TAG, "fallback style: layers installed") }
                                .onFailure { Log.w(TAG, "fallback style: install failed", it) }
                            st.resetKeys()
                            refreshHabitat(map, force = true)
                            syncAll(map)
                        }
                    }
                }
                loadStyle(map, curLayers.value.basemap, st) {
                    curOnBasemap.value(st.basemap == Basemap.DARK)
                    refreshHabitat(map, force = true); syncAll(map)
                }
            }
            mapView
        },
        update = {
            val map = st.map ?: return@AndroidView
            val style = st.style
            if (st.basemap != layers.basemap) {
                st.resetKeys()
                st.fallback = false
                curOnBasemap.value(false)
                loadStyle(map, layers.basemap, st) {
                    curOnBasemap.value(st.basemap == Basemap.DARK)
                    refreshHabitat(map, force = true); syncAll(map)
                }
                return@AndroidView
            }
            if (style == null) return@AndroidView
            syncAll(map)
        },
    )
}

/** Mutable, non-Compose map state: changes here must not trigger recomposition. */
private class State {
    var map: MapLibreMap? = null
    var style: Style? = null
    var basemap: Basemap? = null
    var heatJob: Job? = null
    var habitatKey: String? = null        // what the map shows now
    var habitatTiles = -1
    var habitatComplete = false
    var pendingHabitatKey: String? = null // what is being computed
    /** True once the offline fallback style has been loaded. */
    var fallback = false
    /** This map's side of the shared camera's epoch rule; set when the map is ready. */
    var follower: SharedCamera.Follower? = null
    var keyTrack = ""
    var keyFinds = ""
    var keySuggest = ""
    var keyRing = ""
    var keyMe = ""
    fun resetKeys() { habitatKey = null; habitatTiles = -1; habitatComplete = false; pendingHabitatKey = null; keyTrack = ""; keyFinds = ""; keySuggest = ""; keyRing = ""; keyMe = "" }
}

private fun loadStyle(map: MapLibreMap, basemap: Basemap, st: State, onReady: () -> Unit) {
    val builder = if (basemap == Basemap.DARK) Style.Builder().fromUri(DARK_STYLE)
    else Style.Builder().fromJson(RASTER_STYLE)
    st.style = null
    map.setStyle(builder) { style ->
        st.style = style
        st.basemap = basemap
        runCatching { install(style, basemap) }
            .onSuccess { Log.i(TAG, "style ${basemap.name}: layers installed") }
            .onFailure { Log.w(TAG, "style ${basemap.name}: install failed", it) }
        onReady()
    }
}

/** Installs every app layer. [basemap] null = no base raster (the offline fallback). */
private fun install(style: Style, basemap: Basemap?) {
    basemap?.tileUrl?.let { url ->
        style.addSource(RasterSource(SRC_BASE, TileSet("2.1.0", url).apply { maxZoom = basemap.maxZoom.toFloat() }, 256))
        style.addLayer(RasterLayer("g-base-layer", SRC_BASE))
    }
    // Terrarium must be declared: the default encoding decodes the same bytes to wrong heights.
    style.addSource(
        RasterDemSource(
            SRC_DEM,
            TileSet("2.1.0", "${DemTileStore.TERRARIUM}/{z}/{x}/{y}.png").apply {
                maxZoom = DemTileStore.MAX_DEM_ZOOM.toFloat(); encoding = "terrarium"
            },
            256,
        )
    )
    style.addLayer(
        HillshadeLayer("g-hillshade", SRC_DEM).withProperties(
            PropertyFactory.hillshadeExaggeration(0.9f),
            PropertyFactory.hillshadeShadowColor(arrayOf("#04160C")),
            PropertyFactory.hillshadeHighlightColor(arrayOf("#8BA897")),
            PropertyFactory.hillshadeAccentColor("#1A3324"),
        )
    )

    style.addSource(
        ImageSource(
            SRC_HABITAT,
            LatLngQuad(LatLng(1.0, -1.0), LatLng(1.0, 1.0), LatLng(-1.0, 1.0), LatLng(-1.0, -1.0)),
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        )
    )
    style.addLayer(
        RasterLayer("g-habitat-layer", SRC_HABITAT).withProperties(
            PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
        )
    )

    // Contour lines (ContourLines): under the creeks, hairlines with stronger index contours,
    // brown over the light Topo sheet and pale over the dark map. They fade in once close
    // enough to read, as the small drainages do.
    style.addSource(GeoJsonSource(SRC_CONTOUR, FeatureCollection.fromFeatures(emptyList())))
    val contourColour = if (basemap == Basemap.TOPO) "#7A4B26" else "#D8E6DD"
    // `match` labels may only be strings or numbers (style spec), so the boolean is a `case`.
    fun index(yes: Float, no: Float) = Expression.switchCase(Expression.toBool(get("i")), literal(yes), literal(no))
    style.addLayer(
        LineLayer("g-contour-layer", SRC_CONTOUR).withProperties(
            PropertyFactory.lineColor(contourColour),
            PropertyFactory.lineWidth(index(1.3f, 0.6f)),
            PropertyFactory.lineOpacity(interpolate(linear(), zoom(),
                stop(11, 0f), stop(13, index(0.55f, 0.32f)))),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )

    // Creeks and drains traced from elevation (Hydrology): wider and bluer with more water.
    // Small drainages fade in only when close enough to be read, or they would lace the
    // whole map at low zoom.
    style.addSource(GeoJsonSource(SRC_WATER, FeatureCollection.fromFeatures(emptyList())))
    fun byKind(drainage: Float, creek: Float, stream: Float) = match(
        get("k"), literal(stream), stop("DRAINAGE", drainage), stop("CREEK", creek),
    )
    style.addLayer(
        LineLayer("g-water-layer", SRC_WATER).withProperties(
            PropertyFactory.lineColor(match(get("k"), Expression.color(0xFF2A95F0.toInt()),
                stop("DRAINAGE", Expression.color(0xFF7CCBF5.toInt())), stop("CREEK", Expression.color(0xFF3FB2F7.toInt())))),
            PropertyFactory.lineWidth(interpolate(linear(), zoom(),
                stop(10, byKind(0.4f, 1.0f, 1.8f)), stop(16, byKind(1.6f, 3.0f, 5.0f)))),
            PropertyFactory.lineOpacity(interpolate(linear(), zoom(),
                stop(11, byKind(0f, 0.75f, 0.9f)), stop(14, byKind(0.6f, 0.9f, 0.95f)))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )

    // Where you've been: cool blue, so it never reads as the green habitat ramp.
    style.addSource(GeoJsonSource(SRC_VISITED, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        HeatmapLayer("g-visited-layer", SRC_VISITED).withProperties(
            PropertyFactory.heatmapRadius(interpolate(linear(), zoom(), stop(10, 4f), stop(16, 22f))),
            PropertyFactory.heatmapIntensity(interpolate(linear(), zoom(), stop(10, 0.6f), stop(16, 1.4f))),
            PropertyFactory.heatmapColor(
                interpolate(
                    linear(), heatmapDensity(),
                    stop(0.0, rgba(0, 0, 0, 0)),
                    stop(0.2, rgba(30, 80, 160, 0.35)),
                    stop(0.6, rgba(60, 150, 230, 0.6)),
                    stop(1.0, rgba(170, 225, 255, 0.85)),
                )
            ),
        )
    )
    style.addSource(GeoJsonSource(SRC_TRACK, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        LineLayer("g-track-layer", SRC_TRACK).withProperties(
            PropertyFactory.lineColor("#6FB7FF"), PropertyFactory.lineWidth(2f), PropertyFactory.lineOpacity(0.8f),
        )
    )

    // Your finds: amber heat, every find at full weight (UserFinds).
    style.addSource(GeoJsonSource(SRC_FINDS, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        HeatmapLayer("g-finds-heat", SRC_FINDS).withProperties(
            PropertyFactory.heatmapRadius(interpolate(linear(), zoom(), stop(10, 10f), stop(16, 40f))),
            PropertyFactory.heatmapColor(
                interpolate(
                    linear(), heatmapDensity(),
                    stop(0.0, rgba(0, 0, 0, 0)),
                    stop(0.3, rgba(180, 90, 20, 0.45)),
                    stop(0.7, rgba(255, 170, 40, 0.7)),
                    stop(1.0, rgba(255, 235, 170, 0.9)),
                )
            ),
        )
    )
    style.addLayer(
        CircleLayer("g-finds-dots", SRC_FINDS).withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor("#FFB02E"),
            PropertyFactory.circleStrokeColor("#FFB02E"),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleOpacity(interpolate(linear(), zoom(), stop(12, 0f), stop(14, 1f))),
            PropertyFactory.circleStrokeOpacity(interpolate(linear(), zoom(), stop(12, 0f), stop(14, 1f))),
        )
    )

    style.addSource(GeoJsonSource(SRC_RING, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        LineLayer("g-ring-layer", SRC_RING).withProperties(
            PropertyFactory.lineColor("#E6F4EC"), PropertyFactory.lineWidth(1.2f),
            PropertyFactory.lineOpacity(0.45f), PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        )
    )

    // Suggestions: white ring = annotated by the research model; grey = computed only.
    style.addSource(GeoJsonSource(SRC_SUGGEST, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer("g-suggest-layer", SRC_SUGGEST).withProperties(
            PropertyFactory.circleRadius(interpolate(linear(), zoom(), stop(9, 5f), stop(15, 11f))),
            PropertyFactory.circleColor(match(get("p"), literal("MODEL"), Expression.color(0xFF00FF88.toInt()), Expression.color(0xFF8FA89A.toInt()))),
            PropertyFactory.circleStrokeColor(match(get("s"), literal("FOUND"), Expression.color(0xFFFFB02E.toInt()), Expression.color(0xFF06100B.toInt()))),
            PropertyFactory.circleStrokeWidth(2.5f),
            PropertyFactory.circleOpacity(match(get("s"), literal("NOT_FOUND"), literal(0.35f), literal(0.95f))),
        )
    )

    style.addSource(GeoJsonSource(SRC_ME, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer("g-me-layer", SRC_ME).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#E6F4EC"),
            PropertyFactory.circleStrokeColor("#00FF88"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
}

private fun applyVisibility(style: Style, s: MapLayerState) = runCatching {
    fun vis(id: String, on: Boolean) =
        style.getLayer(id)?.setProperties(PropertyFactory.visibility(if (on) Property.VISIBLE else Property.NONE))
    vis("g-hillshade", s.hillshade)
    vis("g-habitat-layer", s.habitat)
    vis("g-water-layer", s.water)
    vis("g-contour-layer", s.contours)
    style.getLayer("g-habitat-layer")?.setProperties(PropertyFactory.rasterOpacity(s.heatmapOpacity))
    vis("g-visited-layer", s.visited)
    vis("g-track-layer", s.trackLine)
    vis("g-finds-heat", s.finds)
    vis("g-finds-dots", s.finds)
    vis("g-suggest-layer", s.suggestions)
}

private fun pushData(
    style: Style, st: State, me: FieldLocation?, track: List<TrackPoint>, finds: List<Find>,
    suggestions: List<Suggestion>, center: Pair<Double, Double>?,
) = runCatching {
    val kTrack = "${track.size}:${track.lastOrNull()?.time}"
    if (kTrack != st.keyTrack) {
        st.keyTrack = kTrack
        Log.i(TAG, "push: ${track.size} track points")
        style.getSourceAs<GeoJsonSource>(SRC_VISITED)?.setGeoJson(
            FeatureCollection.fromFeatures(track.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) })
        )
        val lines = track.groupBy { it.sessionId }.values.filter { it.size >= 2 }.map { seg ->
            Feature.fromGeometry(LineString.fromLngLats(seg.map { Point.fromLngLat(it.lng, it.lat) }))
        }
        style.getSourceAs<GeoJsonSource>(SRC_TRACK)?.setGeoJson(FeatureCollection.fromFeatures(lines))
    }
    val kFinds = finds.joinToString { it.id }
    if (kFinds != st.keyFinds) {
        st.keyFinds = kFinds
        style.getSourceAs<GeoJsonSource>(SRC_FINDS)?.setGeoJson(
            FeatureCollection.fromFeatures(finds.map { f -> Feature.fromGeometry(Point.fromLngLat(f.lng, f.lat)) })
        )
    }
    val kSug = suggestions.joinToString { it.id + it.status }
    if (kSug != st.keySuggest) {
        st.keySuggest = kSug
        Log.i(TAG, "push: ${suggestions.size} suggestions")
        style.getSourceAs<GeoJsonSource>(SRC_SUGGEST)?.setGeoJson(
            FeatureCollection.fromFeatures(suggestions.map { s ->
                Feature.fromGeometry(Point.fromLngLat(s.lng, s.lat)).apply {
                    addStringProperty("p", s.provenance)
                    addStringProperty("s", s.status)
                    addNumberProperty("r", s.rank)
                }
            })
        )
    }
    val kRing = center?.let { "%.4f:%.4f".format(it.first, it.second) } ?: ""
    if (kRing != st.keyRing) {
        st.keyRing = kRing
        style.getSourceAs<GeoJsonSource>(SRC_RING)?.setGeoJson(
            FeatureCollection.fromFeatures(
                center?.let { listOf(Feature.fromGeometry(ring(it.first, it.second, RadiusScan.RADIUS_M))) } ?: emptyList()
            )
        )
    }
    val kMe = me?.let { "%.6f:%.6f".format(it.lat, it.lng) } ?: ""
    if (kMe != st.keyMe) {
        st.keyMe = kMe
        style.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(
            FeatureCollection.fromFeatures(me?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat))) } ?: emptyList())
        )
    }
}

/** A geodesic circle as a closed line, 96 vertices. */
fun ring(lat: Double, lng: Double, radiusM: Double): LineString {
    val d = radiusM / 6_371_000.0
    val la = Math.toRadians(lat); val lo = Math.toRadians(lng)
    val pts = (0..96).map { i ->
        val b = 2 * Math.PI * i / 96
        val lat2 = Math.asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lng2 = lo + Math.atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(lat2))
        Point.fromLngLat(Math.toDegrees(lng2), Math.toDegrees(lat2))
    }
    return LineString.fromLngLats(pts)
}

/** A raster basemap needs no style server: an empty style with a background. */
private const val RASTER_STYLE = """
{"version":8,"name":"gensingo-raster","sources":{},"layers":[
{"id":"g-bg","type":"background","paint":{"background-color":"#06100B"}}]}
"""
