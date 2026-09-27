package com.rakshak.core.safety

import org.junit.Assert.*
import org.junit.Test

class SafetyValidatorTest {

    // ── AI suggestion CONSISTENT with telemetry → passes through unchanged ──

    @Test
    fun `AI CRITICAL + DISPATCH on confirmed crash passes through unchanged`() {
        val result = SafetyValidator.validate(
            aiSuggestedSeverity = SafetyValidator.Severity.CRITICAL,
            aiSuggestedAction = SafetyValidator.Action.DISPATCH_EMERGENCY,
            isConfirmedCrash = true,
            isAlertDispatched = false,
            peakGForce = 12.5f,
            jerkGs = 850f,
            gyroRadS = 12f
        )
        assertFalse("Should NOT be overridden", result.wasOverridden)
        assertEquals(SafetyValidator.Severity.CRITICAL, result.finalSeverity)
        assertEquals(SafetyValidator.Action.DISPATCH_EMERGENCY, result.finalAction)
        assertNull(result.overrideReason)
    }

    @Test
    fun `AI LOW + NO_ACTION on no crash passes through unchanged`() {
        val result = SafetyValidator.validate(
            aiSuggestedSeverity = SafetyValidator.Severity.LOW,
            aiSuggestedAction = SafetyValidator.Action.NO_ACTION,
            isConfirmedCrash = false,
            isAlertDispatched = false,
            peakGForce = 1.2f,
            jerkGs = 5f,
            gyroRadS = 0.5f
        )
        assertFalse("Should NOT be overridden", result.wasOverridden)
        assertEquals(SafetyValidator.Severity.LOW, result.finalSeverity)
        assertEquals(SafetyValidator.Action.NO_ACTION, result.finalAction)
    }

    // ── AI suggestion CONTRADICTS hard rules → overridden, logged ──

    @Test
    fun `AI NO_ACTION on CONFIRMED crash is overridden to DISPATCH`() {
        val result = SafetyValidator.validate(
            aiSuggestedSeverity = SafetyValidator.Severity.NONE,
            aiSuggestedAction = SafetyValidator.Action.NO_ACTION,
            isConfirmedCrash = true,
            isAlertDispatched = false,
            peakGForce = 12.5f,
            jerkGs = 850f,
            gyroRadS = 12f
        )
        assertTrue("Must be overridden", result.wasOverridden)
        assertEquals(SafetyValidator.Severity.CRITICAL, result.finalSeverity)
        assertEquals(SafetyValidator.Action.DISPATCH_EMERGENCY, result.finalAction)
        assertNotNull(result.overrideReason)
        assertTrue(result.overrideReason!!.contains("OVERRIDE"))
    }

    @Test
    fun `AI LOW severity on CONFIRMED crash is forced to CRITICAL`() {
        val result = SafetyValidator.validate(
            aiSuggestedSeverity = SafetyValidator.Severity.LOW,
            aiSuggestedAction = SafetyValidator.Action.DISPATCH_EMERGENCY,
            isConfirmedCrash = true,
            isAlertDispatched = false,
            peakGForce = 12.5f,
            jerkGs = 850f,
            gyroRadS = 12f
        )
        assertTrue("Must be overridden", result.wasOverridden)
        assertEquals(SafetyValidator.Severity.CRITICAL, result.finalSeverity)
        assertTrue(result.overrideReason!!.contains("OVERRIDE"))
    }

    @Test
    fun `AI cannot retract an already-dispatched alert`() {
        val result = SafetyValidator.validate(
            aiSuggestedSeverity = SafetyValidator.Severity.LOW,
            aiSuggestedAction = SafetyValidator.Action.NO_ACTION,
            isConfirmedCrash = false,
            isAlertDispatched = true,
            peakGForce = 5f,
            jerkGs = 200f,
            gyroRadS = 3f
        )
        assertTrue("Must be overridden", result.wasOverridden)
        assertEquals(SafetyValidator.Action.MONITOR, result.finalAction)
        assertTrue(result.overrideReason!!.contains("already dispatched"))
    }

    @Test
    fun `AI NONE severity on lethal telemetry is forced to CRITICAL`() {
        val result = SafetyValidator.validate(
            aiSuggestedSeverity = SafetyValidator.Severity.NONE,
            aiSuggestedAction = SafetyValidator.Action.NO_ACTION,
            isConfirmedCrash = false,
            isAlertDispatched = false,
            peakGForce = 15f,
            jerkGs = 1200f,
            gyroRadS = 25f
        )
        assertTrue("Must be overridden", result.wasOverridden)
        assertEquals(SafetyValidator.Severity.CRITICAL, result.finalSeverity)
        assertEquals(SafetyValidator.Action.DISPATCH_EMERGENCY, result.finalAction)
    }
}
