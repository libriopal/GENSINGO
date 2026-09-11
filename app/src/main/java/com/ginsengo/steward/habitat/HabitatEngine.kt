package com.ginsengo.steward.habitat

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer

/**
 * Loads and runs the bundled habitat graph (PRD §8.1).
 *
 * Two engines run on every analysis:
 *   1. onnxruntime-android over assets/models/habitat_model.onnx
 *   2. a pure-Kotlin Gemm+Sigmoid over the weights parsed out of that same file
 *
 * The second is PRD §8.1's stated fallback, but running it alongside rather than only on
 * failure buys something the PRD did not ask for and the EINCOL §3 classifier does: a
 * WITNESS. A score computed one way and checked against a second independent computation
 * is pinned; a score only ever computed once is a number nobody has checked. The agreement
 * delta is surfaced in Settings, and a disagreement is visible rather than silent.
 *
 * The Kotlin path is also what the unit tests exercise, since it needs no device.
 */
class HabitatEngine private constructor(
    private val kotlinModel: LinearHabitatModel,
    private val ortSession: OrtSession?,
    private val ortEnv: OrtEnvironment?,
    private val ortInputName: String?,
    val loadNote: String,
) {

    val onnxRuntimeAvailable: Boolean get() = ortSession != null

    fun weightOf(f: HabitatFeature) = kotlinModel.weightOf(f)

    /** Features the shipped graph cannot respond to, whatever value they are given. */
    fun inertFeatures(): List<HabitatFeature> =
        HabitatFeature.entries.filter { kotlinModel.weightOf(it) == 0.0 }

    data class Run(
        val analysis: HabitatAnalysis,
        /** Score from onnxruntime, when it loaded. */
        val onnxScore: Double?,
        /** |onnx - kotlin|, when both ran. */
        val agreementDelta: Double?,
    )

    fun analyse(inputs: List<FeatureInput>): Run {
        val analysis = kotlinModel.analyse(inputs)
        val onnx = runOnnx(inputs)
        return Run(
            analysis = analysis,
            onnxScore = onnx,
            agreementDelta = onnx?.let { kotlin.math.abs(it - analysis.score) },
        )
    }

    private fun runOnnx(inputs: List<FeatureInput>): Double? {
        val session = ortSession ?: return null
        val env = ortEnv ?: return null
        val name = ortInputName ?: return null
        return runCatching {
            val byFeature = inputs.associateBy { it.feature }
            val vec = FloatArray(HabitatFeature.entries.size) { i ->
                (byFeature[HabitatFeature.entries[i]]?.value ?: 0.0).toFloat()
            }
            OnnxTensor.createTensor(
                env, FloatBuffer.wrap(vec), longArrayOf(1, vec.size.toLong())
            ).use { tensor ->
                session.run(mapOf(name to tensor)).use { results ->
                    when (val v = results[0].value) {
                        is Array<*> -> (v.firstOrNull() as? FloatArray)?.firstOrNull()?.toDouble()
                        is FloatArray -> v.firstOrNull()?.toDouble()
                        else -> null
                    }
                }
            }
        }.getOrElse {
            Log.w(TAG, "onnxruntime inference failed; using Kotlin path", it)
            null
        }
    }

    fun close() {
        runCatching { ortSession?.close() }
    }

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

            var env: OrtEnvironment? = null
            var session: OrtSession? = null
            var inputName: String? = null
            var note = "Kotlin linear path only."
            runCatching {
                env = OrtEnvironment.getEnvironment()
                session = env!!.createSession(bytes, OrtSession.SessionOptions())
                inputName = session!!.inputNames.firstOrNull()
                note = "onnxruntime + Kotlin cross-check."
            }.onFailure {
                Log.w(TAG, "onnxruntime unavailable; Kotlin fallback active (PRD §8.1)", it)
                session = null
            }

            return HabitatEngine(kotlinModel, session, env, inputName, note)
        }
    }
}
