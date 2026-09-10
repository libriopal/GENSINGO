package com.discomplemented.ginseng.mapping.layers

import android.content.Context
import android.util.Log
import com.discomplemented.ginseng.domain.model.LatLng
import com.discomplemented.ginseng.mapping.layers.CoordinateTransformer
import com.discomplemented.ginseng.mapping.util.NioGeoTiffParser
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.inject.Inject

/**
 * Implementation of the high-resolution micro-terrain renderer.
 * Manages the lifecycle of high-density vertex/index buffers and handles
 * the synchronization between the MapLibre projection and the local mesh.
 */
class MicroTerrainRendererImpl @Inject constructor(
    private val context: Context
) : MicroTerrainRenderer {

    private val TAG = "MicroTerrainRenderer"
    private var isLoaded = false
    @Volatile
    private var meshFadeAlpha = 1.0f
    @Volatile
    private var transformer: CoordinateTransformer? = null
    private var loadingJob: Job? = null

    // Current active buffers
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: IntBuffer? = null
    private var vertexCount = 0
    private var indexCount = 0

    // Buffers for the incoming terrain (for smooth transition)
    private var nextVertexBuffer: FloatBuffer? = null
    private var nextIndexBuffer: IntBuffer? = null
    private var nextVertexCount = 0
    private var nextIndexCount = 0
    private var isTransitioning = false

    // Buffers to reuse to reduce allocation pressure
    private var reusableVBuffer: FloatBuffer? = null
    private var reusableIBuffer: IntBuffer? = null

    // Current rendering state
    private var currentAlpha = 1.0f
    private var currentProjectionMatrix = FloatArray(16)
    private var lastTransitionTime = 0L

    override suspend fun loadTerrainData(demFile: File, center: LatLng, radius: Double) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading high-fidelity DEM data from ${demFile.absolutePath} for center $center, radius $radius")

            // 1. Initialize Coordinate Transformer
            transformer = CoordinateTransformer(center)

            // 2. Parse DEM file
            val parser = NioGeoTiffParser(demFile)
            val centerElevation = parser.getElevationAt(center.latitude, center.longitude)

            // 3. Generate high-density grid (e.g., 128x128 vertices)
            val gridRes = 128
            val step = (radius * 2.0) / (gridRes - 1)

            // Vertices: [x, y, z, x, y, z, ...]
            if (reusableVBuffer == null || reusableVBuffer!!.capacity() < gridRes * gridRes * 3) {
                reusableVBuffer = ByteBuffer.allocateDirect(gridRes * gridRes * 3 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            }
            val currentVBuffer = reusableVBuffer!!

            for (i in 0 until gridRes) {
                for (j in 0 until gridRes) {
                    val localX = (i.toDouble() * step) - radius
                    val localZ = (j.toDouble() * step) - radius

                    val latLng = transformer?.toGlobal(localX, localZ) ?: center
                    val elevation = parser.getElevationAt(latLng.latitude, latLng.longitude)
                    val localY = (elevation - centerElevation).toFloat()

                    currentVBuffer.put(localX.toFloat())
                    currentVBuffer.put(localY)
                    currentVBuffer.put(localZ.toFloat())
                }
            }
            currentVBuffer.position(0)

            // Indices: [i1, i2, i3, i1, i3, i4, ...]
            if (reusableIBuffer == null || reusableIBuffer!!.capacity() < (gridRes - 1) * (gridRes - 1) * 6) {
                reusableIBuffer = ByteBuffer.allocateDirect((gridRes - 1) * (gridRes - 1) * 6 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer()
            }
            val currentIBuffer = reusableIBuffer!!

            for (i in 0 until gridRes - 1) {
                for (j in 0 until gridRes - 1) {
                    val row1 = i * gridRes
                    val row2 = (i + 1) * gridRes

                    currentIBuffer.put(row1 + j)
                    currentIBuffer.put(row1 + j + 1)
                    currentIBuffer.put(row2 + j)

                    currentIBuffer.put(row1 + j + 1)
                    currentIBuffer.put(row2 + j + 1)
                    currentIBuffer.put(row2 + j)
                }
            }
            currentIBuffer.position(0)

            // Instead of immediate replacement, set up a transition
            nextVertexBuffer = currentVBuffer
            nextIndexBuffer = currentIBuffer
            nextVertexCount = gridRes * gridRes
            nextIndexCount = (gridRes - 1) * (gridRes - 1) * 6
            isTransitioning = true
            meshFadeAlpha = 0.0f
            isLoaded = true

            Log.d(TAG, "New terrain data loaded, starting transition...")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to load terrain data", e)
            isLoaded = false
        }
    }

    override fun updateRenderState(alpha: Float, projectionMatrix: FloatArray) {
        if (!isLoaded) return
        currentAlpha = alpha
        System.arraycopy(projectionMatrix, 0, currentProjectionMatrix, 0, 16)
    }

    override fun onDraw(projectionMatrix: FloatArray, viewMatrix: FloatArray) {
        if (!isLoaded || currentAlpha <= 0.0f) return

        if (isTransitioning) {
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastTransitionTime) / 1000.0 // seconds

            // Transition over 500ms
            val transitionDuration = 0.5
            meshFadeAlpha += (deltaTime / transitionDuration).toFloat()
            lastTransitionTime = currentTime

            if (meshFadeAlpha >= 1.0f) {
                // Transition complete: swap buffers
                vertexBuffer = nextVertexBuffer
                indexBuffer = nextIndexBuffer
                vertexCount = nextVertexCount
                indexCount = nextIndexCount

                nextVertexBuffer = null
                nextIndexBuffer = null
                nextVertexCount = 0
                nextIndexCount = 0

                isTransitioning = false
                meshFadeAlpha = 1.0f
                Log.d(TAG, "Terrain transition complete.")
            }
        }

        // In a real implementation, this would use OpenGL ES to draw the mesh
        // with the currentAlpha * (if (isTransitioning) meshFadeAlpha else 1.0f) applied to the fragment shader.
    }

    override fun release() {
        Log.d(TAG, "Releasing micro-terrain GPU resources.")
        isLoaded = false
        vertexBuffer = null
        indexBuffer = null
        transformer = null
    }
}
