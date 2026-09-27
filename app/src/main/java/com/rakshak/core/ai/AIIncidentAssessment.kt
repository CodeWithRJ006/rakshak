package com.rakshak.core.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

import android.graphics.Bitmap

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

    suspend fun explainAlert(peakGForce: Float, jerkGs: Float, gyroRadS: Float): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded || llmInference == null) {
            return@withContext "[FALLBACK EXPLANATION]\nBased on deterministic rules, ${peakGForce}G and ${jerkGs}G/s exceeded the critical threshold for severe skeletal trauma, and the ${gyroRadS}rad/s rotation confirmed tumbling. Dispatch triggered to prevent critical delay."
        }

        try {
            val userPrompt = """
                [TASK: EXPLAIN ALERT DECISION]
                Telemetry: $peakGForce G, $jerkGs G/s, $gyroRadS rad/s.
                Explain exactly why this sequence triggered a P0 medical dispatch based on NHTSA/WHO data.
            """.trimIndent()

            val fullPrompt = GroundingContext.buildSystemPrompt() + "\n\n" + userPrompt

            Log.i(TAG, "Generating AI explanation...")
            val response = withTimeoutOrNull(15000L) {
                llmInference?.generateResponse(fullPrompt)
            }

            if (response.isNullOrBlank()) {
                return@withContext "[FALLBACK EXPLANATION]\nBased on deterministic rules, ${peakGForce}G and ${jerkGs}G/s exceeded the critical threshold. Dispatched to prevent delay."
            }

            return@withContext response.trim()
        } catch (e: Exception) {
            Log.e(TAG, "LLM inference failed", e)
            return@withContext "[FALLBACK EXPLANATION]\nSystem encountered high forces. Deterministic thresholds met. Alert sent."
        }
    }

    /**
     * Tier 2: Real camera verification path.
     *
     * MediaPipe LlmInference (tasks-genai) is TEXT-ONLY — it does NOT accept image
     * tensors as input.  We capture a real CameraX frame (see CameraCaptureHelper)
     * and describe its metadata (resolution, timestamp) alongside the telemetry in
     * the text prompt.  The output is clearly labelled so no one confuses it with
     * sensor-level measurements.
     *
     * If image input ever becomes supported by the model/config, switch to the
     * multimodal path here.
     */
    suspend fun verifyWithCamera(peakGForce: Float, jerkGs: Float, gyroRadS: Float, image: Bitmap?): String = withContext(Dispatchers.IO) {
        val frameDescription = if (image != null) {
            "CameraX frame captured: ${image.width}x${image.height}px at ${System.currentTimeMillis()}ms"
        } else {
            "Camera frame unavailable — capture failed or permission denied"
        }

        // Attempt real Gemma inference with text context
        if (isModelLoaded && llmInference != null && image != null) {
            try {
                val prompt = """
                    [AI OBSERVATION (visual context — TEXT-ONLY ANALYSIS)]
                    NOTE: Image input is not supported by the current model configuration.
                    This analysis is based on telemetry and frame metadata only, NOT pixel-level visual inspection.

                    Frame metadata: $frameDescription
                    Telemetry: Peak ${peakGForce}G, Jerk $jerkGs G/s, Gyro $gyroRadS rad/s.

                    Based on these readings, provide a 2-sentence assessment of the likely scene.
                    Always prefix your answer with "AI OBSERVATION (visual):".
                """.trimIndent()

                val fullPrompt = GroundingContext.buildSystemPrompt() + "\n\n" + prompt

                val response = withTimeoutOrNull(5000L) {
                    llmInference?.generateResponse(fullPrompt)
                }

                if (!response.isNullOrBlank()) {
                    // Ensure the label is always present even if model forgets
                    val labeled = if (response.contains("AI OBSERVATION")) response.trim()
                                  else "AI OBSERVATION (visual): ${response.trim()}"
                    return@withContext "[CAMERA EVIDENCE STAGED]\n$frameDescription\n\n$labeled\n\n⚠ This is an AI interpretation, not a sensor measurement."
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM camera verification failed", e)
            }
        }

        // Deterministic fallback — clearly labeled
        val severityLabel = when {
            peakGForce > 10f && gyroRadS > 15f -> "CRITICAL (Ejection Profile)"
            peakGForce > 8f -> "SEVERE (High-G Impact)"
            peakGForce > 3f -> "MODERATE (Significant Force)"
            else -> "LOW (Minor Event)"
        }

        return@withContext "[CAMERA EVIDENCE STAGED]\n" +
               "$frameDescription\n\n" +
               "AI OBSERVATION (visual): Image input not supported by current model. " +
               "TEXT-ONLY ANALYSIS based on telemetry: $severityLabel. " +
               "Peak ${peakGForce}G with ${gyroRadS}rad/s rotation.\n\n" +
               "⚠ This is an AI interpretation, not a sensor measurement."
    }

    suspend fun answerVoiceQuery(query: String, peakGForce: Float, jerkGs: Float, gyroRadS: Float): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded || llmInference == null) {
            return@withContext "I am operating in fallback mode. The rider experienced ${peakGForce}G of force. Please dispatch medical assistance immediately."
        }
        try {
            val userPrompt = """
                [VOICE COPILOT INQUIRY]
                Telemetry context: $peakGForce G, $jerkGs G/s, $gyroRadS rad/s.
                User asked: "$query"
                Answer in 1 or 2 concise, spoken-word sentences using the provided telemetry.
            """.trimIndent()
            
            val fullPrompt = GroundingContext.buildSystemPrompt() + "\n\n" + userPrompt
            val response = withTimeoutOrNull(8000L) {
                llmInference?.generateResponse(fullPrompt)
            }
            if (response.isNullOrBlank()) {
                return@withContext "I am analyzing the telemetry, but cannot verify details at this time. Telemetry reads $peakGForce Gs."
            }
            return@withContext response.trim().replace(Regex("\\[.*?\\]"), "") // Clean up brackets for speech
        } catch (e: Exception) {
            return@withContext "I encountered an error. Proceed with standard emergency protocol."
        }
    }

    private fun generateFallback(peakGForce: Float, jerkGs: Float, gyroRadS: Float): String {
        val isEjection = jerkGs > 1000f && gyroRadS > 20f
        
        if (isEjection) {
            return "[FALLBACK ASSESSMENT]\n" +
                   "MEASURED EVIDENCE: Peak $peakGForce G, Jerk $jerkGs G/s, Rotational Velocity $gyroRadS rad/s.\n" +
                   "INTERPRETATION: Critical Ejection Profile Matched. Extreme Jerk implies rider separation.\n" +
                   "UNCERTAINTY: Telemetry strongly suggests high-side ejection. Awaiting medical verification."
        } else {
            return "[FALLBACK ASSESSMENT]\n" +
                   "MEASURED EVIDENCE: Peak $peakGForce G, Jerk $jerkGs G/s, Rotational Velocity $gyroRadS rad/s.\n" +
                   "INTERPRETATION: Severe Frontal Collision Matched. High G-Force implies sudden stop.\n" +
                   "UNCERTAINTY: Collision likely, but severity requires visual or clinical corroboration."
        }
    }
}
