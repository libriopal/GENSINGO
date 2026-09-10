package com.discomplemented.ginseng.ai.feature

import javax.inject.Inject

class FeatureExtractorImpl @Inject constructor() : FeatureExtractor {
    override fun extract(
        latitude: Double,
        longitude: Double,
        altitude: Float,
        canopyCover: Float,
        slopeAngle: Float,
        aspect: Float,
        moistureLevel: Float
    ): FeatureSet {
        return FeatureSet(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            canopyCover = canopyCover,
            slopeAngle = slopeAngle,
            aspect = aspect,
            moistureLevel = moistureLevel
        )
    }
}
