package com.rakshak.core.safety

import android.util.Log

/**
 * SafetyValidator sits between AI-generated recommendations and any action
 * with real-world effect. It enforces the invariant:
 *
 *   "AI interprets, the deterministic system decides."
 *
 * The AI can add interpretation (severity labels, explanations) but can NEVER
 * override a CONFIRMED crash to "no action needed" or downgrade a dispatched
 * alert. Any override is logged for audit.
 */
object SafetyValidator {

    private const val TAG = "SafetyValidator"

    data class ValidationResult(
        val finalSeverity: Severity,
        val finalAction: Action,
        val wasOverridden: Boolean,
        val overrideReason: String?
    )

    enum class Severity { CRITICAL, HIGH, MEDIUM, LOW, NONE }

    enum class Action { DISPATCH_EMERGENCY, MONITOR, NO_ACTION }

    /**
     * Validate an AI suggestion against hard deterministic rules.
     *
     * @param aiSuggestedSeverity  What the AI thinks the severity is.
     * @param aiSuggestedAction    What the AI recommends doing.
     * @param isConfirmedCrash     True if the deterministic state machine has reached CONFIRMED.
     * @param isAlertDispatched    True if an SMS/alert has already been dispatched.
     * @param peakGForce           Measured peak G-force from sensors.
     * @param jerkGs               Measured jerk in G/s.
     * @param gyroRadS             Measured rotational velocity in rad/s.
     */
    fun validate(
        aiSuggestedSeverity: Severity,
        aiSuggestedAction: Action,
        isConfirmedCrash: Boolean,
        isAlertDispatched: Boolean,
        peakGForce: Float,
        jerkGs: Float,
        gyroRadS: Float
    ): ValidationResult {

        // ── HARD RULE 1: AI cannot downgrade a CONFIRMED crash to NO_ACTION ──
        if (isConfirmedCrash && aiSuggestedAction == Action.NO_ACTION) {
            val reason = "OVERRIDE: AI suggested NO_ACTION but crash is CONFIRMED " +
                    "(peak=${peakGForce}G, jerk=${jerkGs}G/s, gyro=${gyroRadS}rad/s). " +
                    "Deterministic decision preserved: DISPATCH_EMERGENCY."
            Log.w(TAG, reason)
            return ValidationResult(
                finalSeverity = Severity.CRITICAL,
                finalAction = Action.DISPATCH_EMERGENCY,
                wasOverridden = true,
                overrideReason = reason
            )
        }

        // ── HARD RULE 2: AI cannot downgrade severity below HIGH when crash is confirmed ──
        if (isConfirmedCrash && (aiSuggestedSeverity == Severity.LOW || aiSuggestedSeverity == Severity.NONE || aiSuggestedSeverity == Severity.MEDIUM)) {
            val reason = "OVERRIDE: AI suggested severity=${aiSuggestedSeverity.name} " +
                    "but crash is CONFIRMED. Severity forced to CRITICAL."
            Log.w(TAG, reason)
            return ValidationResult(
                finalSeverity = Severity.CRITICAL,
                finalAction = if (aiSuggestedAction == Action.NO_ACTION) Action.DISPATCH_EMERGENCY else aiSuggestedAction,
                wasOverridden = true,
                overrideReason = reason
            )
        }

        // ── HARD RULE 3: AI cannot retract an already-dispatched alert ──
        if (isAlertDispatched && aiSuggestedAction == Action.NO_ACTION) {
            val reason = "OVERRIDE: AI suggested NO_ACTION but alert already dispatched. " +
                    "Cannot retract. Action forced to MONITOR."
            Log.w(TAG, reason)
            return ValidationResult(
                finalSeverity = aiSuggestedSeverity,
                finalAction = Action.MONITOR,
                wasOverridden = true,
                overrideReason = reason
            )
        }

        // ── HARD RULE 4: Telemetry above lethal thresholds overrides AI downgrade ──
        if (peakGForce > 10f && jerkGs > 500f && (aiSuggestedSeverity == Severity.LOW || aiSuggestedSeverity == Severity.NONE)) {
            val reason = "OVERRIDE: AI suggested ${aiSuggestedSeverity.name} but telemetry " +
                    "(${peakGForce}G, ${jerkGs}G/s) exceeds lethal thresholds. Forced CRITICAL."
            Log.w(TAG, reason)
            return ValidationResult(
                finalSeverity = Severity.CRITICAL,
                finalAction = Action.DISPATCH_EMERGENCY,
                wasOverridden = true,
                overrideReason = reason
            )
        }

        // ── AI suggestion is consistent with deterministic rules → pass through ──
        return ValidationResult(
            finalSeverity = aiSuggestedSeverity,
            finalAction = aiSuggestedAction,
            wasOverridden = false,
            overrideReason = null
        )
    }
}
