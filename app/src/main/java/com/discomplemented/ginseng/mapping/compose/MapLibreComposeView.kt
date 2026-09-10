package com.discomplemented.ginseng.mapping.compose

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.CameraChange

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    onMapReady: (MapView) -> Unit = {},
    onCameraMove: (lat: Double, lon: Double, zoom: Double) -> Unit = {},
    onMapClick: (lat: Double, lon: Double) -> Unit = {},
    onDraw: (projectionMatrix: FloatArray, viewMatrix: FloatArray) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    // Handle MapView Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                onMapReady(this)
                addOnCameraMoveListener(object : org.maplibre.android.maps.OnCameraMoveListener {
                    override fun onCameraMove(cameraPosition: org.maplibre.android.maps.CameraPosition) {
                        onCameraMove(
                            cameraPosition.target.latitude,
                            cameraPosition.target.longitude,
                            cameraPosition.zoom
                        )
                    }
                })

                addOnMapClickListener { point ->
                    onMapClick(point.latitude, point.longitude)
                    true
                }
            }
        },
        modifier = modifier,
        update = { view ->
            // In a real implementation, we would hook into the GL surface
            // or use a Custom Layer to perform the draw call.
        }
    )
}
