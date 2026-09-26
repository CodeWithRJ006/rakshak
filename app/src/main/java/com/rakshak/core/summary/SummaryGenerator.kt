package com.rakshak.core.summary

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class IncidentData(
    val timestamp: Long,
    val location: String,
    val alertStatus: String,
    val movementResult: String
)

interface LlmInference {
    suspend fun generateResponse(prompt: String): String
}

class SummaryGenerator(private val llmInference: LlmInference? = null) {
    
    suspend fun generateSummary(data: IncidentData): String {
        return if (llmInference != null) {
            try {
                // Hard 3-second timeout
                withTimeout(3000) {
                    val prompt = "Generate a short summary for an incident at ${data.location}. Alert status: ${data.alertStatus}. Movement: ${data.movementResult}."
                    llmInference.generateResponse(prompt)
                }
            } catch (e: Exception) {
                // Trigger fallback on timeout or any error
                generateTemplateSummary(data)
            }
        } else {
            generateTemplateSummary(data)
        }
    }

    private fun generateTemplateSummary(data: IncidentData): String {
        return "Incident reported at ${data.location}. Status: ${data.alertStatus}. Movement: ${data.movementResult}."
    }
}
