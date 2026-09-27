package com.rakshak.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CameraVerifyTest {

    @Test
    fun `verifyWithCamera result contains AI OBSERVATION label when image is null`() = runTest {
        // Without model loaded, we always get the deterministic fallback
        // which must contain the "AI OBSERVATION (visual):" label
        val result = AIIncidentAssessment.verifyWithCamera(
            peakGForce = 12.5f,
            jerkGs = 850f,
            gyroRadS = 12f,
            image = null
        )
        assertTrue(
            "Result must contain 'AI OBSERVATION (visual):' label",
            result.contains("AI OBSERVATION (visual):")
        )
        assertTrue(
            "Result must contain 'not a sensor measurement' disclaimer",
            result.contains("not a sensor measurement")
        )
        assertTrue(
            "Result must say image input not supported or frame unavailable",
            result.contains("not supported") || result.contains("unavailable")
        )
    }

    @Test
    fun `verifyWithCamera fallback labels severity correctly for high G`() = runTest {
        val result = AIIncidentAssessment.verifyWithCamera(
            peakGForce = 15f,
            jerkGs = 1200f,
            gyroRadS = 28f,
            image = null
        )
        assertTrue(
            "High G + high gyro must be labeled CRITICAL (Ejection Profile)",
            result.contains("CRITICAL (Ejection Profile)")
        )
        assertTrue(result.contains("AI OBSERVATION (visual):"))
    }

    @Test
    fun `verifyWithCamera fallback labels severity correctly for moderate G`() = runTest {
        val result = AIIncidentAssessment.verifyWithCamera(
            peakGForce = 5f,
            jerkGs = 200f,
            gyroRadS = 3f,
            image = null
        )
        assertTrue(
            "Moderate G should be labeled MODERATE",
            result.contains("MODERATE (Significant Force)")
        )
    }

    @Test
    fun `verifyWithCamera always includes CAMERA EVIDENCE STAGED header`() = runTest {
        val result = AIIncidentAssessment.verifyWithCamera(
            peakGForce = 12.5f,
            jerkGs = 850f,
            gyroRadS = 12f,
            image = null
        )
        assertTrue(
            "Must include [CAMERA EVIDENCE STAGED] header",
            result.contains("[CAMERA EVIDENCE STAGED]")
        )
    }
}
