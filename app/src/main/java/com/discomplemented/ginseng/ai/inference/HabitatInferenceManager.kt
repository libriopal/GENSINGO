package com.discomplemented.ginseng.ai.inference

import android.content.Context
import com.discomplemented.ginseng.ai.feature.FeatureSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.FloatRange
import java.nio.FloatBuffer
import javax.inject.Inject
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Manages the lifecycle and execution of the local habitat suitability inference model.
 * Uses ONNX Runtime Mobile for efficient, offline inference.
 */
class HabitatInferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    /**
     * Initializes the ONNX runtime and loads the quantized model from assets.
     * Should be called during application startup or when the AI module is first needed.
     */
    suspend fun initialize(modelFileName: String) = withContext(Dispatchers.IO) {
        try {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(modelFileName).readBytes()
            session = env?.createSession(modelBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to initialize ONNX inference engine: ${e.message}")
        }
    }

    /**
     * Executes inference on the provided [FeatureSet].
     * Returns a suitability score between 0.0 and 1.0.
     */
    suspend fun predictSuitability(features: FeatureSet): Float = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw Exception("Inference engine not initialized. Call initialize() first.")
        val currentEnv = env ?: throw Exception("Environment not initialized.")

        // Prepare input tensor (assuming a model input shape of [1, 7])
        val inputData = floatArrayOf(
            features.latitude.toFloat(),
            features.longitude.toFloat(),
            features.altitude,
            features.canopyCover,
            features.slopeAngle,
            features.aspect,
            features.moistureLevel
        )
        val inputBuffer = FloatBuffer.wrap(inputData)
        val inputTensor = OnnxTensor.createTensor(currentEnv, inputBuffer, longArrayOf(1, 7))

        try {
            val output = currentSession.run(mapOf("input" to inputTensor))
            val outputTensor = output[0] as OnnxTensor
            val result = outputTensor.floatBuffer.get(0)

            // Clamp result to [0.0, 1.0]
            result.coerceIn(0.0f, 1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
            0.0f // Default to zero suitability on error
        } finally {
            inputTensor.close()
        }
    }

    /**
     * Releases all ONNX resources. Should be called when the module is no longer needed.
     */
    fun release() {
        session?.close()
        env?.close()
        session = null
        env = null
    }
}
