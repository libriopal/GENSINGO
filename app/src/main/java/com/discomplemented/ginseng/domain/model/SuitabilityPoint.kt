package com.discomplemented.ginseng.domain.model

/**
 * Represents a point in space with a calculated suitability score.
 * Used for rendering predictive heatmaps.
 */
data class SuitabilityPoint(
    val latitude: Double,
    val longitude: Double,
    val score: Float
)
