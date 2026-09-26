package com.rakshak.core.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.rakshak.core.ai.AIIncidentAssessment

object SummaryGenerator {

    suspend fun generateSummary(
        peakGs: Float,
        jerkGs: Float,
        gyroRads: Float,
        location: String,
        alertStatus: String,
        movementResult: String
    ): String = withContext(Dispatchers.IO) {
        
        // 1. Prioritize SLM / Gemma 2B execution with a strict 3000ms timeout
        val llmResponse = withTimeoutOrNull(3000L) {
            try {
                // If the model is loaded, we try to get a smart summary
                if (AIIncidentAssessment.isReady) {
                    AIIncidentAssessment.generateAssessment(peakGs, jerkGs, gyroRads, location)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        // 2. Return LLM summary if successful
        if (!llmResponse.isNullOrBlank() && !llmResponse.contains("[FALLBACK")) {
            return@withContext "AI SUMMARY: $llmResponse"
        }

        // 3. Deterministic Fallback if timeout or exception occurs
        return@withContext "Incident reported at $location. Status: $alertStatus. Movement: $movementResult. " +
                           "(Telemetry: ${peakGs}G, ${jerkGs}G/s, ${gyroRads}rad/s)"
    }
}
