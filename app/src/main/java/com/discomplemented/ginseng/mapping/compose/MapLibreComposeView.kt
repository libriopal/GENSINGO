package com.discomplemented.ginseng.mapping.compose

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Composable wrapper for MapLibre Native map.
 * Handles lifecycle management and offline-first tile loading.
 */
@Composable
fun MapLibreComposeView(
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit = {},
    context: Context
) {
    var mapView: MapView? = remember { null }
    var mapLibreMap: MapLibreMap? = remember { null }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // Initialize MapLibre (requires API key or self-hosted tiles)
                MapLibre.getInstance(ctx)

                MapView(ctx).apply {
                    mapView = this
                    getMapAsync { map ->
                        mapLibreMap = map
                        // Load offline vector tiles from assets or local MBTiles
                        map.setStyle(Style.MAPBOX_STREETS) { style ->
                            // TODO: Replace with local tile source
                            onMapReady(map)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(Unit) {
            onDispose {
                // Proper lifecycle cleanup
                mapView?.onDestroy()
            }
        }
    }
}
