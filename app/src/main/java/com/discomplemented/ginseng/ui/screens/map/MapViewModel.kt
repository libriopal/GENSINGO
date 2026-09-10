package com.discomplemented.ginseng.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.usecase.GetHabitatSuitabilityUseCase
import com.discomplemented.ginseng.mapping.layers.LODTransitionManager
import com.discomplemented.ginseng.mapping.layers.MicroTerrainRenderer
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import com.discomplemented.ginseng.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import android.util.Log
import com.discomplemented.ginseng.core.util.GeoUtils
import kotlinx.coroutines.CancellationException
import java.util.UUID

data class SuitabilityPoint(
    val latitude: Double,
    val longitude: Double,
    val score: Float
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val ginsengPatchRepository: GinsengPatchRepository,
    private val getHabitatSuitabilityUseCase: GetHabitatSuitabilityUseCase,
    private val lodTransitionManager: LODTransitionManager,
    private val microTerrainRenderer: MicroTerrainRenderer,
    private val demRepository: com.discomplemented.ginseng.domain.repository.DemRepository
) : ViewModel() {

    private var terrainLoadingJob: Job? = null
    private var lastProcessedLocation: com.discomplemented.ginseng.domain.model.LatLng? = null

    val trackHistory: StateFlow<List<TrackNode>> = trackRepository.getTrackHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val ginsengPatches: StateFlow<List<GinsengPatch>> = ginsengPatchRepository.getAllPatches()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _suitabilityHeatmap = MutableStateFlow<List<SuitabilityPoint>>(emptyList())
    val suitabilityHeatmap: StateFlow<List<SuitabilityPoint>> = _suitabilityHeatmap.asStateFlow()

    val transitionAlpha: Float get() = lodTransitionManager.transitionAlpha
    val isMicroViewActive: Boolean get() = lodTransitionManager.isMicroViewActive

    /**
     * Updates the LOD state based on camera position and zoom.
     */
    fun onCameraChanged(lat: Double, lon: Double, zoom: Double) {
        val cameraPos = com.discomplemented.ginseng.domain.model.LatLng(lat, lon)
        lodTransitionManager.updateCamera(cameraPos, zoom)

        if (lodTransitionManager.isMicroViewActive) {
            // Significant movement threshold: ~20 meters to avoid jitter/redundant reloads
            val isSignificantMove = lastProcessedLocation?.let {
                GeoUtils.distanceBetween(it, cameraPos) > 20.0
            } ?: true

            if (isSignificantMove) {
                lastProcessedLocation = cameraPos
                terrainLoadingJob?.cancel()
                terrainLoadingJob = viewModelScope.launch {
                    try {
                        demRepository.getDemFileForLocation(lat, lon)?.let { demFile ->
                            microTerrainRenderer.loadTerrainData(demFile, cameraPos, 500.0)
                        }
                    } catch (e: CancellationException) {
                        Log.d("MapViewModel", "Terrain loading cancelled for $cameraPos")
                    } catch (e: Exception) {
                        Log.e("MapViewModel", "Error loading terrain", e)
                    }
                }
            }
        } else {
            terrainLoadingJob?.cancel()
            lastProcessedLocation = null
        }
    }

    /**
     * Logs a new ginseng patch at the specified coordinates.
     */
    fun logPatch(lat: Double, lon: Double) {
        viewModelScope.launch {
            val newPatch = GinsengPatch(
                id = UUID.randomUUID(),
                latitude = lat,
                longitude = lon,
                timestamp = System.currentTimeMillis(),
                confidence = 1.0f, // Default for manual entry
                metadata = mapOf("source" to "manual_entry")
            )
            ginsengPatchRepository.savePatch(newPatch)
        }
    }

    /**
     * Renders the micro-terrain mesh if active.
     */
    fun renderMicroTerrain(projectionMatrix: FloatArray, viewMatrix: FloatArray) {
        if (isMicroViewActive) {
            microTerrainRenderer.updateRenderState(transitionAlpha, projectionMatrix)
            microTerrainRenderer.onDraw(projectionMatrix, viewMatrix)
        }
    }

    /**
     * Performs a predictive scan around a given location to generate habitat suitability data.
     */
    fun runPredictiveScan(centerLat: Double, centerLon: Double, radiusMeters: Double) {
        viewModelScope.launch {
            val points = mutableListOf<SuitabilityPoint>()
            val step = 0.001 // Roughly 100m steps

            for (lat in (centerLat - 0.01)..(centerLat + 0.01) step step) {
                for (lon in (centerLon - 0.01)..(centerLon + 0.01) step step) {
                    val score = getHabitatSuitabilityUseCase(
                        latitude = lat,
                        longitude = lon,
                        altitude = 500f, // Mocked
                        canopyCover = 0.7f, // Mocked
                        slopeAngle = 15f, // Mocked
                        aspect = 45f, // Mocked
                        moistureLevel = 0.6f // Mocked
                    )
                    if (score > 0.5f) {
                        points.add(SuitabilityPoint(lat, lon, score))
                    }
                }
            }
            _suitabilityHeatmap.value = points
        }
    }
}
