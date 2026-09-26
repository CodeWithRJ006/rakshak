package com.rakshak.core.summary

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryGeneratorTest {

    @Test
    fun testDeterministicPath() = runTest {
        val summary = SummaryGenerator.generateSummary(
            peakGs = 12.5f,
            jerkGs = 850f,
            gyroRads = 12.0f,
            location = "37.7749,-122.4194",
            alertStatus = "SENT",
            movementResult = "POST_CRASH_STILLNESS"
        )
        assertTrue(summary.contains("Incident reported at 37.7749,-122.4194"))
        assertTrue(summary.contains("12.5G"))
    }
}
