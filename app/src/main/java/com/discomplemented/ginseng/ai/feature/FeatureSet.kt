package com.discomplemented.ginseng.ai.feature

/**
 * Represents the input features required for the habitat suitability inference model.
 * These features are typically quantized and fed into the ONNX runtime.
 */
data class FeatureSet(
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,
    val canopyCover: Float, // 0.0 to 1.0
    val slopeAngle: Float,  // Degrees
    val aspect: Float,      // 0-360 degrees
    val moistureLevel: Float // 0.0 to 1.0
)
