package com.ginsengo.steward.ui.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ginsengo.steward.data.db.GinsengPatch
import com.ginsengo.steward.field.FieldLocation
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.SuitabilityRasterizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.ColorReliefLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

private const val SRC_BASE_RASTER = "gen-base-raster"
private const val LYR_BASE_RASTER = "gen-base-raster-layer"
private const val SRC_DEM = "gen-dem"
private const val LYR_HILLSHADE = "gen-hillshade"
private const val LYR_RELIEF = "gen-relief"
private const val SRC_HEAT = "gen-heat"
private const val LYR_HEAT = "gen-heat-layer"
private const val SRC_PATCHES = "gen-patches"
private const val LYR_PATCHES = "gen-patches-layer"
private const val SRC_ME = "gen-me"
private const val LYR_ME = "gen-me-layer"

/**
 * Elevation colour ramp for the height overlay, rendered on the GPU by MapLibre's
 * color-relief layer. Stops are chosen for the Appalachian band the app actually covers
 * (roughly 150-1800 m) rather than a global ramp, so the whole gradient is spent on ground
 * a digger might stand on instead of on sea level and the Himalayas.
 */
private const val RELIEF_RAMP =
    """["interpolate",["linear"],["elevation"],""" +
    """120,"#06222E",320,"#0B3B33",560,"#125A3A",820,"#2E7D46",""" +
    """1080,"#8CA84E",1350,"#FFC857",1700,"#FF7A4D"]"""

/**
 * The field map.
 *
 * Layer order, bottom to top: basemap (vector dark, or USGS raster) -> color relief ->
 * hillshade -> habitat heatmap -> track/patches -> current position.
 *
 * On 3D: MapLibre Android has no terrain API at any published version — verified against
 * the 13.6.1 artifact, which contains no Terrain class and no setTerrain, and wires
 * raster-dem only into hillshade. "terrain3d" here tilts the camera and pushes hillshade
 * exaggeration so relief reads as depth. It is a pitched relief view, not a terrain mesh,
 * and it is labelled that way in the UI.
 */
@SuppressLint("MissingPermission")
@Composable
fun FieldMap(
    patches: List<GinsengPatch>,
    me: FieldLocation?,
    followMe: Boolean,
    layers: MapLayerState,
    demStore: DemTileStore,
    modifier: Modifier = Modifier,
    onStyleFailed: () -> Unit = {},
    onHeatmapStatus: (SuitabilityRasterizer.Raster?) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    remember { runCatching { MapLibre.getInstance(context) } }

    val curPatches = rememberUpdatedState(patches)
    val curMe = rememberUpdatedState(me)
    val curFollow = rememberUpdatedState(followMe)
    val curLayers = rememberUpdatedState(layers)

    val mapView = remember { runCatching { MapView(context) }.getOrNull() }
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val styleRef = remember { arrayOfNulls<Style>(1) }
    val loadedBasemap = remember { arrayOfNulls<Basemap>(1) }
    val heatJob = remember { arrayOfNulls<Job>(1) }
    val lastHeatKey = remember { arrayOfNulls<String>(1) }

    if (mapView == null) {
        onStyleFailed()
        return
    }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            heatJob[0]?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onDestroy() }
        }
    }

    fun refreshHeatmap(map: MapLibreMap, force: Boolean) {
        val style = styleRef[0] ?: return
        if (!curLayers.value.habitatHeatmap) return
        val cam = map.cameraPosition
        val region = runCatching { map.projection.visibleRegion.latLngBounds }.getOrNull() ?: return

        val demZoom = DemTileStore.demZoomFor(cam.zoom)
        // Recompute only when the view has actually moved to new ground, otherwise every
        // idle event would re-run multiple-flow accumulation over the whole mosaic.
        val key = "$demZoom:${"%.4f".format(region.latitudeNorth)}:" +
                "${"%.4f".format(region.longitudeWest)}:" +
                "${"%.4f".format(region.latitudeSouth)}:${"%.4f".format(region.longitudeEast)}"
        if (!force && key == lastHeatKey[0]) return
        lastHeatKey[0] = key

        heatJob[0]?.cancel()
        heatJob[0] = scope.launch {
            val mosaic = demStore.grid(
                north = region.latitudeNorth, west = region.longitudeWest,
                south = region.latitudeSouth, east = region.longitudeEast,
                z = demZoom, haloTiles = 1,
            )
            if (mosaic == null) { onHeatmapStatus(null); return@launch }

            val raster = SuitabilityRasterizer.rasterise(
                mosaic = mosaic,
                outSize = DemTileStore.rasterSizeFor(cam.zoom),
                tpiRadiusM = DemTileStore.tpiRadiusMetresFor(cam.zoom),
            )
            runCatching {
                val quad = LatLngQuad(
                    LatLng(raster.north, raster.west),
                    LatLng(raster.north, raster.east),
                    LatLng(raster.south, raster.east),
                    LatLng(raster.south, raster.west),
                )
                val src = style.getSourceAs<ImageSource>(SRC_HEAT)
                src?.setCoordinates(quad)
                src?.setImage(raster.bitmap)
            }
            onHeatmapStatus(raster)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                mapRef[0] = map

                // ---- free movement (PRD: unrestricted pan/zoom/rotate/tilt) ----
                map.uiSettings.apply {
                    isScrollGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true
                    isQuickZoomGesturesEnabled = true
                    isCompassEnabled = true
                    isAttributionEnabled = true
                    isLogoEnabled = false
                }

                val start = curMe.value
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(start?.lat ?: 37.8, start?.lng ?: -81.2))
                    .zoom(if (start != null) 14.0 else 6.0)
                    .build()

                map.addOnCameraIdleListener { refreshHeatmap(map, force = false) }
                applyStyle(map, curLayers.value.basemap, styleRef, loadedBasemap, onStyleFailed) {
                    syncLayers(it, curLayers.value)
                    pushFeatures(it, curPatches.value, curMe.value)
                    refreshHeatmap(map, force = true)
                }
            }
            mapView
        },
        update = {
            val map = mapRef[0] ?: return@AndroidView
            val want = curLayers.value

            if (loadedBasemap[0] != want.basemap) {
                applyStyle(map, want.basemap, styleRef, loadedBasemap, onStyleFailed) {
                    syncLayers(it, want)
                    pushFeatures(it, curPatches.value, curMe.value)
                    refreshHeatmap(map, force = true)
                }
            } else {
                styleRef[0]?.let { style ->
                    syncLayers(style, want)
                    pushFeatures(style, curPatches.value, curMe.value)
                }
                refreshHeatmap(map, force = false)
            }

            // Pitch for the relief "3D" read.
            val targetTilt = if (want.terrain3d) 55.0 else 0.0
            if (kotlin.math.abs(map.cameraPosition.tilt - targetTilt) > 1.0) {
                runCatching {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(map.cameraPosition).tilt(targetTilt).build()
                        ), 600
                    )
                }
            }

            val here = curMe.value
            if (here != null && curFollow.value) {
                runCatching {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(here.lat, here.lng),
                            map.cameraPosition.zoom.coerceAtLeast(14.0),
                        )
                    )
                }
            }
        },
    )
}

private fun applyStyle(
    map: MapLibreMap,
    basemap: Basemap,
    styleRef: Array<Style?>,
    loadedBasemap: Array<Basemap?>,
    onStyleFailed: () -> Unit,
    onReady: (Style) -> Unit,
) {
    val builder = if (basemap == Basemap.DARK) {
        Style.Builder().fromUri(DARK_STYLE)
    } else {
        // A raster basemap needs no style server at all: an empty style plus one raster
        // layer. That also means satellite and topo keep working if the vector style host
        // is unreachable.
        Style.Builder().fromJson(EMPTY_STYLE_JSON)
    }
    runCatching {
        map.setStyle(builder) { style ->
            styleRef[0] = style
            loadedBasemap[0] = basemap
            runCatching { installBaseLayers(style, basemap) }
            onReady(style)
        }
    }.onFailure { onStyleFailed() }
}

private fun installBaseLayers(style: Style, basemap: Basemap) {
    basemap.tileUrl?.let { url ->
        if (style.getSource(SRC_BASE_RASTER) == null) {
            style.addSource(
                RasterSource(
                    SRC_BASE_RASTER,
                    TileSet("2.1.0", url).apply {
                        minZoom = 0f
                        maxZoom = basemap.maxZoom.toFloat()
                    },
                    256,
                )
            )
            style.addLayer(RasterLayer(LYR_BASE_RASTER, SRC_BASE_RASTER))
        }
    }

    if (style.getSource(SRC_DEM) == null) {
        // Terrarium encoding must be declared explicitly. The default is "mapbox", and
        // the two encodings decode to completely different elevations from the same bytes
        // with no error — the hillshade would simply be wrong.
        style.addSource(
            RasterDemSource(
                SRC_DEM,
                TileSet("2.1.0", "${DemTileStore.TERRARIUM}/{z}/{x}/{y}.png").apply {
                    minZoom = 0f
                    maxZoom = DemTileStore.MAX_DEM_ZOOM.toFloat()
                    encoding = "terrarium"
                },
                256,
            )
        )
        style.addLayer(
            ColorReliefLayer(LYR_RELIEF, SRC_DEM).withProperties(
                PropertyFactory.colorReliefColor(Expression.raw(RELIEF_RAMP)),
                PropertyFactory.colorReliefOpacity(0f),
                PropertyFactory.visibility(Property.NONE),
            )
        )
        style.addLayer(
            HillshadeLayer(LYR_HILLSHADE, SRC_DEM).withProperties(
                PropertyFactory.hillshadeExaggeration(0.6f),
                // MapLibre 13 takes ARRAYS here: shadow/highlight colours are specified
                // per illumination source, not as a single colour.
                PropertyFactory.hillshadeShadowColor(arrayOf("#04160C")),
                PropertyFactory.hillshadeHighlightColor(arrayOf("#8BA897")),
                PropertyFactory.hillshadeAccentColor("#1A3324"),
                PropertyFactory.visibility(Property.NONE),
            )
        )
    }

    if (style.getSource(SRC_HEAT) == null) {
        val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        style.addSource(
            ImageSource(
                SRC_HEAT,
                LatLngQuad(
                    LatLng(1.0, -1.0), LatLng(1.0, 1.0),
                    LatLng(-1.0, 1.0), LatLng(-1.0, -1.0),
                ),
                placeholder,
            )
        )
        style.addLayer(
            RasterLayer(LYR_HEAT, SRC_HEAT).withProperties(
                PropertyFactory.rasterOpacity(0f),
                // Linear resampling is the GPU half of the antialiasing: the rasteriser
                // integrates DEM cells into output pixels, and this filters those pixels
                // onto the screen. Nearest here would reintroduce the blocking the
                // rasteriser just spent supersampling to remove.
                PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
                PropertyFactory.visibility(Property.NONE),
            )
        )
    }

    if (style.getSource(SRC_PATCHES) == null) {
        style.addSource(GeoJsonSourceCompat(SRC_PATCHES))
        style.addLayer(
            CircleLayer(LYR_PATCHES, SRC_PATCHES).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#00FF88"),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#06100B"),
                PropertyFactory.circleOpacity(0.9f),
            )
        )
        style.addSource(GeoJsonSourceCompat(SRC_ME))
        style.addLayer(
            CircleLayer(LYR_ME, SRC_ME).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor("#E6F4EC"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#00FF88"),
            )
        )
    }
}

private fun syncLayers(style: Style, s: MapLayerState) {
    runCatching {
        style.getLayer(LYR_RELIEF)?.setProperties(
            PropertyFactory.visibility(if (s.heightOverlay) Property.VISIBLE else Property.NONE),
            PropertyFactory.colorReliefOpacity(if (s.heightOverlay) s.heightOpacity else 0f),
        )
        style.getLayer(LYR_HILLSHADE)?.setProperties(
            PropertyFactory.visibility(
                if (s.hillshade || s.terrain3d) Property.VISIBLE else Property.NONE
            ),
            // Exaggeration is pushed when the pitched view is on: on a tilted camera the
            // extra relief is what sells depth, since there is no real mesh to cast it.
            PropertyFactory.hillshadeExaggeration(if (s.terrain3d) 0.95f else 0.6f),
        )
        style.getLayer(LYR_HEAT)?.setProperties(
            PropertyFactory.visibility(if (s.habitatHeatmap) Property.VISIBLE else Property.NONE),
            PropertyFactory.rasterOpacity(if (s.habitatHeatmap) s.heatmapOpacity else 0f),
        )
    }
}

private fun pushFeatures(style: Style, patches: List<GinsengPatch>, me: FieldLocation?) {
    runCatching {
        style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(SRC_PATCHES)
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    patches.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }
                )
            )
        style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(SRC_ME)
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    me?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat))) }
                        ?: emptyList()
                )
            )
    }
}

private fun GeoJsonSourceCompat(id: String) =
    org.maplibre.android.style.sources.GeoJsonSource(
        id, FeatureCollection.fromFeatures(emptyList())
    )

private typealias Property = org.maplibre.android.style.layers.Property

/** Minimal style so a raster basemap needs no style server. */
private const val EMPTY_STYLE_JSON = """
{"version":8,"name":"gensingo-raster","sources":{},"layers":[
{"id":"gen-bg","type":"background","paint":{"background-color":"#06100B"}}]}
"""
