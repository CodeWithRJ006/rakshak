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
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val restingTime = t1 + defaultConfig.persistenceWindowNanos + 1L
        buffer.add(SensorData(restingTime, vector(9.8f)))
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMED, detector.currentState)
    }

    @Test
    fun testConfirmingPersistenceTimesOutGoesToMonitoring() {
        val detector = CrashDetector(defaultConfig)
        
        val t0 = 0L
        val t1 = 20_000_000L
        val buffer = mutableListOf(
            SensorData(t0, vector(9.8f)),
            SensorData(t1, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.CONFIRMING, detector.currentState)

        val timeoutTime = t1 + defaultConfig.persistenceTimeoutNanos + 1L
        buffer.add(SensorData(timeoutTime, vector(20.0f)))
        
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.MONITORING, detector.currentState)
    }

    @Test
    fun testConfirmedToAlertedToCooldownAndBlocksDuplicateTriggers() {
        val detector = CrashDetector(defaultConfig)
        
        detector.triggerExternalIncident(0L)
        assertEquals(DetectorState.CONFIRMED, detector.currentState)

        detector.markAlerted(100L)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        detector.triggerExternalIncident(200L)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        val buffer = listOf(
            SensorData(300L, vector(9.8f)),
            SensorData(300L + 20_000_000L, vector(40.0f))
        )
        detector.processAccelerometerBuffer(buffer)
        assertEquals(DetectorState.COOLDOWN, detector.currentState)

        val afterCooldown = 100L + defaultConfig.cooldownWindowNanos + 1L
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
