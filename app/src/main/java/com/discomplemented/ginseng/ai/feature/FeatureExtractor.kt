package com.discomplemented.ginseng.ai.feature

/**
 * Responsible for transforming raw sensor and environmental data into a [FeatureSet]
 * compatible with the habitat suitability inference model.
 */
interface FeatureExtractor {
    /**
     * Constructs a [FeatureSet] from the given inputs.
     */
    fun extract(
        latitude: Double,
        longitude: Double,
        altitude: Float,
        canopyCover: Float,
        slopeAngle: Float,
        aspect: Float,
        moistureLevel: Float
    ): FeatureSet
}
