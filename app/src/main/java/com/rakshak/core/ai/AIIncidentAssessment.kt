package com.rakshak.core.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

object AIIncidentAssessment {
    private const val TAG = "AIIncidentAssessment"
    private var llmInference: LlmInference? = null
    private var isModelLoaded = false

    // State for UI to show "READY"
    val isReady: Boolean get() = isModelLoaded

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isModelLoaded) return@withContext
        try {
            Log.i(TAG, "Loading Gemma LLM...")
            val modelPath = "/data/local/tmp/gemma2b.bin"
            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model not found at $modelPath. ADB push it first!")
                return@withContext
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(120)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            Log.i(TAG, "Gemma LLM Loaded Successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM", e)
            isModelLoaded = false
        }
    }

    suspend fun generateAssessment(
        peakGForce: Float,
        jerkGs: Float,
        gyroRadS: Float,
        locationStatus: String
    ): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded || llmInference == null) {
            return@withContext generateFallback(peakGForce, jerkGs, gyroRadS)
        }

        try {
            val userPrompt = """
                [INCIDENT TELEMETRY]
                Input: Accel ${peakGForce}G, Jerk $jerkGs G/s, Gyro $gyroRadS rad/s
                Location Context: $locationStatus
            """.trimIndent()

            val fullPrompt = GroundingContext.buildSystemPrompt() + "\n\n" + userPrompt

            Log.i(TAG, "Generating AI assessment...")
            
            // Timeout after 15 seconds so we don't hang forever
            val response = withTimeoutOrNull(15000L) {
                llmInference?.generateResponse(fullPrompt)
            }

            if (response.isNullOrBlank()) {
                Log.w(TAG, "LLM returned empty or timed out, using fallback")
                return@withContext generateFallback(peakGForce, jerkGs, gyroRadS)
            }

            return@withContext response.trim()
        } catch (e: Exception) {
            Log.e(TAG, "LLM inference failed", e)
            return@withContext generateFallback(peakGForce, jerkGs, gyroRadS)
        }
    }

    private fun generateFallback(peakGForce: Float, jerkGs: Float, gyroRadS: Float): String {
        return "[FALLBACK ASSESSMENT]\n" +
               "MEASURED EVIDENCE: Peak $peakGForce G, Jerk $jerkGs G/s, Rotational Velocity $gyroRadS rad/s.\n" +
               "INTERPRETATION: Deterministic thresholds exceeded. Likely moderate-to-severe impact event.\n" +
               "UNCERTAINTY: Telemetry suggests collision, but clinical severity cannot be AI-verified at this time."
    }
}
