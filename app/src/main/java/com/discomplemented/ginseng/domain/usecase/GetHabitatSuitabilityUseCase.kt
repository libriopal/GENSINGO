package com.discomplemented.ginseng.domain.usecase

import com.discomplemented.ginseng.ai.feature.FeatureExtractor
import com.discomplemented.ginseng.ai.inference.HabitatInferenceManager
import com.discomplemented.ginseng.ai.feature.FeatureSet
import javax.inject.Inject

class GetHabitatSuitabilityUseCase @Inject constructor(
    private val inferenceManager: HabitatInferenceManager,
    private val featureExtractor: FeatureExtractor
) {
    /**
     * Computs a habitat suitability score (0.0 to 1.0) based on environmental inputs.
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        altitude: Float,
        canopyCover: Float,
        slopeAngle: Float,
        aspect: Float,
        moistureLevel: Float
    ): Float {
        val features = featureExtractor.extract(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            canopyCover = canopyCover,
            slopeAngle = slopeAngle,
            aspect = aspect,
            moistureLevel = moistureLevel
        )

        return inferenceManager.predictSuitability(features)
    }
}
