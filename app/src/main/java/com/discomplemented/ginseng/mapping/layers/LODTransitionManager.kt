package com.discomplemented.ginseng.mapping.layers

import com.discomplemented.ginseng.domain.model.LatLng

/**
 * Orchestrates the transition between MapLibre macro-tiles and the custom micro-terrain mesh.
 */
interface LODTransitionManager {
    /**
     * Monitors the camera position and zoom level to manage the lifecycle of the micro-terrain.
     * @param cameraPosition The current camera latitude/longitude.
     * @param zoomLevel The current camera zoom level.
     */
    fun updateCamera(cameraPosition: LatLng, zoomLevel: Double)

    /**
     * Returns the current interpolation factor (0.0 to 1.0) for the micro-mesh alpha.
     * 0.0 = Macro (MapLibre only), 1.0 = Micro (Mesh fully opaque).
     */
    val transitionAlpha: Float

    /**
     * Returns true if the micro-terrain should be actively loaded and rendered.
     */
    val isMicroViewActive: Boolean

    /**
     * Resets the transition state.
     */
    fun reset()
}
