package com.discomplemented.ginseng.mapping.layers

import android.content.Context
import com.discomplemented.ginseng.domain.model.LatLng
import java.io.File

/**
 * Responsible for the high-fidelity rendering of the micro-terrain mesh.
 * Man<ages the lifecycle of high-resolution vertex/index buffers and custom shaders.
 */
interface MicroTerrainRenderer {
    /**
     * Loads high-resolution DEM data and builds the vertex/index buffers.
     * Must be called on a background thread.
     * @param demFile The local file containing the elevation data.
     * @param center The center of the terrain patch.
     * @param radius The radius of the patch in meters.
     */
    suspend fun loadTerrainData(demFile: File, center: LatLng, radius: Double)

    /**
     * Updates the rendering parameters (e.g., alpha for fading) to facilitate transitions.
     * @param alpha The interpolation factor from 0.0 (invisible) to 1.0 (fully opaque).
     * @param projectionMatrix The current projection matrix from the MapLibre camera.
     */
    fun updateRenderState(alpha: Float, projectionMatrix: FloatArray)

    /**
     * Renders the high-resolution mesh into the current GPU context.
     * Should be called within the MapLibre frame loop.
     */
    fun onDraw(projectionMatrix: FloatArray, viewMatrix: FloatArray)

    /**
     * Releases all GPU resources and clears buffers.
     */
    fun release()
}
