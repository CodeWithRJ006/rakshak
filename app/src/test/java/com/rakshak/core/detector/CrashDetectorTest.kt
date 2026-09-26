package com.rakshak.core.detector

import com.rakshak.core.sensor.SensorData
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashDetectorTest {

    private val defaultConfig = CrashDetectionConfig()
    
    private fun vector(x: Float): FloatArray = floatArrayOf(x, 0f, 0f)

    @Test
    fun testEmptyOrInsufficientBufferIsHandledSafely() {
        val detector = CrashDetector(defaultConfig)
        
        detector.processAccelerometerBuffer(emptyList())
        assertEquals(DetectorState.MONITORING, detector.currentState)

        detector.processAccelerometerBuffer(listOf(SensorData(1000L, vector(9.8f))))
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testHistoricalSpikeIsNotReprocessed() {
        // Use an impossible jerk threshold so we stay in IMPACT_CANDIDATE then time out
        val config = CrashDetectionConfig(jerkThreshold = 100000f)
        val detector = CrashDetector(config)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer1 = listOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f)) // Spike, but not enough jerk to trigger CONFIRMING
        )
        detector.processAccelerometerBuffer(buffer1)
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)

        // Timeout candidate
        val timeoutTime = t1 + config.candidateTimeoutNanos + 1L
        val buffer2 = buffer1 + SensorData(timeoutTime, vector(9.8f))
        
        detector.processAccelerometerBuffer(buffer2)
        assertEquals(DetectorState.MONITORING, detector.currentState)

        // Process a new sample, old spike is still in buffer
        val newTime = timeoutTime + 20_000_000L
        val buffer3 = buffer2 + SensorData(newTime, vector(9.8f))
        
        detector.processAccelerometerBuffer(buffer3)
        // Should NOT jump to IMPACT_CANDIDATE from the old 40.0f spike
        assertEquals(DetectorState.MONITORING, detector.currentState)
        
        // A truly NEW spike should still trigger it
        val newSpikeTime = newTime + 20_000_000L
        val buffer4 = buffer3 + SensorData(newSpikeTime, vector(40.0f))
        detector.processAccelerometerBuffer(buffer4)
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)
    }

    @Test
    fun testMonitoringToImpactCandidateToConfirmingWithAccelAndJerk() {
        val detector = CrashDetector(defaultConfig)
        
        val buffer1 = listOf(
            SensorData(0L, vector(9.8f)),
            SensorData(20_000_000L, vector(9.8f))
        )
        detector.processAccelerometerBuffer(buffer1)
        assertEquals(DetectorState.MONITORING, detector.currentState)

        val buffer2 = buffer1 + SensorData(40_000_000L, vector(40.0f))
        
        detector.processAccelerometerBuffer(buffer2)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)
    }

    @Test
    fun testCandidateFailsThresholdGoesBackToMonitoring() {
        val config = CrashDetectionConfig(accelMagnitudeThreshold = 30f, jerkThreshold = 10000f)
        val detector = CrashDetector(config)
        
        val buffer1 = listOf(
            SensorData(0L, vector(9.8f)),
            SensorData(20_000_000L, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer1)
        assertEquals(DetectorState.IMPACT_CANDIDATE, detector.currentState)

        val timeoutTime = 20_000_000L + config.candidateTimeoutNanos + 1L
        val buffer2 = buffer1 + SensorData(timeoutTime, vector(40.0f))
        
        detector.processAccelerometerBuffer(buffer2)
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testConfirmingPersistenceSucceedsGoesToConfirmed() {
        val config = CrashDetectionConfig(maxAcceptableGapNanos = 2_000_000_000L) // Prevent gap failure for simple test
        val detector = CrashDetector(config)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val t2 = t1 + (config.persistenceWindowNanos / 2)
        buffer.add(SensorData(t2, vector(9.8f)))
        
        val t3 = t1 + config.persistenceWindowNanos + 1L
        buffer.add(SensorData(t3, vector(9.8f)))
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMED, detector.currentState)
    }

    @Test
    fun testConfirmingPersistenceTimesOutGoesToMonitoring() {
        val config = CrashDetectionConfig(maxAcceptableGapNanos = 2_000_000_000L)
        val detector = CrashDetector(config)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val timeoutTime = t1 + config.persistenceTimeoutNanos + 1L
        buffer.add(SensorData(timeoutTime, vector(20.0f)))
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testLargeTimestampGapDoesNotFalselySatisfyPersistence() {
        val config = CrashDetectionConfig(maxAcceptableGapNanos = 500_000_000L)
        val detector = CrashDetector(config)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        // Huge gap of 2 seconds
        val hugeGapTime = t1 + 2_000_000_000L
        buffer.add(SensorData(hugeGapTime, vector(9.8f)))
        
        detector.processAccelerometerBuffer(buffer)
        // Because of the gap, it shouldn't confirm.
        // It stays in CONFIRMING until timeout, or returns to monitoring.
        assertEquals(DetectorState.CONFIRMING, detector.currentState)
    }

    @Test
    fun testOutOfOrderAndDuplicateTimestampsHandledSafely() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val t2 = t1 + 20_000_000L
        val t3 = t2 + 20_000_000L
        // Add out of order and duplicates
        buffer.add(SensorData(t3, vector(9.8f)))
        buffer.add(SensorData(t2, vector(9.8f))) // Out of order
        buffer.add(SensorData(t3, vector(9.8f))) // Duplicate
        
        detector.processAccelerometerBuffer(buffer)
        // Should not crash.
        assertEquals(DetectorState.CONFIRMING, detector.currentState)
    }

    @Test
    fun testConfirmedToAlertedToCooldownAndBlocksDuplicateTriggers() {
        val detector = CrashDetector(defaultConfig)
        
        detector.triggerExternalIncident(0L)
        assertEquals(DetectorState.CONFIRMED, detector.currentState)

        detector.markAlerted(100L)
        assertEquals(DetectorState.ALERTED, detector.currentState)

        // The NEXT process triggers the actual cooldown 
        val buffer = listOf(
            SensorData(300L, vector(9.8f)),
            SensorData(300L + 20_000_000L, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        detector.triggerExternalIncident(200L)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        val afterCooldown = 300L + 20_000_000L + defaultConfig.cooldownWindowNanos + 1L
        detector.processAccelerometerBuffer(listOf(
            SensorData(300L + 20_000_000L, vector(40.0f)),
            SensorData(afterCooldown, vector(9.8f))
        ))
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testThresholdConfigurationActuallyChangesDetectionBehavior() {
        val safeConfig = CrashDetectionConfig(accelMagnitudeThreshold = 100f)
        val safeDetector = CrashDetector(safeConfig)
        
        val buffer = listOf(
            SensorData(0L, vector(9.8f)),
            SensorData(20_000_000L, vector(40.0f))
        )
        
        safeDetector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.MONITORING, safeDetector.currentState)
        
        val sensitiveConfig = CrashDetectionConfig(accelMagnitudeThreshold = 20f)
        val sensitiveDetector = CrashDetector(sensitiveConfig)
        
        sensitiveDetector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, sensitiveDetector.currentState)
    }
}
