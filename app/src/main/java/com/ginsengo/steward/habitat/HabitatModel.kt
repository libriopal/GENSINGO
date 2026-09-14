package com.ginsengo.steward.habitat

import kotlin.math.exp

/**
 * The seven inputs the bundled graph declares, in the graph's own order.
 * (Source: gap-closure-bundle docs/MODEL_CARD.md, confirmed against the initialiser dims.)
 */
enum class HabitatFeature(val displayName: String) {
    LATITUDE("Latitude"),
    LONGITUDE("Longitude"),
    ALTITUDE("Elevation"),
    CANOPY_COVER("Canopy cover"),
    SLOPE_ANGLE("Slope angle"),
    ASPECT("Slope aspect"),
    MOISTURE("Moisture"),
}

/** Where a feature's value actually came from. This drives the honesty UI. */
enum class InputOrigin(val label: String) {
    /** Measured by the device or read from a bundled survey dataset. */
    MEASURED("measured"),

    /** Computed from what the digger just told the checklist. */
    FROM_YOUR_CHECKLIST("from your answers"),
}

data class FeatureInput(
    val feature: HabitatFeature,
    val value: Double,
    val origin: InputOrigin,
)

/** One feature's signed push on the logit, for the breakdown UI. */
data class Contribution(
    val feature: HabitatFeature,
    val value: Double,
    val weight: Double,
    val origin: InputOrigin,
) {
    val logitPush: Double get() = value * weight
    val isInert: Boolean get() = weight == 0.0
}

data class HabitatAnalysis(
    val score: Double,
    val contributions: List<Contribution>,
    val bias: Double,
    val engine: String,
) {
    /** Features the shipped graph weights at exactly zero - they cannot move the score. */
    val inertFeatures: List<Contribution> get() = contributions.filter { it.isInert }

    /** Of the movement that did happen, the share driven by the digger's own answers. */
    val shareFromYourAnswers: Double
        get() {
            val active = contributions.filterNot { it.isInert }
            val total = active.sumOf { kotlin.math.abs(it.logitPush) }
            if (total <= 0.0) return 0.0
            val mine = active.filter { it.origin == InputOrigin.FROM_YOUR_CHECKLIST }
                .sumOf { kotlin.math.abs(it.logitPush) }
            return mine / total
        }
}

/**
 * Linear baseline habitat model: score = sigmoid(W . x + b).
 *
 * Weights are supplied by the caller after being read out of the shipped .onnx
 * (see [OnnxGraphWeights]), so this pure-Kotlin path and the onnxruntime path are running
 * the same numbers by construction rather than by care.
 *
 * This is PRD §8.1's stated fallback, and it is also the oracle the unit tests use, since
 * it runs on a plain JVM with no device and no native library.
 */
class LinearHabitatModel(
    private val weights: DoubleArray,
    private val bias: Double,
    private val engineName: String,
) {
    init {
        require(weights.size == HabitatFeature.entries.size) {
            "expected ${HabitatFeature.entries.size} weights, got ${weights.size}"
        }
    }

    fun weightOf(f: HabitatFeature): Double = weights[f.ordinal]

    fun analyse(inputs: List<FeatureInput>): HabitatAnalysis {
        val byFeature = inputs.associateBy { it.feature }
        val contributions = HabitatFeature.entries.map { f ->
            val in0 = byFeature[f]
            Contribution(
                feature = f,
                value = in0?.value ?: 0.0,
                weight = weights[f.ordinal],
                origin = in0?.origin ?: InputOrigin.MEASURED,
            )
        }
        val logit = contributions.sumOf { it.logitPush } + bias
        return HabitatAnalysis(
            score = 1.0 / (1.0 + exp(-logit)),
            contributions = contributions,
            bias = bias,
            engine = engineName,
        )
    }

    companion object {
        /**
         * Builds the model from the initialisers of the bundled graph.
         * Returns null if the graph does not carry a 7-wide float weight vector and a bias,
         * rather than guessing - a wrong-shaped model must fail loudly, not silently score.
         */
        fun fromInitializers(
            init: Map<String, OnnxGraphWeights.Tensor>,
            engineName: String,
        ): LinearHabitatModel? {
            val n = HabitatFeature.entries.size
            val w = init.values.firstOrNull { it.values.size == n }
            val b = init.values.firstOrNull { it.values.size == 1 }
            if (w == null || b == null) return null
            return LinearHabitatModel(
                weights = DoubleArray(n) { w.values[it].toDouble() },
                bias = b.values[0].toDouble(),
                engineName = engineName,
            )
        }
    }
}
