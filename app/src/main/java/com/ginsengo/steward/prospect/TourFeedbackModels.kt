package com.ginsengo.steward.prospect

import androidx.compose.ui.graphics.Color

/**
 * Domain ground-truth verdict classification from in-person land prospecting tours.
 * Feeds back into Bayesian priors and Monte Carlo simulation parameters.
 */
enum class GroundTruthVerdict(val label: String, val desc: String, val colorHex: Long) {
    CONFIRMED_FINDS(
        label = "CONFIRMED FINDS",
        desc = "Wild ginseng discovered and verified on ground",
        colorHex = 0xFF00E676
    ),
    ABSENCE_SURVEY(
        label = "ABSENCE SURVEY",
        desc = "Thorough search completed; no plants found",
        colorHex = 0xFFFFB300
    ),
    SIGNS_OF_HARVEST(
        label = "SIGNS OF PRIOR HARVEST",
        desc = "Old digger root holes / harvested crowns detected",
        colorHex = 0xFF42A5F5
    ),
    FALSE_POSITIVE_HABITAT(
        label = "FALSE POSITIVE GROUND",
        desc = "Habitat unsuitable IRL (heavy shale, dry winds, briars)",
        colorHex = 0xFFEF5350
    );

    val color: Color get() = Color(colorHex)
}

/**
 * Closed-loop feedback result reporting variance reduction, posterior updates,
 * and Monte Carlo credible interval shifts after ground-truth survey integration.
 */
data class ModelCalibrationFeedback(
    val countyName: String,
    val priorVarianceReductionPct: Int,
    val newConfidenceScore: Int,
    val confidenceDeltaPct: Int,
    val priorElevationMeters: Int,
    val priorSlopeDegrees: Int,
    val totalConfirmedFinds: Int,
    val llmSynthesis: String,
    val timestamp: Long = System.currentTimeMillis()
)
