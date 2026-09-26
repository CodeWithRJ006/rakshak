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
            if (_stateFlow.value != value) {
                com.rakshak.core.log.SystemEventLogger.log("STATE", "${_stateFlow.value.name} -> ${value.name}")
            }
            _stateFlow.value = value
        }

    private var stateEnterTimestamp: Long = 0L
    private var lastProcessedTimestamp: Long = 0L
    private var lastMonitoredTimestamp: Long = 0L

    fun triggerExternalIncident(timestamp: Long) {
        if (currentState != DetectorState.COOLDOWN && currentState != DetectorState.ALERTED) {
            com.rakshak.core.log.SystemEventLogger.log("INJECT", "Synthetic Trace Injected: HIGH-G FORCES DETECTED")
            transitionTo(DetectorState.CONFIRMED, timestamp)
        }
    }

    fun markAlerted(timestamp: Long) {
        if (currentState == DetectorState.CONFIRMED) {
            transitionTo(DetectorState.ALERTED, timestamp)
        }
    }

    @Synchronized
    fun processSensorBuffers(accelBuffer: List<SensorData>, gyroBuffer: List<SensorData> = emptyList()) {
        if (accelBuffer.size < 2) return

        var maxTimestamp = Long.MIN_VALUE
        for (sample in accelBuffer) {
            if (sample.timestamp > maxTimestamp) {
                maxTimestamp = sample.timestamp
            }
        }
        val latestTimestamp = maxTimestamp

        if (latestTimestamp <= lastProcessedTimestamp) return
        lastProcessedTimestamp = latestTimestamp

        when (currentState) {
            DetectorState.MONITORING -> evaluateMonitoring(accelBuffer, latestTimestamp, gyroBuffer)
            DetectorState.IMPACT_CANDIDATE -> evaluateImpactCandidate(accelBuffer, latestTimestamp, gyroBuffer)
            DetectorState.CONFIRMING -> evaluateConfirming(accelBuffer, latestTimestamp)
            DetectorState.ALERTED -> {
                transitionTo(DetectorState.COOLDOWN, latestTimestamp)
            }
            DetectorState.COOLDOWN -> evaluateCooldown(latestTimestamp)
            DetectorState.CONFIRMED -> {}
        }
    }

    private fun evaluateMonitoring(accelBuffer: List<SensorData>, latestTimestamp: Long, gyroBuffer: List<SensorData>) {
        for (i in accelBuffer.indices) {
            val sample = accelBuffer[i]
            if (sample.timestamp <= lastMonitoredTimestamp) continue

            lastMonitoredTimestamp = sample.timestamp
            
            val mag = getMagnitude(sample.values)
            if (mag.isNaN()) continue
            
            if (mag >= config.accelMagnitudeThreshold) {
                com.rakshak.core.log.SystemEventLogger.log("SENSOR", "Impact spike detected (Mag: $mag G)")
                transitionTo(DetectorState.IMPACT_CANDIDATE, sample.timestamp)
                evaluateImpactCandidate(accelBuffer, latestTimestamp, gyroBuffer)
                return
            }
        }
    }

    private fun evaluateImpactCandidate(accelBuffer: List<SensorData>, latestTimestamp: Long, gyroBuffer: List<SensorData>) {
        if (currentState != DetectorState.IMPACT_CANDIDATE) return

        for (i in 1 until accelBuffer.size) {
            val prev = accelBuffer[i - 1]
            val curr = accelBuffer[i]
            
            if (curr.timestamp < stateEnterTimestamp) continue
            if (curr.timestamp <= prev.timestamp) continue

            if (curr.timestamp - stateEnterTimestamp > config.candidateTimeoutNanos) {
                break
            }

            val jerk = calculateJerk(curr, prev)
            if (jerk.isNaN()) continue
            
                        if (jerk >= config.jerkThreshold) {
                if (checkGyroCorroboration(curr.timestamp, gyroBuffer)) {
                    com.rakshak.core.log.SystemEventLogger.log("SENSOR", "Jerk + Gyro Corroboration success!")
                    transitionTo(DetectorState.CONFIRMING, curr.timestamp)
                    return
                } else {
                    com.rakshak.core.log.SystemEventLogger.log("SENSOR", "Gyro rejected (false positive)")
                }
            }
        }

        if (latestTimestamp - stateEnterTimestamp >= config.candidateTimeoutNanos) {
            transitionTo(DetectorState.MONITORING, latestTimestamp)
            lastMonitoredTimestamp = latestTimestamp
        }
    }

    private fun evaluateConfirming(accelBuffer: List<SensorData>, latestTimestamp: Long) {
        if (currentState != DetectorState.CONFIRMING) return

        val elapsedNanos = latestTimestamp - stateEnterTimestamp
        
        if (elapsedNanos >= config.persistenceTimeoutNanos) {
            transitionTo(DetectorState.MONITORING, latestTimestamp)
            lastMonitoredTimestamp = latestTimestamp
            return
        }

        var restingDuration = 0L
        var prevTimestamp = Long.MAX_VALUE
        var oldestValidRestingTimestamp = Long.MAX_VALUE
        var newestValidRestingTimestamp = Long.MIN_VALUE
        var isContinuousChain = true

        for (i in accelBuffer.indices.reversed()) {
            val sample = accelBuffer[i]
            if (sample.timestamp <= stateEnterTimestamp) break
            
            if (prevTimestamp == Long.MAX_VALUE) {
                prevTimestamp = sample.timestamp
                newestValidRestingTimestamp = sample.timestamp
                val mag = getMagnitude(sample.values)
                if (mag.isNaN() || mag > config.persistenceRestingThreshold) {
                    isContinuousChain = false
                    break
                }
                oldestValidRestingTimestamp = sample.timestamp
                continue
            }
            
            if (sample.timestamp >= prevTimestamp) {
                isContinuousChain = false
                break
            }
            
            val gap = prevTimestamp - sample.timestamp
            if (gap > config.maxAcceptableGapNanos) {
                isContinuousChain = false
                break
            }
            
            val mag = getMagnitude(sample.values)
            if (mag.isNaN() || mag > config.persistenceRestingThreshold) {
                isContinuousChain = false
                break
            }

            prevTimestamp = sample.timestamp
            oldestValidRestingTimestamp = sample.timestamp
        }

        if (oldestValidRestingTimestamp != Long.MAX_VALUE && newestValidRestingTimestamp != Long.MIN_VALUE) {
            val gapToStart = oldestValidRestingTimestamp - stateEnterTimestamp
            if (gapToStart <= config.maxAcceptableGapNanos) {
                restingDuration = newestValidRestingTimestamp - stateEnterTimestamp
            } else {
                restingDuration = newestValidRestingTimestamp - oldestValidRestingTimestamp
            }
        }

        if (isContinuousChain && restingDuration >= config.persistenceWindowNanos) {
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

    private fun checkGyroCorroboration(jerkTimestamp: Long, gyroBuffer: List<SensorData>): Boolean {
        if (config.gyroMagnitudeThreshold <= 0f) return true
        val startWindow = stateEnterTimestamp
        val endWindow = jerkTimestamp + 100_000_000L
        for (sample in gyroBuffer) {
            if (sample.timestamp in startWindow..endWindow) {
                val mag = getMagnitude(sample.values)
                if (!mag.isNaN() && mag >= config.gyroMagnitudeThreshold) {
                    return true
                }
            }
        }
        return false
    }

    private fun getMagnitude(values: FloatArray): Float {
        if (values.size < 3) return Float.NaN
        for (i in 0..2) {
            if (values[i].isNaN() || values[i].isInfinite()) return Float.NaN
        }
        return sqrt((values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()).toFloat()
    }

    private fun calculateJerk(curr: SensorData, prev: SensorData): Float {
        val dtSec = (curr.timestamp - prev.timestamp) / 1e9f
        if (dtSec <= 0f) return Float.NaN
        
        if (curr.values.size < 3 || prev.values.size < 3) return Float.NaN
        
        for (i in 0..2) {
            if (curr.values[i].isNaN() || curr.values[i].isInfinite()) return Float.NaN
            if (prev.values[i].isNaN() || prev.values[i].isInfinite()) return Float.NaN
        }

        val dx = curr.values[0] - prev.values[0]
        val dy = curr.values[1] - prev.values[1]
        val dz = curr.values[2] - prev.values[2]
        
        val dv = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        return dv / dtSec
    }
}




