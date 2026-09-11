package com.ginsengo.steward.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ginsengo.steward.data.db.GinsengPatch
import com.ginsengo.steward.field.FieldLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val SRC_PATCHES = "gensingo-patches"
private const val LYR_PATCHES = "gensingo-patches-layer"
private const val SRC_ME = "gensingo-me"
private const val LYR_ME = "gensingo-me-layer"

/**
 * Full-screen dark map canvas (PRD §4.1, §5.5).
 *
 * Patch pins are drawn as a GeoJsonSource + CircleLayer rather than view annotations: the
 * circle layer is part of the core SDK, so this avoids pulling in the annotation plugin,
 * and it keeps hundreds of patches on the GPU instead of in the view hierarchy.
 *
 * Tiles are the only thing in GENSINGO that touches the network. If the style fails to
 * load the map stays dark and every other workflow still works - which is the normal case
 * on a hillside with no signal.
 */
@SuppressLint("MissingPermission")
@Composable
fun FieldMap(
    patches: List<GinsengPatch>,
    me: FieldLocation?,
    followMe: Boolean,
    modifier: Modifier = Modifier,
    onStyleFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapLibre.getInstance must run before any MapView is constructed.
    remember { runCatching { MapLibre.getInstance(context) } }

    val currentPatches = rememberUpdatedState(patches)
    val currentMe = rememberUpdatedState(me)
    val currentFollow = rememberUpdatedState(followMe)

    val mapView = remember { runCatching { MapView(context) }.getOrNull() }
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val styleRef = remember { arrayOfNulls<Style>(1) }

    if (mapView == null) {
        // Renderer unavailable (some emulators, no GL). Everything else still works.
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
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onDestroy() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                mapRef[0] = map
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.isLogoEnabled = false

                val start = currentMe.value
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(start?.lat ?: 37.8, start?.lng ?: -81.2))
                    .zoom(if (start != null) 14.0 else 6.0)
                    .build()

                runCatching {
                    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                        styleRef[0] = style
                        style.addSource(GeoJsonSource(SRC_PATCHES, FeatureCollection.fromFeatures(emptyList())))
                        style.addLayer(
                            CircleLayer(LYR_PATCHES, SRC_PATCHES).withProperties(
                                PropertyFactory.circleRadius(7f),
                                PropertyFactory.circleColor("#00FF88"),
                                PropertyFactory.circleStrokeWidth(2f),
                                PropertyFactory.circleStrokeColor("#06100B"),
                                PropertyFactory.circleOpacity(0.9f),
                            )
                        )
                        style.addSource(GeoJsonSource(SRC_ME, FeatureCollection.fromFeatures(emptyList())))
                        style.addLayer(
                            CircleLayer(LYR_ME, SRC_ME).withProperties(
                                PropertyFactory.circleRadius(6f),
                                PropertyFactory.circleColor("#E6F4EC"),
                                PropertyFactory.circleStrokeWidth(3f),
                                PropertyFactory.circleStrokeColor("#00FF88"),
                            )
                        )
                        push(style, currentPatches.value, currentMe.value)
                    }
                }.onFailure { onStyleFailed() }
            }
            mapView
        },
        update = {
            val style = styleRef[0]
            val map = mapRef[0]
            if (style != null) push(style, currentPatches.value, currentMe.value)
            val here = currentMe.value
            if (map != null && here != null && currentFollow.value) {
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

private fun push(style: Style, patches: List<GinsengPatch>, me: FieldLocation?) {
    runCatching {
        (style.getSourceAs<GeoJsonSource>(SRC_PATCHES))?.setGeoJson(
            FeatureCollection.fromFeatures(
                patches.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }
            )
        )
        (style.getSourceAs<GeoJsonSource>(SRC_ME))?.setGeoJson(
            FeatureCollection.fromFeatures(
                me?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat))) }
                    ?: emptyList()
            )
        )
    }
}
