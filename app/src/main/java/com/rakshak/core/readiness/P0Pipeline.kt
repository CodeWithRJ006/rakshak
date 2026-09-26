package com.rakshak.core.readiness

import com.rakshak.core.alert.AlertMode
import com.rakshak.core.alert.AlertSender
import com.rakshak.core.alert.LocationController
import com.rakshak.core.alert.SmsController
import com.rakshak.core.detector.CrashDetector
import com.rakshak.core.detector.DetectorState
import com.rakshak.core.mode.AppMode
import com.rakshak.core.mode.ModeManager
import kotlinx.coroutines.flow.collectLatest

class P0Pipeline(
    private val detector: CrashDetector,
    private val smsController: SmsController,
    private val locationController: LocationController,
    private val contactProvider: () -> List<String>
) {
    var currentIncidentHandled = false
        private set

    var alertCount = 0
        private set
        
    suspend fun collectStateFlow() {
        detector.stateFlow.collectLatest { state ->
            P0PipelineStatus.updateDetectorState(state)

            if (state == DetectorState.MONITORING) {
                currentIncidentHandled = false
            }

            if (state == DetectorState.CONFIRMED && !currentIncidentHandled) {
                currentIncidentHandled = true
                
                val mode = ModeManager.currentMode
                val alertMode = if (mode is AppMode.REAL) AlertMode.REAL_MODE else AlertMode.DEMO_MODE
                
                val alertSender = AlertSender(alertMode, smsController, locationController, contactProvider())
                val result = alertSender.sendEmergencyAlert()
                alertCount++
                
                P0PipelineStatus.updateLastAlertResult(result)
                detector.markAlerted(System.nanoTime())
            }
        }
    }
}

