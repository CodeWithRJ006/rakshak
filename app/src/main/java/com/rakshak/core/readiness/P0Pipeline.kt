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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rakshak.core.log.HMACIncidentLogger

class P0Pipeline(
    private val detector: CrashDetector,
    private val smsController: SmsController,
    private val locationController: LocationController,
    private val contactProvider: () -> List<String>,
    private val incidentLogger: HMACIncidentLogger? = null
) {
    var currentIncidentHandled = false
        private set

    var alertCount = 0
        private set
        
    private val pipelineScope = CoroutineScope(Dispatchers.Default)
        
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
                
                // HMAC Incident Logging
                pipelineScope.launch {
                    incidentLogger?.logIncident("CONFIRMED_CRASH|Result:$result")
                }
                alertCount++
                
                P0PipelineStatus.updateLastAlertResult(result)
                detector.markAlerted(System.nanoTime())
            }
        }
    }
}

