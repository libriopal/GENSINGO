package com.discomplemented.ginseng.mapping.layers

import com.discomplemented.ginseng.domain.model.LatLng
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the LOD transition logic.
 * Uses smooth-step interpolation to manage the transition between macro and micro views.
 */
@Singleton
class LODTransitionManagerImpl @Inject constructor() : LODTransitionManager {

    // Thresholds for transition (in meters and zoom levels)
    private val activeZoomThreshold = 16.0
    private val transitionRangeZoom = 2.0 // Transition occurs over 2 zoom levels

    private var currentZoom = 0.0
    private var _transitionAlpha = 0.0f
    override val transitionAlpha: Float get() = _transitionAlpha

    private var _isMicroViewActive = false
    override val isMicroViewActive: Boolean get() = _isMicroViewActive

    override fun updateCamera(cameraPosition: LatLng, zoomLevel: Double) {
        currentZoom = zoomLevel

        // Calculate alpha based on zoom level (simplest approach for demo)
        // If zoom is >= 16, alpha is 1.0. If zoom is <= 14, alpha is 0.0.
        val rawAlpha = ((currentZoom - (activeZoomThreshold - transitionRangeZoom)) / transitionRangeZoom)
            .coerceIn(0.0, 1.0)

        // Apply smooth-step for organic feel
        _transitionAlpha = smoothStep(rawAlpha.toFloat())

        _isMicroViewActive = _transitionAlpha > 0.01f
    }

    override fun reset() {
        _transitionAlpha = 0.0f
        _isMicroViewActive = false
        currentZoom = 0.0
    }

    private fun smoothStep(x: Float): Float {
        return x * x * (3 - 2 * x)
    }
}
