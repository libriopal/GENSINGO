package com.discomplemented.ginseng.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.discomplemented.ginseng.mapping.compose.MapLibreView
import com.discomplemented.ginseng.mapping.layers.MapOverlayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import org.maplibre.android.maps.MapView

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val trackHistory by viewModel.trackHistory.collectAsState()
    val ginsengPatches by viewModel.ginsengPatches.collectAsState()
    val suitabilityPoints by viewModel.suitabilityHeatmap.collectAsState()

    // Holds the overlay manager once the map is ready
    var overlayManager by remember { mutableStateOf<MapOverlayManager?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { mapView ->
                mapView.getMapAsync { map ->
                    map.getStyle { style ->
                        overlayManager = MapOverlayManager(style)
                    }
                }
            },
            onCameraMove = { lat, lon, zoom ->
                viewModel.onCameraChanged(lat, lon, zoom)
            },
            onMapClick = { lat, lon ->
                viewModel.logPatch(lat, lon)
            },
            onDraw = { projectionMatrix, viewMatrix ->
                viewModel.renderMicroTerrain(projectionMatrix, viewMatrix)
            }
        )

        // UI Overlay for Controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Button(onClick = {
                // Trigger a mock scan at a fixed location for demonstration
                viewModel.runPredictiveScan(35.5, -82.5, 1000.0)
            }) {
                Text("Run AI Scan")
            }
        }

        // Update overlays when data changes
        LaunchedEffect(trackHistory, ginsengPatches, suitabilityPoints, overlayManager) {
            overlayManager?.let { manager ->
                manager.updateTrackLayer(trackHistory)
                manager.updatePatchLayer(ginsengPatches)
                manager.updateSuitabilityLayer(suitabilityPoints)
            }
        }
    }
}
