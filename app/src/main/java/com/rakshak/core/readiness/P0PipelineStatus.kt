package com.rakshak.core.readiness

import com.rakshak.core.alert.AlertResult
import com.rakshak.core.detector.DetectorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object P0PipelineStatus {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    private val _detectorState = MutableStateFlow(DetectorState.MONITORING)
    val detectorState: StateFlow<DetectorState> = _detectorState.asStateFlow()
    
    private val _lastAlertResult = MutableStateFlow<AlertResult?>(null)
    val lastAlertResult: StateFlow<AlertResult?> = _lastAlertResult.asStateFlow()

    fun updateServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateDetectorState(state: DetectorState) {
        _detectorState.value = state
    }
    
    fun updateLastAlertResult(result: AlertResult?) {
        _lastAlertResult.value = result
    }
}
