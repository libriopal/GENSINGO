package com.discomplemented.ginseng.domain.model

/**
 * Result of habitat suitability calculation.
 */
data class HabitatSuitability(
    val score: Float, // 0.0 to 1.0
    val confidence: Float, // 0.0 to 1.0
    val habitat: String // "low", "moderate", "high"
)
