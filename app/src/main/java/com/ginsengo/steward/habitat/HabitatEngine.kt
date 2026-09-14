package com.ginsengo.steward.habitat

import android.content.Context
import android.util.Log

/**
 * Loads and runs the bundled habitat graph (PRD §8.1).
 *
 * A pure-Kotlin Gemm+Sigmoid over the weights parsed out of the bundled graph file.
 *
 * onnxruntime used to run alongside this as a claimed "witness", with the agreement delta
 * shown in Settings. It has been removed, for two reasons that arrived together.
 *
 * The first is that it was never a witness. The graph weights slope and aspect at exactly
 * 0.0, and its two dominant inputs are derivations of the user's own checklist answers, so
 * the two engines were not two opinions about habitat - they were two arithmetic paths over
 * the same restatement of what the digger had already typed in. Showing their agreement next
 * to the terrain forecast invited the reader to see corroboration where there was one source.
 *
 * The second is mechanical: libonnxruntime.so and libonnxruntime4j_jni.so are 4 KB-aligned,
 * and Google Play requires 16 KB-aligned ELF load segments for 64-bit native libraries. They
 * were two of the three libraries in this app that failed that check.
 *
 * Weight PARSING stays - OnnxGraphWeights is a small pure-Kotlin protobuf reader, needs no
 * native code, and reads the same bytes it always did. Only the native runtime is gone.
 */
class HabitatEngine private constructor(
    private val kotlinModel: LinearHabitatModel,
    val loadNote: String,
) {

    fun weightOf(f: HabitatFeature) = kotlinModel.weightOf(f)

    /** Features the shipped graph cannot respond to, whatever value they are given. */
    fun inertFeatures(): List<HabitatFeature> =
        HabitatFeature.entries.filter { kotlinModel.weightOf(it) == 0.0 }

    data class Run(
        val analysis: HabitatAnalysis,
        // onnxScore and agreementDelta used to live here. See the class comment: they were
        // not a second opinion, and presenting them as one was the defect.
    )

    fun analyse(inputs: List<FeatureInput>): Run = Run(analysis = kotlinModel.analyse(inputs))

    fun close() = Unit

    companion object {
        private const val TAG = "HabitatEngine"
        const val MODEL_ASSET = "models/habitat_model.onnx"

        /**
         * Returns null only if the graph cannot be parsed at all - in which case the app
         * must not offer habitat analysis rather than fall back to a made-up number.
         */
        fun load(context: Context): HabitatEngine? {
            val bytes = runCatching {
                context.assets.open(MODEL_ASSET).use { it.readBytes() }
            }.getOrElse {
                Log.e(TAG, "habitat model asset missing", it)
                return null
            }

            val initializers = runCatching {
                OnnxGraphWeights.readInitializers(bytes.inputStream())
            }.getOrElse { emptyMap() }

            val kotlinModel = LinearHabitatModel.fromInitializers(initializers, "kotlin-linear")
            if (kotlinModel == null) {
                Log.e(TAG, "graph has no 7-wide float weight vector; refusing to score")
                return null
            }

            return HabitatEngine(kotlinModel, "Kotlin linear path over the bundled graph weights.")
        }
    }
}
