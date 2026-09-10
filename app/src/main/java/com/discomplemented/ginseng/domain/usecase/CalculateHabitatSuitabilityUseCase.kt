package com.discomplemented.ginseng.domain.usecase

import com.discomplemented.ginseng.domain.model.HabitatSuitability
import javax.inject.Inject

/**
 * Use case for calculating habitat suitability.
 * Stub implementation returns constant score; replace with ONNX inference.
 */
class CalculateHabitatSuitabilityUseCase @Inject constructor() {
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        elevation: Float,
        slope: Float
    ): HabitatSuitability {
        // TODO: Integrate ONNX inference pipeline
        // Stub: return constant score
        return HabitatSuitability(
            score = 0.5f,
            confidence = 0.7f,
            habitat = "moderate"
        )
    }
}
