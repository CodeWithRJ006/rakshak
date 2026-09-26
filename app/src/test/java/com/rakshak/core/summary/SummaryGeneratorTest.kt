package com.rakshak.core.summary

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryGeneratorTest {

    @Test
    fun testDeterministicPath() = runTest {
        val generator = SummaryGenerator(null)
        val data = IncidentData(
            timestamp = 1729000000L,
            location = "37.7749,-122.4194",
            alertStatus = "SENT",
            movementResult = "POST_CRASH_STILLNESS"
        )
        
        val summary = generator.generateSummary(data)
        assertEquals("Incident reported at 37.7749,-122.4194. Status: SENT. Movement: POST_CRASH_STILLNESS.", summary)
    }

    @Test
    fun testLlmSuccess() = runTest {
        val fakeLlm = object : LlmInference {
            override suspend fun generateResponse(prompt: String): String {
                return "The rider was in an accident at 37.7749,-122.4194. Alert was SENT after POST_CRASH_STILLNESS."
            }
        }
        val generator = SummaryGenerator(fakeLlm)
        val data = IncidentData(
            timestamp = 1729000000L,
            location = "37.7749,-122.4194",
            alertStatus = "SENT",
            movementResult = "POST_CRASH_STILLNESS"
        )
        
        val summary = generator.generateSummary(data)
        assertEquals("The rider was in an accident at 37.7749,-122.4194. Alert was SENT after POST_CRASH_STILLNESS.", summary)
    }

    @Test
    fun testLlmTimeoutFallback() = runTest {
        val slowLlm = object : LlmInference {
            override suspend fun generateResponse(prompt: String): String {
                delay(4000) // Longer than the 3000ms timeout
                return "This should not be returned"
            }
        }
        val generator = SummaryGenerator(slowLlm)
        val data = IncidentData(
            timestamp = 1729000000L,
            location = "37.7749,-122.4194",
            alertStatus = "SENT",
            movementResult = "POST_CRASH_STILLNESS"
        )
        
        val summary = generator.generateSummary(data)
        // Should fallback to template
        assertEquals("Incident reported at 37.7749,-122.4194. Status: SENT. Movement: POST_CRASH_STILLNESS.", summary)
    }
}
