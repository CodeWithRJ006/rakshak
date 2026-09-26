package com.rakshak.core.detector

import com.rakshak.core.sensor.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

enum class DetectorState {
    MONITORING,
    IMPACT_CANDIDATE,
    CONFIRMING,
    CONFIRMED,
    ALERTED,
    COOLDOWN
}

class CrashDetector(
    private val config: CrashDetectionConfig = CrashDetectionConfig()
) {
    private val _stateFlow = MutableStateFlow(DetectorState.MONITORING)
    val stateFlow: StateFlow<DetectorState> = _stateFlow.asStateFlow()

    var currentState: DetectorState
        get() = _stateFlow.value
        private set(value) {
            _stateFlow.value = value
        }

    private var stateEnterTimestamp: Long = 0L
    private var lastProcessedTimestamp: Long = 0L
    private var lastMonitoredTimestamp: Long = 0L

    fun triggerExternalIncident(timestamp: Long) {
        if (currentState != DetectorState.COOLDOWN && currentState != DetectorState.ALERTED) {
            transitionTo(DetectorState.CONFIRMED, timestamp)
        }
    }

    fun markAlerted(timestamp: Long) {
        if (currentState == DetectorState.CONFIRMED) {
            transitionTo(DetectorState.ALERTED, timestamp)
        }
    }

    @Synchronized
    fun processAccelerometerBuffer(buffer: List<SensorData>) {
        if (buffer.size < 2) return

        val latestTimestamp = buffer.last().timestamp
        if (latestTimestamp <= lastProcessedTimestamp) return
        lastProcessedTimestamp = latestTimestamp

        when (currentState) {
            DetectorState.MONITORING -> evaluateMonitoring(buffer)
            DetectorState.IMPACT_CANDIDATE -> evaluateImpactCandidate(buffer, latestTimestamp)
            DetectorState.CONFIRMING -> evaluateConfirming(buffer, latestTimestamp)
            DetectorState.ALERTED -> {
                transitionTo(DetectorState.COOLDOWN, latestTimestamp)
            }
            DetectorState.COOLDOWN -> evaluateCooldown(latestTimestamp)
            DetectorState.CONFIRMED -> {}
        }
    }

    private fun evaluateMonitoring(buffer: List<SensorData>) {
        for (i in buffer.indices) {
            val sample = buffer[i]
            if (sample.timestamp <= lastMonitoredTimestamp) continue

            lastMonitoredTimestamp = sample.timestamp
            
            val mag = getMagnitude(sample.values)
            if (mag >= config.accelMagnitudeThreshold) {
                transitionTo(DetectorState.IMPACT_CANDIDATE, sample.timestamp)
                evaluateImpactCandidate(buffer, sample.timestamp)
                return
            }
        }
    }

    private fun evaluateImpactCandidate(buffer: List<SensorData>, latestTimestamp: Long) {
        if (currentState != DetectorState.IMPACT_CANDIDATE) return

        for (i in 1 until buffer.size) {
            val prev = buffer[i - 1]
            val curr = buffer[i]
            
            if (curr.timestamp < stateEnterTimestamp) continue
            if (curr.timestamp <= prev.timestamp) continue

            val jerk = calculateJerk(curr, prev)
            if (jerk >= config.jerkThreshold) {
                transitionTo(DetectorState.CONFIRMING, curr.timestamp)
                return
            }
        }

        if (latestTimestamp - stateEnterTimestamp >= config.candidateTimeoutNanos) {
            transitionTo(DetectorState.MONITORING, latestTimestamp)
            lastMonitoredTimestamp = latestTimestamp
        }
    }

    private fun evaluateConfirming(buffer: List<SensorData>, latestTimestamp: Long) {
        if (currentState != DetectorState.CONFIRMING) return

        val elapsedNanos = latestTimestamp - stateEnterTimestamp
        
        if (elapsedNanos >= config.persistenceTimeoutNanos) {
            transitionTo(DetectorState.MONITORING, latestTimestamp)
            lastMonitoredTimestamp = latestTimestamp
            return
        }

        var isResting = true
        var prevTimestamp = latestTimestamp

        for (i in buffer.indices.reversed()) {
            val sample = buffer[i]
            
            val timeToCheck = if (sample.timestamp <= stateEnterTimestamp) stateEnterTimestamp else sample.timestamp
            
            val gap = prevTimestamp - timeToCheck
            if (gap > config.maxAcceptableGapNanos) {
                isResting = false
                break
            }
            prevTimestamp = timeToCheck
            
            if (sample.timestamp <= stateEnterTimestamp) {
                break
            }
            
            if (sample.timestamp >= prevTimestamp && i != buffer.lastIndex) {
                continue 
            }

            val mag = getMagnitude(sample.values)
            if (mag > config.persistenceRestingThreshold) {
                isResting = false
                break
            }
        }

        if (isResting && elapsedNanos >= config.persistenceWindowNanos) {
            transitionTo(DetectorState.CONFIRMED, latestTimestamp)
        }
    }

    private fun evaluateCooldown(latestTimestamp: Long) {
        if (latestTimestamp - stateEnterTimestamp >= config.cooldownWindowNanos) {
            transitionTo(DetectorState.MONITORING, latestTimestamp)
            lastMonitoredTimestamp = latestTimestamp
        }
    }

    private fun transitionTo(newState: DetectorState, timestamp: Long) {
        currentState = newState
        stateEnterTimestamp = timestamp
        _stateFlow.value = newState
    }

    private fun getMagnitude(values: FloatArray): Float {
        return sqrt((values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()).toFloat()
    }

    private fun calculateJerk(curr: SensorData, prev: SensorData): Float {
        val dtSec = (curr.timestamp - prev.timestamp) / 1e9f
        if (dtSec <= 0f) return 0f

        val dx = curr.values[0] - prev.values[0]
        val dy = curr.values[1] - prev.values[1]
        val dz = curr.values[2] - prev.values[2]
        
        val dv = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        return dv / dtSec
    }
}
